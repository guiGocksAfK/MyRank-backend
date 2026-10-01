package br.com.myrank;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.model.CustomField;
import br.com.myrank.dto.CategoryCreateDTO;
import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.dto.CategoryUpdateDTO;
import br.com.myrank.dto.CustomFieldRequestDTO;
import br.com.myrank.dto.WorkCreateDTO;
import br.com.myrank.dto.WorkUpdateDTO;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.CategoryService;
import br.com.myrank.service.WorkService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static br.com.myrank.domain.enums.CustomFieldType.*;
import static br.com.myrank.domain.enums.TableTemplate.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica JSONB, limpeza dos valores e transações em Postgres descartável. */
@SpringBootTest
@Transactional
class CustomFieldsIntegrationTest {

    @Autowired CategoryService categories;
    @Autowired WorkService works;
    @Autowired CategoryRepository categoryRepository;
    @Autowired WorkRepository workRepository;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void limiteNomesETiposSaoValidadosSemAlterarDefinicoesSalvas() {
        User owner = user();
        Long tableId = table(owner, CUSTOM).getId();
        var five = IntStream.range(0, 5).mapToObj(i -> new CustomFieldRequestDTO(null, "Campo " + i, TEXT)).toList();
        var fields = categories.updateCustomFields(tableId, owner.getId(), five).getCustomFields();
        flush();
        assertThat(categoryRepository.findById(tableId).orElseThrow().getCustomFields()).isEqualTo(fields);
        assertThatThrownBy(() -> categories.updateCustomFields(tableId, owner.getId(),
                IntStream.range(0, 6).mapToObj(i -> new CustomFieldRequestDTO(null, "Campo " + i, TEXT)).toList()))
                .hasMessageContaining("no máximo 5");
        assertThatThrownBy(() -> categories.updateCustomFields(tableId, owner.getId(), List.of(
                new CustomFieldRequestDTO(null, "Nome", TEXT), new CustomFieldRequestDTO(null, "NOME", TEXT))))
                .hasMessageContaining("esse nome");
        assertThatThrownBy(() -> categories.updateCustomFields(tableId, owner.getId(), List.of(
                new CustomFieldRequestDTO(fields.get(0).id(), fields.get(0).name(), NUMBER))))
                .hasMessageContaining("Remova o campo e crie outro");
        assertThat(categoryRepository.findById(tableId).orElseThrow().getCustomFields()).isEqualTo(fields);
    }

    @Test
    void renomearPreservaIdEValores_eAdicionarNaoPreencheItensAntigos() {
        User owner = user();
        Long tableId = table(owner, CUSTOM).getId();
        CustomField original = categories.updateCustomFields(tableId, owner.getId(),
                List.of(new CustomFieldRequestDTO(null, "Local", TEXT))).getCustomFields().get(0);
        Long workId = work(owner, tableId, CUSTOM, Map.of("fields", Map.of(original.id(), "São Paulo"))).getId();

        var fields = categories.updateCustomFields(tableId, owner.getId(), List.of(
                new CustomFieldRequestDTO(original.id(), "Cidade", TEXT), new CustomFieldRequestDTO(null, "Preço", NUMBER)))
                .getCustomFields();
        flush();

        assertThat(fields.get(0).id()).isEqualTo(original.id());
        assertThat(workRepository.findById(workId).orElseThrow().getDetails().get("fields"))
                .isEqualTo(Map.of(original.id(), "São Paulo"));
        assertThat(categories.getCategoriesByUser(owner.getId()).get(0).getCustomFields()).isEqualTo(fields);
    }

    @Test
    void removerApagaValoresDeTodosOsItensDaTabela_ePreservaMetadadosEOutrasTabelas() {
        User owner = user();
        Long tableId = table(owner, CUSTOM, MOVIE).getId();
        var fields = categories.updateCustomFields(tableId, owner.getId(), List.of(
                new CustomFieldRequestDTO(null, "Local", TEXT), new CustomFieldRequestDTO(null, "Preço", NUMBER)))
                .getCustomFields();
        String removed = fields.get(0).id();
        String retained = fields.get(1).id();
        Long first = work(owner, tableId, CUSTOM, Map.of("provider", "manual", "fields", Map.of(removed, "A", retained, 0))).getId();
        Long second = work(owner, tableId, CUSTOM, Map.of("fields", Map.of(removed, "B"))).getId();
        Long movie = work(owner, tableId, MOVIE, Map.of("externalId", "123")).getId();
        Long otherTable = table(owner, CUSTOM).getId();
        String otherField = categories.updateCustomFields(otherTable, owner.getId(),
                List.of(new CustomFieldRequestDTO(null, "Local", TEXT))).getCustomFields().get(0).id();
        Long other = work(owner, otherTable, CUSTOM, Map.of("fields", Map.of(otherField, "C"))).getId();

        categories.updateCustomFields(tableId, owner.getId(), List.of(new CustomFieldRequestDTO(retained, "Preço", NUMBER)));
        flush();

        assertThat(workRepository.findById(first).orElseThrow().getDetails())
                .containsEntry("provider", "manual").containsEntry("fields", Map.of(retained, 0));
        assertThat(workRepository.findById(second).orElseThrow().getDetails().get("fields")).isEqualTo(Map.of());
        assertThat(workRepository.findById(movie).orElseThrow().getDetails()).isEqualTo(Map.of("externalId", "123"));
        assertThat(workRepository.findById(other).orElseThrow().getDetails().get("fields")).isEqualTo(Map.of(otherField, "C"));
        var recreated = categories.updateCustomFields(tableId, owner.getId(), List.of(
                new CustomFieldRequestDTO(retained, "Preço", NUMBER), new CustomFieldRequestDTO(null, "Local", TEXT)))
                .getCustomFields().get(1);
        assertThat(recreated.id()).isNotEqualTo(removed);
        assertThat(workRepository.findById(first).orElseThrow().getDetails().get("fields")).isEqualTo(Map.of(retained, 0));
    }

    @Test
    void osQuatroTiposSaoPersistidos_eValoresInvalidosSaoRecusadosNaCriacaoENaEdicao() {
        User owner = user();
        Long tableId = table(owner, CUSTOM).getId();
        var fields = categories.updateCustomFields(tableId, owner.getId(), List.of(
                new CustomFieldRequestDTO(null, "Texto", TEXT), new CustomFieldRequestDTO(null, "Número", NUMBER),
                new CustomFieldRequestDTO(null, "Data", DATE), new CustomFieldRequestDTO(null, "Ativo", BOOLEAN)))
                .getCustomFields();
        Map<String, Object> values = Map.of(fields.get(0).id(), "texto", fields.get(1).id(), 12.5,
                fields.get(2).id(), "2024-02-29", fields.get(3).id(), false);
        Long workId = work(owner, tableId, CUSTOM, Map.of("fields", values)).getId();
        flush();
        assertThat(workRepository.findById(workId).orElseThrow().getDetails().get("fields")).isEqualTo(values);

        List<Object> invalid = List.of("x".repeat(201), "12.5", "2023-02-29", "false");
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> bad = Map.of("fields", Map.of(fields.get(i).id(), invalid.get(i)));
            assertThatThrownBy(() -> work(owner, tableId, CUSTOM, bad)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> works.updateWork(workId, owner.getId(), update(null, bad)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(workRepository.findById(workId).orElseThrow().getDetails().get("fields")).isEqualTo(values);
    }

    @Test
    void nuloRemoveValor_eObjetoVazioLimpaDetailsSemApagarDefinicoes() {
        User owner = user();
        Long tableId = table(owner, CUSTOM).getId();
        String id = categories.updateCustomFields(tableId, owner.getId(),
                List.of(new CustomFieldRequestDTO(null, "Local", TEXT))).getCustomFields().get(0).id();
        Long workId = work(owner, tableId, CUSTOM, Map.of("fields", Map.of(id, "A"))).getId();
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put(id, null);
        works.updateWork(workId, owner.getId(), update(null, Map.of("provider", "manual", "fields", nullValue)));
        flush();
        assertThat(workRepository.findById(workId).orElseThrow().getDetails())
                .containsEntry("fields", Map.of()).containsEntry("provider", "manual");
        assertThat(categoryRepository.findById(tableId).orElseThrow().getCustomFields()).hasSize(1);
        works.updateWork(workId, owner.getId(), update(null, Map.of()));
        flush();
        assertThat(workRepository.findById(workId).orElseThrow().getDetails()).isEmpty();
    }

    @Test
    void camposExigemDono_eTabelaComCustom() {
        User owner = user();
        User stranger = user();
        Long tableId = table(owner, MOVIE).getId();
        assertThatThrownBy(() -> categories.updateCustomFields(tableId, owner.getId(),
                List.of(new CustomFieldRequestDTO(null, "Local", TEXT)))).hasMessageContaining("Só tabelas");
        Long custom = table(owner, CUSTOM).getId();
        assertThatThrownBy(() -> categories.updateCustomFields(custom, stranger.getId(), List.of())).hasMessageContaining("permissão");
        assertThat(categoryRepository.findById(custom).orElseThrow().getCustomFields()).isEmpty();
    }

    @Test
    void retirarCustomLimpaCampos_eReintroduzirCustomComecaSemCampos() {
        User owner = user();
        Long tableId = table(owner, CUSTOM, MOVIE).getId();
        categories.updateCustomFields(tableId, owner.getId(), List.of(new CustomFieldRequestDTO(null, "Local", TEXT)));
        work(owner, tableId, MOVIE, Map.of("provider", "tmdb"));
        CategoryUpdateDTO change = new CategoryUpdateDTO();
        change.setTemplates(List.of(MOVIE));
        assertThat(categories.updateCategory(tableId, owner.getId(), change).getCustomFields()).isEmpty();
        flush();
        assertThat(categoryRepository.findById(tableId).orElseThrow().getCustomFields()).isEmpty();
        change.setTemplates(List.of(MOVIE, CUSTOM));
        assertThat(categories.updateCategory(tableId, owner.getId(), change).getCustomFields()).isEmpty();
    }

    @Test
    void retirarCustomComItensContinuaRecusado_ePreservaCamposEValores() {
        User owner = user();
        Long tableId = table(owner, CUSTOM, MOVIE).getId();
        var fields = categories.updateCustomFields(tableId, owner.getId(), List.of(new CustomFieldRequestDTO(null, "Local", TEXT)))
                .getCustomFields();
        Long workId = work(owner, tableId, CUSTOM, Map.of("fields", Map.of(fields.get(0).id(), "A"))).getId();
        CategoryUpdateDTO change = new CategoryUpdateDTO();
        change.setTemplates(List.of(MOVIE));
        assertThatThrownBy(() -> categories.updateCategory(tableId, owner.getId(), change)).hasMessageContaining("ainda tem itens");
        assertThat(categoryRepository.findById(tableId).orElseThrow().getCustomFields()).isEqualTo(fields);
        assertThat(workRepository.findById(workId).orElseThrow().getDetails().get("fields"))
                .isEqualTo(Map.of(fields.get(0).id(), "A"));
    }

    @Test
    void camposDesconhecidosItensNaoCustom_eTrocaDeTemplateComValoresSaoRecusados() {
        User owner = user();
        Long tableId = table(owner, CUSTOM, MOVIE).getId();
        String id = categories.updateCustomFields(tableId, owner.getId(),
                List.of(new CustomFieldRequestDTO(null, "Local", TEXT))).getCustomFields().get(0).id();
        assertThatThrownBy(() -> work(owner, tableId, CUSTOM, Map.of("fields", Map.of("f_inexist", "A"))))
                .hasMessageContaining("não existe nesta tabela");
        assertThatThrownBy(() -> work(owner, tableId, MOVIE, Map.of("fields", Map.of(id, "A"))))
                .hasMessageContaining("só podem ser usados");
        Long workId = work(owner, tableId, CUSTOM, Map.of("fields", Map.of(id, "A"))).getId();
        assertThatThrownBy(() -> works.updateWork(workId, owner.getId(), update(MOVIE, null)))
                .hasMessageContaining("só podem ser usados");
        works.updateWork(workId, owner.getId(), update(MOVIE, Map.of()));
        flush();
        assertThat(workRepository.findById(workId).orElseThrow().getTemplate()).isEqualTo(MOVIE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void falhaNaTransacaoDesfazRemocaoDeDefinicoesEDeValores() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        record Fixture(Long userId, Long categoryId, Long workId, String fieldId) {}
        Fixture fixture = transaction.execute(status -> {
            User owner = user();
            Long tableId = table(owner, CUSTOM).getId();
            String id = categories.updateCustomFields(tableId, owner.getId(),
                    List.of(new CustomFieldRequestDTO(null, "Local", TEXT))).getCustomFields().get(0).id();
            Long workId = work(owner, tableId, CUSTOM, Map.of("fields", Map.of(id, "A"))).getId();
            return new Fixture(owner.getId(), tableId, workId, id);
        });
        try {
            assertThatThrownBy(() -> transaction.execute(status -> {
                categories.updateCustomFields(fixture.categoryId(), fixture.userId(), List.of());
                entityManager.flush();
                throw new IllegalStateException("Falha simulada após a limpeza.");
            })).isInstanceOf(IllegalStateException.class);
            transaction.executeWithoutResult(status -> {
                assertThat(categoryRepository.findById(fixture.categoryId()).orElseThrow().getCustomFields())
                        .extracting(CustomField::id).containsExactly(fixture.fieldId());
                assertThat(workRepository.findById(fixture.workId()).orElseThrow().getDetails().get("fields"))
                        .isEqualTo(Map.of(fixture.fieldId(), "A"));
            });
        } finally {
            transaction.executeWithoutResult(status -> users.deleteById(fixture.userId()));
        }
    }

    private User user() {
        User owner = new User();
        owner.setUsername("custom_fields_" + System.nanoTime());
        return users.save(owner);
    }

    private CategoryResponseDTO table(User owner, TableTemplate... templates) {
        CategoryCreateDTO request = new CategoryCreateDTO();
        request.setName("Coleção " + System.nanoTime());
        request.setTemplates(List.of(templates));
        return categories.createCategory(owner, request);
    }

    private Work work(User owner, Long tableId, TableTemplate template, Map<String, Object> details) {
        return works.createWork(owner, new WorkCreateDTO(tableId, "Item", null, null, null, 120, 8, null, template, details));
    }

    private WorkUpdateDTO update(TableTemplate template, Map<String, Object> details) {
        return new WorkUpdateDTO(null, null, null, null, null, null, null, template, details);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
