# Templates — fase 1

Template é o tipo do conteúdo: `movie`, `tv`, `anime`, `game`, `book` ou
`custom` (Personalizado, preenchido à mão). Ele decide a busca, o card e as
conquistas. Nome e emoji da tabela são só apresentação. As próximas fases
acrescentam `music`, `album` e `manga`.

## Regras

- **Uma tabela tem um ou mais templates**, escolhidos na criação, na ordem em
  que a pessoa marcou (ex.: Jogos + Séries + Animes). Personalizado também pode
  entrar na mistura.
- **Cada item guarda o próprio template**, que precisa ser um dos da tabela.
  Numa tabela de um tipo só, o item herda; numa mista, a pessoa escolhe.
- **Mudar os templates depois:** adicionar pode sempre; tirar só se nenhum item
  da tabela usar aquele template. A tabela precisa de pelo menos um.
- **Valores validados no código** (enum `TableTemplate`), não no banco: template
  novo não exige migration.
- **`details`** (JSON) guarda dados específicos do template, como de onde o item
  veio (`provider`, `externalId`). Sempre um objeto, até 8 KB.

## API

- POST `/api/categories`: `{ "name": "🎮 Jogos e séries", "templates": ["game", "tv"] }`.
  Sem `templates`, a tabela é `custom`.
- PUT `/api/categories/{id}`: `name` e, opcionalmente, `templates`. Sem
  `templates`, mantém os atuais.
- POST `/api/works`: `template` e `details`, além dos campos existentes.
  `template` pode faltar só em tabela de um tipo só.
- PUT `/api/works/{id}`: `template` e `details` null mantêm o que há; `details: {}`
  limpa; um objeto substitui.
- Categorias respondem `templates` (lista); obras respondem `template` e `details`.

## V9 e dados antigos

A V9 cria `category_templates` (tabela, posição, template) e as colunas
`works.template` e `works.details`. As tabelas existentes são classificadas uma
única vez pelo nome antigo (emoji primeiro, depois palavras); daí em diante o
nome não decide nada.

A antiga "Séries & Animes" vira **uma tabela mista** com Séries + Animes, sem
mover nenhum item: o que tem capa do MyAnimeList vira anime, o resto vira série.
Notas, subdivisões, ordem manual e takes ficam intactos.

Cadastros novos recebem cinco tabelas: Filmes, Jogos, Livros, Séries e Animes,
uma para cada tipo. A escolha das tabelas no primeiro acesso fica para a fase 6.

## Testes

`TableTemplateMigrationTest` aplica V1–V8 num schema descartável, insere dados
no formato antigo e roda a V9. `TableTemplateIntegrationTest` cobre tabela
mista, herança do tipo, troca de templates e limite do `details`. Os dois
precisam de um Postgres; para rodar a suíte inteira num banco descartável:

```bash
docker run -d --name mr-test-db -e POSTGRES_PASSWORD=x -e POSTGRES_DB=myrank_test -p 55432:5432 postgres:15
./mvnw test -Dspring.datasource.url=jdbc:postgresql://localhost:55432/myrank_test -Dspring.datasource.username=postgres -Dspring.datasource.password=x
docker rm -f mr-test-db
```
