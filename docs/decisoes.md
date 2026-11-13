# Decisões

Por que o MyRank é como é. Cada item diz o que foi decidido e o motivo, para
ninguém desfazer uma escolha sem saber que ela foi feita de propósito. Decisão
nova entra na seção certa; decisão revista é editada aqui, não apagada sem
deixar o motivo.

## Produto

- **O site serve para ranquear qualquer coisa, mas o foco é cultura pop.** O
  visual e os templates prontos são de filmes, séries, animes, mangás, jogos,
  livros e música; o Personalizado acolhe o resto (comida, matérias da escola).
  Times e streamers ficaram de fora: pouco público e estranho de ranquear.
- **Template é o tipo do conteúdo, não a API.** Ele decide o card, a busca e as
  conquistas. Os templates com API externa são os mais completos; sem API só
  existe o Personalizado.
- **Séries e Animes são templates separados, e uma tabela pode misturar
  templates** (ex.: Séries + Animes, Livros + Mangás), escolhidos na criação.
  O Guilherme ranqueia séries e animes juntos; outras pessoas querem separado.
  Adicionar template depois pode sempre; tirar, só se nenhum item usar.
- **Personalizado tem até 5 campos próprios** (texto, número, data, sim/não),
  definidos pela própria pessoa.
- **O site traz os dados; a pessoa só dá a nota.** Por isso não existe campo
  "plataforma" (onde jogou/assistiu): pedir mais preenchimento tira essa sensação.
- **Gêneros são guardados, mas não aparecem no card.** Servem para a IA
  Insights entender o gosto. Ficam como a API manda, sem tradução.
- **O tipo do anime só aparece quando não é TV** (Filme, OVA, ONA…): "TV" é o
  caso comum e só ocuparia espaço.
- **Botão "Atualizar dados" no item**, para obras em andamento (One Piece,
  série no ar).
- **No feed social, a linha de detalhes do card mostra só o criador.**
- **Avaliações voltaram ao feed, mas só agrupadas** (2026-11): um card por
  pessoa por dia ("avaliou 5 obras", com as capas), nunca uma avaliação solta.
  Antes o feed era só de takes, para não virar spam de nota; o agrupamento
  resolve isso e deixa o feed vivo quando ninguém escreve. Só olham 30 dias pra
  trás, porque o backfill criou um evento por obra antiga. Conquistas continuam
  fora, e o card agrupado não recebe reação.
- **% de gosto parecido nas listas da Social**, calculada no backend com a
  mesma conta do Comparar. As sugestões vêm de quem tem o gosto mais parecido.
- **Conta do gosto parecido revista** (2026-11). A primeira versão só olhava a
  diferença das notas e tinha quatro falhas: 3 obras em comum valiam tanto
  quanto 50; quem dá 9 pra tudo combinava com todo mundo que também dá; "Duna"
  filme e livro contavam como a mesma obra; e não dava pra separar parecido de
  diferente. Agora: título + tipo, mistura da nota com o gosto relativo (o
  quanto cada um gostou acima ou abaixo da própria média, com mais peso), a %
  puxada pros 50% quando há poucas obras, e mínimo de 5 em comum. Os números
  mudaram pra todo mundo de propósito.
- **"Pra debater"**: quem tem o gosto mais diferente do seu, pra quem quer
  discutir. Pede 8 obras em comum (discordar com poucas é acaso) e % até 60, e
  mostra a obra da maior briga como gancho.
- **Nota ponderada pelo tempo é opcional**, ligada por um botão. Não vale para
  Músicas, Álbuns e Mangás, onde o tempo gasto não diz nada sobre o quanto a
  pessoa gostou.
- **Ordem manual só desempata notas iguais.** Arrastar não pode contradizer a
  nota; a nota continua sendo a verdade.
- **Criador com várias pessoas conta pra cada uma** ("Vince Gilligan, Peter
  Gould" soma pros dois), nas conquistas, na IA e na tela de Criadores. A regra
  de separar nomes fica num lugar só (`CreatorNames`, e `creatorNames.js` no front).
- **Na tela de Criadores, cada obra aparece uma vez sempre que dá:** quem fez as
  mesmas obras junto vira uma linha só ("David Benioff & D. B. Weiss") e quem só
  tem obras junto com alguém de mais obras aparece dentro dele ("com Peter
  Gould"). Duas carreiras próprias que se cruzam continuam aparecendo nas duas.
- **Conquistas: 6 por tipo, sem "nota 10 em X" e sem média alta.** Revisão de
  2026-11: as de nota 10 por tipo saíram (um clique só) e as de média alta
  também (premiam dar nota alta). No lugar, coisas que pedem hábito: avaliar no
  ano de lançamento, várias obras do mesmo criador, décadas, obras curtas.
  Mangás, Músicas, Álbuns e Personalizado ganharam 6 cada (72 no total). As
  substituídas foram removidas, não reaproveitadas: quem tinha perde, para
  ninguém ficar com conquista que não cumpriu.
- **Foto do criador vem da TMDB** (foto de pessoa ou logo de estúdio), guardada
  uma vez por nome e compartilhada entre todos. Só aceita nome igual, para não
  pegar homônimo; quando erra, a pessoa troca a foto (e o tipo) no "Editar", e
  isso vale só para ela.

## Contas e segurança

- **Cadastro: email → código → usuário e senha.** A conta só passa a existir
  depois que o email foi provado; ninguém reserva o email de outra pessoa.
- **Código de 6 dígitos em vez de link.** O link abria outra aba do MyRank, e a
  pessoa ficava com duas abertas; com o código, ela continua na mesma tela.
- **Códigos só na memória.** Simples, e reiniciar só obriga a pedir outro. Com
  mais de uma instância precisa de Redis (está no SECURITY.md).
- **Depois de trocar a senha, a pessoa já entra logada**, e as sessões dos outros
  aparelhos caem na hora (versão de sessão no token).
- **Sem botão "sair de todos os aparelhos"** por enquanto: não fez falta. Se um
  dia fizer, é só aumentar a versão de sessão.
- **Recuperação de senha não revela quem tem conta**, com uma exceção: conta só
  com Google/Discord é avisada na hora de qual provedor usar. Esconder isso só
  deixaria a pessoa esperando um código que nunca chega.
- **Exclusão de conta é definitiva e exige código por email**, inclusive para
  conta com senha. Depois vai um comprovante.
- **Conta nova começa privada.** Abrir o perfil é escolha da pessoa. Contas que
  já existiam mantiveram o que tinham.
- **Perfil privado funciona como no Instagram:** pedido para seguir, e quem não
  segue vê só o cabeçalho e as contagens.
- **"Visto por último" só entre quem se segue mutuamente**, e **só entra
  direto num grupo quem se segue mutuamente** com quem adiciona (o resto vem
  pelo link). Os dois fecham brechas de vigiar e de assediar desconhecidos.
- **Expulsão de grupo é banimento** até a moderação adicionar de novo; sair por
  conta própria não bane. **Quem entra vê o histórico de antes**, como no Discord.
- **Quem pode mandar mensagem é escolha de quem recebe:** todo mundo (padrão) ou
  só quem a pessoa segue. Vale para conversa nova; as que já existem continuam
  abertas, para ninguém perder um histórico por mudar o ajuste.
- **Avisos silenciados nem são criados**, em vez de escondidos na leitura: o
  contador de não lidas fica certo e o banco não guarda o que ninguém vai ver.
  O que se silencia são grupos (seguidores, reações, comentários, takes novos,
  grupos), não cada tipo, para a tela não virar uma lista de 8 chaves.
- **"Sair dos outros aparelhos" sobe a versão da sessão** (a mesma da troca de
  senha) e devolve um token novo para quem pediu, que continua logado.
- **Sessão em cookie `HttpOnly` (`SameSite=Strict`, `Secure`) + conferência de
  `Origin` nas escritas.** Tira o JWT do `localStorage` para que um XSS não
  consiga ler o token. Como o cookie vai sozinho, `SameSite=Strict` exige que
  front e API fiquem sob o mesmo domínio registrável (ex.: `myrank.dev` e
  `api.myrank.dev`) e o filtro recusa `POST`/`PUT`/`PATCH`/`DELETE` autenticados
  por cookie cujo `Origin` não esteja em `CORS_ALLOWED_ORIGINS`. Durante a
  transição o corpo do login continua trazendo `token` e o filtro ainda aceita
  `Authorization: Bearer`.
- **Exportar as obras é feito no navegador** (CSV ou JSON a partir do
  /works/unified), sem rota nova: os dados já chegam inteiros ao front.
- **Login com Google por botão próprio e código de autorização**, e não pelo
  botão oficial escondido. O botão oficial é um iframe do Google que ficava
  invisível por cima do botão dourado; ele parou de aceitar clique de mouse (só
  o teclado funcionava), e o Chrome trata um iframe transparente como "não
  visível". Agora o botão é nosso, abre a janela do Google e o back troca o
  código pelo token, igual ao Discord. Custo: a API precisa do
  `GOOGLE_CLIENT_SECRET`.

## Tutorial pós-cadastro

- **Escolher pelo menos uma tabela é obrigatório; a primeira obra, não.**
- **Sem botão de refazer o tutorial.** É curto e óbvio; refazer seria inútil.
- **Dicas no lugar certo** (nas telas vazias) em vez de um tour que ninguém lê.
- **A IA só analisa a partir de 5 obras.** Com menos, não há do que tirar
  conclusões.

## IA Insights

- **Gemini pelo plano gratuito**, pela camada compatível com a API da OpenAI:
  trocar de provedor é trocar configuração, não código.
- **15 mensagens de IA por dia por pessoa**, zerando às 6h. Gerar análise e cada
  pergunta do chat contam 1; reaproveitar uma análise do cache não conta, e
  falha do provedor não consome.
- **Análise em cache pela seleção** (obras + notas): a mesma seleção não gasta
  outra chamada.

## Banco e infraestrutura

- **Squash das 16 migrations em 8**, separadas por área, com baseline na
  produção. O histórico de ALTERs tinha virado ruído; o schema novo foi provado
  idêntico ao antigo comparando as "impressões digitais" dos dois.
- **Enums validados no código, sem `CHECK` no banco**, para valor novo não
  exigir migration.
- **API numa VM da Oracle (Always Free) em São Paulo**, e não no Render: o
  Render grátis levava mais de 3 minutos para acordar. Banco no Neon São Paulo,
  front na Vercel.
- **Pool de conexões sem conexão ociosa**, para o Neon poder desligar o banco
  quando ninguém usa.
- **Cache em memória (Caffeine)**, e não Redis: com uma instância é o suficiente,
  e o limite está documentado.
- **Deezer para música, com iTunes de reserva.** A Deezer não pede chave e é
  tem os dois catálogos (faixas e álbuns), mas não tem garantia de
  disponibilidade.

## Visual

- **Base única em `base.css`** (tokens, botões, painéis): as seções podem ser
  diferentes, mas a base é a mesma.
- **Preto e dourado, com pouco ruído.** Navbar fosca em todo o site; nada de
  emoji colorido brigando com o dourado; cinza "lápis" proibido.
- **Animações tocam uma vez** e respeitam `prefers-reduced-motion`.
- **Dados reais nos exemplos** da home (médias públicas de MAL, IMDb,
  Metacritic): nota inventada passa impressão ruim para quem conhece as obras.
- **Fundos das tabelas só em CSS e SVG**, pouco chamativos (ver ROADMAP).
