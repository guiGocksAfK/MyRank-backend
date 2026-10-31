# Notas, ranking unificado e criadores

## Nota e nota final

Cada obra tem `score` de 0 a 10 e `timeMinutes` (0 a 1.000.000). Ao salvar, o
back calcula:

```
timeBonusScore = log10(timeMinutes / 60)     (arredondado em 2 casas)
finalScore     = score + timeBonusScore
```

- O bônus só vale quando há minutos registrados e o template da obra tem
  `timeWeighted` (`TableTemplate`): Filmes, Séries, Jogos, Livros, Animes e
  Personalizado. **Músicas, Álbuns e Mangás não têm bônus**: o tempo não diz
  nada sobre o quanto a pessoa gostou.
- 1h dá bônus 0; 10h dá +1; 100h dá +2. Menos de 1h dá bônus **negativo**
  (30 min = −0,30): uma obra curta perde um pouco quando a ponderação está ligada.
- `finalScore` é sempre gravado, mas a **ponderação é opcional**: a tela mostra a
  nota pura até a pessoa ligar o botão "Ponderação por tempo".
- Mudar o template de uma obra recalcula a nota final.

## Tabelas

- Cada tabela lista as próprias obras (`GET /api/works/category/{id}`).
- **Subcategorias** são divisões dentro da tabela (ex.: "Zerados", "Dropados").
  Apagar uma subcategoria não apaga as obras: elas só ficam sem subcategoria.
- Templates, detalhes e campos próprios: [templates.md](templates.md).

## Ranking unificado

Junta várias tabelas numa lista só.

- O front monta a lista com as obras das tabelas escolhidas e ordena ali mesmo.
  (`GET /api/works/unified` traz todas as obras da conta por `finalScore`; é o
  que o perfil e o rodapé usam.)
- **A seleção e a ordem manual ficam num agrupamento** (`/api/master-table-groups`)
  chamado "Unificado", criado pelo front no primeiro acesso com todas as tabelas
  marcadas. Cada conta só pode ter um agrupamento com o mesmo nome, e só com
  tabelas próprias.
- **Ordenação:** por nota (pura ou ponderada) ou por tempo.
- **Ordem manual só desempata notas iguais.** Uma obra só pode ser solta sobre
  outra com a mesma nota (no celular, as setas trocam com a vizinha de mesma
  nota); ordenado por tempo, não arrasta. A ordem é
  gravada como lista de chaves `tabelaId:obraId` (`PUT /api/master-table-groups/{id}/order`).
  A ordenação por nota é estável sobre essa lista, então ela só decide empates;
  obras fora da lista vão para o fim dos seus empates.
- **Filtros** por ano de lançamento e por data em que a obra foi adicionada.

## Ranking de criadores

Calculado só no front (`CreatorsPanel`), a partir das obras da conta: diretores,
escritores, estúdios e criadores, cada um com as obras dele.

- Nota do criador: média das notas das obras dele (obras com nota 0 ficam de fora).
- Com a ponderação ligada: média das notas **ponderada pelos minutos** de cada
  obra, mais um bônus de volume `log10(número de obras)`, com teto de +1. O
  resultado nunca passa de 12.
- Também dá para ordenar por número de obras.

## Afinidade (comparar com outra pessoa)

Também só no front (`computeTasteMatch`, em `socialData.js`), e é a mesma conta
usada no exemplo da home.

- Obras em comum são achadas pelo **título** (sem diferenciar maiúsculas).
- `afinidade = 100 − (média das diferenças absolutas de nota × 12)`, limitada
  entre 0 e 100. Uma diferença média de 1 ponto dá 88%.
- Mostra as 3 maiores concordâncias, as 3 maiores discordâncias (diferença de
  pelo menos 1 ponto) e os favoritos em comum (as duas notas ≥ 8,5).
