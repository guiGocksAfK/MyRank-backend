# Social: perfis, seguir, feed e takes

Tudo sob `/api/social`, sempre com login.

## Perfil público ou privado

- Conta nova começa **privada**; a pessoa abre o perfil quando quiser.
- **Público:** qualquer um vê obras, conquistas e takes, e seguir é na hora.
- **Privado:** quem não segue vê só o cabeçalho (nome, foto, bio) e as
  contagens de seguidores e seguindo. Obras, conquistas, takes e a comparação
  de gosto ficam fechados. Perfis privados aparecem na busca e nas sugestões,
  pra dar para pedir para seguir.

## Seguir

`POST /api/social/follow/{id}` alterna o vínculo:

| Situação | Efeito |
|---|---|
| Já segue | Deixa de seguir |
| Alvo público | Segue na hora e notifica |
| Alvo privado, sem pedido | Cria pedido pendente e notifica |
| Alvo privado, com pedido | Cancela o pedido |

O dono do perfil privado aprova ou recusa em `/follow-requests/{id}/approve` e
`/reject`. Aprovar notifica quem pediu; cancelar, recusar ou aprovar some com a
notificação "quer te seguir".

## Feed

- **Takes e avaliações agrupadas.** Cada take é um card. Avaliar (adicionar obra
  ou mudar a nota) nunca aparece solto: vira um card por pessoa por dia, tipo
  "avaliou 5 obras", com até 8 capas e o total de obras diferentes. Ganhar
  conquista vira evento, mas não aparece.
- Aparecem as coisas de quem a pessoa segue, as dela mesma e as de **qualquer
  perfil público** (é a timeline geral). Com `?scope=following` sai a timeline
  geral e fica só quem ela segue e ela mesma ("Só quem eu sigo"). O filtro é no
  back, e não no front, pra página não vir cheia de desconhecidos e sumir.
- Ordem do mais novo para o mais antigo. A página é de até 50 **takes**; as
  avaliações de cada página são as que caem entre o último take da página
  anterior e o último desta. Só entram avaliações dos últimos 30 dias, porque o
  backfill criou um evento por obra antiga.
- O card agrupado tem id negativo (não é um evento só) e não recebe reação nem
  comentário.
- Na primeira subida com o social, cada obra já cadastrada virou um evento
  com a data dela, pra o histórico não nascer vazio.

## Takes

- Opinião escrita sobre **uma obra da própria pessoa**: de 3 a 280 caracteres.
- Editar marca como editado; apagar leva junto comentários, reações e
  notificações.
- Postar um take notifica cada seguidor.

## Reações

Três tipos em cada take: `up`, `agree`, `disagree`, uma por pessoa (mandar de
novo troca ou tira). Só reage quem consegue ver o take. As notificações de
reação são agrupadas por take e tipo.

## Comentários

- Dois níveis, como no Instagram: responder a uma resposta vira resposta do
  comentário raiz. Até 500 caracteres.
- Só comenta quem consegue ver o take.
- Apaga o comentário quem escreveu ou o dono do take; as respostas vão junto.
- Notifica o autor do take e, numa resposta, o autor do comentário respondido.

## Comparar gosto

A afinidade entre duas pessoas sai das obras em comum (fórmula em
[ranking.md](ranking.md#afinidade-comparar-com-outra-pessoa)). Só funciona com
quem a pessoa pode ver.

- **Na tela de comparar e no perfil**, o front calcula com a lista inteira.
- **Nas listas** (seguindo, seguidores, sugestões, busca), o back manda
  `sharedCount` (obras em comum), `matchPct` e a obra da maior briga
  (`fightTitle`, `fightMine`, `fightTheirs`), com a mesma conta. A % só vem com
  **5 obras ou mais em comum**; com menos, um número sozinho engana.
- **Sugestões:** a conta roda sobre as 200 contas mais novas que a pessoa ainda
  não segue (as obras de todas numa consulta só) e volta as 20 de % mais alta;
  sem % vão pro fim. Perfil privado que ela não segue não entra na conta.
- **Pra debater** (`GET /api/social/debaters`): até 10 pessoas de % mais baixa,
  com **8 obras ou mais em comum** e % até 60. Entra quem ela já segue também.

## Notificações

`/api/notifications`: reações, novo seguidor, pedido para seguir, pedido
aceito, take novo de quem a pessoa segue, comentário, adicionado a um grupo e
entrada em grupo aprovada. Os textos são gerados em português no back.
Uma falha ao notificar ou gravar evento nunca derruba a ação principal.
