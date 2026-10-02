package br.com.myrank.controller;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.service.external.DeezerService;
import br.com.myrank.service.external.GoogleBooksService;
import br.com.myrank.service.external.MyAnimeListService;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.ShowcaseService;
import br.com.myrank.service.external.TmdbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de busca em bases externas: TMDB (filmes/séries), RAWG (jogos),
 * MyAnimeList (animes e mangás), Google Books (livros), Deezer/iTunes (músicas e álbuns).
 * Protegido pela mesma SecurityConfig já existente (usuário precisa estar logado).
 */
@RestController
@RequestMapping("/api/external")
public class ExternalSearchController {

    private final TmdbService tmdbService;
    private final RawgService rawgService;
    private final MyAnimeListService myAnimeListService;
    private final GoogleBooksService googleBooksService;
    private final ShowcaseService showcaseService;
    private final DeezerService deezerService;

    public ExternalSearchController(TmdbService tmdbService, RawgService rawgService,
                                    MyAnimeListService myAnimeListService, GoogleBooksService googleBooksService,
                                    ShowcaseService showcaseService, DeezerService deezerService) {
        this.tmdbService = tmdbService;
        this.rawgService = rawgService;
        this.myAnimeListService = myAnimeListService;
        this.googleBooksService = googleBooksService;
        this.showcaseService = showcaseService;
        this.deezerService = deezerService;
    }

    /**
     * Grid decorativo da home pública: lista de URLs de pôster de obras populares.
     * Endpoint aberto (sem auth) — ver SecurityConfig. Pode vir vazio/parcial se as
     * bases externas estiverem indisponíveis; o frontend completa com fallback estático.
     */
    @GetMapping("/showcase")
    public ResponseEntity<List<String>> showcasePosters() {
        return ResponseEntity.ok(showcaseService.getShowcasePosters());
    }

    @GetMapping("/search/movies")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchMovies(@RequestParam String query) {
        return ResponseEntity.ok(tmdbService.searchMovies(query));
    }

    @GetMapping("/search/tv")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchTvShows(@RequestParam String query) {
        return ResponseEntity.ok(tmdbService.searchTvShows(query));
    }

    @GetMapping("/search/games")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchGames(@RequestParam String query) {
        return ResponseEntity.ok(rawgService.searchGames(query));
    }

    @GetMapping("/search/anime")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchAnime(@RequestParam String query) {
        return ResponseEntity.ok(myAnimeListService.searchAnime(query));
    }

    @GetMapping("/search/manga")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchManga(@RequestParam String query) {
        return ResponseEntity.ok(myAnimeListService.searchManga(query));
    }

    @GetMapping("/search/books")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchBooks(@RequestParam String query) {
        return ResponseEntity.ok(googleBooksService.searchBooks(query));
    }

    @GetMapping("/search/music")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchMusic(@RequestParam String query) {
        return ResponseEntity.ok(deezerService.searchMusic(query));
    }

    @GetMapping("/search/albums")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchAlbums(@RequestParam String query) {
        return ResponseEntity.ok(deezerService.searchAlbums(query));
    }

    @GetMapping("/music/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getMusicDetails(@PathVariable String id) {
        return ResponseEntity.ok(deezerService.getMusicDetails(id));
    }

    @GetMapping("/albums/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getAlbumDetails(@PathVariable String id) {
        return ResponseEntity.ok(deezerService.getAlbumDetails(id));
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getMovieDetails(@PathVariable Long id) {
        return ResponseEntity.ok(tmdbService.getMovieDetails(id));
    }

    @GetMapping("/tv/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getTvShowDetails(@PathVariable Long id) {
        return ResponseEntity.ok(tmdbService.getTvShowDetails(id));
    }

    @GetMapping("/games/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getGameDetails(@PathVariable Long id) {
        return ResponseEntity.ok(rawgService.getGameDetails(id));
    }

    @GetMapping("/anime/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getAnimeDetails(@PathVariable Long id) {
        return ResponseEntity.ok(myAnimeListService.getAnimeDetails(id));
    }

    @GetMapping("/manga/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getMangaDetails(@PathVariable Long id) {
        return ResponseEntity.ok(myAnimeListService.getMangaDetails(id));
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getBookDetails(@PathVariable String id) {
        return ResponseEntity.ok(googleBooksService.getBookDetails(id));
    }
}
