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
temperatura menor, teto de tamanho da resposta e versão do prompt no cache.

Falta, sem data:

- Três recomendações (uma por tipo principal), descartando no código o que a
  pessoa já avaliou. Hoje é uma, e só o prompt pede pra não repetir.
- Responder no idioma da conta (hoje sempre em português) e analisar uma tabela só.

## 3. Remaster das telas internas

Home, login e cadastro já foram refeitos. Falta o mesmo tratamento (base
`base.css`, padrão preto e dourado) no dashboard, rankings, social, chat,
perfil e IA. O visual das funcionalidades novas (seletor de templates, campos
próprios, tutorial, "Atualizar dados") foi deixado de propósito para esta etapa.

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

- **Conquistas de Músicas, Álbuns e Mangás.** O catálogo só tem badges de jogos,
  filmes, séries, livros e animes; os templates novos não contam para nada.
- **Podcasts** como template (descartado por ora; voltar se houver público).
- **DM com perfil privado:** hoje qualquer pessoa pode abrir conversa com
  qualquer outra. Decidir se perfil privado só recebe DM de quem segue.
- Pendências de segurança: lista completa no [SECURITY.md](../SECURITY.md).
- **Notificações antigas de perfil que virou privado** (ou de quem deixou de
  seguir) continuam mostrando título, capa e nota da obra. Esconder a obra quando
  quem vê já não teria acesso a ela.
- Apagar as colunas do fluxo antigo de link (`email_verification_*`,
  `password_reset_*`) quando não houver mais conta pendente desse fluxo.
