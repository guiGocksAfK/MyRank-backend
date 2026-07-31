package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.WorkCreateDTO;
import br.com.myrank.dto.WorkUpdateDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.WorkRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class WorkService {

    private final WorkRepository workRepository;
    private final CategoryRepository categoryRepository;

    public WorkService(WorkRepository workRepository, CategoryRepository categoryRepository) {
        this.workRepository = workRepository;
        this.categoryRepository = categoryRepository;
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
        work.setTitle(dto.title());
        work.setImageUrl(dto.imageUrl());
        work.setCreator(dto.creator());
        work.setReleaseDate(dto.releaseDate());
        work.setTimeMinutes(dto.timeMinutes());
        work.setScore(BigDecimal.valueOf(dto.score()));

        applyScoreCalculation(work);

        return workRepository.save(work);
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

        applyScoreCalculation(work);

        return workRepository.save(work);
    }

    public void deleteWork(Long workId, Long userId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new IllegalArgumentException("Obra não encontrada."));

        if (!work.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Você não tem permissão para excluir essa obra.");
        }

        workRepository.delete(work);
    }

    // Nota_Final = Nota_Original + Log10(Minutos / 60)
    private void applyScoreCalculation(Work work) {
        double timeBonus = Math.log10(work.getTimeMinutes() / 60.0);
        BigDecimal bonus = BigDecimal.valueOf(timeBonus).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalScore = work.getScore().add(bonus).setScale(2, RoundingMode.HALF_UP);

        work.setTimeBonusScore(bonus);
        work.setFinalScore(finalScore);
    }
}