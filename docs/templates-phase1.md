# Templates — fase 1

Esta entrega prepara o modelo para músicas, álbuns, mangás e campos
personalizados. As integrações e cards dessas fases ainda não fazem parte dela.

## Contrato

`categories.template` define o template padrão da tabela. `works.template`
guarda a classificação do próprio item, permitindo misturar conteúdos em
tabelas livres. Renomear uma tabela ou trocar seu template não reclassifica
os itens já salvos.

Valores JSON: `movie`, `tv`, `game`, `book`, `anime`, `custom`. O banco guarda
os mesmos valores em maiúsculas. Identificadores desconhecidos retornam 400.

- POST `/api/categories`: `{ "name": "🎬 Favoritos", "template": "movie" }`.
  Sem template, cria uma tabela `custom`.
- PUT `/api/categories/{id}`: aceita `name` e `template`. Template omitido
  mantém o valor atual.
- POST `/api/works`: aceita `template` e `details`, além dos campos existentes.
  Template omitido usa o padrão da tabela; details omitido vira `{}`.
- PUT `/api/works/{id}`: template ou details omitidos/null mantêm o conteúdo;
  `details: {}` limpa os detalhes. Um objeto substitui os detalhes anteriores.
- As respostas de categorias e obras incluem o template. Obras também
  retornam `details`, incluindo objetos aninhados.

O formulário usa o template da tabela como seleção inicial da busca. Após
selecionar uma sugestão, salva `details.provider` e `details.externalId`.
Edições comuns preservam os detalhes. Trocar o tipo no formulário limpa a
proveniência da seleção anterior. Perfil, criadores, conquistas, feed e
notificações usam o template do item, sem analisar o nome da tabela.

## V9 e dados antigos

V1–V8 não foram alteradas. A V9 é transacional no PostgreSQL e adiciona as
colunas e constraints sem recriar tabelas. O backfill usa o emoji/nome antigo
uma única vez; essa inferência não existe no código da aplicação.

Na tabela mista Séries & Animes, capas do MyAnimeList identificam animes e
capas do TMDB identificam séries. Obras de outros tipos ou sem informação
suficiente ficam na tabela original livre. As não classificadas recebem
`details.legacyClassificationRequired: true`, e podem ser classificadas pelo
seletor de tipo já existente ao editar o item.

Sem obras ambíguas, a tabela original vira Séries conservando o ID; Animes
recebe sua própria tabela. Com obras ambíguas, a tabela original permanece
intacta e ambas as tabelas específicas são criadas. Os itens identificados
são movidos com suas subdivisões equivalentes. IDs de obras, notas, datas,
takes e referências sociais são preservados. Os grupos que incluíam a tabela
mista passam a incluir as tabelas separadas; a ordem de desempate é remapeada.

Cadastros novos recebem cinco tabelas: Filmes, Jogos, Livros, Séries e Animes.
A escolha das tabelas no primeiro acesso fica para a fase 6.

## Aplicação e validação

Em um banco que já registra V1–V8, basta iniciar o backend atualizado: Flyway
aplica a V9. Não use baseline para pular a V9. Bancos ainda no histórico
antigo V1–V16 precisam concluir o procedimento do squash documentado em
`application.properties` antes de aplicar esta versão. Faça backup antes de
atualizar um banco com dados, e publique o frontend correspondente depois do backend.

Os testes de migration criam um schema temporário, aplicam V1–V8, inserem
fixtures legadas e aplicam V9. Conferem casos vazios, com origem conhecida e
ambíguos, subdivisões, referências sociais, notas e ordem do ranking.
Os testes de persistência conferem renomeação, mudança de template,
tabelas mistas, detalhes aninhados e updates parciais.

Para executar toda a suíte em um banco descartável local:

```powershell
docker exec myrank-db createdb -U postgres myrank_phase1_test
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/myrank_phase1_test" "-Dspring.datasource.username=postgres" "-Dspring.datasource.password=postgres" clean package
docker exec myrank-db dropdb -U postgres myrank_phase1_test
```

O nome acima é exclusivo de teste. Nunca substitua o nome por `myrank` no
comando de remoção. No frontend: `npm run build` e `npm run lint`.
