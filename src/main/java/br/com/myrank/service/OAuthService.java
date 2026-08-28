package br.com.myrank.service;

import br.com.myrank.config.OAuthProperties;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.AuthProvider;
import br.com.myrank.dto.auth.LoginResponseDTO;
import br.com.myrank.security.JwtService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OAuthService {

    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String DISCORD_USER_URL = "https://discord.com/api/users/@me";

    private final RestClient restClient;
    private final OAuthProperties oAuthProperties;
    private final UserService userService;
    private final JwtService jwtService;

    public OAuthService(OAuthProperties oAuthProperties, UserService userService, JwtService jwtService) {
        this.restClient = RestClient.create();
        this.oAuthProperties = oAuthProperties;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public LoginResponseDTO loginWithGoogle(String idToken) {
        return authenticateOAuth(verifyGoogleToken(idToken), AuthProvider.GOOGLE);
    }

    public LoginResponseDTO loginWithDiscord(String accessToken) {
        return authenticateOAuth(verifyDiscordToken(accessToken), AuthProvider.DISCORD);
    }

    public LoginResponseDTO loginWithDiscordCode(String code, String redirectUri) {
        String accessToken = exchangeDiscordCode(code, redirectUri);
        return loginWithDiscord(accessToken);
    }

    private String exchangeDiscordCode(String code, String redirectUri) {
        String clientId = oAuthProperties.getDiscord().getClientId();
        String clientSecret = oAuthProperties.getDiscord().getClientSecret();
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Discord OAuth não configurado (client id/secret).");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        try {
            DiscordTokenResponse tokenResponse = restClient.post()
                    .uri(DISCORD_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(DiscordTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new IllegalArgumentException("Não foi possível obter token do Discord.");
            }

            return tokenResponse.accessToken();
        } catch (RestClientResponseException ex) {
            throw new IllegalArgumentException("Code do Discord inválido ou expirado.");
        }
    }

    private LoginResponseDTO authenticateOAuth(OAuthUserInfo userInfo, AuthProvider provider) {
        User user = userService.findOrCreateFromOAuth(userInfo, provider);
        return buildLoginResponse(user);
    }

    private OAuthUserInfo verifyGoogleToken(String idToken) {
        String clientId = oAuthProperties.getGoogle().getClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google OAuth não configurado.");
        }

        try {
            GoogleTokenInfo tokenInfo = restClient.get()
                    .uri(GOOGLE_TOKEN_INFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(GoogleTokenInfo.class);

            if (tokenInfo == null || tokenInfo.sub() == null || tokenInfo.sub().isBlank()) {
                throw new IllegalArgumentException("Token do Google inválido.");
            }

            if (!clientId.equals(tokenInfo.aud())) {
                throw new IllegalArgumentException("Token do Google inválido.");
            }

            if (!"true".equalsIgnoreCase(tokenInfo.emailVerified())) {
                throw new IllegalArgumentException("Email do Google não verificado.");
            }

            String usernameBase = resolveUsernameBase(tokenInfo.name(), tokenInfo.email());
            return new OAuthUserInfo(tokenInfo.sub(), tokenInfo.email(), usernameBase, tokenInfo.picture());
        } catch (RestClientResponseException ex) {
            throw new IllegalArgumentException("Token do Google inválido ou expirado.");
        }
    }

    private OAuthUserInfo verifyDiscordToken(String accessToken) {
        String clientId = oAuthProperties.getDiscord().getClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Discord OAuth não configurado.");
        }

        try {
            DiscordUser discordUser = restClient.get()
                    .uri(DISCORD_USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(DiscordUser.class);

            if (discordUser == null || discordUser.id() == null || discordUser.id().isBlank()) {
                throw new IllegalArgumentException("Token do Discord inválido.");
            }

            if (discordUser.email() == null || discordUser.email().isBlank()) {
                throw new IllegalArgumentException("Email do Discord não disponível. Autorize o acesso ao email no login.");
            }

            if (!Boolean.TRUE.equals(discordUser.verified())) {
                throw new IllegalArgumentException("Email do Discord não verificado.");
            }

            String avatarUrl = buildDiscordAvatarUrl(discordUser);
            String usernameBase = discordUser.username() != null ? discordUser.username() : "discord" + discordUser.id();

            return new OAuthUserInfo(discordUser.id(), discordUser.email(), usernameBase, avatarUrl);
        } catch (RestClientResponseException ex) {
            throw new IllegalArgumentException("Token do Discord inválido ou expirado.");
        }
    }

    private String buildDiscordAvatarUrl(DiscordUser discordUser) {
        if (discordUser.avatar() != null && !discordUser.avatar().isBlank()) {
            return "https://cdn.discordapp.com/avatars/" + discordUser.id() + "/" + discordUser.avatar() + ".png";
        }

        long defaultAvatarIndex = (Long.parseLong(discordUser.id()) >> 22) % 6;
        return "https://cdn.discordapp.com/embed/avatars/" + defaultAvatarIndex + ".png";
    }

    private String resolveUsernameBase(String name, String email) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "user";
    }

    private LoginResponseDTO buildLoginResponse(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Conta OAuth sem email. Não foi possível autenticar.");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new LoginResponseDTO(token, user.getUsername());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiscordTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Integer expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("scope") String scope
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenInfo(
            @JsonProperty("sub") String sub,
            @JsonProperty("aud") String aud,
            @JsonProperty("email") String email,
            @JsonProperty("email_verified") String emailVerified,
            @JsonProperty("name") String name,
            @JsonProperty("picture") String picture
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiscordUser(
            @JsonProperty("id") String id,
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("verified") Boolean verified,
            @JsonProperty("avatar") String avatar
    ) {}
}
