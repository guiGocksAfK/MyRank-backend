package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.AccountDeleteRequestDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.social.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Exclusão definitiva da conta (LGPD art. 18, VI). Não é soft delete: depois do
 * DELETE os dados pessoais somem do banco e o email fica livre pra um cadastro novo.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatService chatService;
    private final BrevoEmailClient emailClient;

    public AccountDeletionService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  ChatService chatService,
                                  BrevoEmailClient emailClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.chatService = chatService;
        this.emailClient = emailClient;
    }

    /** Confirma posse do email antes de excluir uma conta que não tem senha. */
    public void issueDeletionCode(User user) {
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("Esta conta usa senha para confirmar a exclusão.");
        }
        if (!emailClient.isConfigured()) {
            throw new IllegalStateException("Envio de email indisponível. Tente novamente mais tarde.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getAccountDeletionCodeExpiresAt() != null
                && user.getAccountDeletionCodeExpiresAt().minusMinutes(9).isAfter(now)) {
            throw new IllegalArgumentException("Aguarde um minuto antes de pedir outro código.");
        }
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        String value = code.toString();
        emailClient.send(user.getEmail(), "Código para excluir sua conta MyRank",
                "<p>Use este código para confirmar a exclusão da sua conta MyRank:</p>"
                        + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:3px\">" + value + "</p>"
                        + "<p>Ele vence em 10 minutos. Se você não pediu a exclusão, ignore este email.</p>");
        user.setAccountDeletionCodeHash(sha256(value));
        user.setAccountDeletionCodeExpiresAt(now.plusMinutes(10));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(User user, AccountDeleteRequestDTO dto) {
        if (!user.getUsername().equals(dto.confirmUsername().trim())) {
            throw new IllegalArgumentException("O nome de usuário digitado não confere.");
        }

        // Token roubado não basta pra apagar a conta: quem tem senha precisa digitá-la.
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        if (hasPassword && (dto.password() == null || !passwordEncoder.matches(dto.password(), user.getPasswordHash()))) {
            throw new IllegalArgumentException("Senha incorreta.");
        }
        if (!hasPassword) {
            String supplied = dto.deletionCode() == null ? "" : dto.deletionCode().trim().toUpperCase();
            String expectedHash = user.getAccountDeletionCodeHash();
            if (expectedHash == null || user.getAccountDeletionCodeExpiresAt() == null
                    || !user.getAccountDeletionCodeExpiresAt().isAfter(LocalDateTime.now())
                    || !MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                    sha256(supplied).getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Código de exclusão inválido ou expirado.");
            }
        }

        Long userId = user.getId();
        chatService.releaseForAccountDeletion(userId);
        userRepository.hardDeleteById(userId);
        log.info("Conta {} excluída a pedido do usuário", userId);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
