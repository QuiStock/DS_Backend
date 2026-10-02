# Plano da API de autenticação NoSQL

**Repositório de destino:** [QuiStock/DS_Auth](https://github.com/QuiStock/DS_Auth)

**Status:** plano inicial para implementar no repositório de autenticação indicado. Este documento descreve o serviço futuro; não adiciona essas rotas ao backend QuiStock atual.

## Decisões já alinhadas

- O serviço futuro é somente de autenticação e sessão. Não implementa perfil, cadastro, edição de usuário ou recuperação de senha.
- O login usa email e senha.
- `user_account`, email, status, papel e `password_hash` continuam no PostgreSQL/SQL. O NoSQL não deve guardar senha, hash de senha nem uma cópia do perfil.
- O JWT de acesso usa `sub = user_account.id` como string decimal e inclui `email` como claim.
- O access token expira em 5 minutos; o refresh token expira em 15 dias.
- A API de negócio existente consome o access token como OAuth2 Resource Server. Seu issuer, JWKS URL e audience são configurados por `AUTH_JWT_ISSUER`, `AUTH_JWT_JWK_SET_URI` e `AUTH_JWT_AUDIENCE`; a audience padrão é `quistock-api`.

## Responsabilidades e fluxo

1. O cliente mobile envia email e senha para o endpoint de login.
2. A API de autenticação procura a conta por email no SQL e verifica a senha contra `user_account.password_hash`. Deve recusar contas inativas e responder com erro genérico para email inexistente ou senha incorreta.
3. Após autenticar, emite um JWT assinado com `sub`, `email`, `iss`, `aud`, `iat`, `exp` e `jti`. `exp` deve limitar o access token a 300 segundos.
4. Emite também um refresh token opaco, aleatório e imprevisível. Só o hash criptográfico do refresh token é persistido no NoSQL, associado à conta SQL e às datas de emissão/expiração.
5. No refresh, verifica a sessão, sua expiração e revogação, emite novos tokens e invalida o refresh token anterior (rotação).
6. No logout, revoga a sessão associada ao refresh token enviado. Os access tokens existentes continuam válidos até expirarem, no máximo em 5 minutos, porque a API de negócio os valida localmente pela assinatura e não consulta o estado da sessão.

## Rotas propostas

As rotas são do serviço futuro e não existem no backend atual.

### `POST /auth/login`

Request:

```json
{
  "email": "person@example.com",
  "password": "user supplied password"
}
```

Success `200 OK`:

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "<opaque token>",
  "refresh_expires_in": 1296000
}
```

Return the same generic `401 Unauthorized` for an unknown email, wrong password, or inactive account, so the response does not reveal whether an account exists. Invalid request fields return `400 Bad Request`; repeated attempts should be rate limited.

### `POST /auth/refresh`

Request:

```json
{
  "refresh_token": "<opaque token>"
}
```

On success return a fresh access token and a new refresh token using the login response shape. Invalidate the presented refresh token atomically with issuing its replacement. Expired, revoked, unknown, or previously consumed tokens return `401 Unauthorized`. Refresh-token reuse should revoke the affected token family/session to limit replay.

### `POST /auth/logout`

Request:

```json
{
  "refresh_token": "<opaque token>"
}
```

Revoke the corresponding session and return `204 No Content`. Repeating logout for an already revoked/unknown token may also return `204` so the operation is idempotent. The mobile app must discard both tokens after logout.

## Persistência NoSQL proposta

Use uma coleção `refresh_sessions` (ou equivalente do banco escolhido). Não criar uma coleção de usuários/perfis.

| Campo | Uso |
| --- | --- |
| `id` | Identificador interno da sessão |
| `family_id` | Agrupa a cadeia de refresh tokens rotacionados |
| `user_account_id` | ID SQL de `user_account.id`; armazenar como valor escalar, sem duplicar o perfil |
| `refresh_token_hash` | Hash do token opaco; nunca salvar o token em texto puro |
| `created_at`, `last_used_at` | Auditoria básica da sessão |
| `expires_at` | Expiração de 15 dias; índice de expiração/TTL para limpeza posterior |
| `revoked_at` | Revogação por logout, inatividade ou reutilização |
| `replaced_by_session_id` | Liga uma rotação à sessão sucessora |

Use operações atômicas para consumir um refresh token e criar seu sucessor, evitando que duas chamadas concorrentes validem o mesmo token. Tokens opacos devem ter entropia alta; um hash rápido como SHA-256 é adequado somente para esse token aleatório de alta entropia. Para senhas, use o verificador/algoritmo já adotado para os hashes existentes no SQL; nunca compare ou registre senha em log.

## Contrato JWT para integração

Claims obrigatórios:

```json
{
  "iss": "<AUTH_JWT_ISSUER>",
  "aud": ["quistock-api"],
  "sub": "<user_account.id decimal>",
  "email": "person@example.com",
  "iat": 0,
  "exp": 0,
  "jti": "<unique token id>"
}
```

Para compatibilidade com a API atual, assine com chave assimétrica RS256 e publique somente a chave pública no JWKS. O decoder atual usa RS256. Nunca compartilhar a chave privada com o backend QuiStock. `kid` deve identificar a chave ativa durante rotação; uma troca de algoritmo exige configuração coordenada nos dois serviços. A API atual valida assinatura via JWKS, `iss`, `aud`, `exp`, `sub` numérico positivo e claim `email`. Não inclua nem dependa de claim de papel até existir uma regra de autorização acordada; as rotas atuais exigem identidade autenticada, mas não aplicam autorização por papel.

## Dependência de acesso ao SQL

O serviço precisa verificar contas e hashes em SQL, enquanto usa NoSQL para refresh sessions. Recomendação: credencial SQL somente leitura, limitada a `user_account` e aos campos de autenticação necessários (`id`, email, status e `password_hash`). Cadastro e manutenção de usuários pertencem ao componente/API dono dos dados SQL; não adicionar rotas de perfil a este serviço. Esse fluxo de criação/manutenção precisa estar definido para que existam contas válidas para login.

Antes de codificar no outro repositório, confirmar como esse acesso será fornecido: conexão SQL read-only direta ou uma interface interna confiável para validar credenciais. O algoritmo/custo de hash deve corresponder aos registros reais em `password_hash`; não migrar hashes para NoSQL.

## Segurança e operação

- Guardar as chaves privadas em secret manager; nunca em Git ou no banco NoSQL.
- Usar TLS em todas as chamadas e limitar tentativas de login/refresh por IP e conta.
- Não registrar senha, refresh token, access token ou hash em logs. Registrar eventos de login/refresh/logout sem credenciais.
- Validar e normalizar espaços/case do email de forma consistente com a unicidade SQL.
- No login e refresh, consultar o status atual da conta no SQL para impedir novas sessões de usuários inativos. Os access tokens já emitidos expiram em até 5 minutos.
- Definir política de retenção para sessões expiradas; índice TTL é limpeza eventual, não mecanismo de autorização.
- No app mobile, guardar refresh token no armazenamento seguro do sistema operacional e manter o access token apenas em memória quando possível.

## Casos de teste/aceite

- Login válido emite `sub` igual ao ID SQL, email correto e `exp - iat` de até 300 segundos; devolve refresh com prazo de 15 dias.
- Email inexistente, senha errada e conta inativa têm o mesmo status e formato de erro.
- Campos ausentes/malformados são rejeitados sem consultar/gravar uma sessão incompleta.
- Refresh válido rotaciona o token atomicamente; o antigo não pode ser usado novamente.
- Refresh expirado, revogado, desconhecido ou reutilizado é rejeitado; reutilização aplica a política de revogação da família.
- Logout revoga a sessão, é idempotente e o refresh não pode ser reutilizado.
- Após inativação da conta, login e refresh falham. Um access token já emitido permanece utilizável somente até sua expiração curta.
- A chave publicada em JWKS verifica um access token na API QuiStock; tokens com issuer, audience, assinatura ou claims inválidos recebem `401` na API de negócio.
- Testar rotação de `kid` com tokens assinados pela chave anterior ainda dentro da janela de validade.
- Confirmar que nenhum fluxo grava senha, hash da senha ou perfil no NoSQL e que credenciais/tokens não aparecem nos logs.

## Decisões ainda necessárias no repositório futuro

1. Qual banco NoSQL e qual framework serão usados.
2. Como o serviço recebe acesso somente leitura ao SQL e quais são os campos reais de status/email/hash.
3. Qual algoritmo de hash está presente em `password_hash` e qual encoder manterá compatibilidade.
4. URL pública final de `issuer` e JWKS, audience por ambiente e processo de guarda/rotação da chave privada RS256.
5. Política de rate limit, limite de sessões por usuário e revogação de todas as sessões.
6. Se logout usa somente refresh token ou também exige access token válido; a proposta acima usa o refresh token para permitir logout mesmo quando o access token expirou.

Antes de implementar no [repositório QuiStock/DS_Auth](https://github.com/QuiStock/DS_Auth), revisar este plano contra a estrutura real e adaptar rotas, configuração, persistência, segurança e testes.
