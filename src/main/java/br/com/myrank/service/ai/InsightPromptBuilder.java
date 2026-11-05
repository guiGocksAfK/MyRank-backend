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
    static final String VERSION = "v4";

    static final String SYSTEM = """
            Você analisa o gosto de uma pessoa a partir do ranking dela no MyRank.
            Responde SEMPRE com um único objeto JSON válido, sem texto fora do JSON,
            sem markdown, sem backticks. Escreva em português do Brasil, em segunda
            pessoa ("você"), com tom direto e um pouco de humor.

            O que vale:
            - Ser específico: toda afirmação se apoia em obras do ranking, citadas pelo
              nome (sem a nota entre parênteses: a pessoa já vê as notas no site). No
              máximo um número por texto, e só quando ele surpreende.
            - Mostrar padrões que só ESTE ranking tem: onde está o tempo (obras longas
              ou curtas, uma obra que domina as horas), a mistura de tipos, um criador
              com 3+ obras, algo dito nos takes. Se o texto caberia no perfil de qualquer
              pessoa, está errado.
            - Usar os FATOS como verdade: médias, faixa de notas, tempo e tensões já
              foram calculados. Não invente números nem notas.
            - Os takes são opiniões escritas pela própria pessoa: são o melhor sinal do
              gosto dela.

            O que é proibido:
            - Frases genéricas que serviriam pra qualquer pessoa, como "narrativas
              profundas", "personagens complexos", "histórias envolventes", "boas
              histórias", "gosto eclético", "gosto refinado", "obras-primas".
            - Citar obra que não está no ranking como se a pessoa tivesse avaliado.
            - Recomendar uma obra que já está no ranking.
            - Repetir o que a tela já mostra: a média geral, o número de obras, a lista
              de gêneros. Um traço que é só "gosta de ação/drama" está errado.
            - Falar em "notas altas" quando a FAIXA DE NOTAS for estreita: aí todas são
              altas e isso não diferencia nada.
            """;

    static final String CHAT_SYSTEM = """
            Você é o assistente do MyRank conversando com a pessoa sobre o gosto dela.
            Você recebe a análise que ela acabou de ver, o ranking completo dela (uma obra
            por linha) e fatos calculados sobre as notas.

            Responda qualquer pergunta sobre o ranking, as notas, as obras, o gosto e
            recomendações: qual a série mais bem avaliada, por que algo combina com ela,
            o que ver em seguida, se ela gostaria de uma obra.

            Duas fontes, cada uma no seu lugar:
            - Sobre a PESSOA (o que ela avaliou, as notas, o ranking): só os dados
              abaixo. Nunca invente nota ou obra que ela teria avaliado.
            - Sobre OBRAS de fora do ranking: use o que você sabe delas (gênero, tom,
              duração, estilo, público) e compare com obras do ranking pelo nome.
              Obra fora do ranking não é motivo pra dizer que não sabe.

            Quando perguntarem se ela gostaria de algo, dê um veredito ("provavelmente
            sim", "talvez" ou "provavelmente não") e o porquê, ligando a 1 ou 2 obras do
            ranking; diga também o que pode não agradar. Só diga que não sabe quando
            nem os dados nem o seu conhecimento ajudam.

            Cite obras pelo nome, sem a nota entre parênteses. Ao recomendar, não
            sugira o que já está no ranking. Português do Brasil, segunda pessoa,
            direto, no máximo 2 parágrafos curtos. Texto puro, sem markdown, sem títulos.
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
                  "summaryText": "2 frases sobre o que é característico desta pessoa, citando 1 ou 2 obras pelo nome; sem média e sem contagem de obras",
                  "traits": [
                    { "icon": "um emoji", "label": "nome curto do traço", "description": "1 a 2 frases citando obras pelo nome, sem nota" }
                  ],
                  "contradictions": [
                    { "icon": "um emoji", "text": "1 frase sobre uma nota que contraria o padrão, ex.: 'Ama terror psicológico, mas deu 4 pra Hereditário'" }
                  ],
                  "recommendations": [
                    {
                      "title": "obra que NÃO está no ranking",
                      "year": 2000,
                      "category": "Filme, Série, Anime, Mangá, Jogo, Livro, Música ou Álbum",
                      "reason": "1 a 2 frases curtas ligando a recomendação a UMA obra do ranking, pelo nome, sem nota"
                    }
                  ]
                }

                Regras:
                - traits: exatamente 3, diferentes entre si e com obras diferentes; cada um
                  nasce de um padrão dos FATOS ou dos takes (tempo, mistura de tipos,
                  criador com 3+ obras), não do tipo nem do gênero de obra, e se apoia em
                  pelo menos 3 obras ou num fato forte.
                - contradictions: de 0 a 2, só a partir das TENSÕES dos fatos; lista vazia se não houver.
                - recommendations: exatamente 3, obras diferentes entre si e de tipos
                  diferentes quando o ranking tiver mais de um tipo; cada uma ligada a
                  obras diferentes do ranking.
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
        String reco = a.recommendations() == null || a.recommendations().isEmpty() ? "—"
                : a.recommendations().stream()
                    .map(r -> r.title() + (r.year() != null ? " (" + r.year() + ")" : "")
                            + (r.category() != null ? ", " + r.category() : "")
                            + ": " + r.reason())
                    .collect(Collectors.joining("; "));

        return """
                ANÁLISE QUE A PESSOA VIU:
                Título: %s
                Resumo: %s
                Traços: %s
                Contradições: %s
                Recomendações: %s

                RANKING (título | tipo | criador | ano | nota | tempo | posição na tabela | gêneros | take):
                %s
                FATOS:
                %s""".formatted(a.summaryTitle(), a.summaryText(), traits, contradictions, reco,
                facts.ranking(), facts.summary());
    }
}
