package br.com.myrank.service;

import br.com.myrank.domain.entity.CreatorPhoto;
import br.com.myrank.domain.entity.UserCreator;
import br.com.myrank.dto.creator.CreatorProfileDTO;
import br.com.myrank.dto.creator.CreatorProfilesRequestDTO;
import br.com.myrank.dto.creator.CreatorProfilesRequestDTO.Item;
import br.com.myrank.dto.creator.CreatorUpdateDTO;
import br.com.myrank.repository.CreatorPhotoRepository;
import br.com.myrank.repository.UserCreatorRepository;
import br.com.myrank.service.external.TmdbService;
import br.com.myrank.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatorProfileServiceTest {

    private final CreatorPhotoRepository photos = mock(CreatorPhotoRepository.class);
    private final UserCreatorRepository edits = mock(UserCreatorRepository.class);
    private final TmdbService tmdb = mock(TmdbService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T12:00:00Z"));
    private final CreatorProfileService service = new CreatorProfileService(photos, edits, tmdb, clock);

    @BeforeEach
    void setUp() {
        when(photos.save(any(CreatorPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(edits.save(any(UserCreator.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photos.findAllById(any())).thenReturn(List.of());
        when(edits.findByUserIdAndCreatorKeyIn(anyLong(), anyCollection())).thenReturn(List.of());
        when(edits.findByUserIdAndCreatorKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(photos.findById(any())).thenReturn(Optional.empty());
    }

    private static CreatorProfilesRequestDTO request(Item... items) {
        return new CreatorProfilesRequestDTO(List.of(items));
    }

    @Test
    void semCache_procuraNaTmdbEGuardaPraTodos() {
        when(tmdb.findPersonPhoto("Christopher Nolan")).thenReturn("https://img/nolan.jpg");
        when(tmdb.findCompanyLogo("Toei Animation")).thenReturn("https://img/toei.png");

        List<CreatorProfileDTO> out = service.profiles(1L, request(
                new Item("Christopher Nolan", "PERSON"), new Item("Toei Animation", "COMPANY")));

        assertThat(out).extracting(CreatorProfileDTO::photoUrl).containsExactly("https://img/nolan.jpg", "https://img/toei.png");
        assertThat(out).noneMatch(CreatorProfileDTO::pending);
        verify(photos, times(2)).save(any(CreatorPhoto.class));
    }

    @Test
    void comCache_naoChamaATmdb_eNaoAchouTambemFicaGuardado() {
        LocalDateTime ontem = LocalDateTime.now(clock).minusDays(1);
        when(photos.findAllById(any())).thenReturn(List.of(
                new CreatorPhoto(new CreatorPhoto.Id("PERSON", "christopher nolan"), "https://img/nolan.jpg", ontem),
                new CreatorPhoto(new CreatorPhoto.Id("PERSON", "fulano"), null, ontem)));

        List<CreatorProfileDTO> out = service.profiles(1L, request(
                new Item("Christopher Nolan", "PERSON"), new Item("Fulano", "PERSON")));

        assertThat(out).extracting(CreatorProfileDTO::photoUrl).containsExactly("https://img/nolan.jpg", null);
        verify(tmdb, never()).findPersonPhoto(anyString());
    }

    @Test
    void naoAchouHaMaisDe30Dias_procuraDeNovo() {
        when(photos.findAllById(any())).thenReturn(List.of(new CreatorPhoto(new CreatorPhoto.Id("PERSON", "fulano"),
                null, LocalDateTime.now(clock).minusDays(31))));
        when(tmdb.findPersonPhoto("Fulano")).thenReturn("https://img/fulano.jpg");

        assertThat(service.profiles(1L, request(new Item("Fulano", "PERSON"))).get(0).photoUrl())
                .isEqualTo("https://img/fulano.jpg");
    }

    @Test
    void fotoDoUsuario_ganhaDaAutomatica_eNemProcura() {
        UserCreator mine = new UserCreator(1L, "vince gilligan");
        mine.setPhotoUrl("https://minha/foto.jpg");
        mine.setCreatorType("Criador");
        when(edits.findByUserIdAndCreatorKeyIn(eq(1L), anyCollection())).thenReturn(List.of(mine));

        CreatorProfileDTO out = service.profiles(1L, request(new Item("Vince Gilligan", "PERSON"))).get(0);

        assertThat(out.photoUrl()).isEqualTo("https://minha/foto.jpg");
        assertThat(out.customPhoto()).isTrue();
        assertThat(out.type()).isEqualTo("Criador");
        verify(tmdb, never()).findPersonPhoto(anyString());
    }

    @Test
    void limiteDeBuscasPorPedido_oRestoVoltaPendente() {
        when(tmdb.findPersonPhoto(anyString())).thenReturn(null);
        Item[] items = IntStream.range(0, CreatorProfileService.LOOKUPS_PER_REQUEST + 3)
                .mapToObj(i -> new Item("Pessoa " + i, "PERSON")).toArray(Item[]::new);

        List<CreatorProfileDTO> out = service.profiles(1L, request(items));

        assertThat(out.stream().filter(CreatorProfileDTO::pending)).hasSize(3);
        verify(tmdb, times(CreatorProfileService.LOOKUPS_PER_REQUEST)).findPersonPhoto(anyString());
    }

    @Test
    void tmdbFora_naoGuardaONaoAchou() {
        when(tmdb.findPersonPhoto(anyString())).thenThrow(new RestClientException("fora do ar"));

        CreatorProfileDTO out = service.profiles(1L, request(new Item("Christopher Nolan", "PERSON"))).get(0);

        assertThat(out.photoUrl()).isNull();
        verify(photos, never()).save(any(CreatorPhoto.class));
    }

    @Test
    void nomeRepetidoComGrafiaDiferente_viraUmSo() {
        when(tmdb.findPersonPhoto(anyString())).thenReturn(null);

        assertThat(service.profiles(1L, request(new Item("Hayao Miyazaki", "PERSON"), new Item("hayao miyazáki", "PERSON"))))
                .hasSize(1);
    }

    @Test
    void editar_guardaFotoETipo_eVazioApagaAEdicao() {
        CreatorProfileDTO saved = service.update(1L, new CreatorUpdateDTO("Vince Gilligan", " https://minha/foto.jpg ", "Criador", "PERSON"));
        assertThat(saved.photoUrl()).isEqualTo("https://minha/foto.jpg");
        assertThat(saved.customPhoto()).isTrue();

        UserCreator mine = new UserCreator(1L, "vince gilligan");
        when(edits.findByUserIdAndCreatorKey(1L, "vince gilligan")).thenReturn(Optional.of(mine));
        when(photos.findById(new CreatorPhoto.Id("PERSON", "vince gilligan"))).thenReturn(Optional.of(
                new CreatorPhoto(new CreatorPhoto.Id("PERSON", "vince gilligan"), "https://img/vince.jpg", LocalDateTime.now(clock))));

        CreatorProfileDTO reset = service.update(1L, new CreatorUpdateDTO("Vince Gilligan", "", null, "PERSON"));

        verify(edits).delete(mine);
        assertThat(reset.photoUrl()).isEqualTo("https://img/vince.jpg");
        assertThat(reset.customPhoto()).isFalse();
    }

    @Test
    void editar_recusaLinkSemHttps() {
        assertThatThrownBy(() -> service.update(1L, new CreatorUpdateDTO("Vince Gilligan", "http://x/y.jpg", null, "PERSON")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
