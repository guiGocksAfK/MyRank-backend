package br.com.myrank.controller;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.service.external.TmdbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de busca em bases externas (TMDB por enquanto; RAWG, Google Books
 * e Jikan entram depois seguindo o mesmo padrão).
 * Protegido pela mesma SecurityConfig já existente (usuário precisa estar logado).
 */
@RestController
@RequestMapping("/api/external")
public class ExternalSearchController {

    private final TmdbService tmdbService;

    public ExternalSearchController(TmdbService tmdbService) {
        this.tmdbService = tmdbService;
    }

    @GetMapping("/search/movies")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchMovies(@RequestParam String query) {
        return ResponseEntity.ok(tmdbService.searchMovies(query));
    }

    @GetMapping("/search/tv")
    public ResponseEntity<List<ExternalSearchResultDTO>> searchTvShows(@RequestParam String query) {
        return ResponseEntity.ok(tmdbService.searchTvShows(query));
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getMovieDetails(@PathVariable Long id) {
        return ResponseEntity.ok(tmdbService.getMovieDetails(id));
    }

    @GetMapping("/tv/{id}")
    public ResponseEntity<ExternalWorkDetailsDTO> getTvShowDetails(@PathVariable Long id) {
        return ResponseEntity.ok(tmdbService.getTvShowDetails(id));
    }
}