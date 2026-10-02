# Segurança — MyRank API

Resumo das proteções em vigor e do que ainda precisa de decisão. O frontend tem
as proteções do lado dele (CSP e headers via `vercel.json`) descritas no
repositório do front.

## Em vigor

| Área | Como |
|---|---|
| Senhas | BCrypt com salt por usuário (custo padrão do Spring, 10). Nunca voltam em resposta. Mínimo de 8 caracteres na criação e na troca. Conta criada pelo Google ou Discord não tem senha. |
| Cadastro | O email é provado **antes** de existir conta: código de 6 dígitos por email, e só com ele o cadastro final é aceito. Quem digita o email de outra pessoa não consegue reservar nada. |
| Códigos por email | Cadastro, recuperação de senha e exclusão de conta usam código. Ele fica só como SHA-256 (nunca o valor), vale 15 minutos (10 na exclusão), morre depois de 5 erros e tem intervalo de 30s entre reenvios. Comparação em tempo constante. |
| Passes entre etapas | Acertar o código gera um passe curto (JWT) que libera a etapa seguinte. Cada fluxo assina com uma chave derivada do segredo do login + propósito: o passe do cadastro não troca senha, e nenhum passe serve como sessão. |
| Login | Conta social, email inexistente e senha errada dão a **mesma** mensagem. "Email não confirmado" só aparece depois que a senha confere, pra não revelar quais emails têm cadastro pendente. |
| Recuperação de senha | Email sem conta segue o mesmo caminho de uma conta real (o código é gerado, só não é mandado), então resposta e erros não revelam quem tem conta. Exceção decidida: conta só com Google/Discord é avisada na hora de qual provedor usar. |
| Sessão | JWT HS256 de 24h. `JWT_SECRET` é obrigatório no boot (mínimo 32 caracteres). A cada requisição o usuário é lido do banco. Cada token carrega a **versão de sessão** da conta: trocar a senha aumenta a versão e derruba todas as sessões abertas, em todos os aparelhos, na hora. |
| Autenticação | Toda rota exige token, menos as abertas de propósito: `/api/auth/**`, `POST /api/users` (cadastro final, que exige o passe do código), `GET /api/health`, `GET /api/external/showcase` e o handshake do WebSocket. Sem login válido a resposta é **401**; sem permissão, **403**. |
| Autorização | Dono conferido em toda operação de tabela, obra, subdivisão e campo próprio. Perfil privado: obras, takes e comparação só pra quem segue. O item só aceita um template que a tabela tenha. |
| Chat em tempo real | O `CONNECT` do STOMP exige JWT válido (com a mesma checagem de versão de sessão). Cada inscrição é autorizada por conversa, e publicar direto no broker é proibido: mensagem só entra pela API. |
| Bot do Discord | Header `X-Bot-Key` comparado em tempo constante, válido só num conjunto fechado de rotas. A pessoa precisa ter vinculado o Discord à conta. Rate limit por usuário do Discord (e não pelo IP do bot). |
| Rate limiting | Janela fixa de 1 minuto, em memória, por IP: login 10, OAuth 15, envio de código 3, conferência de código 10, cadastro 5, exclusão de conta 5, busca externa 40. Não confia em `X-Forwarded-For` direto: atrás do Caddy, o Tomcat só aceita o IP real vindo da rede interna (`SERVER_FORWARD_HEADERS_STRATEGY=native`). |
| Validação de entrada | Bean Validation nos DTOs, com tamanho máximo nos textos. O `details` de cada obra é sempre um objeto JSON de até 8 KB. Campos próprios validados pelo tipo: texto até 200, número finito, data real, sim/não sem conversão. |
| Concorrência | Gravar uma obra e mudar os campos ou templates da tabela não acontecem ao mesmo tempo (trava na linha da tabela), pra um campo removido não voltar por uma gravação simultânea. |
| Vazamento de erro | Exceção não mapeada vira um 500 genérico, sem stack trace. Erros de regra viram 400 com mensagem em português. |
| Dados guardados | Tokens e códigos de uso único só como hash. Exclusão de conta é definitiva (não é soft delete), confirmada por código no email, com comprovante depois. |
| Headers HTTP | Padrões do Spring Security: HSTS (1 ano, com subdomínios; o Spring reconhece o HTTPS que chega pelo Caddy), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` e `Cache-Control: no-store`. Conferido na produção. |
| CORS | Origens explícitas em `CORS_ALLOWED_ORIGINS`, sem curinga. |
| Segredos | Tudo por variável de ambiente. O `.env` da VM não vai pro git. |
| Banco | Neon com TLS obrigatório (`sslmode=require`). Schema sob Flyway, e o Hibernate só valida (`ddl-auto=validate`). |

## Pendências e decisões abertas

1. **JWT no `localStorage` do front.** Um XSS no front conseguiria roubar o
   token e usar por até 24h. A defesa de pé hoje é a CSP restritiva do front.
   O ideal é cookie `httpOnly`, mas front (Vercel) e API (duckdns) são sites
   diferentes: o cookie precisaria de `SameSite=None`, o que abre uma
   superfície de CSRF que hoje não existe (token no header `Authorization` é
   imune a CSRF). O caminho certo, nesta ordem e junto:
   1. pôr front e API sob o mesmo domínio (rewrite `/api` na Vercel, ou o Caddy
      servindo os dois);
   2. aí sim cookie `httpOnly; Secure; SameSite=Strict`;
   3. no front, ler o usuário por `GET /api/users/me` em vez do token.

2. **Backups sem criptografia e só na VM.** O dump diário guarda 14 dias, mas
   em texto puro e no mesmo servidor da API: se a VM for perdida, sobra só o
   PITR do Neon. Falta o que a Escola Imaculada já tem: dump criptografado com
   `age` na origem, cópia no Backblaze B2 com Object Lock e alerta de falha
   (healthchecks.io).

3. **Estado em memória: só funciona com uma instância.** O rate limit, os
   códigos por email e o cache das buscas de música ficam na memória do
   processo. Com mais de uma instância, cada uma teria o próprio limite e os
   próprios códigos (um código enviado por uma não valeria na outra). Reiniciar
   a API também zera os códigos pendentes (a pessoa só pede outro). Se um dia
   houver mais de uma instância, mover os três pra um store compartilhado
   (Redis).

4. **Contêiner roda como root.** O `Dockerfile` não troca de usuário. Correção
   simples: criar um usuário sem privilégio na imagem final e usar `USER`.

5. **Custo do BCrypt no padrão (10).** Subir pra 12 dobra duas vezes o trabalho
   de quem tenta quebrar hashes vazados (~200ms por login, imperceptível).
   Hashes antigos continuam válidos, porque o custo vai gravado no próprio hash,
   e migram sozinhos na próxima troca de senha.

6. **Logout não revoga o token no servidor.** Sair apaga o token no navegador,
   mas um token copiado antes continua válido até expirar (24h). A revogação
   imediata hoje só acontece trocando a senha (versão de sessão). Um "sair de
   todos os aparelhos" seria só aumentar essa versão, e foi descartado por ora.

7. **Sem bloqueio por conta no login.** O limite é por IP; alguém com muitos IPs
   pode tentar senhas numa mesma conta devagar. Opções: atraso progressivo por
   conta, ou código por email depois de N erros.

8. **Sem CSP nem `Referrer-Policy` na API.** A API manda os padrões do Spring
   Security (ver "Em vigor"), mas não esses dois. Como ela só devolve JSON, o
   risco é baixo; as duas fazem diferença mesmo é no front, que já as tem.

9. **Avatar por URL externa.** A foto de perfil é um link que a própria pessoa
   informa e que o navegador de quem vê o perfil carrega direto. Quem controla
   o servidor da imagem enxerga o IP de quem abriu o perfil. Alternativa:
   upload com armazenamento próprio, ou proxy de imagens.

10. **LGPD: sem exportação dos dados.** A exclusão de conta existe; a
    portabilidade (baixar os próprios dados) ainda não.

11. **Sem auditoria e sem análise de dependências automática.** Não há trilha
    de quem alterou o quê, e não há CI rodando os testes nem checando CVEs das
    dependências (por exemplo com o OWASP Dependency-Check).
