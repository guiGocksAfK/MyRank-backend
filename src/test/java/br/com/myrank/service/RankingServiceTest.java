package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.MasterTableGroup;
import br.com.myrank.domain.entity.UserCreator;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.CreatorRankingDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.MasterTableGroupRepository;
import br.com.myrank.repository.UserCreatorRepository;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.RankingService.CreatorOrder;
import br.com.myrank.service.RankingService.UnifiedSort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private static final long USER = 1L;

    private final WorkRepository works = mock(WorkRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final MasterTableGroupRepository groups = mock(MasterTableGroupRepository.class);
    private final UserCreatorRepository creators = mock(UserCreatorRepository.class);
    private final RankingService service = new RankingService(works, categories, groups, creators);

    private static Category table(long id) {
        Category category = new Category();
        category.setId(id);
        return category;
    }

    private static Work work(long id, Category table, double score, double finalScore, int minutes,
                             String creator, TableTemplate template) {
        Work work = new Work();
        work.setId(id);
        work.setCategory(table);
        work.setTitle("Obra " + id);
        work.setScore(BigDecimal.valueOf(score));
        work.setFinalScore(BigDecimal.valueOf(finalScore));
        work.setTimeMinutes(minutes);
        work.setCreator(creator);
        work.setTemplate(template);
        work.setPosition((int) id);
        return work;
    }

    private static MasterTableGroup unifiedGroup(Set<Category> tables, List<String> order) {
        MasterTableGroup group = new MasterTableGroup();
        group.setName(RankingService.UNIFIED_GROUP);
        group.setCategories(tables);
        group.setManualOrder(order);
        return group;
    }

    // ── Ranking geral ─────────────────────────────────────────────────────

    @Test
    void unificado_soDasTabelasEscolhidas_eOrdemManualDesempata() {
        Category filmes = table(10);
        Category series = table(20);
        Category jogos = table(30);
        Work a = work(1, filmes, 9, 9, 100, null, TableTemplate.MOVIE);
        Work b = work(2, series, 9, 9.5, 900, null, TableTemplate.TV);
        Work c = work(3, filmes, 8, 9.8, 120, null, TableTemplate.MOVIE);
        Work fora = work(4, jogos, 10, 10, 5000, null, TableTemplate.GAME);
        when(categories.findByUserId(USER)).thenReturn(List.of(filmes, series, jogos));
        when(works.findByUserId(USER)).thenReturn(List.of(a, b, c, fora));
        when(groups.findByUserId(USER)).thenReturn(List.of(
                unifiedGroup(Set.of(filmes, series), List.of("20:2"))));

        // nota pura: a e b empatam em 9; a ordem manual põe b antes
        assertThat(service.unified(USER, UnifiedSort.SCORE, null)).containsExactly(b, a, c);
        assertThat(service.unified(USER, UnifiedSort.WEIGHTED, null)).containsExactly(c, b, a);
        assertThat(service.unified(USER, UnifiedSort.TIME, null)).containsExactly(b, c, a);
    }

    @Test
    void unificado_semAgrupamento_usaTodasAsTabelas_naOrdemDelasEDasPosicoes() {
        Category filmes = table(10);
        Category series = table(20);
        Work s = work(5, series, 7, 7, 0, null, TableTemplate.TV);
        Work f2 = work(2, filmes, 7, 7, 0, null, TableTemplate.MOVIE);
        Work f1 = work(1, filmes, 7, 7, 0, null, TableTemplate.MOVIE);
        when(categories.findByUserId(USER)).thenReturn(List.of(filmes, series));
        when(works.findByUserId(USER)).thenReturn(List.of(s, f2, f1));
        when(groups.findByUserId(USER)).thenReturn(List.of());

        assertThat(service.unified(USER, UnifiedSort.SCORE, null)).containsExactly(f1, f2, s);
    }

    @Test
    void unificado_umaTabela_desempataPelaPosicao_eRecusaTabelaDeOutraPessoa() {
        Category filmes = table(10);
        Category series = table(20);
        Work p2 = work(2, filmes, 8, 8, 0, null, TableTemplate.MOVIE);
        Work p1 = work(1, filmes, 8, 8, 0, null, TableTemplate.MOVIE);
        Work top = work(3, filmes, 9, 9, 0, null, TableTemplate.MOVIE);
        Work outra = work(4, series, 10, 10, 0, null, TableTemplate.TV);
        when(categories.findByUserId(USER)).thenReturn(List.of(filmes, series));
        when(works.findByUserId(USER)).thenReturn(List.of(p2, outra, p1, top));

        assertThat(service.unified(USER, UnifiedSort.SCORE, 10L)).containsExactly(top, p1, p2);
        assertThatThrownBy(() -> service.unified(USER, UnifiedSort.SCORE, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Ranking de criadores ──────────────────────────────────────────────

    @Test
    void criadores_juntaDuplas_eAbsorveQuemCabeEmAlguemMaior() {
        Category t = table(1);
        Work bb = work(1, t, 10, 10, 3000, "Vince Gilligan", TableTemplate.TV);
        Work bcs = work(2, t, 9, 9, 3000, "Vince Gilligan, Peter Gould", TableTemplate.TV);
        Work got1 = work(3, t, 8, 8, 4000, "David Benioff & D. B. Weiss", TableTemplate.TV);
        Work got2 = work(4, t, 6, 6, 1000, "David Benioff, D. B. Weiss", TableTemplate.TV);
        when(works.findByUserIdOrderByFinalScoreDesc(USER)).thenReturn(List.of(bb, bcs, got1, got2));
        when(creators.findByUserIdAndCreatorKeyIn(eq(USER), anyCollection())).thenReturn(List.of());

        List<CreatorRankingDTO> ranking = service.creators(USER, CreatorOrder.AVG, null);

        // Peter Gould só tem uma obra, que já está com o Vince: não ganha linha
        assertThat(ranking).extracting(CreatorRankingDTO::name)
                .containsExactly("Vince Gilligan", "David Benioff & D. B. Weiss");
        assertThat(ranking.get(0).average()).isEqualTo(9.5);
        assertThat(ranking.get(0).count()).isEqualTo(2);
        assertThat(ranking.get(0).type()).isEqualTo("Diretor");
        assertThat(ranking.get(1).works()).hasSize(2);
    }

    @Test
    void criadores_ponderada_pesaOsMinutos_eSomaBonusDeVolumeComTeto() {
        Category t = table(1);
        Work longa = work(1, t, 10, 10, 900, "Fulano", TableTemplate.MOVIE);
        Work curta = work(2, t, 4, 4, 100, "Fulano", TableTemplate.MOVIE);
        Work semNota = work(3, t, 0, 0, 5000, "Fulano", TableTemplate.MOVIE);

        RankingService.Score score = RankingService.score(List.of(longa, curta, semNota), true);

        assertThat(score.raw()).isEqualTo(7.0); // a obra sem nota fica de fora
        // (10*900 + 4*100) / 1000 = 9.4, + log10(2)
        assertThat(score.value()).isCloseTo(9.4 + Math.log10(2), within(1e-9));

        List<Work> muitas = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(i -> work(i, t, 10, 10, 60, "X", TableTemplate.MOVIE)).toList();
        assertThat(RankingService.score(muitas, true).value()).isEqualTo(11.0); // bônus no teto de 1
    }

    @Test
    void criadores_ordemPorObras_eTipoEscolhidoPelaPessoa() {
        Category t = table(1);
        Work a1 = work(1, t, 6, 6, 0, "Studio A", TableTemplate.ANIME);
        Work a2 = work(2, t, 6, 6, 0, "Studio A", TableTemplate.ANIME);
        Work b1 = work(3, t, 9, 9, 0, "Autora B", TableTemplate.BOOK);
        when(works.findByUserIdOrderByFinalScoreDesc(USER)).thenReturn(List.of(a1, a2, b1));
        UserCreator escolhido = new UserCreator(USER, "autora b");
        escolhido.setCreatorType("Criador");
        when(creators.findByUserIdAndCreatorKeyIn(eq(USER), anyCollection())).thenReturn(List.of(escolhido));

        assertThat(service.creators(USER, CreatorOrder.WORKS, null))
                .extracting(CreatorRankingDTO::name).containsExactly("Studio A", "Autora B");
        assertThat(service.creators(USER, CreatorOrder.AVG, null))
                .extracting(CreatorRankingDTO::name).containsExactly("Autora B", "Studio A");
        assertThat(service.creators(USER, CreatorOrder.AVG, "Criador"))
                .extracting(CreatorRankingDTO::type).containsExactly("Criador");
        assertThat(service.creators(USER, CreatorOrder.AVG, "Studio"))
                .extracting(CreatorRankingDTO::name).containsExactly("Studio A");
    }

    @Test
    void criadores_tipoInvalido_recusa() {
        assertThatThrownBy(() -> service.creators(USER, CreatorOrder.AVG, "Ator"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
