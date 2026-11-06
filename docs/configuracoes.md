# Configurações

Página dentro do Perfil (o ⚙). Cada ajuste salva na hora pelo `PUT /api/users/me`;
o `GET /api/users/me` devolve o estado atual.

| Seção | Ajuste | Campo | Onde vale |
|---|---|---|---|
| Aparência | Modo escuro | (só no navegador) | todo o site |
| Aparência | Idioma | `language` | todo o site e os emails |
| Rankings | Nota ponderada ligada | `weightedByDefault` | Dashboard e Rankings já abrem em "Ponderada" |
| Rankings | Visualização padrão | `rankingsView`: `LIST` \| `GRID` | Rankings já abrem em lista ou grade |
| Privacidade | Perfil público | `isPublic` | ver social.md |
| Privacidade | Quem pode te mandar mensagem | `dmPolicy`: `EVERYONE` \| `FOLLOWING` | `ChatService.startDirect` |
| Notificações | Grupos de aviso | `mutedNotifications`: lista de `NotificationGroup` | `NotificationService` |
| Conta | Como você entra | `authProvider` (só leitura): `LOCAL` \| `GOOGLE` \| `DISCORD` | — |
| Conta | Trocar senha | fluxo do "esqueci a senha" (código no email) | só contas com senha (`hasPassword`) |
| Conta | Sair dos outros aparelhos | `POST /api/users/me/sessions/revoke` | todos os tokens antigos |
| Conta | Sair desta conta | só no front (apaga o token deste aparelho) | este aparelho |
| Seus dados | Exportar obras | feito no front, a partir do `/works/unified` | — |
| Seus dados | Excluir conta | ver autenticacao.md | — |

## Regras

- **Mensagens:** com `FOLLOWING`, só abre conversa nova quem a pessoa segue. As
  conversas que já existem continuam abertas.
- **Avisos:** cada `NotificationType` pertence a um `NotificationGroup`
  (`FOLLOWS`, `REACTIONS`, `COMMENTS`, `TAKES`, `GROUPS`). Aviso de um grupo
  silenciado não é criado. Lista vazia recebe tudo; grupo desconhecido no
  `PUT` é recusado, e um grupo que saiu do código é ignorado na leitura.
- **Sessões:** sobe `users.token_version`; o token novo vem na resposta.
  Trocar a senha faz o mesmo.
- **Banco:** `V14__configuracoes.sql` (`dm_policy`, `muted_notifications`,
  `weighted_by_default`) e `V15__visualizacao_rankings.sql` (`rankings_view`),
  sem `CHECK`: os valores são validados no código.
