package br.com.myrank.service;

import java.text.Normalizer;

/**
 * Infere o "tipo" de uma obra (jogo / filme / serie / livro / anime / outro)
 * a partir do nome da categoria. Mesma lógica do front (useUnifiedItems.js).
 */
public final class WorkTypeResolver {

    private WorkTypeResolver() {}

    public static String fromCategoryName(String categoryName) {
        if (categoryName == null) return "outro";
        String s = Normalizer.normalize(categoryName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        if (s.matches(".*(livro|book).*")) return "livro";
        if (s.matches(".*(jogo|game).*")) return "jogo";
        if (s.contains("anime")) return "anime";
        if (s.matches(".*(serie|series|show|\\btv\\b).*")) return "serie";
        if (s.matches(".*(filme|movie).*")) return "filme";
        return "outro";
    }
}
