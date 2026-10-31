# Chat

Conversas diretas (DM) e grupos, sob `/api/chat`, sempre com login.

## Conversas

- **DM:** duas pessoas. Abrir a mesma DM duas vezes devolve a que já existe
  (inclusive com dois pedidos ao mesmo tempo). Hoje **qualquer pessoa pode abrir
  DM com qualquer outra**, mesmo com perfil privado (em aberto no ROADMAP).
- **Sugestões de DM:** quem a pessoa segue de volta e com quem ainda não conversa.
- **Grupo:** nome (até 80), descrição (até 300), foto por URL `https` e até
  **50 participantes**.

## Acesso ao grupo

| Acesso | Pelo diretório | Pelo link de convite |
|---|---|---|
| `OPEN` | Entra na hora | Entra na hora |
| `REQUEST` | Pede, e um moderador aprova ou recusa | Entra na hora |
| `CLOSED` | Não entra | Entra na hora |

- O **diretório** lista grupos de que a pessoa não participa, 30 por página.
- O **link de convite** é reutilizável e pode ser trocado ou revogado (o
  antigo para de valer). Quem tem o link entra, qualquer que seja o acesso.

## Cargos

`OWNER > ADMIN > MOD > MEMBER`.

| Pode | Quem |
|---|---|
| Aprovar pedidos de entrada, expulsar, apagar mensagem de qualquer um | Dono, admin, mod |
| Adicionar participantes, editar nome/foto/acesso, mexer no link de convite | Dono, admin |
| Promover e rebaixar (até admin) | Dono, admin |
| Excluir o grupo | Só o dono |

Só se age sobre quem tem cargo abaixo do seu, e o cargo do dono não muda.
Quando o dono sai, o grupo passa para o próximo da hierarquia; grupo vazio é
apagado.

## Mensagens

- Até 2000 caracteres; histórico em páginas de até 100.
- Responder, editar, apagar (vira uma "lápide": a mensagem some, o lugar fica)
  e reagir com um emoji por pessoa, de uma lista fixa de 10.
- Entradas, saídas, convites e mudanças no grupo geram mensagens de sistema,
  em português.
- "Digitando…", "Visto" nas DMs e contador de não lidas.

## Tempo real

- WebSocket STOMP em `/ws` (SockJS). O `CONNECT` exige JWT válido, com a mesma
  checagem de versão de sessão das requisições normais.
- Cada pessoa só se inscreve nos próprios canais (`/user/queue/chat` e
  `/user/queue/chat-events`). A participação é conferida a cada envio: quem foi
  removido para de receber na hora.
- Mensagem só entra pela API REST; publicar direto no broker é proibido.
- O tempo real é um extra: **a API REST é a fonte da verdade**. Se o WebSocket
  cair, o front continua por consulta periódica. A consulta de não lidas também
  marca a presença.

## Exclusão de conta

As DMs da pessoa somem (a outra ponta não tem com quem falar). Dos grupos, ela
sai como numa saída normal, antes de a conta ser apagada.
