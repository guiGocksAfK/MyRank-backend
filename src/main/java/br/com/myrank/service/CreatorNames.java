package br.com.myrank.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * O campo "criador" de uma obra pode ter mais de uma pessoa: as APIs juntam os nomes
 * com vírgula ("Vince Gilligan, Peter Gould"), e quem digita à mão às vezes usa ";"
 * ou " & ". Quem agrupa por criador (conquistas, IA, tela de Criadores) separa aqui,
 * pra Better Call Saul contar pro Vince Gilligan junto com Breaking Bad. Não quebra
 * no " e ": ele aparece dentro de nome de estúdio.
 */
public final class CreatorNames {

    private static final Pattern SEPARATORS = Pattern.compile("\\s*(?:,|;|\\s&\\s)\\s*");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private CreatorNames() {}

    /** Cada pessoa do campo, sem espaços sobrando e sem repetir (na ordem em que aparecem). */
    public static List<String> split(String creator) {
        if (creator == null || creator.isBlank()) return List.of();
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String part : SEPARATORS.split(creator.trim())) {
            String name = SPACES.matcher(part).replaceAll(" ").trim();
            if (!name.isEmpty()) byKey.putIfAbsent(key(name), name);
        }
        return new ArrayList<>(byKey.values());
    }

    /** Chave de comparação: "Studio Pierrot" e "studio  pierrót" viram a mesma pessoa. */
    public static String key(String name) {
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return SPACES.matcher(noAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** As chaves de cada pessoa do campo. */
    public static List<String> keys(String creator) {
        return split(creator).stream().map(CreatorNames::key).toList();
    }
}
