# Roadmap

O que falta fazer, na ordem combinada. Quando algo ficar pronto, sai daqui (o
histórico fica no git). Decisões já tomadas ficam em [decisoes.md](decisoes.md).

## Estado atual

Back e front na branch `seguranca-e-novasTabelas`, tudo commitado:
squash das migrations, templates (fases 1 a 5), tutorial pós-cadastro (fase 6),
cadastro por código e sessões que caem ao trocar a senha. A produção ainda está
na versão anterior.

## 1. Teste geral no navegador

Feito na conta existente (tabela mista, Músicas, Álbuns, Mangás, Personalizada,
"Atualizar dados", ranking unificado). Falta, **depois do deploy**:

- Conta nova: cadastro com código, tutorial inteiro e retomar no meio.
- Recuperação de senha, conferindo que a sessão de outro navegador cai.

Localmente o Brevo recusa o envio porque o IP de casa não está autorizado
(Brevo → Security → Authorised IPs). Na produção, o IP da VM precisa estar
autorizado, ou o bloqueio por IP desligado.

## 2. Consertar a IA Insights

Problemas relatados: lenta, falha muito e o conteúdo é genérico ("não fala nada
específico da tabela"). Itens 1 a 4 primeiro, junto com os de velocidade.

**Qualidade**

1. O chat recebe o ranking da pessoa (compacto) e responde perguntas sobre ele.
   Hoje só recebe o resumo da análise e é instruído a recusar o resto.
2. Mais dados por obra: template, criador, ano, gêneros (já guardados no
   `details`), posição na tabela e os takes.
3. Números calculados no código, não pela IA: média por tipo, gêneros mais
   frequentes e mais bem avaliados, criadores favoritos, se a pessoa é exigente.
   A IA só interpreta.
4. Exigir especificidade: cada traço cita pelo menos duas obras com nota, uma
   seção de contradições ("ama terror, mas deu 4 pra Hereditário"), frases
   genéricas proibidas.
5. Três recomendações, uma por tipo principal, explicadas com obras da pessoa.
   O código descarta o que ela já avaliou.
6. Responder no idioma da conta e permitir analisar uma tabela só.
7. Robustez: temperatura menor, uma nova tentativa se o JSON vier quebrado, e a
   versão do prompt no hash do cache.

**Velocidade e falhas** (feito)

- Raciocínio do Gemini 2.5 Flash desligado (`gemini.api.reasoning-effort`).
  Com 45 obras: de ~24s para ~3,3s, com o JSON válido em todas as medições.
- Nova tentativa automática em 429, 5xx, sem resposta e JSON quebrado; 4xx
  não repete.
- O front esperava no máximo 30s e cortava análises lentas; as chamadas da IA
  agora esperam até 75s.
- O prompt compacto entra junto com a reescrita da qualidade.

Depois de pronto, escrever `docs/ia-insights.md`.

## 3. Publicar

Seguir [deploy/README.md](../deploy/README.md#atualizar-a-produção-para-esta-versão):
backup no Neon, baseline na V8, V9–V11, desligar o baseline. Antes, mesclar
`seguranca-e-novasTabelas` na `master` (back) e na `main` (front). Back e front
sobem juntos.

Sem pressa: hoje só o Guilherme usa o site.

## 4. Remaster das telas internas

Home, login e cadastro já foram refeitos. Falta o mesmo tratamento (base
`base.css`, padrão preto e dourado) no dashboard, rankings, social, chat,
perfil e IA. O visual das funcionalidades novas (seletor de templates, campos
próprios, tutorial, "Atualizar dados") foi deixado de propósito para esta etapa.

## 5. Fundos das tabelas (depois do remaster)

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
- Apagar as colunas do fluxo antigo de link (`email_verification_*`,
  `password_reset_*`) quando não houver mais conta pendente desse fluxo.
