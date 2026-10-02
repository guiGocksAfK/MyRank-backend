package br.com.myrank.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicSearchCacheTest {

    @Test
    void servicosSaoCriadosPorInjecaoComORestTemplateExistenteSemChaves() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RestTemplate.class, () -> restTemplate);
            context.register(MusicSearchCache.class, ItunesService.class, DeezerService.class);
            context.refresh();
            assertThat(context.getBean(DeezerService.class).searchMusic(" ")).isEmpty();
            assertThat(context.getBean(ItunesService.class).searchAlbums(" ")).isEmpty();
            verifyNoInteractions(restTemplate);
        }
    }

    @Test
    void entradaExpiraAposDezMinutosMesmoQuandoLidaDuranteOPeriodo() {
        AtomicLong now = new AtomicLong();
        MusicSearchCache cache = new MusicSearchCache(now::get);
        JsonNode original = JsonNodeFactory.instance.textNode("original");
        JsonNode renewed = JsonNodeFactory.instance.textNode("renovado");
        cache.remember("busca", original);
        now.set(Duration.ofMinutes(9).toNanos());
        assertThat(cache.find("busca")).isSameAs(original);
        now.set(Duration.ofMinutes(10).toNanos());
        assertThat(cache.find("busca")).isNull();
        assertThat(cache.get("busca", () -> renewed)).isSameAs(renewed);
    }

    @Test
    void cacheCompartilhadoRespeitaTetoDeQuinhentasEntradas() {
        MusicSearchCache cache = new MusicSearchCache();
        for (int i = 0; i < 600; i++) {
            cache.remember("resposta:" + i, JsonNodeFactory.instance.numberNode(i));
        }
        Cache<?, ?> responses = (Cache<?, ?>) ReflectionTestUtils.getField(cache, "responses");
        assertThat(responses).isNotNull();
        responses.cleanUp();
        assertThat(responses.estimatedSize()).isLessThanOrEqualTo(500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void respostaEmCacheDispensaNovaChamadaAoProvedor() {
        MusicSearchCache cache = new MusicSearchCache();
        Supplier<JsonNode> loader = mock(Supplier.class);
        JsonNode response = JsonNodeFactory.instance.objectNode();
        when(loader.get()).thenReturn(response);
        assertThat(cache.get("detalhes", loader)).isSameAs(response);
        assertThat(cache.get("detalhes", loader)).isSameAs(response);
        verify(loader, times(1)).get();
    }

    @Test
    void falhasNaoSaoArmazenadas_ePermitemTentarNovamente() {
        MusicSearchCache cache = new MusicSearchCache();
        assertThatThrownBy(() -> cache.get("detalhes", () -> { throw new IllegalStateException("Falha externa"); }))
                .isInstanceOf(IllegalStateException.class);
        JsonNode success = JsonNodeFactory.instance.objectNode();
        assertThat(cache.get("detalhes", () -> success)).isSameAs(success);
    }
}
