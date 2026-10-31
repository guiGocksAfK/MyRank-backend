package br.com.myrank.service.ai;

import br.com.myrank.dto.insight.InsightPayloadDTO;

import java.util.stream.Collectors;

/** Monta os prompts mandados pro modelo. */
final class InsightPromptBuilder {

    private InsightPromptBuilder() {}

    /**
     * Versão do prompt. Entra no hash do cache: mudou o prompt, sobe a versão, e uma
     * análise antiga não é reaproveitada como se fosse do formato novo.
     */
    static final String VERSION = "v2";

    static final String SYSTEM = """
            Você analisa o gosto de uma pessoa a partir do ranking dela no MyRank.
            Responde SEMPRE com um único objeto JSON válido, sem texto fora do JSON,
            sem markdown, sem backticks. Escreva em português do Brasil, em segunda
            pessoa ("você"), com tom direto e um pouco de humor.

            O que vale:
            - Ser específico: toda afirmação se apoia em obras do ranking, citadas pelo
              nome e com a nota entre parênteses, como "Breaking Bad (10)".
            - Mostrar padrões que só ESTE ranking tem: um criador favorito, um tipo ou
              gênero com média bem acima ou abaixo dos outros, algo dito nos takes, o
              rigor das notas. Se o texto caberia no perfil de qualquer pessoa, está errado.
            - Usar os FATOS como verdade: médias, rigor e tensões já foram calculados.
              Não invente números nem notas.
            - Os takes são opiniões escritas pela própria pessoa: são o melhor sinal do
              gosto dela.

            O que é proibido:
            - Frases genéricas que serviriam pra qualquer pessoa, como "narrativas
              profundas", "personagens complexos", "histórias envolventes", "boas
              histórias", "gosto eclético", "gosto refinado", "obras-primas".
            - Citar obra que não está no ranking como se a pessoa tivesse avaliado.
            - Recomendar uma obra que já está no ranking.
            """;

    static final String CHAT_SYSTEM = """
            Você é o assistente do MyRank conversando com a pessoa sobre o gosto dela.
            Você recebe a análise que ela acabou de ver, o ranking completo dela (uma obra
            por linha) e fatos calculados sobre as notas.

            Responda qualquer pergunta sobre o ranking, as notas, as obras, o gosto e
            recomendações: qual a série mais bem avaliada, por que algo combina com ela,
            o que ver em seguida. Use os dados: cite obras pelo nome e com a nota. Se a
            resposta não está nos dados, diga que não sabe; nunca invente notas ou obras
            que ela teria avaliado. Ao recomendar, não sugira o que já está no ranking.

            Português do Brasil, segunda pessoa, direto, no máximo 2 parágrafos curtos.
            Texto puro, sem markdown, sem títulos.
            """;

    static String user(TasteFacts facts) {
        return """
                RANKING (título | tipo | criador | ano | nota | tempo | posição na tabela | gêneros | take):
                %s
                FATOS:
                %s
                Responda com este JSON (troque os valores):
                {
                  "summaryTitle": "título curto e personalizado do perfil, ex.: 'O Colecionador de Anti-heróis'",
                  "summaryText": "2 a 3 frases sobre o gosto da pessoa, citando pelo menos 2 obras com nota e pelo menos 1 número dos FATOS",
                  "traits": [
                    { "icon": "um emoji", "label": "nome curto do traço", "description": "1 a 2 frases citando pelo menos 2 obras com nota" }
                  ],
                  "contradictions": [
                    { "icon": "um emoji", "text": "1 frase sobre uma nota que contraria o padrão, ex.: 'Ama terror psicológico, mas deu 4 pra Hereditário'" }
                  ],
                  "recommendation": {
                    "title": "obra que NÃO está no ranking",
                    "year": 2000,
                    "category": "Filme, Série, Anime, Mangá, Jogo, Livro, Música ou Álbum",
                    "reason": "2 frases ligando a recomendação a pelo menos 2 obras do ranking, com nota"
                  }
                }

                Regras:
                - traits: exatamente 3, diferentes entre si e com obras diferentes; cada um
                  nasce de um padrão dos FATOS ou dos takes, não do tipo de obra.
                - contradictions: de 0 a 2, só a partir das TENSÕES dos fatos; lista vazia se não houver.
                - Responda SOMENTE o JSON.
                """.formatted(facts.ranking(), facts.summary());
    }

    /** Contexto do chat: a análise, o ranking e os fatos. */
    static String chatContext(InsightPayloadDTO a, TasteFacts facts) {
        String traits = a.traits() == null ? "—" : a.traits().stream()
                .map(t -> t.label() + " (" + t.description() + ")")
                .collect(Collectors.joining("; "));
        String contradictions = a.contradictions() == null || a.contradictions().isEmpty() ? "—"
                : a.contradictions().stream()
                    .map(InsightPayloadDTO.Contradiction::text)
                    .collect(Collectors.joining("; "));
        var r = a.recommendation();
        String reco = r == null ? "—"
                : r.title() + (r.year() != null ? " (" + r.year() + ")" : "")
                  + (r.category() != null ? ", " + r.category() : "")
                  + ": " + r.reason();

        return """
                ANÁLISE QUE A PESSOA VIU:
                Título: %s
                Resumo: %s
                Traços: %s
                Contradições: %s
                Recomendação: %s

                RANKING (título | tipo | criador | ano | nota | tempo | posição na tabela | gêneros | take):
                %s
                FATOS:
                %s""".formatted(a.summaryTitle(), a.summaryText(), traits, contradictions, reco,
                facts.ranking(), facts.summary());
    }
}
