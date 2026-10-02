# Plano executável da API de autenticação

- **Repositório de destino:** [QuiStock/DS_Auth](https://github.com/QuiStock/DS_Auth)
- **Documento mantido em:** QuiStock/DS_Backend
- **Revisão dos repositórios:** 2026-10-02
- **Status:** implementado localmente em DS_Auth; validação final e configuração dos ambientes ainda pendentes. MongoDB, BCrypt, provisionamento SQL externo e retirada futura do Firebase Auth no Mobile foram confirmados. Esta implementação não altera o código do Mobile.

Este documento fecha o contrato entre DS_Auth, DS_Backend, o schema PostgreSQL e o app Android QuiStock Mobile. A implementação local da API está descrita na seção 9.1; a integração do Mobile e a configuração de produção continuam em entregas posteriores.

## 1. Escopo

DS_Auth será responsável somente por autenticar credenciais e administrar sessões: login, rotação de refresh token e logout. Não terá cadastro, perfil, edição de usuário ou recuperação de senha.

- PostgreSQL continua como fonte de verdade de usuário, email, status e hash de senha.
- MongoDB guarda somente registros de refresh token e estado de sessão. Não guarda senha, hash de senha ou cópia de perfil.
- O access token é JWT RS256 com subject igual ao ID decimal de user_account.id e claim email.
- Access token tem validade de 5 minutos. Cada refresh token tem validade de 15 dias desde sua emissão; a rotação renova esse prazo como timeout deslizante por inatividade.
- A API de negócio usa audience quistock-api.
- Nenhuma rota de autenticação será adicionada ao DS_Backend; DS_Auth é serviço separado.

### Fora do escopo desta entrega

Cadastro e manutenção de conta ficam em outro fluxo que grava no SQL. Esse fluxo também gera password_hash compatível com BCrypt. DS_Auth não cria nem altera contas e usa credencial SQL somente leitura.

O Mobile tem uma tela visual de cadastro, mas seu fragmento atual apenas infla o layout e não envia dados. O DS_Backend também não tem rota de usuário no código inspecionado. O cadastro mobile não passa a funcionar com DS_Auth; a integração futura exige definir ou implementar esse outro fluxo.

## 2. Evidência dos três repositórios

| Projeto | Estado conferido | Consequência para a implementação |
| --- | --- | --- |
| DS_Backend | Spring Boot usa context path /api; toda rota exige JWT. O decoder aceita RS256 e valida issuer, audience, subject numérico positivo e email. Audience padrão: quistock-api. | DS_Auth publica JWKS e emite exatamente esse contrato. A propriedade padrão atual do backend aponta para /.well-known/jwks.json, sem /api; ajustar para /api/.well-known/jwks.json. |
| PostgreSQL | user_account tem id BIGINT, email VARCHAR(255) UNIQUE NOT NULL, password_hash VARCHAR(255) NOT NULL e status user_status com ACTIVE/INACTIVE. A unicidade atual diferencia maiúsculas e minúsculas. | DS_Auth lê somente id, email, status e password_hash. Login case-insensitive exige pré-checagem de duplicidades e índice único funcional no banco. |
| DS_Auth | Esqueleto Spring Boot 4.1/Java 25. Inclui JPA, PostgreSQL e H2, mas ainda não tem MongoDB, Spring Security/JWT, controladores ou lógica de auth. ddl-auto=update e show-sql=true estão ativos. | Remover JPA/DDL automático e log SQL; usar SQL somente leitura e adicionar suporte a MongoDB e JWT. |
| Mobile | No branch main, login chama Firebase Auth por email/senha. Firebase Analytics e Crashlytics também são usados. UserPreferences salva somente o ID Firebase em SharedPreferences normal; não há cliente de auth, tokens, interceptor Bearer nem renovação. | O login futuro passará ao DS_Auth. Manter Analytics/Crashlytics. Esta análise não altera o código do Mobile. |
| Mobile ↔ DS_Backend | Retrofit usa BACKEND_BASE_URL terminado em /api/; POST chat coincide com /api/chat. O Mobile envia user_id e message, mas o DTO do backend aceita somente message. O backend exige JWT e o Retrofit atual não envia Authorization. | A integração futura alinha o payload para message e envia Bearer. Não confiar em identidade no corpo; o servidor usa o subject validado quando precisar identificar o usuário. |
| Mobile cadastro | CadastroPessoalFragment só infla o XML. Não há implementação de criação de conta. | A tela atual não é cadastro funcional e não é atendida por DS_Auth. |

A inspeção corresponde aos branches locais existentes em 2026-10-01. Reconfirmar os branches antes da implementação.

## 3. Decisões confirmadas

- Login por email e senha.
- Serviço separado, somente autenticação e sessão.
- Dados de conta permanecem no PostgreSQL; sessão e refresh ficam no MongoDB.
- MongoDB executa em replica set; transações são necessárias para rotação e revogação atômicas.
- Password hash usa BCrypt. Configurar custo 12 por padrão e permitir ajuste por ambiente; o provisionador SQL externo grava hash BCrypt compatível.
- Contas e hashes são provisionados por outro fluxo SQL. DS_Auth não terá cadastro e não fará escrita no SQL.
- O Mobile removerá Firebase Auth na integração futura; Firebase Analytics e Crashlytics podem permanecer.
- O pedido atual analisa o Mobile e atualiza o plano; não altera nenhum arquivo do repositório Mobile.
- Subject é o ID SQL como string decimal positiva; email é claim obrigatório.
- Access token de 5 minutos; refresh token de 15 dias com prazo renovado a cada rotação.
- Assinatura RS256, compatível com o decoder atual do DS_Backend.
- Audience quistock-api.
- Não incluir claim de papel até existir regra de autorização acordada.
- Logout recebe refresh token. Access tokens emitidos continuam aceitos até expiração, por no máximo 5 minutos.
- Cada login cria uma família independente. Na v1 não há limite de sessões por usuário nem rota para revogar todas as famílias; logout revoga a família do refresh recebido.
- Respostas de login e refresh incluem somente a identidade mínima {id, email}; id é igual ao subject do JWT. Isso não constitui endpoint nem objeto de perfil.

## 4. Contrato HTTP

DS_Auth já define context path /api. Os caminhos externos completos são:

- POST /api/auth/login
- POST /api/auth/refresh
- POST /api/auth/logout
- GET /api/.well-known/jwks.json — público, sem autenticação

As quatro rotas acima são públicas no filtro de segurança; refresh e logout se autenticam pelo refresh token no corpo. A API é stateless, sem cookies e sem sessão HTTP. Não armazenar em cache respostas de login ou refresh; enviar Cache-Control: no-store. Rotas não listadas não fazem parte do contrato.

Usar porta 8090 no perfil local para não colidir com DS_Backend, que usa 8080. O Mobile deverá receber configuração AUTH_BASE_URL terminada em /api/. Ela pode apontar para o mesmo gateway do backend se o gateway rotear /api/auth/** e /api/.well-known/** ao DS_Auth; se os serviços tiverem hosts separados, configurar a origem de auth.

### Login

Request:

    {
      "email": "person@example.com",
      "password": "senha informada pelo usuário"
    }

Sucesso: 200 OK.

    {
      "access_token": "<JWT>",
      "token_type": "Bearer",
      "expires_in": 300,
      "refresh_token": "<token opaco>",
      "refresh_expires_in": 1296000,
      "user": {
        "id": "<ID SQL decimal>",
        "email": "person@example.com"
      }
    }

Remover espaços externos e normalizar email para minúsculas usando Locale.ROOT. Não alterar a senha. A busca usa a mesma forma normalizada. A claim email contém o email canônico armazenado no SQL.

Email inexistente, senha incorreta e conta INACTIVE retornam o mesmo 401 e o mesmo código/mensagem genéricos. A resposta não revela se a conta existe. Para email inexistente, comparar a senha com um hash BCrypt fictício criado em memória com o mesmo custo, para reduzir diferença de tempo observável.

### Refresh

Request:

    {
      "refresh_token": "<token opaco>"
    }

Sucesso: 200 OK com o mesmo formato da resposta de login, access token e refresh token novos. A identidade devolvida é a do mesmo usuário SQL. O token apresentado fica consumido na mesma transação que cria seu sucessor.

Token desconhecido, expirado, consumido ou revogado retorna 401. Se um token consumido ainda estiver dentro do prazo original e for apresentado novamente, revogar toda a família de sessão. Tokens antigos expirados podem ter sido removidos pelo TTL; nesse caso retornam 401 sem revogar outras sessões.

### Logout

Request:

    {
      "refresh_token": "<token opaco>"
    }

Revogar a família do refresh token recebido e responder 204 No Content. Token desconhecido ou já revogado também resulta em 204, tornando a operação idempotente. O Mobile apaga a sessão local mesmo se o pedido de rede falhar. O access token corrente permanece utilizável no DS_Backend até expirar.

### Formato uniforme de erro

Usar JSON { "code": "...", "message": "..." } em respostas com corpo:

| Caso | HTTP | code |
| --- | --- | --- |
| Campo ausente ou inválido | 400 | invalid_request |
| Credencial inexistente, incorreta ou conta inativa | 401 | invalid_credentials |
| Refresh inválido, expirado, revogado ou replay | 401 | invalid_refresh_token |
| Limite de tentativas atingido | 429 | rate_limited, com Retry-After |
| SQL ou MongoDB indisponível | 503 | service_unavailable |
| Erro inesperado | 500 | internal_error, sem detalhes internos |

Nunca devolver senha, hash, stack trace, existência de email ou estado de conta na mensagem pública.

## 5. Acesso ao SQL e provisionamento de conta

Usar JDBC (JdbcClient/JdbcTemplate), sem entidades mutáveis nem JPA. Consulta prevista:

    SELECT id, email, status::text AS status, password_hash
    FROM user_account
    WHERE lower(btrim(email)) = ?

A conta SQL do DS_Auth recebe somente SELECT nas colunas id, email, status e password_hash de user_account. Não recebe INSERT, UPDATE, DELETE, DDL nem permissões de perfil/loja. O serviço compara a senha em memória pelo BCrypt e nunca registra credenciais.

Antes da migração de normalização de email:

    SELECT lower(btrim(email)), count(*)
    FROM user_account
    GROUP BY lower(btrim(email))
    HAVING count(*) > 1;

Resolver colisões e criar o índice abaixo por migração do banco, nunca por Hibernate/DS_Auth. A unicidade existente sobre o texto original pode permanecer.

    CREATE UNIQUE INDEX uq_user_account_email_normalized
      ON user_account (lower(btrim(email)));

A tabela também exige role_id; o provisionador externo atribui papel válido e obedece às demais chaves estrangeiras do schema, mesmo que DS_Auth não emita papel no token. A implementação de DS_Auth pode ser desenvolvida e testada com contas de fixture; disponibilizar login para usuários reais depende do fluxo externo de provisionamento.

## 6. Sessões no MongoDB

Coleção refresh_token, um documento por token emitido:

| Campo | Tipo/uso |
| --- | --- |
| id | UUID interno |
| family_id | UUID comum à cadeia de rotação; indexado |
| user_account_id | BIGINT/Long com o ID SQL, sem perfil |
| token_hash | SHA-256 do token opaco; índice único |
| state | ACTIVE, CONSUMED ou REVOKED |
| created_at, expires_at | UTC; expiração em 15 dias após emissão |
| consumed_at, revoked_at | Instantes opcionais |
| replaced_by_id | UUID do token sucessor, opcional |

Gerar refresh token com 32 bytes criptograficamente aleatórios e codificação Base64URL sem padding. Retornar o token original somente na resposta; persistir e pesquisar somente SHA-256. Índices: único em token_hash, em family_id e TTL em expires_at. TTL é limpeza eventual: toda leitura verifica estado e instante de expiração. Criar os índices por bootstrap versionado/migração explícita do banco; não depender de criação automática de índices do ORM.

Rotação executa em transação MongoDB: consumir condicionalmente o token ativo ainda válido, inserir sucessor e registrar replaced_by_id. Replay detectado antes de expirar revoga a família dentro da transação e retorna 401 depois que a revogação foi confirmada. Replica set é requisito de execução e dos testes de integração; transações não funcionam em Mongo standalone. Mobile serializa refresh concorrente para não provocar replay acidental.

No refresh, consultar novamente status e hash da conta no SQL. Conta inativa impede rotação. Usar estado atual do SQL; não persistir papel nem perfil no Mongo.

## 7. JWT, chaves e compatibilidade com DS_Backend

Claims obrigatórios:

    {
      "iss": "<AUTH_JWT_ISSUER>",
      "aud": ["quistock-api"],
      "sub": "<user_account.id positivo em decimal>",
      "email": "person@example.com",
      "iat": 0,
      "exp": 0,
      "jti": "<UUID único>"
    }

Header JOSE: alg=RS256 e kid identificando a chave ativa. A diferença entre exp e iat é 300 segundos. JWKS contém somente chaves públicas. A chave privada fica em secret manager ou arquivo montado protegido, nunca em Git, SQL, Mongo ou Mobile.

Durante troca de chave, publicar chave antiga e nova simultaneamente; assinar tokens novos com o novo kid. Manter a chave pública antiga no JWKS por ao menos 10 minutos depois do fim da assinatura antiga (5 minutos de validade mais margem de clock skew/cache), depois removê-la.

Backend e serviço de auth usam exatamente o mesmo issuer e audience. DS_Backend aponta AUTH_JWT_JWK_SET_URI para o caminho externo /api/.well-known/jwks.json. O endpoint JWKS não exige access token, pois o decoder precisa consultá-lo sem autenticação.

## 8. Integração futura no Mobile e no backend

Esta seção alinha a implementação futura; não autoriza nem realiza alterações no repositório Mobile nesta tarefa.

### Mobile

1. Adicionar Retrofit AuthApi para auth/login, auth/refresh e auth/logout, com DTOs explícitos em snake_case usando @SerialName; o conversor atual ignora campos desconhecidos, mas não converte nomes automaticamente.
2. Trocar o binding de AuthenticationPort de FirebaseAuthenticationPort para implementação Retrofit. Mapear user.id/email para o modelo local User e invalid_credentials ao erro genérico de login existente.
3. Criar armazenamento de sessão na camada data: access token em memória; refresh token protegido por chave criada no Android Keystore. Não colocar token em SharedPreferences em texto puro, logs, Analytics ou Crashlytics.
4. Na inicialização, se houver refresh persistido, tentar renovação; se a API responder 401, apagar sessão e mostrar login. Erros 429/503/rede são transitórios e não apagam um refresh potencialmente válido.
5. Usar cliente HTTP separado para auth e chamadas de negócio. O cliente de negócio adiciona Authorization: Bearer <access_token> e renova em resposta 401 uma única vez; refresh é serializado. Falha de refresh limpa sessão e retorna à tela de login.
6. Logout tenta chamar a API e sempre limpa tokens localmente, inclusive quando offline.
7. Deixar de tratar Firebase UID como identidade. O ID SQL do token é o identificador aceito pelo backend. /api/chat recebe somente message; remover user_id do DTO mobile. Se uma rota futura precisar do usuário, obter identidade do subject autenticado no servidor.
8. Manter Firebase Analytics e Crashlytics se ainda necessários; retirar somente Firebase Auth.
9. Configurar AUTH_BASE_URL durante build/deploy. Mobile define usesCleartextTraffic=false, enquanto .env.example sugere URL HTTP do emulador; testes locais devem usar HTTPS de desenvolvimento ou permissão HTTP restrita ao build debug, nunca liberar HTTP no release.

### DS_Backend

1. Atualizar a URI JWKS padrão local para http://localhost:8090/api/.well-known/jwks.json e configurar URL pública correspondente no ambiente de deploy.
2. Configurar o mesmo AUTH_JWT_ISSUER do DS_Auth e manter audience quistock-api.
3. Manter rotas de negócio autenticadas. Validar RS256, issuer, audience, subject positivo e email.
4. A requisição Chat do backend aceita somente message; alinhar o DTO Mobile. Em fluxos com auditoria/usuário, usar o principal JWT autenticado, nunca aceitar identidade declarada pelo cliente.

## 9. Configuração e ajustes no esqueleto DS_Auth

Remover spring-boot-starter-data-jpa, configurações spring.jpa.*, DDL automático e log SQL. Adicionar suporte Spring JDBC/PostgreSQL, Spring Data MongoDB, validação, Spring Security e JOSE/JWT. H2 não substitui testes de contrato com PostgreSQL; usar PostgreSQL em integração e Mongo replica set para transações. Conservar os gates de qualidade Gradle existentes.

Configurar variáveis por ambiente:

- DS_Auth: SERVER_PORT (8090 local), DB_URL, DB_USERNAME, DB_PASSWORD (credencial read-only), MONGODB_URI, MONGODB_DATABASE, AUTH_JWT_ISSUER, AUTH_JWT_AUDIENCE (quistock-api), AUTH_JWT_PRIVATE_KEY_PATH, AUTH_JWT_PUBLIC_KEY_PATH, AUTH_JWT_PREVIOUS_PUBLIC_KEYS, AUTH_JWT_KEY_ID, AUTH_BCRYPT_STRENGTH, AUTH_RATE_LIMIT_HMAC_KEY e AUTH_TRUSTED_PROXY_CIDRS.
- DS_Backend: AUTH_JWT_ISSUER, AUTH_JWT_JWK_SET_URI, AUTH_JWT_AUDIENCE.
- Mobile: AUTH_BASE_URL e BACKEND_BASE_URL, ambas terminadas em /api/.

Produção exige HTTPS, secrets fora do repositório, Mongo replica set disponível e credenciais SQL restritas. O serviço falha no startup se chave, banco ou issuer estiver ausente/inseguro; não inicia com chave default de desenvolvimento.

### Rate limit

Aplicar limite distribuído entre réplicas com contador atômico e TTL no MongoDB ou gateway equivalente. Defaults configuráveis: 10 tentativas de login por IP em 15 minutos; 5 falhas por par IP+email normalizado em 15 minutos; 30 chamadas de refresh por IP por minuto. Acima do limite, responder 429 com Retry-After. Só confiar em cabeçalhos de IP de proxies configurados como confiáveis; não aceitar X-Forwarded-For arbitrário. Os identificadores dos contadores não contêm IP/email em claro: são HMAC-SHA256 com segredo obrigatório de pelo menos 32 bytes (`AUTH_RATE_LIMIT_HMAC_KEY`).

### 9.1 Implementação local em DS_Auth

O repositório `QuiStock/DS_Auth`, branch local `feat/auth-api`, agora implementa o contrato desta seção:

- JDBC consulta `user_account` e a pool é read-only. Colisões em `lower(btrim(email))` falham fechadas até o índice funcional ser instalado.
- Login usa BCrypt configurável (custo padrão 12), comparação fictícia para email inexistente e rejeita senha acima do limite de 72 bytes UTF-8 do BCrypt.
- JWT RS256 usa `sub` decimal positivo, `email`, `iss`, `aud`, `iat`, `exp`, `jti` e `kid`. Chave privada e pública são obrigatórias; chaves públicas anteriores podem ser publicadas durante rotação. O issuer deve usar HTTPS, exceto em loopback local.
- MongoDB guarda somente hash SHA-256 dos refresh tokens, aplica índices idempotentes no startup e recusa startup quando `hello` não informa replica set.
- Rotação Mongo usa transação, consumo condicional e revogação da família em replay, inclusive quando duas requisições concorrem. Contadores distribuídos usam janela fixa e expiração TTL.
- As quatro rotas do contrato estão implementadas, com erros JSON uniformes, `Retry-After` no 429, `Cache-Control: no-store` em respostas de autenticação e JWKS público.
- `README.md` em DS_Auth contém configuração local, requisitos de banco, geração de chaves e comandos de validação. `.env.example` documenta a configuração sem armazenar secrets reais.
- Testes com Testcontainers cobrem PostgreSQL e Mongo replica set, emissão/verificação JWT/JWKS, credenciais, validação, refresh/replay, expiração, logout, rate limits e colisão de emails normalizados.

A configuração JWKS do DS_Backend, a migração SQL do índice funcional, secrets/URLs de deploy e a integração do Mobile continuam dependências externas à implementação DS_Auth. Os testes end-to-end no emulador ficam para a entrega de integração Mobile.

### 9.2 Resultado de validação local

- Google Java Format 1.28.0 em modo de verificação e `git diff --check`: aprovados.
- PMD 7.16.0 sobre `src/main/java`: aprovado; o PMD imprime um aviso padrão sobre `LoosePackageCoupling` não configurado.
- Os gates Gradle e testes não chegaram a executar. O Gradle 9.7.1 falha durante a inicialização do daemon com `java.io.IOException: Unable to establish loopback connection`, inclusive fora do sandbox e usando o JDK 21 disponível. O JDK configurado no projeto é 25; o JDK padrão local é 17. Docker também não está disponível para o Testcontainers.
- Depois de disponibilizar JDK 25, permitir a conexão loopback do Gradle e iniciar Docker com suporte a containers, executar os comandos listados no README de DS_Auth. Ainda falta confirmar Checkstyle, compilação, testes Testcontainers e o limite de cobertura de 80%.

## 10. Critérios de aceite e testes

### DS_Auth e bancos

- Login com conta ACTIVE e senha correta emite tokens, devolve user.id/email iguais ao subject/claim email, audience/issuer configurados e exp - iat = 300.
- Email desconhecido, senha errada e conta INACTIVE retornam mesmo 401, corpo e formato de processamento comparável.
- Campo ausente/inválido retorna 400; excesso de tentativas retorna 429 com Retry-After.
- Senha em texto puro nunca chega ao banco, log, Mongo ou resposta. Mongo persiste hash do refresh, nunca token original.
- Credencial SQL read-only não consegue inserir, atualizar ou apagar.
- Refresh válido consome token e emite sucessor em transação; refresh antigo ainda válido revoga a família; refresh simultâneo não emite dois sucessores ativos.
- Refresh desconhecido/expirado/revogado retorna 401; logout ativo revoga família e é idempotente.
- SQL/Mongo indisponível produz 503 sem stack trace nem credenciais nos logs.
- Expiração é decidida pela aplicação, não pela remoção eventual do TTL Mongo.
- JWKS público contém somente chaves públicas e verifica token RS256 válido. Kid desconhecido, issuer/audience errados, assinatura inválida, subject inválido, email ausente e token expirado são rejeitados no backend.
- Após inativar conta no SQL, login e refresh falham. Access token já emitido permanece válido até expirar.

### Mobile ↔ DS_Auth ↔ DS_Backend

- Login válido mostra estado autenticado sem expor tokens ao ViewModel/tela.
- Refresh token sobrevive reinício do processo em armazenamento protegido; access token fica em memória e pode ser renovado.
- 401 no backend causa uma renovação e no máximo uma repetição da chamada; falha de refresh apaga sessão e volta ao login.
- Refresh concorrente no cliente é serializado; logout apaga sessão mesmo offline.
- Chamada protegida Mobile → POST /api/chat carrega Bearer válido e envia DTO acordado (message).
- DS_Backend valida token publicado por DS_Auth; rota sem token ou token inválido retorna 401.
- O fluxo externo de provisionamento SQL cria conta válida e hash BCrypt antes de login de usuário real.
- A tela de cadastro atual não é critério de aceite de DS_Auth e permanece sem persistência até uma entrega específica.

## 11. Ordem de implementação

1. Tratar este plano como contrato dos três projetos. **Concluído.**
2. Fazer preflight de emails normalizados no SQL, resolver colisões e aplicar índice único funcional. **Pendente no ambiente PostgreSQL.**
3. Ajustar dependências/configuração do scaffold DS_Auth; retirar JPA/DDL/log SQL e configurar SQL read-only e Mongo replica set. **Implementado localmente.**
4. Implementar consulta/autenticação SQL, contrato de login e emissão RS256/JWKS. **Implementado localmente.**
5. Implementar documentos, índices, transação de refresh, replay, logout, rate limit e erros. **Implementado localmente.**
6. Atualizar configuração JWKS do DS_Backend.
7. Em tarefa futura, atualizar Mobile para login/logout/refresh, armazenamento protegido e Bearer no Retrofit; alinhar request de chat e estados de rede.
8. Executar testes unitários, integração PostgreSQL/Mongo replica set e gates Gradle. **Testes configurados; execução final pendente neste ambiente.** Validar fluxo ponta a ponta em emulador na entrega Mobile.
9. Configurar secrets, issuer, JWKS público, URLs HTTPS, credenciais read-only e monitoramento por ambiente antes de disponibilizar a API.

## 12. Dependências operacionais para produção

- O responsável pelo fluxo externo SQL provisiona usuários e gera hashes BCrypt compatíveis.
- O índice único case-insensitive é aplicado depois de resolver duplicidades existentes.
- URLs públicas, issuer, JWKS, chaves e credenciais são fornecidos por ambiente no deploy; não são valores fixos do documento.
- MongoDB de execução e integração tem replica set habilitado.
- O Mobile só será compatível depois da tarefa de integração descrita na seção 8; esta revisão não alterou seu repositório.
