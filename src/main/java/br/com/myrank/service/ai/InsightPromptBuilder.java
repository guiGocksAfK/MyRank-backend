package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.insight.InsightPayloadDTO;

import java.util.List;
import java.util.stream.Collectors;

/** Monta os prompts mandados pro modelo. Espelho do antigo buildPrompt do front. */
final class InsightPromptBuilder {

    private InsightPromptBuilder() {}

    static final String SYSTEM = """
            Você é um analista de perfil de consumo de mídia. Responde SEMPRE com um
            único objeto JSON válido, sem texto fora do JSON, sem markdown, sem backticks.
            Baseie-se apenas nas obras fornecidas — nunca invente títulos que o usuário
            teria avaliado. Escreva em português do Brasil.
            """;

    /** Chat de follow-up: o usuário conversa sobre a análise que acabou de receber. */
    static final String CHAT_SYSTEM = """
            Você é o assistente do MyRank conversando com o usuário sobre a análise de
            perfil de consumo de mídia que ele acabou de receber. Responda em português
            do Brasil, de forma direta e útil, em no máximo 2 parágrafos curtos.
            Baseie-se na análise e nos dados fornecidos; se perguntarem algo fora desse
            escopo, diga gentilmente que você só pode falar sobre o perfil e as
            recomendações dele. Nunca invente obras que o usuário teria avaliado.
            Texto puro, sem markdown, sem títulos.
            """;

    /** Resumo em texto da análise, usado como contexto do chat. */
    static String chatContext(InsightPayloadDTO a) {
        String traits = a.traits() == null ? "—" : a.traits().stream()
                .map(t -> t.label() + " (" + t.description() + ")")
                .collect(Collectors.joining("; "));
        String taste = a.tasteProfile() == null ? "—" : a.tasteProfile().stream()
                .map(s -> s.name() + " " + s.percent() + "%")
                .collect(Collectors.joining("; "));
        var r = a.recommendation();
        String reco = r == null ? "—"
                : r.title() + (r.year() != null ? " (" + r.year() + ")" : "")
                  + (r.category() != null ? ", " + r.category() : "")
                  + " — " + r.compatPercent() + "% compatível: " + r.reason();

        return """
                ANÁLISE GERADA PARA O USUÁRIO:
                Título do perfil: %s
                Resumo: %s
                Traços: %s
                Perfil de gosto: %s
                Recomendação: %s
                """.formatted(a.summaryTitle(), a.summaryText(), traits, taste, reco);
    }

    static String user(List<Work> works) {
        StringBuilder list = new StringBuilder();
        int i = 1;
        for (Work w : works) {
            String category = w.getCategory() != null ? w.getCategory().getName() : "—";
            list.append(i++).append(". \"").append(w.getTitle()).append("\" (").append(category)
                    .append(") — nota ").append(stripZeros(w.getScore()))
                    .append("/10, tempo: ").append(w.getTimeMinutes()).append("min\n");
        }

        return """
                RANKING DO USUÁRIO:
                %s
                Responda com este JSON exato (troque os valores):
                {
                  "summaryTitle": "título curto e personalizado do perfil (ex: 'O Explorador de Mundos Épicos')",
                  "summaryText": "parágrafo de 2-3 frases descrevendo o perfil de consumo deste usuário com base nos dados",
                  "traits": [
                    { "icon": "emoji", "label": "nome do traço", "description": "frase curta explicando este traço" },
                    { "icon": "emoji", "label": "nome do traço", "description": "frase curta explicando este traço" },
                    { "icon": "emoji", "label": "nome do traço", "description": "frase curta explicando este traço" }
                  ],
                  "tasteProfile": [
                    { "name": "gênero/categoria", "percent": 0 },
                    { "name": "gênero/categoria", "percent": 0 },
                    { "name": "gênero/categoria", "percent": 0 },
                    { "name": "gênero/categoria", "percent": 0 },
                    { "name": "gênero/categoria", "percent": 0 }
                  ],
                  "recommendation": {
                    "title": "título da obra recomendada",
                    "year": 2000,
                    "category": "Filme/Série/Jogo/Livro/Anime",
                    "compatPercent": 0,
                    "reason": "2 frases explicando por que esta obra combina com o perfil acima"
                  }
                }

                Regras:
                - tasteProfile: exatamente 5 gêneros/categorias com percent de 0 a 100, refletindo o perfil real.
                - recommendation: uma obra que o usuário provavelmente ainda não consumiu, compatPercent entre 70 e 99.
                - Responda SOMENTE o JSON.
                """.formatted(list.toString());
    }

    private static String stripZeros(java.math.BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }
}
