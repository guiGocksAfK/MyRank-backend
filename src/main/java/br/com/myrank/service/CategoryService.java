package br.com.myrank.service;

import br.com.myrank.dto.CategoryCreateDTO;
import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public CategoryResponseDTO createCategory(User user, CategoryCreateDTO dto) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), dto.getName())) {
            throw new IllegalArgumentException("Você já tem uma categoria com esse nome.");
        }

        Category category = new Category();
        category.setUser(user);
        category.setName(dto.getName());
        category.setDefault(false); // categorias criadas via API nunca são default

        Category saved = categoryRepository.save(category);
        return toResponseDTO(saved);
    }

    public List<CategoryResponseDTO> getCategoriesByUser(Long userId) {
        return categoryRepository.findByUserId(userId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public void deleteCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para excluir essa categoria.");
        }

        categoryRepository.delete(category);
    }

    // dentro da classe CategoryService, adiciona esse método

    public void createDefaultCategories(User user) {
        List<String> defaults = List.of(
                "🎬 Filmes",
                "🎮 Jogos",
                "📚 Livros",
                "📺 Séries & Animes"
        );

        for (String name : defaults) {
            Category category = new Category();
            category.setUser(user);
            category.setName(name);
            category.setDefault(true);
            categoryRepository.save(category);
        }
    }


    private CategoryResponseDTO toResponseDTO(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.isDefault(),
                category.getCreatedAt()
        );
    }
}