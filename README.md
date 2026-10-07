# DS_Backend

## Persistência PostgreSQL

As rotas de produtos, análises, ações, filiais, dashboard e status ERP usam PostgreSQL. O
Flyway aplica as migrações em `src/main/resources/db/migration` ao iniciar a aplicação. Para
instalação nova, crie o banco indicado em `DB_URL` e inicie a API. Em uma base já criada pelo
script SQL anterior, o Flyway registra o baseline na versão 1 e aplica a migração incremental
das versões seguintes. `V4` exige SKU de até 100 caracteres e preço, custo e validade em cada
lote já sincronizado; corrija dados antigos que violem essas regras antes de migrar.

O script completo para execução manual está em [`database/quistock-schema.sql`](database/quistock-schema.sql).
Use-o em banco novo; não o reaplique sobre tabelas existentes. Copie `.env.example` para `.env`
e ajuste as credenciais, o endereço do ERP e as variáveis `AUTH_JWT_*` para a API de autenticação.

O ERP é sincronizado na inicialização e, por padrão, a cada 15 minutos. Se a primeira leitura
de produtos ocorrer antes da sincronização agendada, `GET /products` tenta carregar o primeiro
snapshot. Configure o intervalo com `ERP_SYNC_FIXED_DELAY_MS` ou desative o processo com
`ERP_SYNC_ENABLED=false`.

O endpoint ERP atual fornece vendas agregadas de 7 e 30 dias; elas são salvas em `product_store`.
Como ele não envia eventos de venda individuais, as tabelas `sale` e `sale_item` ficam prontas
para uma integração ERP que disponibilize esses eventos. Todas as rotas exigem um access token
JWT. Para as ações, `sub` deve ser o ID numérico de `user_account.id`; ele é salvo como autor no
SQL. A API verifica a assinatura pelo JWKS, issuer, audience, validade e os claims `sub` e `email`.

O contrato da API está em [`api-contract.md`](api-contract.md), o plano da persistência atual em
[`persistence-plan.md`](persistence-plan.md). A API de autenticação está no repositório DS_Auth.

## Java 25 e roteamento por ambiente

Use Eclipse Temurin JDK 25 e o wrapper versionado: `./gradlew clean check bootJar`
(Windows: `./gradlew.bat clean check bootJar`). Configure `JAVA_HOME` e selecione o mesmo
JDK na IDE para o projeto e para Gradle. Não é necessário instalar Gradle globalmente.
Os testes de integração exigem Docker; não devem ser omitidos na validação da PR.
O CI usa Java 25 e executa a JVM ARM64 via QEMU.

As rotas usam contexto raiz por padrão. `SERVER_PORT` controla a porta interna;
`SERVER_SERVLET_CONTEXT_PATH` permite um contexto temporário durante a migração.
O gateway define e remove prefixos públicos, por exemplo `/api/auth-service` e
`/api/core-service`. Configure `AUTH_COOKIE_PATH` com o prefixo público da Auth
(`/` no acesso direto), independentemente do contexto interno. Os cookies continuam
configuráveis por `AUTH_COOKIE_DOMAIN`, `AUTH_COOKIE_SECURE` e `AUTH_COOKIE_SAME_SITE`.

Configure o mesmo `AUTH_JWT_ISSUER` e `AUTH_JWT_AUDIENCE` nas duas APIs. Backend recebe
`AUTH_JWT_JWK_SET_URI` completo, por exemplo `http://localhost:8090/.well-known/jwks.json`
para a Auth local na porta 8090; nenhum prefixo é concatenado pela aplicação.
Preserve o issuer ao migrar o roteamento. Em produção forneça explicitamente as URLs,
credenciais de banco e configurações JWT. ERP exige `ERP_API_BASE_URL` HTTP(S) quando
`ERP_SYNC_ENABLED=true`; `ERP_API_PRODUCTS_PATH` é um path absoluto (padrão `/products`).
O exemplo local espera um ERP em `http://localhost:8091`; os testes usam fixture HTTP local.

Promova imagem e configuração de roteamento juntas; o rollback deve restaurar ambas.

## Health público

`GET /health` e `GET /health/readiness` dispensam autenticação e verificam SQL,
JWKS e ERP necessário. Retornam HTTP 200 quando disponíveis e HTTP 503 quando
indisponíveis, com status e detalhes seguros da falha. Nenhum outro endpoint
Actuator além de health é exposto. O contexto configurado se aplica às rotas.

Configure a readiness probe em `/health/readiness` e a liveness probe em
`/health/liveness`. Liveness verifica somente o estado da aplicação, sem acessar
SQL, JWKS ou ERP; uma falha externa deve retirar a instância do tráfego, sem
provocar reinícios em cascata. Esses endpoints ficam disponíveis após a aplicação
iniciar; não contornam falhas de conexão durante a inicialização do Flyway.

Os resultados das dependências ficam em cache por cinco segundos por padrão. Configure
`HEALTH_CACHE_TTL` (por exemplo `10s` ou `0ms` para desativar), ou sobrescreva
`MANAGEMENT_ENDPOINT_HEALTH_CACHE_TIME_TO_LIVE`. Uma mudança de estado pode levar
até o TTL configurado para aparecer. Chamadas concorrentes compartilham uma única
verificação em andamento por instância, inclusive se um chamador exceder seu timeout.
O limite de espera de cada chamada é `HEALTH_TIMEOUT_MS` (4000 ms por padrão,
máximo 30000). SQL usa timeout de query de dois segundos; a obtenção de conexão
segue o timeout do pool. Trabalho bloqueado permanece compartilhado até terminar,
sem abrir novas verificações para substituir as que ainda estão executando.

O pool PostgreSQL usa no máximo três conexões por instância e não exige um mínimo
de conexões ociosas. `DB_POOL_MAX_SIZE` e `DB_POOL_MIN_IDLE` permitem ajustar esses
limites; as variáveis padrão `SPRING_DATASOURCE_HIKARI_*` também podem sobrescrevê-los.
Dimensione o total de todas as réplicas, sobreposição de
deploys e outros clientes para caber nos slots disponíveis no PostgreSQL. Evite
pool de uma conexão: o Flyway pode precisar de outra durante a inicialização.

Auth verifica as permissões de leitura das tabelas de autenticação no PostgreSQL,
um primary MongoDB de replica set e uma leitura em transação na coleção de refresh.
Não emite tokens nem altera dados. Backend verifica SQL, busca diretamente o JWKS
configurado e exige uma chave pública RSA utilizável para RS256, além de uma leitura
HTTP do ERP. As chamadas HTTP têm timeout de dois segundos e não seguem redirects.
`HEALTH_ERP_REQUIRED=true` é o padrão, independente de `ERP_SYNC_ENABLED`. Use false
somente em um deployment cujas funcionalidades realmente não dependam do ERP.

A suíte verifica as probes HTTP, o cache e a concorrência com dependências controladas. O smoke do ambiente implantado continua sendo manual.

Smoke de ambiente a executar posteriormente: consultar a rota sem token, exigir 200 com dependências disponíveis e
503 ao interromper individualmente SQL, MongoDB, JWKS ou ERP necessário. Restaurar
cada dependência deve recuperar 200. Repetir pelo gateway e com contexto configurado.
JWT em cache não deve mascarar falha do JWKS. Complementar o health com login,
refresh, logout e consulta autenticada para validar o contrato funcional completo.
