# Cadastro, login e sessões

Como uma conta nasce, entra e sai. As proteções em volta (rate limit, headers,
pendências) estão no [SECURITY.md](../SECURITY.md).

## Códigos por email e passes

Cadastro e recuperação de senha seguem o mesmo desenho:

1. **Código de 6 dígitos** mandado por email (Brevo), no idioma da tela.
   Guardado só na memória (`EmailCodeStore`) e só como SHA-256. Vale 15 minutos,
   morre depois de 5 erros ou ao ser usado, e um novo só sai 30s depois do
   anterior. Pedir outro invalida o anterior. Reiniciar a API apaga os
   pendentes: a pessoa só pede outro.
2. **Passe.** Acertar o código devolve um JWT curto (`EmailPass`) que libera a
   etapa seguinte: 30 minutos no cadastro, 15 na recuperação. A chave é derivada
   do `JWT_SECRET` + o propósito (`signup-pass`, `reset-pass`): o passe do
   cadastro não troca senha, e nenhum passe vale como sessão.

A exclusão de conta é diferente e mais antiga: código de 8 caracteres
(letras e números), guardado como hash na própria linha do usuário, vale 10
minutos, com 1 minuto entre pedidos. Não tem limite de erros por código; o que
segura tentativas é o rate limit por IP.

Sem `BREVO_API_KEY`, os fluxos respondem que o envio está indisponível.

## Cadastro com senha

| Etapa | Rota | Corpo | Resposta |
|---|---|---|---|
| 1. Email | POST `/api/auth/signup/code` | `email`, `language` | 204 |
| 2. Código | POST `/api/auth/signup/verify` | `email`, `code` | `{ pass }` |
| 3. Conta | POST `/api/users` | `signupPass`, `username`, `password`, `language` | token |

- Email de conta já confirmada é recusado na etapa 1 ("Email já está em uso").
- Usuário de 3 a 50 caracteres; senha de 8 a 100.
- A conta nasce confirmada, privada e com o tutorial em `TABLES`
  ([onboarding.md](onboarding.md)), e a pessoa já sai logada.
- Um cadastro antigo nunca confirmado (do fluxo por link) não prende o email:
  é reaproveitado por quem provou a posse agora.

## Google e Discord

- Google: o front manda o `idToken`, conferido no `tokeninfo` do Google.
  Discord: o front manda o `code` do OAuth e o back troca pelo token.
- Só é aceito email **verificado pelo provedor**.
- Email que já tem conta:
  - conta do mesmo provedor ou com senha confirmada: entra nela;
  - conta com senha **nunca confirmada**: a senha é descartada e a conta passa a
    ser de quem o provedor provou ser o dono do email;
  - conta de outro provedor: recusado ("Email já cadastrado com outro método").
- Conta nova pelo provedor: usuário gerado a partir do nome, privada, tutorial
  em `TABLES`, sem senha.

## Login

POST `/api/auth/login` com `email` e `password`.

- Conta inexistente, conta sem senha (Google/Discord) e senha errada dão a mesma
  mensagem: "Email ou senha inválidos."
- "Email não confirmado" só aparece **depois** que a senha confere, pra não
  revelar quais emails têm cadastro pendente.

## Recuperação de senha

| Etapa | Rota | Resposta |
|---|---|---|
| 1. Pedir | POST `/api/auth/forgot-password` | `SENT`, ou `SOCIAL` + provedor |
| 2. Código | POST `/api/auth/forgot-password/verify` | `{ pass }` |
| 3. Senha nova | POST `/api/auth/reset-password` | token |

- Email sem conta segue o mesmo caminho de uma conta real (o código é gerado e
  não é mandado), então a resposta não revela quem tem conta.
- Exceção decidida: conta só com Google/Discord recebe `SOCIAL` com o provedor,
  e a tela avisa na hora.
- Trocar a senha confirma o email, **derruba as sessões de todos os aparelhos**
  e já devolve uma sessão nova.

## Sessão

- JWT HS256 de 24h, com o id da conta e a **versão de sessão** (claim `ver`).
- A cada requisição a conta é lida do banco e o token só vale se a versão bate
  com `users.token_version`. Trocar a senha aumenta a versão; token sem versão
  (anterior a esse recurso) conta como 0.
- `JWT_SECRET` com menos de 32 caracteres impede a API de subir.
- No front, qualquer 401 apaga o token e leva ao login.
- Sair só apaga o token no navegador; um token copiado vale até vencer
  (pendência no SECURITY.md).

## Exclusão de conta

1. POST `/api/users/me/deletion-code`: manda o código (toda conta, com ou sem
   senha, confirma pelo email).
2. DELETE `/api/users/me` com o código e o nome de usuário digitado
   (`confirmUsername`): apaga de verdade, não é soft delete.
   DMs somem, grupos passam o dono para o próximo da hierarquia, e vai um
   comprovante por email.

## Fluxo antigo por link

`/api/auth/verify-email` e `/api/auth/resend-verification` continuam só para
contas criadas antes do cadastro por código e nunca confirmadas. As colunas
`email_verification_*` e `password_reset_*` são restos desse fluxo.
