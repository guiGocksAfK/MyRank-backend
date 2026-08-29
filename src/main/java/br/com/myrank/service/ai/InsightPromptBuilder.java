package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.Work;

import java.util.List;

/** Monta os prompts mandados pro modelo. Espelho do antigo buildPrompt do front. */
final class InsightPromptBuilder {

    private InsightPromptBuilder() {}

    static final String SYSTEM = """
            Você é um analista de perfil de consumo de mídia. Responde SEMPRE com um
            único objeto JSON válido, sem texto fora do JSON, sem markdown, sem backticks.
            Baseie-se apenas nas obras fornecidas — nunca invente títulos que o usuário
            teria avaliado. Escreva em português do Brasil.
            """;

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
