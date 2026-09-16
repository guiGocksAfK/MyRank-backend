package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Autentica o bot de Discord por API key de serviço, em vez de JWT de usuário —
 * um JWT de 24h obrigaria cada pessoa a reautenticar no Discord todo dia.
 *
 * <p>Headers:
 * <ul>
 *   <li>{@code X-Bot-Key} — segredo compartilhado ({@code MYRANK_BOT_API_KEY});</li>
 *   <li>{@code X-Discord-Id} — snowflake do usuário, resolvido contra
 *       {@code users.discord_id}.</li>
 * </ul>
 *
 * <p>A key é efetivamente uma chave mestra (quem a tiver age como qualquer usuário),
 * então vale só para o conjunto fechado de rotas em {@link #BOT_ROUTES}. Fora delas o
 * header é ignorado por completo e a cadeia segue para o {@link JwtAuthenticationFilter}.
 */
@Component
public class BotAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BotAuthenticationFilter.class);

    public static final String DISCORD_ID_ATTRIBUTE = "myrank.bot.discordId";

    private static final String KEY_HEADER = "X-Bot-Key";
    private static final String DISCORD_ID_HEADER = "X-Discord-Id";

    /**
     * Prefixos em que a bot key vale. {@code /api/external} entra porque o cadastro
     * pelo bot busca a obra pela própria API do MyRank — a alternativa seria o bot
     * falar direto com TMDB/RAWG/Jikan, duplicando a integração e as chaves.
     */
    private static final List<String> BOT_ROUTES = List.of(
            "/api/works",
            "/api/categories",
            "/api/badges",
            "/api/external"
    );

    private final UserRepository userRepository;

    /** Vazio = filtro desativado. Deploy sem a env var nunca fica com bypass aberto. */
    private final byte[] apiKey;

    public BotAuthenticationFilter(UserRepository userRepository,
                                   @Value("${myrank.bot.api-key:}") String apiKey) {
        this.userRepository = userRepository;
        this.apiKey = (apiKey == null || apiKey.isBlank())
                ? null
                : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String presentedKey = request.getHeader(KEY_HEADER);

        // Sem key configurada, sem key no request ou fora do escopo: nada a fazer —
        // segue para o fluxo JWT normal (o front não enxerga diferença).
        if (apiKey == null || presentedKey == null || presentedKey.isBlank() || !isBotRoute(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (!keyMatches(presentedKey)) {
            log.warn("Bot key inválida em {} {} (origem {})",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            unauthorized(response, "Bot key inválida.");
            return;
        }

        String discordId = request.getHeader(DISCORD_ID_HEADER);
        if (discordId == null || discordId.isBlank()) {
            unauthorized(response, "Header " + DISCORD_ID_HEADER + " obrigatório.");
            return;
        }

        Optional<User> user = userRepository.findByDiscordId(discordId.trim());
        if (user.isEmpty()) {
            unauthorized(response,
                    "Esta conta do Discord não está vinculada a um usuário do MyRank. "
                            + "Entre em myrank.duckdns.org com o Discord para vincular.");
            return;
        }

        String email = user.get().getEmail();
        if (email == null || email.isBlank()) {
            unauthorized(response, "Usuário vinculado não possui email.");
            return;
        }

        try {
            // O principal precisa ser um UserDetails com o EMAIL como username: é assim
            // que o AuthUtils resolve o User real, igual ao fluxo do JWT.
            UserDetails principal = org.springframework.security.core.userdetails.User
                    .withUsername(email)
                    .password("")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                            new SimpleGrantedAuthority("BOT_REQUEST"))
                    .build();

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // Lido pelo RateLimitFilter: o bot inteiro sai de um IP só, então o
            // contador precisa ser por usuário do Discord.
            request.setAttribute(DISCORD_ID_ATTRIBUTE, discordId.trim());

            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isBotRoute(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : BOT_ROUTES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Comparação em tempo constante — {@code equals} vaza o tamanho do prefixo igual. */
    private boolean keyMatches(String presented) {
        return MessageDigest.isEqual(apiKey, presented.getBytes(StandardCharsets.UTF_8));
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
