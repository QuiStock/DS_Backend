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
CI verifica Java 25 no toolchain, workflow e imagens e executa a JVM ARM64 via QEMU.

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

Smoke funcional: `python scripts/smoke-routing.py`. Configure `SMOKE_AUTH_BASE_URL`,
`SMOKE_BACKEND_BASE_URL`, `SMOKE_EMAIL`, `SMOKE_PASSWORD` e `SMOKE_PLATFORM` (padrão `mobile`).
As bases incluem somente os prefixos públicos efetivamente usados. Execute com acesso
direto e depois com um gateway que remova os prefixos, usando o mesmo artefato.
O smoke cobre JWKS, login, consulta autenticada, refresh e logout sem imprimir tokens.
Health/probes e rollout serão tratados na entrega de health e no Infra.
Promova imagem e configuração de roteamento juntas; o rollback deve restaurar ambas.
