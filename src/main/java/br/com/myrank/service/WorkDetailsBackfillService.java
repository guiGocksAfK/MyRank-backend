package br.com.myrank.service;

import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.RefreshDetailsResultDTO;
import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.external.DeezerService;
import br.com.myrank.service.external.GoogleBooksService;
import br.com.myrank.service.external.MyAnimeListService;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.TmdbService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Preenche de uma vez os detalhes (gêneros, temporadas, volumes…) das obras da
 * conta, como o botão "Atualizar dados" faz uma a uma. Obra que já sabe de onde
 * veio (provider + externalId) é buscada pelo id. Obra antiga, de antes do
 * details existir, é procurada pelo título, e só é ligada a um resultado se ele
 * for o único com o mesmo título (e o mesmo ano, ±1, quando a obra tem data):
 * sem alguém pra escolher, na dúvida é melhor pular do que ligar ao remake errado.
 *
 * Nunca mexe em nota, título, capa nem criador. Sem transação em volta: são
 * dezenas de chamadas externas, e cada obra é gravada sozinha.
 */
@Service
public class WorkDetailsBackfillService {

    private static final Logger log = LoggerFactory.getLogger(WorkDetailsBackfillService.class);

    private static final int MAX_DETAILS_BYTES = 8 * 1024;
    /** O MyAnimeList recusa buscas com menos de 3 caracteres. */
    private static final int MIN_QUERY_LENGTH = 3;

    private final WorkRepository workRepository;
    private final TmdbService tmdb;
    private final RawgService rawg;
    private final MyAnimeListService mal;
    private final GoogleBooksService googleBooks;
    private final DeezerService deezer;
    private final ObjectMapper objectMapper;

    public WorkDetailsBackfillService(WorkRepository workRepository,
                                      TmdbService tmdb,
                                      RawgService rawg,
                                      MyAnimeListService mal,
                                      GoogleBooksService googleBooks,
                                      DeezerService deezer,
                                      ObjectMapper objectMapper) {
        this.workRepository = workRepository;
        this.tmdb = tmdb;
        this.rawg = rawg;
        this.mal = mal;
        this.googleBooks = googleBooks;
        this.deezer = deezer;
        this.objectMapper = objectMapper;
    }

    public RefreshDetailsResultDTO refreshAll(Long userId) {
        List<String> updated = new ArrayList<>();
        List<RefreshDetailsResultDTO.Skipped> skipped = new ArrayList<>();

        for (Work work : workRepository.findByUserId(userId)) {
            if (work.getTemplate() == TableTemplate.CUSTOM) continue; // preenchida à mão, sem API

            String reason;
            try {
                reason = refresh(work);
            } catch (RuntimeException e) {
                log.warn("Falha ao atualizar os detalhes da obra {}: {}", work.getId(), e.getMessage());
                reason = "a busca falhou agora; tente de novo depois";
            }
            if (reason == null) {
                updated.add(work.getTitle());
            } else {
                skipped.add(new RefreshDetailsResultDTO.Skipped(work.getTitle(), reason));
            }
        }
        return new RefreshDetailsResultDTO(updated.size(), updated, skipped);
    }

    /** Atualiza e grava a obra. Devolve null se deu certo, ou o motivo de ter pulado. */
    private String refresh(Work work) {
        Map<String, Object> current = work.getDetails() == null ? Map.of() : work.getDetails();
        Object knownId = current.get("externalId");

        String externalId;
        if (knownId != null && !knownId.toString().isBlank()) {
            externalId = knownId.toString();
        } else {
            String title = work.getTitle() == null ? "" : work.getTitle().trim();
            if (title.length() < MIN_QUERY_LENGTH) return "título curto demais pra buscar";
            List<ExternalSearchResultDTO> matches = search(work.getTemplate(), title).stream()
                    .filter(r -> sameTitle(r.getTitle(), title))
                    .filter(r -> sameYear(work, r.getReleaseDate()))
                    .filter(r -> sameArtist(work, r.getSubtitle()))
                    .toList();
            if (matches.isEmpty()) return "nenhum resultado com o mesmo título e ano";
            if (matches.size() > 1) return "mais de um resultado com o mesmo título e ano";
            externalId = matches.get(0).getExternalId();
        }

        ExternalWorkDetailsDTO fetched = details(work.getTemplate(), externalId);
        Map<String, Object> next = new LinkedHashMap<>(current);
        if (fetched.getDetails() != null) next.putAll(fetched.getDetails());
        next.put("provider", provider(work.getTemplate()));
        next.put("externalId", externalId);

        if (tooBig(next)) return "detalhes grandes demais";
        work.setDetails(next);
        workRepository.save(work);
        return null;
    }

    private List<ExternalSearchResultDTO> search(TableTemplate template, String title) {
        return switch (template) {
            case MOVIE -> tmdb.searchMovies(title);
            case TV -> tmdb.searchTvShows(title);
            case GAME -> rawg.searchGames(title);
            case ANIME -> mal.searchAnime(title);
            case MANGA -> mal.searchManga(title);
            case BOOK -> googleBooks.searchBooks(title);
            case MUSIC -> deezer.searchMusic(title);
            case ALBUM -> deezer.searchAlbums(title);
            case CUSTOM -> List.of();
        };
    }

    private ExternalWorkDetailsDTO details(TableTemplate template, String id) {
        return switch (template) {
            case MOVIE -> tmdb.getMovieDetails(Long.valueOf(id));
            case TV -> tmdb.getTvShowDetails(Long.valueOf(id));
            case GAME -> rawg.getGameDetails(Long.valueOf(id));
            case ANIME -> mal.getAnimeDetails(Long.valueOf(id));
            case MANGA -> mal.getMangaDetails(Long.valueOf(id));
            case BOOK -> googleBooks.getBookDetails(id);
            case MUSIC -> deezer.getMusicDetails(id);
            case ALBUM -> deezer.getAlbumDetails(id);
            case CUSTOM -> throw new IllegalStateException("Personalizado não tem API.");
        };
    }

    /** Mesmo valor que o front grava no "Atualizar dados" (tableTemplates.js). */
    private static String provider(TableTemplate template) {
        return switch (template) {
            case MOVIE, TV -> "tmdb";
            case GAME -> "rawg";
            case ANIME, MANGA -> "myanimelist";
            case BOOK -> "google-books";
            case MUSIC, ALBUM -> "deezer";
            case CUSTOM -> null;
        };
    }

    /** Ano igual (±1) quando a obra tem data; sem data na obra, qualquer ano serve. */
    private static boolean sameYear(Work work, String resultDate) {
        if (work.getReleaseDate() == null) return true;
        if (resultDate == null || resultDate.length() < 4) return false;
        try {
            return Math.abs(Integer.parseInt(resultDate.substring(0, 4)) - work.getReleaseDate().getYear()) <= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Música e álbum: muitos títulos se repetem, então o artista também precisa bater. */
    private static boolean sameArtist(Work work, String resultArtist) {
        if (work.getTemplate() != TableTemplate.MUSIC && work.getTemplate() != TableTemplate.ALBUM) return true;
        if (work.getCreator() == null || work.getCreator().isBlank()) return true;
        return resultArtist != null && normalize(resultArtist).equals(normalize(work.getCreator()));
    }

    private static boolean sameTitle(String a, String b) {
        return a != null && normalize(a).equals(normalize(b));
    }

    /** Sem acento, sem pontuação e sem diferença de maiúsculas: "Steins;Gate" = "steins gate". */
    static String normalize(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private boolean tooBig(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details).getBytes(StandardCharsets.UTF_8).length > MAX_DETAILS_BYTES;
        } catch (JsonProcessingException e) {
            return true;
        }
    }
}
