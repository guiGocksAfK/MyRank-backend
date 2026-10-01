package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.Subcategory;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.WorkCreateDTO;
import br.com.myrank.dto.WorkUpdateDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.SubcategoryRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.badge.BadgeService;
import br.com.myrank.service.social.FeedEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class WorkService {

    /** details vem do navegador: sem limite, alguém poderia encher o banco. */
    private static final int MAX_DETAILS_BYTES = 8 * 1024;

    private final WorkRepository workRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final BadgeService badgeService;
    private final FeedEventService feedEventService;
    private final ObjectMapper objectMapper;

    public WorkService(WorkRepository workRepository, CategoryRepository categoryRepository,
                       SubcategoryRepository subcategoryRepository,
                       BadgeService badgeService, FeedEventService feedEventService,
                       ObjectMapper objectMapper) {
        this.workRepository = workRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.badgeService = badgeService;
        this.feedEventService = feedEventService;
        this.objectMapper = objectMapper;
    }

    public Work createWork(User user, WorkCreateDTO dto) {
        Category category = categoryRepository.findById(dto.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        if (!category.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Você não tem permissão para adicionar obras nessa categoria.");
        }

        Work work = new Work();
        work.setCategory(category);
        work.setUser(user);
        work.setTemplate(resolveTemplate(category, dto.template()));
        work.setDetails(checkedDetails(dto.details()));
        work.setTitle(dto.title());
        work.setImageUrl(dto.imageUrl());
        work.setCreator(dto.creator());
        work.setReleaseDate(dto.releaseDate());
        work.setTimeMinutes(dto.timeMinutes());
        work.setScore(BigDecimal.valueOf(dto.score()));
        if (dto.subcategoryId() != null && dto.subcategoryId() != 0) {
            work.setSubcategory(resolveSubcategory(dto.subcategoryId(), category));
        }

        applyScoreCalculation(work);

        Work saved = workRepository.save(work);
        feedEventService.recordAdded(saved);
        badgeService.recalculateAsync(user.getId());
        return saved;
    }

    public List<Work> getWorksByCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        if (!category.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para ver essa categoria.");
        }

        return workRepository.findByCategoryId(categoryId);
    }

    public List<Work> getUnifiedWorks(Long userId) {
        return workRepository.findByUserIdOrderByFinalScoreDesc(userId);
    }

    public Work updateWork(Long workId, Long userId, WorkUpdateDTO dto) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new IllegalArgumentException("Obra não encontrada."));

        if (!work.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para editar essa obra.");
        }

        BigDecimal previousScore = work.getScore();

        if (dto.template() != null) work.setTemplate(resolveTemplate(work.getCategory(), dto.template()));
        if (dto.details() != null) work.setDetails(checkedDetails(dto.details()));

        if (dto.title() != null && !dto.title().isBlank()) {
            work.setTitle(dto.title());
        }
        if (dto.imageUrl() != null) {
            work.setImageUrl(dto.imageUrl());
        }
        if (dto.creator() != null) {
            work.setCreator(dto.creator());
        }
        if (dto.releaseDate() != null) {
            work.setReleaseDate(dto.releaseDate());
        }
        if (dto.timeMinutes() != null) {
            work.setTimeMinutes(dto.timeMinutes());
        }
        if (dto.score() != null) {
            work.setScore(BigDecimal.valueOf(dto.score()));
        }
        if (dto.subcategoryId() != null) {
            work.setSubcategory(dto.subcategoryId() == 0
                    ? null
                    : resolveSubcategory(dto.subcategoryId(), work.getCategory()));
        }

        applyScoreCalculation(work);

        Work saved = workRepository.save(work);
        if (previousScore == null || previousScore.compareTo(saved.getScore()) != 0) {
            feedEventService.recordRated(saved);
        }
        badgeService.recalculateAsync(userId);
        return saved;
    }

    /**
     * O tipo do item precisa ser um dos tipos da tabela. Sem tipo informado, só
     * dá pra adivinhar quando a tabela tem um tipo só.
     */
    private static TableTemplate resolveTemplate(Category category, TableTemplate requested) {
        List<TableTemplate> allowed = category.getTemplates();
        if (requested == null) {
            if (allowed.size() == 1) return allowed.get(0);
            throw new IllegalArgumentException("Escolha o tipo do item.");
        }
        if (!allowed.contains(requested)) {
            throw new IllegalArgumentException("Esse tipo não faz parte da tabela.");
        }
        return requested;
    }

    private Map<String, Object> checkedDetails(Map<String, Object> details) {
        if (details == null) return null;
        try {
            if (objectMapper.writeValueAsString(details).getBytes(StandardCharsets.UTF_8).length > MAX_DETAILS_BYTES) {
                throw new IllegalArgumentException("Detalhes do item grandes demais.");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Detalhes do item inválidos.");
        }
        return details;
    }

    public void deleteWork(Long workId, Long userId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new IllegalArgumentException("Obra não encontrada."));

        if (!work.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para excluir essa obra.");
        }

        workRepository.delete(work);
        badgeService.recalculateAsync(userId);
    }

    /** A subcategoria tem que ser da tabela da obra — o que também garante que é do mesmo dono. */
    private Subcategory resolveSubcategory(Long subcategoryId, Category category) {
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Subcategoria não encontrada."));
        if (!subcategory.getCategory().getId().equals(category.getId())) {
            throw new IllegalArgumentException("Essa subcategoria é de outra tabela.");
        }
        return subcategory;
    }

    // Nota_Final = Nota_Original + Log10(Minutos / 60)
    private void applyScoreCalculation(Work work) {
        double timeBonus = work.getTimeMinutes() > 0
            ? Math.log10(work.getTimeMinutes() / 60.0)
            : 0.0;
        BigDecimal bonus = BigDecimal.valueOf(timeBonus).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalScore = work.getScore().add(bonus).setScale(2, RoundingMode.HALF_UP);

        work.setTimeBonusScore(bonus);
        work.setFinalScore(finalScore);
    }
}
