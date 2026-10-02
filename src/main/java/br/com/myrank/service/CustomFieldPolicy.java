package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.domain.model.CustomField;
import br.com.myrank.dto.CustomFieldRequestDTO;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validação compartilhada das definições da tabela e dos valores dos itens CUSTOM. */
final class CustomFieldPolicy {

    private static final int MAX_FIELDS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CustomFieldPolicy() {}

    static List<CustomField> definitions(List<CustomField> current, List<CustomFieldRequestDTO> requested) {
        if (requested == null) throw new IllegalArgumentException("Informe a lista completa de campos próprios.");
        if (requested.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Uma tabela pode ter no máximo 5 campos próprios.");
        }
        Map<String, CustomField> existing = new HashMap<>();
        current.forEach(field -> existing.put(field.id(), field));
        Set<String> reservedIds = new HashSet<>(existing.keySet());
        Set<String> selectedIds = new HashSet<>();
        Set<String> selectedNames = new HashSet<>();
        List<CustomField> next = new ArrayList<>();

        for (CustomFieldRequestDTO request : requested) {
            if (request == null || request.name() == null) {
                throw new IllegalArgumentException("Informe o nome de cada campo próprio.");
            }
            String name = request.name().trim();
            int length = name.codePointCount(0, name.length());
            if (length < 1 || length > 40) {
                throw new IllegalArgumentException("O nome do campo próprio deve ter de 1 a 40 caracteres.");
            }
            if (selectedNames.stream().anyMatch(name::equalsIgnoreCase)) {
                throw new IllegalArgumentException("A tabela já tem um campo próprio com esse nome.");
            }
            selectedNames.add(name);
            if (request.type() == null) {
                throw new IllegalArgumentException("Informe o tipo do campo próprio: TEXT, NUMBER, DATE ou BOOLEAN.");
            }
            String id = request.id();
            if (id == null) {
                id = newId(reservedIds);
            } else {
                CustomField previous = existing.get(id);
                if (previous == null) throw new IllegalArgumentException("Campo próprio não encontrado nesta tabela.");
                if (previous.type() != request.type()) {
                    throw new IllegalArgumentException("Não é possível trocar o tipo de um campo próprio. Remova o campo e crie outro.");
                }
            }
            if (!selectedIds.add(id)) throw new IllegalArgumentException("Um campo próprio não pode aparecer duas vezes na lista.");
            next.add(new CustomField(id, name, request.type()));
        }
        return next;
    }

    static Map<String, Object> values(Category category, TableTemplate template, Map<String, Object> details) {
        if (details == null) return null;
        Map<String, Object> normalized = new LinkedHashMap<>(details);
        if (!details.containsKey("fields")) return normalized;
        if (template != TableTemplate.CUSTOM) {
            throw new IllegalArgumentException("Campos próprios só podem ser usados em itens Personalizados (CUSTOM).");
        }
        if (!(details.get("fields") instanceof Map<?, ?> fields)) {
            throw new IllegalArgumentException("Os valores dos campos próprios devem formar um objeto em details.fields.");
        }
        Map<String, CustomField> definitions = new HashMap<>();
        category.getCustomFields().forEach(field -> definitions.put(field.id(), field));
        Map<String, Object> checked = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            if (!(entry.getKey() instanceof String id) || !definitions.containsKey(id)) {
                throw new IllegalArgumentException("O valor informado pertence a um campo próprio que não existe nesta tabela.");
            }
            Object value = entry.getValue();
            if (value == null) continue; // null remove o valor, sem apagar a definição do campo.
            CustomField definition = definitions.get(id);
            boolean valid = switch (definition.type()) {
                case TEXT -> value instanceof String text && text.codePointCount(0, text.length()) <= 200;
                case NUMBER -> finiteNumber(value);
                case DATE -> validDate(value);
                case BOOLEAN -> value instanceof Boolean;
            };
            if (!valid) {
                String rule = switch (definition.type()) {
                    case TEXT -> "texto de até 200 caracteres";
                    case NUMBER -> "número finito";
                    case DATE -> "data válida no formato yyyy-MM-dd";
                    case BOOLEAN -> "true ou false";
                };
                throw new IllegalArgumentException("O campo \"" + definition.name() + "\" deve receber " + rule + ".");
            }
            checked.put(id, value);
        }
        normalized.put("fields", checked);
        return normalized;
    }

    private static boolean finiteNumber(Object value) {
        if (!(value instanceof Number)) return false;
        if (value instanceof Double number) return Double.isFinite(number);
        if (value instanceof Float number) return Float.isFinite(number);
        return true;
    }

    private static boolean validDate(Object value) {
        if (!(value instanceof String date) || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String newId(Set<String> reservedIds) {
        String id;
        do {
            byte[] random = new byte[6];
            RANDOM.nextBytes(random);
            id = "f_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        } while (!reservedIds.add(id));
        return id;
    }
}
