package br.com.myrank.controller;

import jakarta.validation.Valid;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.CategoryCreateDTO;
import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.dto.CategoryUpdateDTO;
import br.com.myrank.dto.SubcategoryDTO;
import br.com.myrank.dto.SubcategoryRequestDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final AuthUtils authUtils;

    public CategoryController(CategoryService categoryService, AuthUtils authUtils) {
        this.categoryService = categoryService;
        this.authUtils = authUtils;
    }

    @PostMapping
    public ResponseEntity<CategoryResponseDTO> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CategoryCreateDTO dto) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.createCategory(user, dto));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getMyCategories(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.getCategoriesByUser(user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateDTO dto) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.updateCategory(id, user.getId(), dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = authUtils.getUser(userDetails);
        categoryService.deleteCategory(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/subcategories")
    public ResponseEntity<SubcategoryDTO> createSubcategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody SubcategoryRequestDTO dto) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.createSubcategory(id, user.getId(), dto));
    }

    @PutMapping("/{id}/subcategories/{subcategoryId}")
    public ResponseEntity<SubcategoryDTO> renameSubcategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long subcategoryId,
            @Valid @RequestBody SubcategoryRequestDTO dto) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.renameSubcategory(id, subcategoryId, user.getId(), dto));
    }

    @DeleteMapping("/{id}/subcategories/{subcategoryId}")
    public ResponseEntity<Void> deleteSubcategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long subcategoryId) {
        User user = authUtils.getUser(userDetails);
        categoryService.deleteSubcategory(id, subcategoryId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
