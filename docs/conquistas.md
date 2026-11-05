# Conquistas

72 badges, calculadas no back a partir das obras e da conta.

## Catálogo

O catálogo é o enum `BadgeDefinition`: cada badge tem código, grupo, nome,
descrição, ícone, meta e a regra que calcula o progresso. A cada subida da API,
`BadgeCatalogInitializer` sincroniza a tabela `badges` com o enum (cria,
atualiza e remove pelo código). Por isso **mudar nome, descrição ou meta não
precisa de migration**, e uma badge removida do enum some também de quem já
tinha.

| Grupo | Quantas | Exemplos |
|---|---|---|
| Jogos | 6 | 5 e 25 jogos, 500h em jogos, jogo avaliado no ano em que saiu, 10 estúdios |
| Filmes | 6 | 10 e 50 filmes, filme antes de 1970, 3 filmes do mesmo diretor |
| Séries | 6 | 5 e 20 séries, 300h em séries, série antes de 1990, série de até 10h |
| Livros | 6 | 5, 20 e 50 livros, 10 autores, 5 livros do mesmo autor |
| Animes | 6 | 5 e 25 animes, 200h em animes, anime avaliado no ano em que saiu |
| Mangás | 6 | 5 e 25 mangás, 5 autores, 3 do mesmo autor, 3 décadas |
| Músicas | 6 | 10 e 50 músicas, 10 artistas, 5 do mesmo artista, 4 décadas |
| Álbuns | 6 | 5 e 25 álbuns, 10 artistas, 3 do mesmo artista, álbum antes de 1980 |
| Outros (Personalizado) | 6 | 5 e 25 obras, 50h, obras em 3 tabelas, 10 com nota ≥ 9 |
| Gerais | 12 | uma obra de cada tipo, 5 décadas diferentes, crítico rigoroso, 5 notas ≤ 5 |
| Uso do site | 6 | primeira obra, foto e bio, tabela própria, editar uma nota, 7 dias diferentes, 1 ano de conta |

Os grupos por tipo usam o tipo do template da obra (`TableTemplate.type()`).
Músicas, Álbuns e Mangás não têm badge de horas: nesses templates o tempo não
diz nada (ver decisões). "Polivalente" continua pedindo só os 5 tipos
originais.

**Sem "dê nota 10 a um X" por tipo:** é um clique, não uma conquista. A nota 10
aparece só em "Dez de dez" (10 obras com nota 10). Também não há badge de média
alta por tipo: média alta não é bom gosto.

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
