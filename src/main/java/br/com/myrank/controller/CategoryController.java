package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.CategoryCreateDTO;
import br.com.myrank.dto.CategoryResponseDTO;
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
            @RequestBody CategoryCreateDTO dto) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.createCategory(user, dto));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getMyCategories(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(categoryService.getCategoriesByUser(user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = authUtils.getUser(userDetails);
        categoryService.deleteCategory(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}