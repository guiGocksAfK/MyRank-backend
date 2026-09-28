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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    private static final List<AntPathRequestMatcher> PUBLIC_ROUTES = List.of(
            new AntPathRequestMatcher("/api/auth/**"),
            new AntPathRequestMatcher("/api/users", "POST")
    );

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean isPublic = PUBLIC_ROUTES.stream().anyMatch(matcher -> matcher.matches(request));
        if (isPublic) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final Long userId = jwtService.extractUserId(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findById(userId).orElseThrow();
                UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

                if (jwtService.isTokenValid(token, userId)) {
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
}
