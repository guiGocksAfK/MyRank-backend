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

Cadastros novos começam sem tabelas e com perfil privado. A escolha das tabelas
no primeiro acesso acontece pelo [tutorial pós-cadastro](onboarding.md), com
uma tabela para cada template escolhido.

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

## Fase 5: campos próprios

Os campos próprios pertencem à tabela e só podem ser definidos quando ela tem
o template `CUSTOM` (Personalizado). Uma tabela pode ter até **5 campos**, com
os tipos `TEXT`, `NUMBER`, `DATE` e `BOOLEAN`. Cada definição tem `{ id, name,
type }`. O servidor gera um ID curto e aleatório (`f_` + 8 caracteres), que
continua o mesmo ao renomear. O nome tem de 1 a 40 caracteres, após remover
espaços das bordas, e é único na tabela sem diferenciar maiúsculas.

### Definições e API

`PUT /api/categories/{id}/custom-fields` exige o dono da tabela, pela mesma
checagem dos outros endpoints de categoria, e recebe a **lista completa**:

```json
[
  { "name": "Local", "type": "TEXT" },
  { "name": "Visita", "type": "DATE" }
]
```

Sem `id`, cria um campo. Com `id`, atualiza um campo existente da mesma tabela.
IDs desconhecidos ou repetidos são recusados. A resposta é o
`CategoryResponseDTO` atualizado, com `customFields` em todas as respostas de
categoria. Para renomear, envie o mesmo ID e tipo com o novo nome. Campos
existentes omitidos da lista são removidos; `[]` remove todos.

- **Renomear:** permitido, respeitando tamanho e unicidade; preserva valores.
- **Adicionar:** permitido até o limite; itens antigos ficam sem valor.
- **Trocar tipo:** retorna 400 com mensagem orientando remover e criar outro.
- **Remover:** apaga o valor de cada campo removido em todos os itens da tabela,
  preservando outros campos e metadados, na mesma transação. A confirmação é
  responsabilidade do frontend.
- **Retirar `CUSTOM` da tabela:** mantém a regra de que não pode haver itens
  usando esse template; quando permitido, apaga também as definições. Adicionar
  `CUSTOM` novamente começa sem campos próprios.

A V10 adiciona `categories.custom_fields JSONB NOT NULL DEFAULT '[]'` com
`CHECK (jsonb_typeof(custom_fields) = 'array')`. Os tipos e limites são validados
no código; não há CHECK com a lista de tipos. V1–V9 permanecem intactas.

### Valores dos itens

Só itens `CUSTOM` podem enviar `details.fields`, um objeto com os IDs das
definições da tabela como chaves. Por exemplo, usando IDs devolvidos pelo
servidor:

```json
{
  "template": "custom",
  "details": {
    "fields": {
      "f_a1B2c3D4": "São Paulo",
      "f_e5F6g7H8": "2026-10-01"
    }
  }
}
```

| Tipo | Valor aceito |
| --- | --- |
| `TEXT` | String de até 200 caracteres, inclusive vazia |
| `NUMBER` | Número finito; strings numéricas, NaN e infinito são recusados |
| `DATE` | String com data válida e completa no formato `yyyy-MM-dd` |
| `BOOLEAN` | `true` ou `false`, sem conversão de strings ou números |

`null` como valor remove a chave do campo no item, sem remover sua definição.
Campos podem ficar sem valor. Chaves desconhecidas e `details.fields` fora de
um objeto são recusados com 400 e mensagem em português. Itens de outro
template recusam `details.fields`, inclusive vazio ou nulo. Ao trocar o
template de um item, os detalhes resultantes também são validados.

O contrato de edição continua igual: `details: null` mantém os detalhes;
`details: {}` limpa; um objeto substitui os detalhes completos. O limite total
de **8 KB em UTF-8** continua valendo, inclusive para campos próprios. As
gravações de itens e mudanças de definições/templates bloqueiam a mesma
categoria durante a transação, evitando reintroduzir um campo removido em uma
gravação concorrente.

### Testes da fase 5

`CustomFieldsServiceTest` e `CustomFieldsControllerTest` cobrem validações,
contratos HTTP e mensagens 400. `CustomFieldsIntegrationTest` verifica JSONB,
renomeação, limpeza em todos os itens, preservação de outras tabelas e rollback
conjunto de definições e valores. `CustomFieldsMigrationTest` aplica a V10 sobre
um schema na V9, confere default e CHECK de array e garante que o banco não
restringe a lista de tipos. O teste antigo da V9 permanece limitado à V9.

Execute a suíte inteira no Postgres descartável com o comando da seção
**Testes** acima. No Windows, use `.\mvnw.cmd` no lugar de `./mvnw`. Depois,
valide a compilação com `./mvnw -q -DskipTests compile` (ou
`.\mvnw.cmd -q -DskipTests compile`).

Validação em 01/10/2026: a suíte inteira passou no Postgres 15 descartável,
com **210 testes, nenhuma falha, nenhum erro e nenhum teste ignorado**.
