package br.com.myrank;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.domain.enums.OnboardingStep;
import br.com.myrank.dto.UserCreateDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.security.JwtService;
import br.com.myrank.service.OAuthUserInfo;
import br.com.myrank.service.OnboardingService;
import br.com.myrank.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static br.com.myrank.domain.enums.TableTemplate.MOVIE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercita cadastro, autenticação, transações e contrato HTTP em Postgres descartável. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnboardingIntegrationTest {

    private static final String ALL_TEMPLATES = "{\"templates\":[\"movie\",\"tv\",\"anime\",\"manga\",\"game\",\"book\",\"music\",\"album\"]}";

    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired OnboardingService onboarding;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void cadastroEmail_nascePrivadoSemTabelas_eMeExibeTables() throws Exception {
        User user = newEmailUser("EN");
        checkNewAccount(user);
        mvc.perform(get("/api/users/me").header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.onboardingStep").value("TABLES"))
                .andExpect(jsonPath("$.isPublic").value(false));
        mvc.perform(get("/api/categories").header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @EnumSource(value = AuthProvider.class, names = {"GOOGLE", "DISCORD"})
    void cadastroOAuth_nascePrivadoSemTabelas(AuthProvider provider) throws Exception {
        String unique = unique();
        User user = userService.findOrCreateFromOAuth(
                new OAuthUserInfo(unique, unique + "@myrank.dev", unique, null), provider);
        checkNewAccount(user);
        mvc.perform(get("/api/users/me").header("Authorization", token(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.onboardingStep").value("TABLES"))
                .andExpect(jsonPath("$.isPublic").value(false));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "PT|🎬 Filmes,📺 Séries,🎌 Animes,📖 Mangás,🎮 Jogos,📚 Livros,🎵 Músicas,💿 Álbuns",
            "EN|🎬 Movies,📺 Series,🎌 Anime,📖 Manga,🎮 Games,📚 Books,🎵 Music,💿 Albums",
            "ES|🎬 Películas,📺 Series,🎌 Anime,📖 Manga,🎮 Juegos,📚 Libros,🎵 Música,💿 Álbumes"
    })
    void escolha_criaUmaTabelaPorTemplateNoIdiomaDaConta(String language, String expectedNames) throws Exception {
        User user = newEmailUser(language);
        String body = mvc.perform(post("/api/onboarding/tables").header("Authorization", token(user))
                        .contentType(MediaType.APPLICATION_JSON).content(ALL_TEMPLATES))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].templates[0]").value("movie"))
                .andExpect(jsonPath("$[7].templates[0]").value("album"))
                .andExpect(jsonPath("$[0].default").value(true))
                .andExpect(jsonPath("$[0].customFields").isEmpty())
                .andExpect(jsonPath("$[0].subcategories").isEmpty()).andReturn().getResponse().getContentAsString();

        var response = json.readTree(body);
        for (int i = 0; i < 8; i++) {
            assertThat(response.get(i).get("name").asText()).isEqualTo(expectedNames.split(",")[i]);
            assertThat(response.get(i).get("templates").size()).isEqualTo(1);
            assertThat(response.get(i).get("default").asBoolean()).isTrue();
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(users.findById(user.getId()).orElseThrow().getOnboardingStep()).isEqualTo(OnboardingStep.FIRST_WORK);
        String listed = mvc.perform(get("/api/categories").header("Authorization", token(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<Long, JsonNode> listedById = new HashMap<>();
        json.readTree(listed).forEach(category -> listedById.put(category.get("id").asLong(), category));
        assertThat(listedById).hasSize(response.size());
        for (JsonNode created : response) {
            JsonNode stored = listedById.get(created.get("id").asLong());
            assertThat(stored).isNotNull();
            // O Postgres guarda microssegundos; o instante recém-criado pode ter nanossegundos.
            assertThat(LocalDateTime.parse(stored.get("createdAt").asText()))
                    .isCloseTo(LocalDateTime.parse(created.get("createdAt").asText()), within(1, ChronoUnit.MICROS));
            ((ObjectNode) stored).remove("createdAt");
            ((ObjectNode) created).remove("createdAt");
            assertThat(stored).isEqualTo(created);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"templates\":[]}", "{\"templates\":[\"movie\",\"movie\"]}",
            "{\"templates\":[\"custom\"]}", "{\"templates\":[\"music\",\"custom\"]}",
            "{\"templates\":[null]}", "{\"templates\":[\"inexistente\"]}"})
    void listaInvalida_responde400SemCriarTabelas(String body) throws Exception {
        User user = newEmailUser("PT");
        mvc.perform(post("/api/onboarding/tables").header("Authorization", token(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(categories.findByUserId(user.getId())).isEmpty();
        assertThat(users.findById(user.getId()).orElseThrow().getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
    }

    @ParameterizedTest
    @EnumSource(value = OnboardingStep.class, names = {"FIRST_WORK", "DONE"})
    void escolhaNaEtapaErrada_responde400(OnboardingStep step) throws Exception {
        User user = newEmailUser("PT");
        user.setOnboardingStep(step);
        users.saveAndFlush(user);
        mvc.perform(post("/api/onboarding/tables").header("Authorization", token(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"templates\":[\"movie\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(categories.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void finishAntesDaEscolha_responde400() throws Exception {
        User user = newEmailUser("PT");
        mvc.perform(post("/api/onboarding/finish").header("Authorization", token(user)))
                .andExpect(status().isBadRequest());
        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finish_comOuSemObra_repetidoResponde204(boolean createWork) throws Exception {
        User user = newEmailUser("PT");
        onboarding.chooseTables(user.getId(), List.of(MOVIE));
        entityManager.flush();
        if (createWork) {
            Long categoryId = categories.findByUserId(user.getId()).get(0).getId();
            sql.update("INSERT INTO works(category_id,user_id,title,score,final_score,template) VALUES (?,?,?,?,?,?)",
                    categoryId, user.getId(), "Primeira obra", 8, 8, "MOVIE");
        }
        mvc.perform(post("/api/onboarding/finish").header("Authorization", token(user)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/onboarding/finish").header("Authorization", token(user)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", token(user)))
                .andExpect(jsonPath("$.onboardingStep").value("DONE"));
    }

    @Test
    void endpointsExigemLogin() throws Exception {
        mvc.perform(post("/api/onboarding/tables").contentType(MediaType.APPLICATION_JSON).content(ALL_TEMPLATES))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/onboarding/finish")).andExpect(status().isUnauthorized());
    }

    @Test
    void escolhaEFinalizacao_afetamSomenteOUsuarioAutenticado() throws Exception {
        User owner = newEmailUser("PT");
        User other = newEmailUser("PT");
        mvc.perform(post("/api/onboarding/tables").header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templates\":[\"music\"],\"userId\":" + other.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/onboarding/finish").header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + other.getId() + "}"))
                .andExpect(status().isNoContent());
        assertThat(users.findById(owner.getId()).orElseThrow().getOnboardingStep()).isEqualTo(OnboardingStep.DONE);
        assertThat(users.findById(other.getId()).orElseThrow().getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
        assertThat(categories.findByUserId(other.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void escolhasSimultaneas_naoDuplicamTabelasMesmoComUsuarioCarregadoAntesDoBloqueio() throws Exception {
        User user = newEmailUser("PT");
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);
        try {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        users.findById(user.getId()).orElseThrow();
                        loaded.countDown();
                        try {
                            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Tempo de espera esgotado.");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(ex);
                        }
                        onboarding.chooseTables(user.getId(), List.of(MOVIE));
                    });
                    return true;
                } catch (IllegalArgumentException ex) {
                    assertThat(ex).hasMessageContaining("TABLES");
                    return false;
                }
            })).toList();
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int success = 0;
            for (var attempt : attempts) if (attempt.get(30, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
            transaction.executeWithoutResult(status -> {
                assertThat(categories.findByUserId(user.getId())).hasSize(1);
                assertThat(users.findById(user.getId()).orElseThrow().getOnboardingStep()).isEqualTo(OnboardingStep.FIRST_WORK);
            });
        } finally {
            start.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> users.hardDeleteById(user.getId()));
        }
    }

    private User newEmailUser(String language) {
        String unique = unique();
        return userService.createUser(new UserCreateDTO("passe-confirmado", unique, "senha12345", language),
                unique + "@myrank.dev");
    }

    private void checkNewAccount(User user) {
        entityManager.flush();
        entityManager.clear();
        User stored = users.findById(user.getId()).orElseThrow();
        assertThat(stored.getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
        assertThat(stored.isPublic()).isFalse();
        assertThat(categories.findByUserId(user.getId())).isEmpty();
    }

    private String token(User user) {
        return "Bearer " + jwt.generateToken(user);
    }

    private String unique() {
        return "onboarding" + Long.toUnsignedString(System.nanoTime());
    }
}
