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

- **Só takes entram no feed** (avaliar, adicionar obra e ganhar conquista viram
  eventos, mas não aparecem).
- Aparecem os takes de quem a pessoa segue, os dela mesma e os de **qualquer
  perfil público** (é a timeline geral).
- Ordem do mais novo para o mais antigo, páginas de até 50.
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

A afinidade entre duas pessoas é calculada no front a partir das obras em
comum (fórmula em [ranking.md](ranking.md#afinidade-comparar-com-outra-pessoa)).
Só funciona com quem a pessoa pode ver.

## Notificações

`/api/notifications`: reações, novo seguidor, pedido para seguir, pedido
aceito, take novo de quem a pessoa segue, comentário, adicionado a um grupo e
entrada em grupo aprovada. Os textos são gerados em português no back.
Uma falha ao notificar ou gravar evento nunca derruba a ação principal.
