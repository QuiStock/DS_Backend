# Persistência QuiStock

**Status:** persistência ERP, integração de identidade e gerenciamento de usuários implementados no backend. O SQL completo para banco novo está em `database/quistock-schema.sql`; bancos existentes recebem as mudanças por Flyway. DS_Auth continua responsável por login, refresh e logout, com sessões/refresh tokens em NoSQL conforme `nosql-auth-api-plan.md`.

## Comportamento persistido

- A API sincroniza os registros ERP de `/products` para PostgreSQL e serve os dados salvos por `GET /products`, `GET /products/{id}` e `GET /branches`.
- Cada registro ERP representa um lote. O snapshot é agrupado por produto e filial, com chave estável ERP e transação. Um snapshot vazio não desativa um catálogo existente; uma falha preserva o snapshot anterior e registra o erro em `erp_sync`.
- `product_store` guarda os valores que variam por filial. `batch` guarda saldo, lote, validade e os valores ERP por lote.
- O contrato acordado exige `data_validade`, `preco` e `custo` em todos os lotes ERP. Preço e custo devem ser valores numéricos não negativos. O código de produto usado como SKU deve ter até 100 caracteres. Dados que descumpram essas regras são rejeitados antes da persistência do snapshot.
- `codigo_produto_erp` é o SKU; `id` do ERP é preferido como identificador de lote, com `num_lote` como fallback. O número de lote pode se repetir porque o identificador salvo combina produto, filial e identificador de lote.
- `POST /flows/analyze` persiste análise e contexto em `product_analysis`; `ml_execution_id` permanece nulo até a integração ML ser definida. `GET /flows` lê essas análises.
- A geração e atualização de ações persistem em `suggestion`, `suggestion_triage`, `suggestion_decision` e `suggestion_log`, com histórico transacional. A geração continua baseada nas regras atuais: `HIGH` gera pedido, `LOW` gera promoção, `MEDIUM` não gera ação. `MONITOR` foi adicionado ao enum/schema para compatibilidade com o domínio, mas ainda não é gerado pelo fluxo atual de ML.
- `GET /dashboard/summary` e `GET /erp-integration/status` consultam dados persistidos e o estado da integração.
- `GET /profile` consulta a conta SQL pelo `sub` do JWT. `ADMIN` gerencia Gerentes em `/managers`; `GERENTE` gerencia apenas seus subordinados diretos em `/team-members`.
- Os usuários provisionados recebem senhas iniciais, salvas como BCrypt cost 12; o hash e a senha nunca são devolvidos. Não há rota pública para cadastrar o primeiro `ADMIN`; ele precisa ser provisionado com segurança antes do uso de `/managers`.
- `FUNCIONARIO` tem uma única filial ativa em `user_store`; trocas fecham o vínculo anterior e preservam o histórico. `GERENTE_REGIONAL` tem uma região ativa, e cada região pode ter um único Gerente Regional ativo. Desativações encerram as atribuições ativas.
- Ao desativar um `GERENTE`, o ADM informa um Gerente ativo substituto. Em uma transação, `created_by_id` dos subordinados diretos é transferido ao substituto e o Gerente anterior é desativado. Não foi criada a coluna `manager_user_id`.
- `/regions` consulta as regiões recebidas do ERP. `/branches` continua como rota de filiais somente leitura e mantém `id` com o código ERP; adiciona `store_id` interno para cadastros e atribuições.
- O endpoint `/chat` existente é apenas um protótipo baseado em regras; não é a funcionalidade de chat com IA/ML planejada. Notificações e ajustes do dashboard ficam fora deste escopo.

## Mapeamento do ERP

| Campo da API | Fonte ERP | Persistência |
| --- | --- | --- |
| `current_stock` | Soma de `quantidade` dos lotes ativos | `batch.current_balance` |
| `minimum_stock` | Maior `estoque_minimo` válido para produto/filial | `product_store.minimum_stock_quantity` |
| `sales_7d`, `sales_30d` | Soma dos agregados ERP por lote | `product_store.sales_7d`, `sales_30d` |
| `supplier_lead_time` | Maior `lead_time_dias` válido na filial | `product_store.supplier_lead_time_days` |
| `price`, `cost` | Valores do lote mais recente por `data_entrada` | `product_store.sale_price`, `unit_cost`; cada lote também mantém seus valores |
| `last_restock` | `data_entrada` do lote mais recente | `product_store.last_restock_at` |
| `expiration_days` | Validade mais próxima entre lotes com saldo positivo | `batch.expiration_date` |
| ID público | Código de produto e código/nome da filial | `product.erp_id`, `store.erp_id` |

O ERP fornece agregados de venda, não eventos individuais; portanto, a sincronização não inventa linhas em `sale`/`sale_item`. O `region_id` externo é salvo em `region_type.code`, associado a `store.region_id` pelo ID interno e exposto em `/regions`. Como o ERP não envia nome da região, `region_type.name` inicialmente replica o código. Campos de endereço e coordenadas continuam não preenchidos sem fonte.

## Identidade e autoria

- As rotas de negócio aceitam o cookie HTTP-only `access_token` da Auth ou `Authorization: Bearer <access_token>`. Mutations autenticadas por cookie exigem o token CSRF de `GET /csrf`; Bearer mantém compatibilidade sem CSRF.
- O resource server valida assinatura, emissor, audiência, validade, `sub` numérico positivo e claim `email`. `sub` corresponde a `user_account.id` no PostgreSQL. A URL JWKS, o `issuer` e a audiência são configuráveis.
- A API usa o `sub` autenticado — nunca um ID enviado livremente no corpo — para preencher `suggestion.created_by_id`, `suggestion_triage.employee_id`, `suggestion_decision.manager_id` e `suggestion_log.user_id`.
- Usuários e `password_hash` continuam no SQL. O NoSQL planejado serve para sessões e refresh tokens. O serviço de autenticação deve emitir o mesmo contrato JWT; a arquitetura para esse serviço está em `nosql-auth-api-plan.md`.
- `store.created_by_id` pode continuar nulo quando a filial é criada pela sincronização do ERP, pois esse registro de sistema não tem um usuário humano como autor.

## Schema e migrações

- `database/quistock-schema.sql` é o schema consolidado para instalação nova. Inclui `product_store`, enum `suggestion_type.MONITOR`, os papéis de usuário, e-mail normalizado único, no máximo uma filial ativa por usuário, SKU limitado a 100 e campos ERP obrigatórios para preço, custo e validade.
- `V1__initial_quistock_schema.sql` representa o baseline histórico; não deve ser editada em bancos que já registraram essa migração.
- `V2__erp_projection_and_rules_persistence.sql` adiciona a projeção por filial e as regras de persistência ERP.
- `V3__add_monitor_suggestion_type.sql` adiciona `MONITOR`; a migração separada garante que o valor do enum esteja confirmado antes de ser usado pela restrição seguinte.
- `V4__enforce_erp_contract.sql` valida dados existentes e então aplica as obrigatoriedades do contrato ERP e a restrição de `MONITOR`. A migração falha com mensagem clara se encontrar registros históricos sem os valores obrigatórios ou com SKU maior que 100; corrija esses dados antes de reaplicar.
- `V5__manager_user_management.sql` migra `REPOSITOR` para `FUNCIONARIO`, garante papéis `ADMIN`, `GERENTE`, `GERENTE_REGIONAL` e `FUNCIONARIO`, normaliza e-mail, e exige no máximo uma filial ativa por usuário. Antes de aplicar, corrija e-mails duplicados após trim/lowercase, usuários ativos com múltiplas filiais e `FUNCIONARIO`/`GERENTE_REGIONAL` ativos sem atribuição.
- Flyway usa baseline na versão 1 para um schema preexistente. Antes de habilitar o baseline, confirme que o banco é o schema QuiStock esperado.

## Configuração e validação operacional

Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ERP_API_BASE_URL`, `ERP_API_PRODUCTS_PATH`, `AUTH_JWT_ISSUER`, `AUTH_JWT_JWK_SET_URI`, `AUTH_JWT_AUDIENCE`, `COOKIE_SECURE` e `CORS_ALLOWED_ORIGINS`; há exemplos em `.env.example`. Para cookie, Auth e Core precisam compartilhar host/gateway ou domínio pai, e Auth deve usar `Path=/`. A sincronização ERP roda a cada 15 minutos por padrão e pode ser ajustada por `ERP_SYNC_FIXED_DELAY_MS` ou desligada por `ERP_SYNC_ENABLED=false`.

As migrações foram atualizadas no repositório, mas precisam ser aplicadas em um PostgreSQL de destino para validar o estado daquele banco. A execução local do Gradle também precisa completar para confirmar testes e estilo; o daemon apresentou falha ao estabelecer uma conexão loopback neste ambiente.
