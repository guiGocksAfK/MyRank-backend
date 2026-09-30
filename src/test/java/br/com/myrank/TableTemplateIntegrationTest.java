package br.com.myrank;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.*;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.CategoryService;
import br.com.myrank.service.WorkService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TableTemplateIntegrationTest {
    @Autowired CategoryService categories;
    @Autowired WorkService works;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categoryRepository;
    @Autowired WorkRepository workRepository;
    @Autowired EntityManager entityManager;

    @Test
    void renamingAndChangingTableTemplateNeverReclassifiesExistingItems() {
        User owner = user();
        CategoryCreateDTO table = new CategoryCreateDTO();
        table.setName("🎬 Favoritos");
        table.setTemplate(TableTemplate.MOVIE);
        Long categoryId = categories.createCategory(owner, table).getId();
        Work work = works.createWork(owner, new WorkCreateDTO(categoryId, "Exemplo", null,
                "Diretor", null, 120, 9, null, null,
                Map.of("provider", "tmdb", "externalId", "123", "nested", Map.of("language", "pt"))));
        Long workId = work.getId();
        entityManager.flush();
        entityManager.clear();

        CategoryUpdateDTO rename = new CategoryUpdateDTO();
        rename.setName("🎵 Sem palavras de filme");
        assertEquals(TableTemplate.MOVIE, categories.updateCategory(categoryId, owner.getId(), rename).getTemplate());
        rename.setTemplate(TableTemplate.CUSTOM);
        categories.updateCategory(categoryId, owner.getId(), rename);
        entityManager.flush();
        entityManager.clear();

        WorkResponseDTO response = WorkResponseDTO.fromEntity(workRepository.findById(workId).orElseThrow());
        assertEquals(TableTemplate.MOVIE, response.template());
        assertEquals("filme", response.template().type());
        assertEquals("123", response.details().get("externalId"));
        assertEquals(Map.of("language", "pt"), response.details().get("nested"));
        assertEquals(TableTemplate.CUSTOM, categoryRepository.findById(categoryId).orElseThrow().getTemplate());

        works.updateWork(workId, owner.getId(), new WorkUpdateDTO(null, null, null, null,
                null, 8.5, null, null, null));
        assertEquals("123", workRepository.findById(workId).orElseThrow().getDetails().get("externalId"));
        works.updateWork(workId, owner.getId(), new WorkUpdateDTO(null, null, null, null,
                null, null, null, null, Map.of()));
        entityManager.flush();
        entityManager.clear();
        assertTrue(workRepository.findById(workId).orElseThrow().getDetails().isEmpty());
    }

    @Test
    void customTableKeepsTheExplicitItemTemplateAcrossRoundTrips() {
        User owner = user();
        CategoryCreateDTO table = new CategoryCreateDTO();
        table.setName("Minha coleção");
        Long categoryId = categories.createCategory(owner, table).getId();
        Work work = works.createWork(owner, new WorkCreateDTO(categoryId, "Anime", null,
                null, null, 0, 8, null, TableTemplate.ANIME, null));
        entityManager.flush();
        entityManager.clear();
        assertEquals(TableTemplate.ANIME, workRepository.findById(work.getId()).orElseThrow().getTemplate());
        assertEquals(TableTemplate.CUSTOM, categoryRepository.findById(categoryId).orElseThrow().getTemplate());
    }

    @Test
    void defaultTablesSeparateSeriesFromAnime() {
        User owner = user();
        categories.createDefaultCategories(owner);
        var defaults = categories.getCategoriesByUser(owner.getId());
        assertEquals(5, defaults.size());
        assertEquals(Set.of(TableTemplate.MOVIE, TableTemplate.TV, TableTemplate.ANIME,
                TableTemplate.BOOK, TableTemplate.GAME), defaults.stream()
                .map(CategoryResponseDTO::getTemplate).collect(Collectors.toSet()));
        assertTrue(defaults.stream().allMatch(CategoryResponseDTO::isDefault));
    }

    private User user() {
        User user = new User();
        user.setUsername("template_test_" + System.nanoTime());
        return users.save(user);
    }
}
