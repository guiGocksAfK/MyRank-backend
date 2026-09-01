package br.com.myrank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting em memória, janela fixa de 1 minuto, por IP. Cobre os endpoints
 * sensíveis a abuso: login/OAuth (brute-force), registro (spam de conta) e o
 * proxy pras APIs externas (que gasta as nossas quotas de TMDB/RAWG/MAL/Books).
 *
 * Chave = {@code request.getRemoteAddr()} — NÃO confia em X-Forwarded-For (é
 * spoofável). Atrás de proxy em prod, configure
 * {@code server.forward-headers-strategy=framework} pra que o remoteAddr venha certo.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    /** Acima disso, zera o mapa inteiro — teto de memória bruto pro volume atual. */
    private static final int MAX_KEYS = 20_000;

    private record Rule(String prefix, String method, int limit) {}

    private static final Rule[] RULES = {
            new Rule("/api/auth/login", "POST", 10),
            new Rule("/api/auth/oauth/", "POST", 15),
            new Rule("/api/users", "POST", 5),      // registro
            new Rule("/api/external/", null, 40),
    };

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        volatile long start;
        final AtomicInteger count = new AtomicInteger();
        Window(long start) { this.start = start; }
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        Rule rule = match(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        if (windows.size() > MAX_KEYS) windows.clear();

        String key = rule.prefix() + '|' + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(key, k -> new Window(now));

        synchronized (w) {
            if (now - w.start >= WINDOW_MS) {
                w.start = now;
                w.count.set(0);
            }
            if (w.count.incrementAndGet() > rule.limit()) {
                long retry = Math.max(1, (WINDOW_MS - (now - w.start)) / 1000);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retry));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"message\":\"Muitas requisições. Tente de novo em " + retry + "s.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Rule match(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String method = req.getMethod();
        for (Rule r : RULES) {
            boolean pathOk = r.prefix().endsWith("/") ? uri.startsWith(r.prefix()) : uri.equals(r.prefix());
            if (pathOk && (r.method() == null || r.method().equals(method))) {
                return r;
            }
        }
        return null;
    }
}
