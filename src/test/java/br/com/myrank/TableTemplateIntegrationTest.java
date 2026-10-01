package br.com.myrank;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.*;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.CategoryService;
import br.com.myrank.service.WorkService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static br.com.myrank.domain.enums.TableTemplate.*;
import static org.junit.jupiter.api.Assertions.*;

/** Precisa de um Postgres (o mesmo dos testes de contexto). */
@SpringBootTest
@Transactional
class TableTemplateIntegrationTest {
    @Autowired CategoryService categories;
    @Autowired WorkService works;
    @Autowired UserRepository users;
    @Autowired WorkRepository workRepository;
    @Autowired EntityManager entityManager;

    @Test
    void renomearATabelaNaoMudaOTipo_eDetailsSobrevivemAEdicoes() {
        User owner = user();
        Long tableId = table(owner, "🎬 Favoritos", MOVIE).getId();
        Work work = works.createWork(owner, newWork(tableId, null,
                Map.of("provider", "tmdb", "externalId", "123", "nested", Map.of("language", "pt"))));
        assertEquals(MOVIE, work.getTemplate()); // tabela de um tipo só: o item herda

        CategoryUpdateDTO rename = new CategoryUpdateDTO();
        rename.setName("🎵 Sem palavras de filme");
        assertEquals(List.of(MOVIE), categories.updateCategory(tableId, owner.getId(), rename).getTemplates());
        flush();

        WorkResponseDTO response = WorkResponseDTO.fromEntity(workRepository.findById(work.getId()).orElseThrow());
        assertEquals("filme", response.template().type());
        assertEquals(Map.of("language", "pt"), response.details().get("nested"));

        works.updateWork(work.getId(), owner.getId(), new WorkUpdateDTO(null, null, null, null, null, 8.5, null, null, null));
        assertEquals("123", workRepository.findById(work.getId()).orElseThrow().getDetails().get("externalId"));
        works.updateWork(work.getId(), owner.getId(), new WorkUpdateDTO(null, null, null, null, null, null, null, null, Map.of()));
        flush();
        assertTrue(workRepository.findById(work.getId()).orElseThrow().getDetails().isEmpty());
    }

    @Test
    void tabelaMista_exigeEscolherOTipo_eSoAceitaOsTiposDela() {
        User owner = user();
        Long tableId = table(owner, "🎮 Jogos e séries", GAME, TV, ANIME).getId();

        assertThrows(IllegalArgumentException.class, () -> works.createWork(owner, newWork(tableId, null, null)));
        assertThrows(IllegalArgumentException.class, () -> works.createWork(owner, newWork(tableId, BOOK, null)));
        Work anime = works.createWork(owner, newWork(tableId, ANIME, null));
        flush();
        assertEquals(ANIME, workRepository.findById(anime.getId()).orElseThrow().getTemplate());
        assertEquals(List.of(GAME, TV, ANIME), categories.getCategoriesByUser(owner.getId()).get(0).getTemplates());
    }

    @Test
    void adicionarTipoPodeSempre_tirarSoSeNenhumItemUsar() {
        User owner = user();
        Long tableId = table(owner, "📚 Leituras", BOOK).getId();
        works.createWork(owner, newWork(tableId, BOOK, null));

        CategoryUpdateDTO change = new CategoryUpdateDTO();
        change.setName("📚 Leituras");
        change.setTemplates(List.of(BOOK, CUSTOM));
        assertEquals(List.of(BOOK, CUSTOM), categories.updateCategory(tableId, owner.getId(), change).getTemplates());

        change.setTemplates(List.of(CUSTOM)); // ainda tem um livro na tabela
        assertThrows(IllegalArgumentException.class, () -> categories.updateCategory(tableId, owner.getId(), change));
        change.setTemplates(List.of(BOOK)); // ninguém usa CUSTOM: pode tirar
        assertEquals(List.of(BOOK), categories.updateCategory(tableId, owner.getId(), change).getTemplates());
        change.setTemplates(List.of());
        assertThrows(IllegalArgumentException.class, () -> categories.updateCategory(tableId, owner.getId(), change));
    }

    @Test
    void detailsGrandesDemais_saoRecusados() {
        User owner = user();
        Long tableId = table(owner, "🎬 Filmes", MOVIE).getId();
        Map<String, Object> huge = Map.of("blob", "x".repeat(9 * 1024));
        assertThrows(IllegalArgumentException.class, () -> works.createWork(owner, newWork(tableId, null, huge)));
    }

    @Test
    void tabelasPadrao_umaPorTipo_comSeriesEAnimesSeparados() {
        User owner = user();
        categories.createDefaultCategories(owner);
        var defaults = categories.getCategoriesByUser(owner.getId());
        assertEquals(5, defaults.size());
        assertEquals(Set.of(List.of(MOVIE), List.of(TV), List.of(ANIME), List.of(BOOK), List.of(GAME)),
                defaults.stream().map(CategoryResponseDTO::getTemplates).collect(Collectors.toSet()));
        assertTrue(defaults.stream().allMatch(CategoryResponseDTO::isDefault));
    }

    @Test
    void musicaEAlbum_naoGanhamBonusDeTempo() {
        User owner = user();
        Long tableId = table(owner, "🎵 Músicas", MUSIC, ALBUM, MOVIE).getId();
        Work track = works.createWork(owner, newWork(tableId, MUSIC, null));  // 120 min
        Work movie = works.createWork(owner, newWork(tableId, MOVIE, null));  // 120 min
        assertEquals(0, track.getTimeBonusScore().signum());
        assertEquals(0, track.getScore().compareTo(track.getFinalScore()));
        assertTrue(movie.getTimeBonusScore().signum() > 0);
    }

    @Test
    void tabelaSemTipoInformado_viraPersonalizada() {
        User owner = user();
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName("Minha coleção");
        assertEquals(List.of(CUSTOM), categories.createCategory(owner, dto).getTemplates());
    }

    private CategoryResponseDTO table(User owner, String name, TableTemplate... templates) {
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName(name);
        dto.setTemplates(List.of(templates));
        return categories.createCategory(owner, dto);
    }

    private static WorkCreateDTO newWork(Long tableId, TableTemplate template, Map<String, Object> details) {
        return new WorkCreateDTO(tableId, "Exemplo", null, "Criador", null, 120, 9, null, template, details);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private User user() {
        User user = new User();
        user.setUsername("template_test_" + System.nanoTime());
        return users.save(user);
    }
}
