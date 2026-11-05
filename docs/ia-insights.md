# IA Insights

Análise do gosto da pessoa a partir do ranking dela, com um chat de perguntas
em cima. Modelo: Gemini 2.5 Flash, pelo plano gratuito, pela camada compatível
com a API da OpenAI (`gemini.api.*`): trocar de provedor é trocar configuração.

## Divisão do trabalho

**O código calcula, a IA interpreta.** Antes de chamar o modelo, `TasteFacts`
monta tudo o que dá pra saber sem IA; o modelo recebe isso pronto e só escreve
os textos. Assim os números da tela são reais e o texto tem de onde tirar
coisas específicas.

| Parte da análise | Quem faz |
|---|---|
| Título, resumo, 3 traços, contradições, 3 recomendações | IA |
| Perfil de gosto (média e quantidade por gênero ou tipo) | Código |
| Rigor, médias, criadores, tensões (base pras contradições) | Código, mandado pra IA |

## O que a IA recebe

**Uma linha por obra**, das melhores pras piores notas finais (até 100 obras):

```
"Breaking Bad" | série | Vince Gilligan | 2008 | nota 10 | 58h | #1 em 📺 Séries | Drama, Crime, Thriller | take: "o melhor final da TV"
```

- Tempo só nos templates com ponderação (`TableTemplate.timeWeighted`): a
  duração de uma música não diz nada.
- Posição dentro da própria tabela, até 3 gêneros e o take mais recente,
  cortado em 160 caracteres. Os takes são o melhor sinal do gosto.

**Fatos:**

- **Rigor:** média geral, quantas notas 10 e um rótulo (generoso a partir de
  8,5 de média, exigente até 6,5, equilibrado no meio).
- **Médias por tipo**, por **gênero** e por **criador** (gênero e criador só
  com 2 ou mais obras).
- **Tensões:** obras com nota pelo menos 2,5 abaixo da média do próprio gênero
  ou tipo (grupos de 3 ou mais obras), no máximo 4, uma vez por obra. São a
  matéria-prima das contradições ("ama terror, mas deu 4 pra Hereditário").

**Perfil de gosto da tela:** os gêneros com mais obras, quando há pelo menos 3
gêneros com 2 ou mais obras; senão, os tipos. Obras criadas antes de o site
guardar gêneros caem nesse segundo caso.

## O prompt

`InsightPromptBuilder`. Regras principais:

- Toda afirmação cita obras do ranking com a nota, como "Breaking Bad (10)".
- Os traços nascem de padrões que só esse ranking tem (criador favorito, tipo
  ou gênero muito acima dos outros, um take, o rigor). Se caberia no perfil de
  qualquer pessoa, está errado.
- Lista de frases genéricas proibidas ("narrativas profundas", "personagens
  complexos", "gosto eclético"…).
- Três recomendações, de tipos diferentes quando o ranking tem mais de um tipo.
  O prompt proíbe recomendar o que já está no ranking, e o código confere de
  novo: tira as que já estão no ranking (título sem diferenciar maiúsculas), as
  repetidas e as sem título, e corta em 3 (`withCleanRecommendations`).
- Análises de antes do prompt v3 têm uma recomendação só (`recommendation`); ao
  ler, ela vira a lista `recommendations` com um item, e o front só olha a lista.
- Contradições: de 0 a 2, só a partir das tensões.

`VERSION` entra no hash do cache. **Mudou o prompt, sobe a versão**: senão uma
análise antiga é reaproveitada como se fosse do formato novo.

## Chamada ao modelo

`GeminiClient`:

- **Raciocínio interno desligado** (`gemini.api.reasoning-effort=none`). Ligado,
  o modelo gastava ~4.000 tokens "pensando" e a análise levava ~24s; desligado,
  ~3s, com o mesmo JSON válido.
- **Temperatura** 0,5 na análise e 0,6 no chat.
- **Teto de resposta** (`max_tokens`): 2048 na análise (usa ~650) e 1024 no
  chat. Sem ele, no modo JSON o modelo às vezes não para de gerar.
- **Duas tentativas:** 429, 5xx, sem resposta, conteúdo vazio e JSON quebrado
  tentam de novo depois de 1s. 4xx não repete (é erro nosso).
- Timeout de leitura de 30s; o front espera até 75s nas rotas da IA (cobre as
  duas tentativas).
- Falha que sobra vira 503 com mensagem clara.

## Cache e orçamento

- Cada análise é guardada pelo hash de (modelo + versão do prompt + obras e
  notas). A mesma seleção não chama o modelo de novo; "Gerar novamente" força.
- **15 mensagens de IA por dia por pessoa**, zerando às 6h (`AiUsageService`).
  Gerar análise e cada pergunta do chat contam 1; reaproveitar do cache não
  conta; falha do provedor devolve a mensagem.
- O front só libera a análise a partir de 5 obras.

## Chat

Recebe a análise que a pessoa viu, o ranking completo no mesmo formato de uma
linha por obra e os fatos. Responde qualquer pergunta sobre o ranking, as notas,
o gosto e recomendações; se a resposta não está nos dados, diz que não sabe.

## Formato salvo

`InsightPayloadDTO` em `ai_insights.payload`. Análises antigas trazem
`tasteProfile[].percent` e `recommendation.compatPercent` (números que a IA
inventava); as novas trazem `average` e `count` no lugar. O front mostra os
dois formatos.

## Testes

`TasteFactsTest` (linhas, fatos, tensões, perfil de gosto) e `GeminiClientTest`
(contra um servidor HTTP local: raciocínio desligado, novas tentativas, 4xx sem
repetir). Nenhum teste chama o Gemini de verdade.
