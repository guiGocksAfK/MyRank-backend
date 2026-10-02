package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.OnboardingStep;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static br.com.myrank.domain.enums.TableTemplate.*;

@Service
public class OnboardingService {

    private record TableName(String emoji, String pt, String en, String es) {
        String localized(String language) {
            String name = switch (language) {
                case "EN" -> en;
                case "ES" -> es;
                default -> pt;
            };
            return emoji + " " + name;
        }
    }

    private static final Map<TableTemplate, TableName> NAMES = Map.of(
            MOVIE, new TableName("🎬", "Filmes", "Movies", "Películas"),
            TV, new TableName("📺", "Séries", "Series", "Series"),
            ANIME, new TableName("🎌", "Animes", "Anime", "Anime"),
            MANGA, new TableName("📖", "Mangás", "Manga", "Manga"),
            GAME, new TableName("🎮", "Jogos", "Games", "Juegos"),
            BOOK, new TableName("📚", "Livros", "Books", "Libros"),
            MUSIC, new TableName("🎵", "Músicas", "Music", "Música"),
            ALBUM, new TableName("💿", "Álbuns", "Albums", "Álbumes")
    );

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final EntityManager entityManager;

    public OnboardingService(UserRepository userRepository, CategoryRepository categoryRepository,
                             CategoryService categoryService, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<CategoryResponseDTO> chooseTables(Long userId, List<TableTemplate> templates) {
        User user = lockedUser(userId);
        if (user.getOnboardingStep() != OnboardingStep.TABLES) {
            throw new IllegalArgumentException("A escolha de tabelas só é permitida na etapa TABLES do tutorial.");
        }
        validateTemplates(templates);

        List<Category> categories = templates.stream().map(template -> {
            String name = NAMES.get(template).localized(user.getLanguage());
            if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
                throw new IllegalArgumentException("Você já tem uma categoria com esse nome: " + name + ".");
            }
            Category category = new Category();
            category.setUser(user);
            category.setName(name);
            category.setTemplates(List.of(template));
            category.setDefault(true);
            return category;
        }).toList();

        List<Category> saved = categoryRepository.saveAll(categories);
        user.setOnboardingStep(OnboardingStep.FIRST_WORK);
        userRepository.save(user);
        return saved.stream().map(category -> categoryService.toResponseDTO(category, List.of())).toList();
    }

    @Transactional
    public void finish(Long userId) {
        User user = lockedUser(userId);
        if (user.getOnboardingStep() == OnboardingStep.DONE) return;
        if (user.getOnboardingStep() != OnboardingStep.FIRST_WORK) {
            throw new IllegalArgumentException("Conclua a escolha de tabelas antes de finalizar o tutorial.");
        }
        user.setOnboardingStep(OnboardingStep.DONE);
        userRepository.save(user);
    }

    private User lockedUser(Long userId) {
        // Serializa as transições para que pedidos simultâneos não dupliquem as tabelas.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        // A autenticação pode ter carregado a conta antes do bloqueio; relê a etapa atual.
        entityManager.refresh(user);
        return user;
    }

    private static void validateTemplates(List<TableTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("Escolha pelo menos um template para iniciar o tutorial.");
        }
        if (templates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A lista não pode conter templates nulos.");
        }
        if (new HashSet<>(templates).size() != templates.size()) {
            throw new IllegalArgumentException("A lista não pode conter templates repetidos.");
        }
        if (templates.contains(CUSTOM)) {
            throw new IllegalArgumentException("Personalizado (custom) deve ser criado depois do tutorial.");
        }
    }
}
