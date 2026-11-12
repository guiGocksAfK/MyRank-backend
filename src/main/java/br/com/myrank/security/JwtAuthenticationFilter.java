package br.com.myrank.security;

import br.com.myrank.domain.entity.User;
import br.com.myrank.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Métodos que só leem: não precisam da conferência de origem. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SessionCookie sessionCookie;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, SessionCookie sessionCookie) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessionCookie = sessionCookie;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (isPublicRoute(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = resolveToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final Long userId = jwtService.extractUserId(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findById(userId).orElseThrow();

                if (jwtService.isTokenValid(token, user)) {
                    UserDetails userDetails = new AuthenticatedUser(user);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            // Token malformado / expirado / assinatura inválida / usuário sumido:
            // segue sem autenticação — o endpoint responde 401/403, não 500.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * O token vem do header {@code Authorization} (front antigo, enquanto a troca
     * não termina) ou do cookie da sessão. O header é imune a CSRF, porque só o
     * código do próprio front consegue colocá-lo; o cookie vai sozinho, então
     * requisição que muda algo só vale se a origem for a do front.
     */
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String fromCookie = sessionCookie.read(request);
        if (fromCookie == null) return null;
        if (!SAFE_METHODS.contains(request.getMethod()) && !sessionCookie.isTrustedOrigin(request)) {
            return null;
        }
        return fromCookie;
    }

    private static boolean isPublicRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        return path.startsWith("/api/auth/")
                || ("POST".equalsIgnoreCase(request.getMethod()) && "/api/users".equals(path));
    }
}
