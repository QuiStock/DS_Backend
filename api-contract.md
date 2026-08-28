# Contrato de API — QuiStock

Versão: 1.1.0  
Última atualização: 28/08/2026

## Base URL

Local:

http://localhost:8080/api

---
## Origem dos dados

Os dados retornados por este endpoint têm origem em uma API externa de ERP.

No MVP, a origem é uma MockAPI contendo dados falsos, porém estruturados como se fossem dados reais de um ERP.

O endpoint externo utilizado pelo backend é `GET /produto`. Apesar do nome do endpoint
estar no singular, cada registro retornado representa um lote de um produto, e não um
produto consolidado.

O backend do QuiStock é responsável por consumir essa API externa, tratar os campos,
agrupar os lotes por produto e filial e retornar um modelo padronizado de produtos para
o frontend. O frontend não precisa conhecer o modelo de lotes do ERP.

---

# 1. Produtos

## 1.1 Listar produtos

Retorna os produtos consolidados pelo backend a partir dos lotes vindos da MockAPI.

Método:

GET /produtos

Resposta 200:

[
  {
    "id": "PROD001:Loja Santana",
    "sku": "PROD001",
    "nome": "Leite Integral 1L",
    "categoria": "Laticinios",
    "estoque_atual": 121,
    "estoque_minimo": 40,
    "vendas_7d": 50,
    "vendas_30d": 200,
    "dias_validade": 12,
    "lead_time_fornecedor": 5,
    "preco": 7.99,
    "custo": 5.20,
    "ultima_reposicao": "2026-08-20T00:00:00Z",
    "status": true,
    "filial": "Loja Santana"
  }
]

### Consolidação dos lotes

Cada registro recebido de `GET /produto` representa um lote. O backend consolida os
registros antes de montar a resposta pública.

A chave de agrupamento é composta por:

```text
codigo_produto_erp + filial
```

O mesmo produto em filiais diferentes resulta em produtos consolidados diferentes.
O agrupamento não é feito somente pelo nome ou somente pelo código do produto.

Regras de transformação:

- `sku`: recebe `codigo_produto_erp`.
- `nome`: recebe `nome_produto`.
- `categoria`: recebe `categoria`.
- `filial`: recebe `filial`.
- `estoque_atual`: soma `quantidade` de todos os lotes do grupo.
- `vendas_7d`: soma `vendas_7d` de todos os lotes do grupo.
- `vendas_30d`: soma `vendas_30d` de todos os lotes do grupo.
- `dias_validade`: calcula os dias até a data de validade mais próxima entre os lotes com quantidade maior que zero.
- `estoque_minimo`: não é somado. Quando os lotes apresentam valores diferentes, é usado o maior valor válido e a inconsistência é registrada no log.
- `lead_time_fornecedor`: recebe `lead_time_dias` e não é somado. Quando há valores diferentes, é usado o maior valor válido e a inconsistência é registrada no log.
- `preco`: vem do lote mais recente segundo `data_entrada`.
- `custo`: vem do lote mais recente segundo `data_entrada`.
- `status`: é `true` quando o estoque consolidado é maior que zero.
- `id`: nesta etapa sem persistência, é gerado de forma determinística como `codigo_produto_erp:filial`.

Campos internos do lote, como `num_lote`, `certificado_qualidade`, `data_entrada`,
`data_validade` e `unidade_medida`, não são expostos nessa rota pública.

Datas podem ser recebidas como data ISO-8601, timestamp Unix em segundos ou timestamp
Unix em milissegundos. Valores numéricos podem ser recebidos como números JSON ou como
strings numéricas. O backend normaliza esses formatos antes da consolidação.

Um lote vencido pode resultar em `dias_validade` igual a zero ou negativo. A implementação
utiliza o número real de dias entre a data atual e o vencimento, portanto datas passadas
resultam em valores negativos. Quando não existe lote disponível com data de validade,
o campo é retornado como `null`.

### Filtros opcionais

A rota de listagem de produtos pode receber filtros via query params. Os filtros são
aplicados somente depois da consolidação dos lotes.

Exemplos:

GET /produtos?filial=Loja Centro  
GET /produtos?categoria=Laticinios  
GET /produtos?status=true  

Parâmetros possíveis:

- filial: filtra produtos por loja/filial
- categoria: filtra produtos por categoria
- status: filtra produtos ativos ou inativos


---

## 1.2 Buscar produto por ID

Método:

GET /produtos/{id}

Exemplo:

GET /produtos/1

Resposta 200:

{
  "id": "1",
  "sku": "LAT001",
  "nome": "Leite Integral 1L",
  "categoria": "Laticínios",
  "estoque_atual": 51,
  "estoque_minimo": 58,
  "vendas_7d": 36,
  "vendas_30d": 150,
  "dias_validade": 0,
  "lead_time_fornecedor": 3,
  "preco": 7.90,
  "custo": 4.50,
  "status": true,
  "filial": "Loja Centro"
}

---

# 2. Fluxos

O fluxo é a classificação do produto após a análise.

Tipos possíveis:

- ALTO: vai ter ruptura
- MEDIO: está adequado
- BAIXO: vai sobrar ou vencer

## Observação sobre a classificação

No MVP, a classificação ALTO, MEDIO e BAIXO será feita por um classificador simples dentro do backend.

O classificador utilizará campos como:

- estoque_atual
- vendas_7d
- vendas_30d
- dias_validade
- lead_time_fornecedor

A arquitetura permite substituir essa lógica futuramente por um modelo de Machine Learning treinado.

---

## 2.1 Analisar um produto

Método:

POST /fluxos/analisar

Body:

{
  "produto_id": "1"
}

Resposta 201:

{
  "id": "101",
  "produto_id": "1",
  "produto_nome": "Leite Integral 1L",
  "tipo_fluxo": "BAIXO",
  "status": "ANALISADO",
  "motivo": "Produto vencido ou próximo do vencimento.",
  "media_vendas_diaria": 5.14,
  "cobertura_estoque_dias": 9.92,
  "dias_validade": 0,
  "lead_time_fornecedor": 3,
  "data_analise": "2026-07-30T11:47:00-03:00"
}

---

## 2.2 Listar fluxos analisados

Método:

GET /fluxos

Resposta 200:

[
  {
    "id": "101",
    "produto_id": "1",
    "produto_nome": "Leite Integral 1L",
    "tipo_fluxo": "BAIXO",
    "status": "ANALISADO",
    "motivo": "Produto vencido ou próximo do vencimento."
  }
]

### Filtros opcionais

A rota de listagem de fluxos poderá receber filtros via query params.

Exemplos:

GET /fluxos?tipo_fluxo=BAIXO  
GET /fluxos?produto_id=1  
GET /fluxos?status=ANALISADO  

Parâmetros possíveis:

- tipo_fluxo: ALTO, MEDIO ou BAIXO
- produto_id: filtra fluxos de um produto específico
- status: filtra pelo status da análise

---

# 3. Ações

A ação é o que o sistema recomenda fazer com base no fluxo.

Tipos possíveis:

- PROMOCAO
- PEDIDO_ESTOQUE
- MONITORAR

Status possíveis:

- SUGERIDA
- APROVADA
- RECUSADA
- CONCLUIDA

## Regras de geração de ações

- Fluxo ALTO: gera PEDIDO_ESTOQUE
- Fluxo MEDIO: gera MONITORAR
- Fluxo BAIXO: gera PROMOCAO


---

## 3.1 Gerar ações para um fluxo

Método:

POST /acoes/gerar

Body:

{
  "fluxo_id": "101"
}

Resposta 201:

{
  "fluxo_id": "101",
  "acoes_geradas": [
    {
      "id": "501",
      "tipo_acao": "PROMOCAO",
      "status": "SUGERIDA",
      "justificativa": "Produto com risco de vencimento ou sobra em estoque."
    }
  ]
}


---

## 3.2 Listar ações

Método:

GET /acoes

Resposta 200:

[
  {
    "id": "501",
    "fluxo_id": "101",
    "produto_nome": "Leite Integral 1L",
    "tipo_acao": "PROMOCAO",
    "status": "SUGERIDA",
    "justificativa": "Produto com risco de vencimento."
  }
]
### Filtros opcionais

A rota de listagem de ações poderá receber filtros via query params.

Exemplos:

GET /acoes?status=SUGERIDA  
GET /acoes?tipo_acao=PROMOCAO  
GET /acoes?fluxo_id=101  

Parâmetros possíveis:

- status: SUGERIDA, APROVADA, RECUSADA ou CONCLUIDA
- tipo_acao: PROMOCAO, PEDIDO_ESTOQUE ou MONITORAR
- fluxo_id: filtra ações geradas a partir de um fluxo específico
---

## 3.3 Atualizar status de uma ação

Método:

PATCH /acoes/{id}/status

Exemplo:

PATCH /acoes/501/status

Body:

{
  "status": "APROVADA"
}

Resposta 200:

{
  "id": "501",
  "status": "APROVADA"
}

---

# 4. Chatbot

## 4.1 Enviar mensagem para o chatbot

Método:

POST /chat

Body:

{
  "usuario_id": "1",
  "mensagem": "Quais produtos precisam de promoção?"
}

Resposta 200:

{
  "resposta": "Hoje existem produtos com risco de vencimento. A principal sugestão é criar promoção para Leite Integral 1L.",
  "agente_responsavel": "agente_operacional",
  "dados_referenciados": [
    {
      "produto_id": "1",
      "nome": "Leite Integral 1L",
      "tipo_fluxo": "BAIXO",
      "acao_sugerida": "PROMOCAO"
    }
  ]
}

---

# 5. Filiais

## 5.1 Listar filiais

Método:

GET /filiais

Resposta 200:

[
  {
    "id": "1",
    "nome": "Loja Centro",
    "endereco": "Av. Paulista, 1000",
    "cidade": "São Paulo",
    "estado": "SP",
    "latitude": -23.561684,
    "longitude": -46.655981
  }
]

---

# 6. Dashboard

Observação:
Os números retornados pelo dashboard são calculados a partir dos produtos analisados, fluxos gerados e ações sugeridas pelo sistema.

## 6.1 Buscar resumo do dashboard

Método:

GET /dashboard/resumo

Resposta 200:

{
  "total_produtos": 5,
  "produtos_alto": 1,
  "produtos_medio": 2,
  "produtos_baixo": 2,
  "acoes_sugeridas": 3,
  "promocoes_sugeridas": 2,
  "pedidos_estoque_sugeridos": 1
}

---

# 7. Integração ERP

## 7.1 Testar conexão com ERP

Método:

GET /integracao-erp/status

Resposta 200:

{
  "origem": "MockAPI",
  "status": "CONECTADO",
  "ultima_sincronizacao": "2026-07-30T11:47:00-03:00"
}

---
# 8. Padrão de erros

## Produto não encontrado

Status:

404 Not Found

Resposta:

{
  "erro": "PRODUTO_NAO_ENCONTRADO",
  "mensagem": "Produto não encontrado para o ID informado."
}

---

## Erro na integração com ERP

Status:

503 Service Unavailable

Resposta:

{
  "erro": "ERP_INDISPONIVEL",
  "mensagem": "Não foi possível conectar com a API externa do ERP."
}

---

## Requisição inválida

Status:

400 Bad Request

Resposta:

{
  "erro": "REQUISICAO_INVALIDA",
  "mensagem": "Campos obrigatórios ausentes ou inválidos."
}
