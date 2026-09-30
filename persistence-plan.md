# Persistência QuiStock

**Status:** persistência implementada no código e versionada com Flyway. O script completo para banco novo está em `database/quistock-schema.sql`. As migrações ainda precisam ser aplicadas no banco do ambiente de execução.

## Escopo implementado

- `GET /products` e `GET /products/{id}` leem o snapshot ERP de `product`, `category`, `batch`, `store` e `product_store`.
- A aplicação sincroniza o ERP no início e em intervalo configurável. Se ainda não houver sincronização concluída, a primeira leitura de produtos tenta sincronizar imediatamente.
- A sincronização grava o status e as contagens em `erp_sync`, usa chaves ERP estáveis e aplica o snapshot numa transação. Se a integração falhar, registra a falha e mantém o snapshot anterior. Uma resposta vazia não desativa um catálogo já existente.
- `product_store` preserva métricas por produto e filial: mínimo, prazo do fornecedor, vendas de 7/30 dias, preço, custo e última reposição. `batch` preserva lote, saldo, validade, data de entrada, preço e custo.
- `POST /flows/analyze` persiste métricas e contexto em `product_analysis` com `ml_execution_id = NULL`; `GET /flows` consulta essas análises.
- As rotas de ações persistem em `suggestion`, `suggestion_triage`, `suggestion_decision` e `suggestion_log`. A geração é idempotente por análise e as alterações de status, decisão e histórico usam transação.
- `GET /branches`, `/dashboard/summary` e `/erp-integration/status` consultam a projeção e os dados persistidos.
- Não foram criadas rotas novas. Chat/ML e operações de usuário continuam fora do código atual.

## Mapeamento do ERP

| Campo da API | Fonte | Tabela/coluna |
| --- | --- | --- |
| `current_stock` | soma de `quantidade` dos lotes ativos | `batch.current_balance` |
| `minimum_stock` | maior `estoque_minimo` válido do produto na filial | `product_store.minimum_stock_quantity` |
| `sales_7d`, `sales_30d` | soma dos agregados do ERP por lote | `product_store.sales_7d`, `sales_30d` |
| `supplier_lead_time` | maior `lead_time_dias` válido na filial | `product_store.supplier_lead_time_days` |
| `price`, `cost` | lote mais recente por `data_entrada` | `product_store.sale_price`, `unit_cost`; valor bruto também fica em `batch` |
| `last_restock` | `data_entrada` do lote mais recente | `product_store.last_restock_at`; valor bruto também fica em `batch.entry_date` |
| `expiration_days` | menor validade entre lotes com saldo positivo | `batch.expiration_date` |
| ID público | `codigo_produto_erp:codigo_filial_erp` | `product.erp_id:store.erp_id` |

O endpoint ERP atual só fornece agregados de vendas; por isso, não cria linhas artificiais em `sale`/`sale_item`. Essas tabelas podem receber eventos quando o ERP fornecer esse detalhe. `NUMERIC(14,3)` é preservado como decimal no contrato público.

## Ajustes do schema

- Adicionada `product_store` para valores que variam por filial.
- Adicionados a `batch` os campos `entry_date`, `sale_price` e `unit_cost`; validade e preço do produto podem ser nulos quando o ERP não os fornece. `batch_number` deixou de ser único globalmente.
- `product_analysis.ml_execution_id` agora aceita `NULL`, mantendo a FK para o uso futuro de ML.
- `store.region_id` e `store.created_by_id` aceitam `NULL`, pois o endpoint ERP atual não informa região nem usuário criador.
- `suggestion_triage.employee_id`, `suggestion_decision.manager_id` e a autoria da sugestão podem ficar sem ator até integrar a sessão NoSQL. As FKs permanecem. A geração atual registra origem `EMPLOYEE`; depois a origem e o autor devem vir da identidade validada.
- Os tipos enum do schema foram preservados; `flow_type_catalog` recebe `HIGH`, `MEDIUM` e `LOW`.

## Migrações e execução

- `V1__initial_quistock_schema.sql` cria o schema completo atualizado em um banco vazio.
- `V2__erp_projection_and_rules_persistence.sql` atualiza uma instalação anterior do SQL enviado.
- Flyway está configurado com baseline na versão 1 para bases preexistentes sem histórico. Em ambiente de execução, confirme que o schema não contém tabelas de outra aplicação antes de habilitar esse baseline automático.
- Ajuste `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ERP_API_BASE_URL` e `ERP_API_PRODUCTS_PATH`. A sincronização roda a cada 15 minutos por padrão; configure `ERP_SYNC_FIXED_DELAY_MS` ou desligue com `ERP_SYNC_ENABLED=false`.

## Integrações futuras

- A API ainda não autentica os usuários nem resolve o identificador da sessão NoSQL para `user_account.id`. Até isso ser ligado, as FKs de ator ficam nulas; não enviar `user_id` livremente no corpo da requisição.
- A origem de uma sugestão gerada sem identidade autenticada usa provisoriamente `EMPLOYEE`. Ao integrar autenticação e papéis, derive `EMPLOYEE`/`MANAGER` do contexto autenticado.
- O ERP atual não fornece endereço/região de filial nem eventos individuais de venda. Os campos sem fonte permanecem nulos e não há como persistir esses dados até o ERP oferecê-los.
- Não foi executada a migração em um banco externo. O comando Gradle de compilação também não conseguiu iniciar neste ambiente: o daemon falhou ao abrir conexão loopback local. Portanto, a execução real das migrações contra PostgreSQL ainda precisa ser validada no ambiente com acesso ao banco.