# Roadmap

O que falta fazer, na ordem combinada. Quando algo ficar pronto, sai daqui (o
histórico fica no git). Decisões já tomadas ficam em [decisoes.md](decisoes.md).

## Estado atual

Em produção: squash das migrations (baseline na V8), templates, campos próprios,
tutorial pós-cadastro, cadastro e recuperação de senha por código, sessões que
caem ao trocar a senha, e a IA Insights refeita (rápida e específica). Back e
front na branch `remaster-e-ajustes`.

## 1. Pendências antes do remaster

- **Testar a recuperação de senha na produção**, conferindo que a sessão de
  outro navegador cai.
- **Preencher os gêneros das obras antigas.** As obras criadas antes de o site
  guardar gêneros estão sem eles, e a IA cai pra média por tipo. Rodar uma vez o
  mesmo "Atualizar dados" em todas as obras que vieram de alguma API.

## 2. IA Insights: o que ficou de fora

Feito: o chat vê o ranking, dados completos por obra (com takes), fatos
calculados no código, traços que citam obras, contradições, perfil de gosto com
médias reais, raciocínio desligado (~24s → ~3s), nova tentativa automática,
temperatura menor, teto de tamanho da resposta, versão do prompt no cache e
resposta no idioma da conta (`PT`, `EN`, `ES`).

Falta, sem data:

- Analisar uma tabela só.

## 3. Remaster das telas internas

Home, login e cadastro já foram refeitos. Falta o mesmo tratamento (base
`base.css`, padrão preto e dourado) no dashboard, rankings, social, chat,
perfil e IA. O visual das funcionalidades novas (seletor de templates, campos
próprios, tutorial, "Atualizar dados") foi deixado de propósito para esta etapa.

Navbar: feita (moldura comum com a home, abas sem emoji com fio dourado, menu
do avatar com idioma e Sair, barra de ícones no celular). Ficou com cara
genérica; ideias testadas e guardadas pra voltar depois:

- holofote dourado saindo de baixo da aba ativa e a borda de baixo como um fio
  de luz que acende no centro (identidade de "palco", como a home);
- ícones de traço ao lado das abas também no desktop;
- anel dourado no avatar.

## 4. Fundos das tabelas (depois do remaster)

- Cada tabela escolhe um fundo de uma lista pronta, sem upload.
- 3 a 5 fundos por tipo (Filmes, Séries, Animes, Mangás, Jogos, Livros, Músicas,
  Álbuns) e uns 10 livres (Personalizado e mistas). Começar com 3 por tipo e 6
  livres (~30) e expandir os favoritos.
- **Onde:** tela inteira, como ambiente (estilo Netflix/Spotify). A tabela fica
  num painel escuro sólido por cima; o fundo aparece nas bordas, no topo e nos
  respiros, nunca atrás das notas.
- **Como:** só CSS e SVG, nada de imagem. Gradientes em camadas (luz dourada
  fraca), grão de filme em SVG, padrão temático quase invisível, vinheta.
  Movimento lento opcional, desligado com `prefers-reduced-motion`. Funciona no
  tema claro e no celular.
- **Motivos:** 🎬 perfuração de rolo · 📺 linhas de TV · 🎌 ondas japonesas ·
  📖 retícula de mangá · 🎮 grade de pixels · 📚 textura de papel ·
  🎵 sulcos de vinil · 💿 brilho de CD.
- **Back:** uma coluna em `categories` (ex.: `background = 'anime-2'`), migration
  nova, validação no código (fundo do tipo da tabela ou livre), sem `CHECK`.
- **Front:** seletor com miniaturas em "Editar tabela"; fundos num registro só,
  como `tableTemplates.js`.
- **Regra de ouro:** pouco chamativo. Se competir com o conteúdo, está errado.

## Ideias sem data

- **Podcasts** como template (descartado por ora; voltar se houver público).
- Pendências de segurança: lista completa no [SECURITY.md](../SECURITY.md).
