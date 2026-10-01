package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.domain.model.CustomField;
import br.com.myrank.dto.CategoryUpdateDTO;
import br.com.myrank.dto.CustomFieldRequestDTO;
import br.com.myrank.dto.WorkCreateDTO;
import br.com.myrank.dto.WorkUpdateDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.SubcategoryRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.badge.BadgeService;
import br.com.myrank.service.social.FeedEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static br.com.myrank.domain.enums.CustomFieldType.*;
import static br.com.myrank.domain.enums.TableTemplate.CUSTOM;
import static br.com.myrank.domain.enums.TableTemplate.MOVIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomFieldsServiceTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final SubcategoryRepository subcategories = mock(SubcategoryRepository.class);
    private final WorkRepository works = mock(WorkRepository.class);
    private final BadgeService badges = mock(BadgeService.class);
    private final FeedEventService feed = mock(FeedEventService.class);
    private final CategoryService categoryService = new CategoryService(categories, subcategories, works);
    private final WorkService workService = new WorkService(works, categories, subcategories, badges, feed, new ObjectMapper());
    private final User owner = new User();
    private final Category category = new Category();

    @BeforeEach
    void prepare() {
        owner.setId(1L);
        category.setId(10L);
        category.setUser(owner);
        category.setName("Coleção");
        category.setTemplates(List.of(CUSTOM, MOVIE));
        category.setCustomFields(List.of(new CustomField("f_text0001", "Texto", TEXT),
                new CustomField("f_num00001", "Número", NUMBER), new CustomField("f_date0001", "Data", DATE),
                new CustomField("f_bool0001", "Ativo", BOOLEAN)));
        when(categories.findByIdForUpdate(10L)).thenReturn(Optional.of(category));
        when(categories.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));
        when(works.save(any(Work.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void limiteDeCinco_eIdsCurtosGeradosPeloServidor() {
        var five = IntStream.range(0, 5).mapToObj(i -> new CustomFieldRequestDTO(null, "Campo " + i, TEXT)).toList();
        var result = categoryService.updateCustomFields(10L, 1L, five).getCustomFields();
        assertThat(result).hasSize(5).extracting(CustomField::id).doesNotHaveDuplicates();
        assertThat(result).allSatisfy(field -> assertThat(field.id()).matches("f_[A-Za-z0-9_-]{8}"));
        List<CustomFieldRequestDTO> six = new ArrayList<>(five);
        six.add(new CustomFieldRequestDTO(null, "Sexto", TEXT));
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L, six))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no máximo 5");
    }

    @Test
    void nomesRepetidosIgnoramMaiusculas_eEspacosNasBordas() {
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L, List.of(
                new CustomFieldRequestDTO(null, " Preço ", NUMBER), new CustomFieldRequestDTO(null, "PREÇO", TEXT))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("esse nome");
        verify(categories, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12345678901234567890123456789012345678901"})
    void nomePrecisaTerDeUmAQuarentaCaracteres(String name) {
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L,
                List.of(new CustomFieldRequestDTO(null, name, TEXT)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tiposAusentesIdsDesconhecidos_eIdsDuplicadosSaoRecusados() {
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L,
                List.of(new CustomFieldRequestDTO(null, "Tipo", null)))).hasMessageContaining("Informe o tipo");
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L,
                List.of(new CustomFieldRequestDTO("id-forjado", "Tipo", TEXT)))).hasMessageContaining("não encontrado");
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L, List.of(
                new CustomFieldRequestDTO("f_text0001", "A", TEXT), new CustomFieldRequestDTO("f_text0001", "B", TEXT))))
                .hasMessageContaining("duas vezes");
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L, null)).hasMessageContaining("lista completa");
    }

    @Test
    void trocarTipoERecusadoAntesDeAlterarDefinicoesOuValores() {
        var previous = List.copyOf(category.getCustomFields());
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L,
                List.of(new CustomFieldRequestDTO("f_text0001", "Texto", NUMBER))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Remova o campo e crie outro");
        assertThat(category.getCustomFields()).isEqualTo(previous);
        verifyNoInteractions(works);
        verify(categories, never()).save(any());
    }

    @Test
    void renomear_eAdicionarMantemIds_eNaoAtualizaItensAntigos() {
        List<CustomFieldRequestDTO> requested = new ArrayList<>(currentRequests());
        requested.set(0, new CustomFieldRequestDTO("f_text0001", "Novo nome", TEXT));
        requested.add(new CustomFieldRequestDTO(null, "Novo campo", TEXT));
        var fields = categoryService.updateCustomFields(10L, 1L, requested).getCustomFields();
        assertThat(fields.get(0)).isEqualTo(new CustomField("f_text0001", "Novo nome", TEXT));
        assertThat(fields).hasSize(5);
        verifyNoInteractions(works);
    }

    @Test
    void removerCampoApagaSeusValores_ePreservaOutrosValoresEMetadados() {
        Work first = create(Map.of("provider", "manual", "fields", Map.of("f_text0001", "texto", "f_bool0001", false)));
        Work second = create(Map.of("fields", Map.of("f_text0001", "outro")));
        Work unchanged = create(Map.of("fields", Map.of("f_num00001", 0)));
        when(works.findByCategoryId(10L)).thenReturn(List.of(first, second, unchanged));

        categoryService.updateCustomFields(10L, 1L, currentRequests().subList(1, 4));

        assertThat(first.getDetails()).containsEntry("provider", "manual");
        assertThat(first.getDetails().get("fields")).isEqualTo(Map.of("f_bool0001", false));
        assertThat(second.getDetails().get("fields")).isEqualTo(Map.of());
        assertThat(unchanged.getDetails().get("fields")).isEqualTo(Map.of("f_num00001", 0));
        verify(works).saveAll(List.of(first, second));
    }

    @Test
    void somenteODonoPodeAlterarCampos_eTabelaPrecisaDeCustom() {
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 2L, List.of()))
                .hasMessageContaining("permissão");
        category.setTemplates(List.of(MOVIE));
        assertThatThrownBy(() -> categoryService.updateCustomFields(10L, 1L, List.of()))
                .hasMessageContaining("Só tabelas com o template Personalizado");
        verify(categories, never()).save(any());
        verifyNoInteractions(works);
    }

    @Test
    void retirarCustomLimpaDefinicoes_eContinuaProibidoQuandoHaItensCustom() {
        CategoryUpdateDTO request = new CategoryUpdateDTO();
        request.setTemplates(List.of(MOVIE));
        when(works.existsByCategoryIdAndTemplate(10L, CUSTOM)).thenReturn(true);
        assertThatThrownBy(() -> categoryService.updateCategory(10L, 1L, request)).hasMessageContaining("ainda tem itens");
        assertThat(category.getCustomFields()).hasSize(4);
        when(works.existsByCategoryIdAndTemplate(10L, CUSTOM)).thenReturn(false);
        assertThat(categoryService.updateCategory(10L, 1L, request).getCustomFields()).isEmpty();
    }

    @Test
    void criacaoAceitaOsQuatroTipos_eEdicaoMantemValoresQuandoDetailsENulo() {
        Map<String, Object> values = Map.of("f_text0001", "a".repeat(200), "f_num00001", new BigDecimal("12.50"),
                "f_date0001", "2024-02-29", "f_bool0001", false);
        Work work = create(Map.of("fields", values));
        when(works.findById(20L)).thenReturn(Optional.of(work));
        workService.updateWork(20L, 1L, update(null, null));
        assertThat(work.getDetails().get("fields")).isEqualTo(values);
    }

    @Test
    void nuloRemoveValorSemModificarMapaDeEntrada() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("f_text0001", null);
        values.put("f_bool0001", false);
        Work work = create(Map.of("fields", values));
        assertThat(work.getDetails().get("fields")).isEqualTo(Map.of("f_bool0001", false));
        assertThat(values).containsKey("f_text0001");
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void valoresInvalidosSaoRecusadosNaCriacaoENaEdicao(String id, Object value, String rule) {
        Map<String, Object> details = Map.of("fields", Map.of(id, value));
        assertThatThrownBy(() -> create(details)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(rule);
        Work existing = create(Map.of("provider", "manual"));
        when(works.findById(20L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> workService.updateWork(20L, 1L, update(null, details)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(rule);
        assertThat(existing.getDetails()).isEqualTo(Map.of("provider", "manual"));
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("f_text0001", 12, "texto"), Arguments.of("f_text0001", "a".repeat(201), "200 caracteres"),
                Arguments.of("f_num00001", "12", "número finito"), Arguments.of("f_num00001", Double.NaN, "número finito"),
                Arguments.of("f_num00001", Double.POSITIVE_INFINITY, "número finito"),
                Arguments.of("f_num00001", Float.NEGATIVE_INFINITY, "número finito"),
                Arguments.of("f_date0001", "2023-02-29", "yyyy-MM-dd"), Arguments.of("f_date0001", "2024-04-31", "yyyy-MM-dd"),
                Arguments.of("f_date0001", "2024-2-01", "yyyy-MM-dd"), Arguments.of("f_date0001", "2024-01-01T00:00:00Z", "yyyy-MM-dd"),
                Arguments.of("f_bool0001", "true", "true ou false"), Arguments.of("f_bool0001", 1, "true ou false"));
    }

    @Test
    void numerosInteirosGrandes_eTextoVazioSaoValoresValidos() {
        assertThat(create(Map.of("fields", Map.of("f_num00001", new BigInteger("9".repeat(400)), "f_text0001", "")))
                .getDetails()).containsKey("fields");
    }

    @Test
    void campoDeOutraTabela_eFieldsQueNaoSejaObjetoSaoRecusados() {
        assertThatThrownBy(() -> create(Map.of("fields", Map.of("id-de-outra-tabela", 1))))
                .hasMessageContaining("não existe nesta tabela");
        assertThatThrownBy(() -> create(Map.of("fields", List.of()))).hasMessageContaining("devem formar um objeto");
        Map<String, Object> nullFields = new LinkedHashMap<>();
        nullFields.put("fields", null);
        assertThatThrownBy(() -> create(nullFields)).hasMessageContaining("devem formar um objeto");
    }

    @Test
    void itensNaoCustomRecusamFields_eMudancaDeTemplateValidaValoresJaExistentes() {
        assertThatThrownBy(() -> workService.createWork(owner, request(MOVIE, Map.of("fields", Map.of()))))
                .hasMessageContaining("só podem ser usados em itens Personalizados");
        Work existing = create(Map.of("fields", Map.of("f_text0001", "valor")));
        when(works.findById(20L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> workService.updateWork(20L, 1L, update(MOVIE, null)))
                .hasMessageContaining("só podem ser usados em itens Personalizados");
        assertThat(existing.getTemplate()).isEqualTo(CUSTOM);
        assertThat(workService.updateWork(20L, 1L, update(MOVIE, Map.of())).getTemplate()).isEqualTo(MOVIE);
    }

    @Test
    void limiteDeOitoKbContinuaValendoNaCriacao_eEdicao() {
        Map<String, Object> tooLarge = Map.of("blob", "é".repeat(4096));
        assertThatThrownBy(() -> create(tooLarge)).hasMessageContaining("grandes demais");
        Work existing = create(Map.of());
        when(works.findById(20L)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> workService.updateWork(20L, 1L, update(null, tooLarge))).hasMessageContaining("grandes demais");
    }

    private List<CustomFieldRequestDTO> currentRequests() {
        return category.getCustomFields().stream().map(field -> new CustomFieldRequestDTO(field.id(), field.name(), field.type())).toList();
    }

    private Work create(Map<String, Object> details) {
        return workService.createWork(owner, request(CUSTOM, details));
    }

    private WorkCreateDTO request(TableTemplate template, Map<String, Object> details) {
        return new WorkCreateDTO(10L, "Item", null, null, null, 120, 8, null, template, details);
    }

    private WorkUpdateDTO update(TableTemplate template, Map<String, Object> details) {
        return new WorkUpdateDTO(null, null, null, null, null, null, null, template, details);
    }
}
