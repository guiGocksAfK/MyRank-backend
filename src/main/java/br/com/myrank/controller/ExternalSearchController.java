package br.com.myrank.controller;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.TmdbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de busca em bases externas (TMDB para filmes/séries, RAWG para
 * jogos; Google Books e Jikan entram depois seguindo o mesmo padrão).
 * Protegido pela mesma SecurityConfig já existente (usuário precisa estar logado).
 */
@RestController
@RequestMapping("/api/external")
public class ExternalSearchController {

    private final TmdbService tmdbService;
    private final RawgService rawgService;

    public ExternalSearchController(TmdbService tmdbService, RawgService rawgService) {
        this.tmdbService = tmdbService;
        this.rawgService = rawgService;
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
}