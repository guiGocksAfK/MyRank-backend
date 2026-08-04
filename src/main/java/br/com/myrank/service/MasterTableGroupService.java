package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.MasterTableGroup;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.MasterTableGroupCreateDTO;
import br.com.myrank.dto.MasterTableGroupUpdateDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.MasterTableGroupRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MasterTableGroupService {

    private final MasterTableGroupRepository groupRepository;
    private final CategoryRepository categoryRepository;

    public MasterTableGroupService(MasterTableGroupRepository groupRepository, CategoryRepository categoryRepository) {
        this.groupRepository = groupRepository;
        this.categoryRepository = categoryRepository;
    }

    public MasterTableGroup createGroup(User user, MasterTableGroupCreateDTO dto) {
        if (groupRepository.existsByUserIdAndNameIgnoreCase(user.getId(), dto.name())) {
            throw new IllegalArgumentException("Você já tem um agrupamento com esse nome.");
        }

        Set<Category> categories = resolveOwnedCategories(user.getId(), dto.categoryIds());

        MasterTableGroup group = new MasterTableGroup();
        group.setUser(user);
        group.setName(dto.name());
        group.setCategories(categories);

        return groupRepository.save(group);
    }

    public List<MasterTableGroup> getGroupsByUser(Long userId) {
        return groupRepository.findByUserId(userId);
    }

    public MasterTableGroup updateGroup(Long groupId, Long userId, MasterTableGroupUpdateDTO dto) {
        MasterTableGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Agrupamento não encontrado."));

        if (!group.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para editar esse agrupamento.");
        }

        if (dto.name() != null && !dto.name().isBlank()) {
            group.setName(dto.name());
        }
        if (dto.categoryIds() != null) {
            group.setCategories(resolveOwnedCategories(userId, dto.categoryIds()));
        }

        return groupRepository.save(group);
    }

    public void deleteGroup(Long groupId, Long userId) {
        MasterTableGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Agrupamento não encontrado."));

        if (!group.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para excluir esse agrupamento.");
        }

        groupRepository.delete(group);
    }

    private Set<Category> resolveOwnedCategories(Long userId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos uma categoria.");
        }

        Set<Category> categories = new HashSet<>();
        for (Long id : categoryIds) {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + id));
            if (!category.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("Você não tem permissão sobre a categoria: " + id);
            }
            categories.add(category);
        }
        return categories;
    }
}