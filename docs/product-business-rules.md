# Regras de negócio de produtos e análise de fluxos

## Identidade e quantidade do produto

Cada SKU identifica um produto com apresentação e quantidade fechadas. Leite 1L e
Leite 500 mL são produtos distintos, com SKUs distintos. Dez itens de Leite 1L e dez
itens de Leite 500 mL representam dez unidades dos respectivos produtos; o sistema
não converte essas quantidades para litros, mililitros, quilos ou outra medida comum.

Unidade de medida não é um atributo obrigatório do produto nem um requisito para
sincronizar o ERP, calcular estoque, classificar vendas ou identificar riscos.
Não adicionar dependência de `product.unit_of_measure` nessas operações.

## Produtos comparáveis na análise

O grupo de comparação contém produtos ativos da mesma categoria SQL e da mesma
filial autorizada, com vínculo ativo à filial e `sales_7d` conhecido. O próprio
produto analisado faz parte do grupo. Apresentações diferentes podem participar do
mesmo grupo quando pertencem à mesma categoria e filial.

A classificação usa a quantidade vendida de cada SKU, sem conversão de medidas e
sem filtrar os pares por tamanho da embalagem ou unidade de medida. Permanecem os
percentis existentes e o mínimo de cinco produtos comparáveis. Vendas desconhecidas,
categoria ausente ou pares insuficientes podem impedir a classificação; unidade de
medida ausente não pode causar `INSUFFICIENT_DATA`.

## Integração ERP e compatibilidade de banco

O campo `unidade_medida` pode continuar no payload recebido do ERP para
compatibilidade, mas não é persistido nem utilizado pela análise. Não é necessário
preencher esse campo ou ressincronizar o ERP para obter unidades antes de analisar.

A migração histórica V7 e o script de criação do banco mantêm a coluna opcional
`product.unit_of_measure`. Não alterar migrações já publicadas para remover essa
coluna: isso afetaria o histórico do Flyway. A aplicação funciona sem ler ou escrever
a coluna, esteja ela presente ou ausente. Isso não dispensa as demais alterações da
V7. Esta correção não exige nova migração nem remoção de dados legados.

Análises já persistidas são snapshots e não são recalculadas por esta alteração.
Novas execuções manuais ou agendadas passam a selecionar pares por categoria e filial.
