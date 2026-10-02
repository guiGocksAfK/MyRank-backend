package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.OnboardingStep;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.SubcategoryRepository;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.repository.WorkRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static br.com.myrank.domain.enums.TableTemplate.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CategoryService categoryService = new CategoryService(categories,
            mock(SubcategoryRepository.class), mock(WorkRepository.class));
    private final OnboardingService service = new OnboardingService(users, categories, categoryService, entityManager);
    private User user;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(1L);
        user.setOnboardingStep(OnboardingStep.TABLES);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void escolher_criaSoOsTemplatesPedidosEAvancaAEtapa() {
        when(categories.saveAll(any())).thenAnswer(inv -> {
            List<Category> saved = inv.getArgument(0);
            for (int i = 0; i < saved.size(); i++) saved.get(i).setId((long) i + 10);
            return saved;
        });

        List<CategoryResponseDTO> response = service.chooseTables(1L, List.of(MUSIC, MOVIE));

        assertThat(response).extracting(CategoryResponseDTO::getName).containsExactly("🎵 Músicas", "🎬 Filmes");
        assertThat(response).extracting(CategoryResponseDTO::getTemplates).containsExactly(List.of(MUSIC), List.of(MOVIE));
        assertThat(response).allMatch(CategoryResponseDTO::isDefault);
        assertThat(response).allSatisfy(dto -> {
            assertThat(dto.getCustomFields()).isEmpty();
            assertThat(dto.getSubcategories()).isEmpty();
        });
        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.FIRST_WORK);
        verify(entityManager).refresh(user);
        verify(users).save(user);
    }

    @ParameterizedTest
    @MethodSource("invalidLists")
    void listasInvalidas_naoCriamTabelasNemAvancam(List<TableTemplate> templates) {
        assertThatThrownBy(() -> service.chooseTables(1L, templates)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
        verify(categories, never()).saveAll(any());
        verify(users, never()).save(any());
    }

    static Stream<List<TableTemplate>> invalidLists() {
        return Stream.of(null, List.of(), List.of(MOVIE, MOVIE), List.of(CUSTOM),
                List.of(MOVIE, CUSTOM), Arrays.asList(MOVIE, null));
    }

    @ParameterizedTest
    @EnumSource(value = OnboardingStep.class, names = {"FIRST_WORK", "DONE"})
    void escolhaForaDaEtapaTables_eRecusada(OnboardingStep step) {
        user.setOnboardingStep(step);
        assertThatThrownBy(() -> service.chooseTables(1L, List.of(MOVIE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TABLES");
        verify(categories, never()).saveAll(any());
    }

    @Test
    void nomesJaExistentes_naoCriamTabelasParcialmente() {
        when(categories.existsByUserIdAndNameIgnoreCase(1L, "🎬 Filmes")).thenReturn(true);
        assertThatThrownBy(() -> service.chooseTables(1L, List.of(MUSIC, MOVIE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("já tem uma categoria");
        verify(categories, never()).saveAll(any());
        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.TABLES);
    }

    @Test
    void finish_permitaPularAPrimeiraObra_eSejaIdempotente() {
        user.setOnboardingStep(OnboardingStep.FIRST_WORK);
        service.finish(1L);
        service.finish(1L);
        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.DONE);
        verify(users, times(1)).save(user);
        verifyNoInteractions(categories);
    }

    @Test
    void finishEmTables_eRecusado() {
        assertThatThrownBy(() -> service.finish(1L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escolha de tabelas");
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"choose", "finish"})
    void usuarioInexistente_eRecusado(String operation) {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> {
            if (operation.equals("choose")) service.chooseTables(1L, List.of(MOVIE));
            else service.finish(1L);
        }).isInstanceOf(IllegalArgumentException.class).hasMessage("Usuário não encontrado.");
        verifyNoInteractions(categories, entityManager);
    }
}
