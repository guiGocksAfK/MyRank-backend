package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.AccountDeleteRequestDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.email.BrevoEmailClient;
import br.com.myrank.service.email.EmailLayout;
import br.com.myrank.service.social.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Exclusão definitiva da conta (LGPD art. 18, VI). Não é soft delete: depois do
 * DELETE os dados pessoais somem do banco e o email fica livre pra um cadastro novo.
 *
 * Toda conta (com ou sem senha) confirma com um código mandado ao email: só exclui
 * quem está logado E tem acesso ao email. Depois da exclusão vai um comprovante.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_TTL_MINUTES = 10;

    private final UserRepository userRepository;
    private final ChatService chatService;
    private final BrevoEmailClient emailClient;

    public AccountDeletionService(UserRepository userRepository,
                                  ChatService chatService,
                                  BrevoEmailClient emailClient) {
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.emailClient = emailClient;
    }

    /** Manda o código que confirma a posse do email antes de excluir a conta. */
    public void issueDeletionCode(User user) {
        if (!emailClient.isConfigured()) {
            throw new IllegalStateException("Envio de email indisponível. Tente novamente mais tarde.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getAccountDeletionCodeExpiresAt() != null
                && user.getAccountDeletionCodeExpiresAt().minusMinutes(CODE_TTL_MINUTES - 1).isAfter(now)) {
            throw new IllegalArgumentException("Aguarde um minuto antes de pedir outro código.");
        }
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        String value = code.toString();

        CodeText text = CodeText.of(user.getLanguage());
        emailClient.send(user.getEmail(), text.subject(), text.html(user.getUsername(), value));
        user.setAccountDeletionCodeHash(sha256(value));
        user.setAccountDeletionCodeExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(User user, AccountDeleteRequestDTO dto) {
        if (!user.getUsername().equals(dto.confirmUsername().trim())) {
            throw new IllegalArgumentException("O nome de usuário digitado não confere.");
        }

        String supplied = dto.deletionCode() == null ? "" : dto.deletionCode().trim().toUpperCase();
        String expectedHash = user.getAccountDeletionCodeHash();
        if (expectedHash == null || user.getAccountDeletionCodeExpiresAt() == null
                || !user.getAccountDeletionCodeExpiresAt().isAfter(LocalDateTime.now())
                || !MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                sha256(supplied).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Código de exclusão inválido ou expirado.");
        }

        // Guardados antes do DELETE pro comprovante.
        String email = user.getEmail();
        String username = user.getUsername();
        String language = user.getLanguage();

        Long userId = user.getId();
        chatService.releaseForAccountDeletion(userId);
        userRepository.hardDeleteById(userId);
        log.info("Conta {} excluída a pedido do usuário", userId);

        afterCommit(() -> sendDeletedNotice(email, username, language));
    }

    /** Comprovante de exclusão. Falha no envio só é logada: a conta já foi apagada. */
    private void sendDeletedNotice(String email, String username, String language) {
        if (!emailClient.isConfigured() || email == null) return;
        try {
            DeletedText text = DeletedText.of(language);
            emailClient.send(email, text.subject(), text.html(username));
        } catch (RestClientException ex) {
            log.error("Falha ao enviar o comprovante de exclusão: {}", ex.getMessage());
        }
    }

    /** Só manda o comprovante se a exclusão realmente foi gravada. */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    /** Email do código (PT | EN | ES); o visual vem do EmailLayout. */
    private record CodeText(String subject, String preheader, String greeting, String intro,
                            String safetyTitle, String safetyText, String note, String footer) {

        static CodeText of(String language) {
            return switch (language == null ? "PT" : language) {
                case "EN" -> new CodeText(
                        "Your code to delete your MyRank account",
                        "The code is valid for 10 minutes.",
                        "Hi, %s!",
                        "Use the code below to confirm the deletion of your MyRank account.",
                        "Wasn't you?",
                        "Your account is safe. Nobody can delete it without this code, so just ignore this email.",
                        "The code is valid for 10 minutes.",
                        "You got this email because someone asked to delete your MyRank account.");
                case "ES" -> new CodeText(
                        "Tu código para eliminar tu cuenta de MyRank",
                        "El código vale por 10 minutos.",
                        "¡Hola, %s!",
                        "Usa el código de abajo para confirmar la eliminación de tu cuenta de MyRank.",
                        "¿No fuiste tú?",
                        "Tu cuenta está segura. Nadie puede eliminarla sin este código, así que ignora este email.",
                        "El código vale por 10 minutos.",
                        "Recibiste este email porque alguien pidió eliminar tu cuenta de MyRank.");
                default -> new CodeText(
                        "Seu código para excluir a conta do MyRank",
                        "O código vale por 10 minutos.",
                        "Olá, %s!",
                        "Use o código abaixo pra confirmar a exclusão da sua conta no MyRank.",
                        "Não foi você?",
                        "Sua conta está segura. Sem esse código ninguém consegue excluí-la, então é só ignorar este email.",
                        "O código vale por 10 minutos.",
                        "Você recebeu este email porque alguém pediu a exclusão da sua conta no MyRank.");
            };
        }

        String html(String username, String code) {
            return EmailLayout.render(new EmailLayout.Content(
                    preheader, greeting.formatted(username), intro, null, null, code,
                    null, safetyTitle, safetyText, note, null, footer));
        }
    }

    /** Comprovante de exclusão (PT | EN | ES): só aviso, sem botão. */
    private record DeletedText(String subject, String preheader, String greeting, String intro,
                               String outro, String footer) {

        static DeletedText of(String language) {
            return switch (language == null ? "PT" : language) {
                case "EN" -> new DeletedText(
                        "Your MyRank account was deleted",
                        "Your data has been permanently erased.",
                        "Hi, %s!",
                        "Your MyRank account was deleted. All your data (tables, scores, takes and messages) has been permanently erased.",
                        "Thanks for ranking with us. If you want to come back someday, just create a new account with this email.",
                        "You got this email as proof that your MyRank account was deleted.");
                case "ES" -> new DeletedText(
                        "Tu cuenta de MyRank fue eliminada",
                        "Tus datos se borraron de forma definitiva.",
                        "¡Hola, %s!",
                        "Tu cuenta de MyRank fue eliminada. Todos tus datos (tablas, notas, takes y mensajes) se borraron de forma definitiva.",
                        "Gracias por clasificar con nosotros. Si quieres volver algún día, solo crea una cuenta nueva con este email.",
                        "Recibiste este email como comprobante de la eliminación de tu cuenta de MyRank.");
                default -> new DeletedText(
                        "Sua conta no MyRank foi excluída",
                        "Seus dados foram apagados de vez.",
                        "Olá, %s!",
                        "Sua conta no MyRank foi excluída. Todos os seus dados (tabelas, notas, takes e mensagens) foram apagados de vez.",
                        "Obrigado por ter ranqueado com a gente. Se quiser voltar um dia, é só criar uma conta nova com este email.",
                        "Você recebeu este email como comprovante da exclusão da sua conta no MyRank.");
            };
        }

        String html(String username) {
            return EmailLayout.render(new EmailLayout.Content(
                    preheader, greeting.formatted(username), intro, null, null, null,
                    outro, null, null, null, null, footer));
        }
    }
}
