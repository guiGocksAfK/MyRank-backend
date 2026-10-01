package br.com.myrank.service;

import br.com.myrank.dto.CategoryCreateDTO;
import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.dto.CategoryUpdateDTO;
import br.com.myrank.dto.SubcategoryDTO;
import br.com.myrank.dto.SubcategoryRequestDTO;
import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.Subcategory;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.SubcategoryRepository;
import br.com.myrank.repository.WorkRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final int MAX_SUBCATEGORIES = 20;

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final WorkRepository workRepository;

    public CategoryService(CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
                           WorkRepository workRepository) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.workRepository = workRepository;
    }

    public CategoryResponseDTO createCategory(User user, CategoryCreateDTO dto) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), dto.getName())) {
            throw new IllegalArgumentException("Você já tem uma categoria com esse nome.");
        }

        Category category = new Category();
        category.setUser(user);
        category.setName(dto.getName());
        List<TableTemplate> templates = distinct(dto.getTemplates());
        category.setTemplates(templates.isEmpty() ? List.of(TableTemplate.CUSTOM) : templates);
        category.setDefault(false); // categorias criadas via API nunca são default

        Category saved = categoryRepository.save(category);
        return toResponseDTO(saved, List.of());
    }

    public List<CategoryResponseDTO> getCategoriesByUser(Long userId) {
        List<Category> categories = categoryRepository.findByUserId(userId);
        // Uma query pras subcategorias de todas as tabelas, em vez de uma por tabela.
        Map<Long, List<SubcategoryDTO>> subsByCategory = subcategoryRepository
                .findByCategoryIdInOrderByCreatedAtAscIdAsc(categories.stream().map(Category::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(sub -> sub.getCategory().getId(),
                        Collectors.mapping(SubcategoryDTO::fromEntity, Collectors.toList())));

        return categories.stream()
                .map(category -> toResponseDTO(category, subsByCategory.getOrDefault(category.getId(), List.of())))
                .toList();
    }

    public CategoryResponseDTO updateCategory(Long categoryId, Long userId, CategoryUpdateDTO dto) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para editar essa categoria.");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            String newName = dto.getName();
            if (!category.getName().equalsIgnoreCase(newName)
                    && categoryRepository.existsByUserIdAndNameIgnoreCase(userId, newName)) {
                throw new IllegalArgumentException("Você já tem uma categoria com esse nome.");
            }
            category.setName(newName);
        }

        if (dto.getTemplates() != null) changeTemplates(category, distinct(dto.getTemplates()));
        Category saved = categoryRepository.save(category);
        return toResponseDTO(saved, subcategoriesOf(saved.getId()));
    }

    /**
     * Adicionar template pode sempre. Tirar só se nenhum item da tabela usar ele,
     * senão o item ficaria com um tipo que a tabela não tem.
     */
    private void changeTemplates(Category category, List<TableTemplate> next) {
        if (next.isEmpty()) {
            throw new IllegalArgumentException("A tabela precisa de pelo menos um tipo.");
        }
        for (TableTemplate removed : category.getTemplates()) {
            if (!next.contains(removed) && workRepository.existsByCategoryIdAndTemplate(category.getId(), removed)) {
                throw new IllegalArgumentException(
                        "Não dá pra tirar esse tipo: ainda tem itens dele na tabela. Mova ou apague esses itens antes.");
            }
        }
        category.setTemplates(next);
    }

    /** Sem repetidos, mantendo a ordem escolhida. */
    private static List<TableTemplate> distinct(List<TableTemplate> templates) {
        if (templates == null) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(templates.stream().filter(t -> t != null).toList()));
    }

    public void deleteCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para excluir essa categoria.");
        }

        categoryRepository.delete(category);
    }

    // ── Subcategorias ────────────────────────────────────────────────────

    public SubcategoryDTO createSubcategory(Long categoryId, Long userId, SubcategoryRequestDTO dto) {
        Category category = ownedCategory(categoryId, userId);
        String name = dto.name().trim();

        if (subcategoryRepository.countByCategoryId(categoryId) >= MAX_SUBCATEGORIES) {
            throw new IllegalArgumentException("Uma tabela pode ter no máximo " + MAX_SUBCATEGORIES + " subcategorias.");
        }
        if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(categoryId, name)) {
            throw new IllegalArgumentException("Essa tabela já tem uma subcategoria com esse nome.");
        }

        Subcategory subcategory = new Subcategory();
        subcategory.setCategory(category);
        subcategory.setName(name);
        return SubcategoryDTO.fromEntity(subcategoryRepository.save(subcategory));
    }

    public SubcategoryDTO renameSubcategory(Long categoryId, Long subcategoryId, Long userId, SubcategoryRequestDTO dto) {
        Subcategory subcategory = ownedSubcategory(categoryId, subcategoryId, userId);
        String name = dto.name().trim();

        if (!subcategory.getName().equalsIgnoreCase(name)
                && subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(categoryId, name)) {
            throw new IllegalArgumentException("Essa tabela já tem uma subcategoria com esse nome.");
        }

        subcategory.setName(name);
        return SubcategoryDTO.fromEntity(subcategoryRepository.save(subcategory));
    }

    /** As obras continuam na tabela, só ficam sem subcategoria (FK ON DELETE SET NULL). */
    public void deleteSubcategory(Long categoryId, Long subcategoryId, Long userId) {
        subcategoryRepository.delete(ownedSubcategory(categoryId, subcategoryId, userId));
    }

    private Category ownedCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));
        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para editar essa categoria.");
        }
        return category;
    }

    private Subcategory ownedSubcategory(Long categoryId, Long subcategoryId, Long userId) {
        ownedCategory(categoryId, userId);
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Subcategoria não encontrada."));
        if (!subcategory.getCategory().getId().equals(categoryId)) {
            throw new IllegalArgumentException("Subcategoria não encontrada.");
        }
        return subcategory;
    }

    private List<SubcategoryDTO> subcategoriesOf(Long categoryId) {
        return subcategoryRepository.findByCategoryIdInOrderByCreatedAtAscIdAsc(List.of(categoryId))
                .stream().map(SubcategoryDTO::fromEntity).toList();
    }

    public void createDefaultCategories(User user) {
        record DefaultTable(String name, TableTemplate template) {}
        List<DefaultTable> defaults = List.of(
                new DefaultTable("🎬 Filmes", TableTemplate.MOVIE),
                new DefaultTable("🎮 Jogos", TableTemplate.GAME),
                new DefaultTable("📚 Livros", TableTemplate.BOOK),
                new DefaultTable("📺 Séries", TableTemplate.TV),
                new DefaultTable("🎌 Animes", TableTemplate.ANIME)
        );

        for (DefaultTable table : defaults) {
            Category category = new Category();
            category.setUser(user);
            category.setName(table.name());
            category.setTemplates(List.of(table.template()));
            category.setDefault(true);
            categoryRepository.save(category);
        }
    }


    private CategoryResponseDTO toResponseDTO(Category category, List<SubcategoryDTO> subcategories) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getTemplates(),
                category.isDefault(),
                category.getCreatedAt(),
                subcategories
        );
    }
}
