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
- **Nota ponderada pelo tempo é opcional**, ligada por um botão. Não vale para
  Músicas, Álbuns e Mangás, onde o tempo gasto não diz nada sobre o quanto a
  pessoa gostou.
- **Ordem manual só desempata notas iguais.** Arrastar não pode contradizer a
  nota; a nota continua sendo a verdade.

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
