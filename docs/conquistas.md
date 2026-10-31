# Conquistas

48 badges, calculadas no back a partir das obras e da conta.

## Catálogo

O catálogo é o enum `BadgeDefinition`: cada badge tem código, grupo, nome,
descrição, ícone, meta e a regra que calcula o progresso. A cada subida da API,
`BadgeCatalogInitializer` sincroniza a tabela `badges` com o enum (cria,
atualiza e remove pelo código). Por isso **mudar nome, descrição ou meta não
precisa de migration**, e uma badge removida do enum some também de quem já
tinha.

| Grupo | Quantas | Exemplos |
|---|---|---|
| Jogos | 6 | 5 e 25 jogos, 500h em jogos, um jogo com 100h+, 10 estúdios |
| Filmes | 6 | 10 e 50 filmes, filme antes de 1970, filme com 3h+ |
| Séries | 6 | 5 e 20 séries, 300h em séries, média ≥ 8 com 10 séries |
| Livros | 6 | 5, 20 e 50 livros, 10 autores, livro antes de 1950 |
| Animes | 6 | 5 e 25 animes, 200h em animes, anime antes de 2000 |
| Gerais | 12 | uma obra de cada tipo, 5 décadas diferentes, crítico rigoroso, nota ≤ 2 |
| Uso do site | 6 | primeira obra, foto e bio, tabela própria, editar uma nota, 7 dias diferentes, 1 ano de conta |

Os grupos por tipo usam o tipo do template da obra (`TableTemplate.type()`).
**Músicas, Álbuns e Mangás ainda não têm badges próprias**; só contam nas
gerais que olham todas as obras (anotado no ROADMAP).

## Cálculo

- A regra devolve um progresso bruto; a badge é conquistada quando ele alcança a
  meta. Com `hasProgress`, a tela mostra barra; sem, é só liga/desliga.
- **Conquistada fica conquistada**, mesmo que a pessoa apague a obra depois.
- Criar, editar e apagar obra disparam o recálculo **em segundo plano**
  (`badgeExecutor`), para não atrasar a resposta. Abrir a tela de conquistas
  também recalcula.
- Cada badge nova gera um evento (que não aparece no feed) e o front mostra um
  aviso quando ela é conquistada.
- Um erro no recálculo nunca derruba a gravação da obra.

## Textos

Os textos do enum estão em português; o front traduz por código
(`badgeI18n.js`). Badge nova precisa da tradução em EN e ES.
