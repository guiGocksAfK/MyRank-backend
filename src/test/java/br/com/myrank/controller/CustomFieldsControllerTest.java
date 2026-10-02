package br.com.myrank.controller;

import br.com.myrank.config.GlobalExceptionHandler;
import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.model.CustomField;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.SubcategoryRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.CategoryService;
import br.com.myrank.service.WorkService;
import br.com.myrank.service.badge.BadgeService;
import br.com.myrank.service.social.FeedEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static br.com.myrank.domain.enums.CustomFieldType.*;
import static br.com.myrank.domain.enums.TableTemplate.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomFieldsControllerTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final SubcategoryRepository subcategories = mock(SubcategoryRepository.class);
    private final WorkRepository works = mock(WorkRepository.class);
    private final AuthUtils auth = mock(AuthUtils.class);
    private final CategoryService categoryService = new CategoryService(categories, subcategories, works);
    private final WorkService workService = new WorkService(works, categories, subcategories,
            mock(BadgeService.class), mock(FeedEventService.class), new ObjectMapper());
    private final User owner = new User();
    private final Category category = new Category();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CategoryController(categoryService, auth),
                    new WorkController(workService, auth))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @BeforeEach
    void prepare() {
        owner.setId(1L);
        category.setId(10L);
        category.setName("Coleção");
        category.setUser(owner);
        category.setTemplates(List.of(CUSTOM, MOVIE));
        category.setCustomFields(List.of(new CustomField("f_text0001", "Texto", TEXT),
                new CustomField("f_num00001", "Número", NUMBER), new CustomField("f_date0001", "Data", DATE),
                new CustomField("f_bool0001", "Ativo", BOOLEAN)));
        when(auth.getUser(any())).thenReturn(owner);
        when(categories.findByIdForUpdate(10L)).thenReturn(Optional.of(category));
        when(categories.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));
        when(works.save(any(Work.class))).thenAnswer(call -> {
            Work work = call.getArgument(0);
            work.setId(20L);
            return work;
        });
    }

    @Test
    void listaCompletaCriaCampoComIdGerado_eDevolveCategoryResponseAtualizado() throws Exception {
        mvc.perform(put("/api/categories/10/custom-fields").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\" Local \",\"type\":\"TEXT\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.customFields.length()").value(1))
                .andExpect(jsonPath("$.customFields[0].id").value(matchesPattern("f_[A-Za-z0-9_-]{8}")))
                .andExpect(jsonPath("$.customFields[0].name").value("Local"))
                .andExpect(jsonPath("$.customFields[0].type").value("TEXT"));
    }

    @Test
    void trocarTipoRetorna400ComMensagemClara() throws Exception {
        mvc.perform(put("/api/categories/10/custom-fields").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"f_text0001\",\"name\":\"Texto\",\"type\":\"NUMBER\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Remova o campo e crie outro")));
        verify(categories, never()).save(any());
    }

    @Test
    void outroDonoRecebeMesmaRecusaDosDemaisEndpointsDeCategoria() throws Exception {
        User stranger = new User();
        stranger.setId(2L);
        when(auth.getUser(any())).thenReturn(stranger);
        mvc.perform(put("/api/categories/10/custom-fields").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("permissão")));
        verify(categories, never()).save(any());
        verifyNoInteractions(works);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"name\":\"A\",\"type\":\"OUTRO\"}]", "{}", "[null]", "null"})
    void corpoOuTipoInvalidoRetorna400(String body) throws Exception {
        mvc.perform(put("/api/categories/10/custom-fields").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        verify(categories, never()).save(any());
    }

    @Test
    void tabelaSemCustomRetorna400() throws Exception {
        category.setTemplates(List.of(MOVIE));
        mvc.perform(put("/api/categories/10/custom-fields").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Local\",\"type\":\"TEXT\"}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("CUSTOM")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"f_text0001\":12}", "{\"f_num00001\":\"12\"}",
            "{\"f_date0001\":\"2023-02-29\"}", "{\"f_bool0001\":\"true\"}", "{\"f_inexist\":1}", "[]"})
    void valoresInvalidosPorTipoOuEstruturaRetornam400EmPortugues(String fields) throws Exception {
        mvc.perform(post("/api/works").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":10,\"title\":\"Item\",\"template\":\"custom\",\"score\":8,\"details\":{\"fields\":" + fields + "}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        verify(works, never()).save(any());
    }

    @Test
    void valorValidoENuloSaoDevolvidosSemSalvarChaveNula() throws Exception {
        mvc.perform(post("/api/works").contentType(MediaType.APPLICATION_JSON).content("""
                {"categoryId":10,"title":"Item","template":"custom","score":8,
                 "details":{"fields":{"f_text0001":null,"f_bool0001":false}}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.fields.f_text0001").doesNotExist())
                .andExpect(jsonPath("$.details.fields.f_bool0001").value(false));
    }
}
