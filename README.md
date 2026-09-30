# DS_Backend

## Persistência PostgreSQL

As rotas de produtos, análises, ações, filiais, dashboard e status ERP usam PostgreSQL. O
Flyway aplica as migrações em `src/main/resources/db/migration` ao iniciar a aplicação. Para
instalação nova, crie o banco indicado em `DB_URL` e inicie a API. Em uma base já criada pelo
script SQL anterior, o Flyway registra o baseline na versão 1 e aplica a migração incremental
da versão 2.

O script completo para execução manual está em [`database/quistock-schema.sql`](database/quistock-schema.sql).
Use-o em banco novo; não o reaplique sobre tabelas existentes. Copie `.env.example` para `.env`
e ajuste as credenciais e o endereço do ERP.

O ERP é sincronizado na inicialização e, por padrão, a cada 15 minutos. Se a primeira leitura
de produtos ocorrer antes da sincronização agendada, `GET /products` tenta carregar o primeiro
snapshot. Configure o intervalo com `ERP_SYNC_FIXED_DELAY_MS` ou desative o processo com
`ERP_SYNC_ENABLED=false`.

O endpoint ERP atual fornece vendas agregadas de 7 e 30 dias; elas são salvas em `product_store`.
Como ele não envia eventos de venda individuais, as tabelas `sale` e `sale_item` ficam prontas
para uma integração ERP que disponibilize esses eventos. Os IDs de autor das ações permanecem
nulos até a integração da sessão NoSQL com `user_account.id`; as FKs continuam definidas no SQL.
