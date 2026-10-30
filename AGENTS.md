# Regras do projeto — MyRank API

Leia antes de mexer em qualquer coisa. Vale para qualquer pessoa ou ferramenta
(Claude, Codex, etc.) que trabalhe neste repositório.

O frontend fica em `../MyRank-frontend` e tem o próprio `AGENTS.md`. O que está
pendente fica em [`docs/ROADMAP.md`](docs/ROADMAP.md), e o porquê das decisões em
[`docs/decisoes.md`](docs/decisoes.md).

## Git

- **Nunca rodar `git commit`, `git add` nem `git push`.** Quem commita é o
  Guilherme. Ao terminar, entregue cada commit como **uma linha pronta para
  colar** no terminal (PowerShell, por isso `;` e não `&&`), com caminhos a
  partir da raiz do repo (`:/`):
  `git add ":/docs/a.md" ":/docs/b.md"; git commit -m "docs: ..."`
- **No máximo 5 arquivos por commit.** Se passar disso, divida em vários.
- **Mensagem de uma linha só, curta, em português, no padrão Conventional Commits**
  (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`), por exemplo
  `feat: busca de mangás pelo MyAnimeList`.
- Nada de linha `Co-Authored-By` nas mensagens.
- **Nada de plano de commit dentro dos arquivos do projeto** (docs, comentários).
- Trabalho novo vai numa branch nova, nunca direto na `master`.

## Banco e migrations

- O site está no ar com dados reais. **Nunca dropar o banco**, nem o local sem
  combinar antes (o local tem dados que o Guilherme usa).
- **Migration aplicada nunca é editada.** Toda mudança de schema é uma migration
  nova (`V12__...sql`, `V13__...sql`) com `ALTER`/`CREATE`.
- V1–V8 são o squash das 16 antigas; a produção entra nelas por baseline
  (ver [`deploy/README.md`](deploy/README.md)).
- Valores de enum (templates, tipos de campo, etapas) são validados **no código**,
  não com `CHECK` no banco, para um valor novo não exigir migration.
- Toda migration que mexe em dados existentes ganha um teste de migração
  (aplica as anteriores num schema descartável, insere dados antigos, confere).

## Testes

- Testes de integração e de migração precisam de um Postgres descartável (o
  comando está no [README](README.md#tests)). Use uma porta livre e **remova o
  contêiner no fim**, mesmo se o teste falhar.
- APIs externas sempre mockadas. Nada de chamada real em teste.
- Antes de entregar: a suíte inteira passando e `./mvnw -q -DskipTests compile`.

## Código

- Siga o estilo do código em volta: comentários curtos em português explicando o
  **porquê**, mensagens de erro em português para a tela.
- Um construtor por bean do Spring (com dois, a injeção quebra). O relógio vem do
  bean `Clock` (`ClockConfig`), nunca de `Instant.now()` direto em regra testável.
- Erros de regra são `IllegalArgumentException` (vira 400) ou
  `IllegalStateException` (vira 503); nada de stack trace na resposta.
- Toda operação confere o dono do recurso.
- Estado em memória (códigos por email, rate limit, cache) só funciona com uma
  instância: está registrado no [SECURITY.md](SECURITY.md).

## Docs

- Doc de funcionalidade fica em `docs/`, em português, e explica regras e o
  porquê, não o que o código já mostra.
- README em inglês; `SECURITY.md`, `deploy/` e `docs/` em português.
- Não coloque contagem de testes nem datas de validação nos docs: envelhecem.
- Decidiu algo com o Guilherme? Registre em `docs/decisoes.md`. Terminou ou
  adiou algo? Atualize `docs/ROADMAP.md`.

## Conversa

- Sempre em português.
- **Debater e fechar a proposta antes de editar código.** Mudança de
  comportamento, de texto para o usuário ou de visual passa pelo Guilherme antes.
