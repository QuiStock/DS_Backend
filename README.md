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
[`persistence-plan.md`](persistence-plan.md), e o plano para a futura API de autenticação NoSQL em
[`nosql-auth-api-plan.md`](nosql-auth-api-plan.md). A API NoSQL ficará em outro repositório e
será implementada quando esse repositório for informado.
