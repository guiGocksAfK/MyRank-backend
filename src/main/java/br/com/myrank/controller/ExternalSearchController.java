package br.com.myrank.controller;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.service.external.GoogleBooksService;
import br.com.myrank.service.external.JikanService;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.ShowcaseService;
import br.com.myrank.service.external.TmdbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de busca em bases externas: TMDB (filmes/séries), RAWG (jogos),
 * Jikan (anime), Google Books (livros).
 * Protegido pela mesma SecurityConfig já existente (usuário precisa estar logado).
 */
@RestController
@RequestMapping("/api/external")
public class ExternalSearchController {

    private final TmdbService tmdbService;
    private final RawgService rawgService;
    private final JikanService jikanService;
    private final GoogleBooksService googleBooksService;
    private final ShowcaseService showcaseService;

    public ExternalSearchController(TmdbService tmdbService, RawgService rawgService,
                                    JikanService jikanService, GoogleBooksService googleBooksService,
                                    ShowcaseService showcaseService) {
        this.tmdbService = tmdbService;
        this.rawgService = rawgService;
        this.jikanService = jikanService;
        this.googleBooksService = googleBooksService;
        this.showcaseService = showcaseService;
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
        return ResponseEntity.ok(jikanService.searchAnime(query));
    }

    @GetMapping("/search/books")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchBooks(@RequestParam String query) {
        return ResponseEntity.ok(googleBooksService.searchBooks(query));
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
        return ResponseEntity.ok(jikanService.getAnimeDetails(id));
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getBookDetails(@PathVariable String id) {
        return ResponseEntity.ok(googleBooksService.getBookDetails(id));
    }
}