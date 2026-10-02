# Tutorial pós-cadastro

Toda conta nova criada por email, Google ou Discord começa com perfil privado
(`isPublic: false`), sem tabelas e com `onboardingStep: "TABLES"`. O fluxo
persistido é `TABLES` → `FIRST_WORK` → `DONE`. Boas-vindas e tela final são
apenas telas do frontend.

A V11 marca as contas existentes como `DONE` e mantém a privacidade e as
tabelas que elas já tinham. Reutilizar um cadastro pendente antigo, vincular
OAuth ou fazer login novamente não reinicia o tutorial nem muda a privacidade.
O enum `OnboardingStep` valida as etapas no código; a coluna não tem CHECK
com uma lista de etapas.

## Autenticação e estado

Os endpoints abaixo exigem `Authorization: Bearer <token>` e atuam somente
sobre a conta autenticada. Não recebem identificador de usuário.

`GET /api/users/me` inclui `onboardingStep`, com um dos valores `TABLES`,
`FIRST_WORK` ou `DONE`, além dos demais campos já existentes. O frontend pode
consultá-lo ao retomar uma sessão.

## Escolher tabelas

`POST /api/onboarding/tables`

```json
{
  "templates": ["movie", "music", "manga"]
}
```

- Aceito somente em `TABLES`.
- A lista precisa conter pelo menos um template, sem repetidos ou valores nulos.
- Templates disponíveis: `movie`, `tv`, `anime`, `manga`, `game`, `book`,
  `music` e `album`.
- `custom` é recusado: a pessoa pode criar uma tabela Personalizada depois,
  pelo endpoint normal de categorias.
- Cada escolha cria uma tabela com um único template e `is_default = true`.
  A ordem da resposta acompanha a ordem da escolha.
- Nome e emoji vêm do idioma atual da conta (`PT`, `EN` ou `ES`):

| Template | PT | EN | ES |
| --- | --- | --- | --- |
| `movie` | 🎬 Filmes | 🎬 Movies | 🎬 Películas |
| `tv` | 📺 Séries | 📺 Series | 📺 Series |
| `anime` | 🎌 Animes | 🎌 Anime | 🎌 Anime |
| `manga` | 📖 Mangás | 📖 Manga | 📖 Manga |
| `game` | 🎮 Jogos | 🎮 Games | 🎮 Juegos |
| `book` | 📚 Livros | 📚 Books | 📚 Libros |
| `music` | 🎵 Músicas | 🎵 Music | 🎵 Música |
| `album` | 💿 Álbuns | 💿 Albums | 💿 Álbumes |

Responde **200** com uma lista de `CategoryResponseDTO`, no mesmo formato
de `GET /api/categories`: `id`, `name`, `templates`, `default` (tabela padrão),
`createdAt`, `subcategories` e `customFields`. As tabelas nascem sem subcategorias
e sem campos próprios.

A criação e a mudança para `FIRST_WORK` ocorrem na mesma transação. Pedidos
simultâneos são serializados por usuário; repetir a escolha após o sucesso
retorna **400** e não duplica tabelas. Se já houver uma categoria com um dos
nomes padrão escolhidos, retorna **400**, sem criar tabelas parcialmente.

## Finalizar ou pular a primeira obra

`POST /api/onboarding/finish`, sem corpo.

- Em `FIRST_WORK`, marca `DONE` e responde **204**, com ou sem obra cadastrada.
- Em `DONE`, responde **204** sem alterar a conta: pode ser repetido.
- Em `TABLES`, responde **400**: é necessário escolher as tabelas primeiro.

O cadastro da primeira obra usa a API de obras existente. Este endpoint não
cria obras nem exige que uma obra exista para concluir o tutorial.

## Erros

Listas inválidas e transições fora de ordem retornam **400** com `message`
em português. Requisições sem login válido retornam **401**.

## Validação

Os testes unitários cobrem as regras de etapa, listas inválidas, idempotência e
preservação dos perfis existentes. Os testes de integração cobrem os três
cadastros, os idiomas, o contrato HTTP autenticado, a migração V10 → V11 e
pedidos simultâneos. Precisam de Postgres.

Para executar a suíte inteira em banco descartável (use outra porta livre se
55432 estiver ocupada):

```bash
docker run -d --name mr-onboarding-test-db -e POSTGRES_PASSWORD=x -e POSTGRES_DB=myrank_test -p 127.0.0.1:55433:5432 postgres:15
./mvnw test -Dspring.datasource.url=jdbc:postgresql://localhost:55433/myrank_test -Dspring.datasource.username=postgres -Dspring.datasource.password=x
docker rm -f mr-onboarding-test-db
./mvnw -q -DskipTests compile
```

No Windows, use `./mvnw.cmd`. Remova o contêiner também se a execução falhar.
