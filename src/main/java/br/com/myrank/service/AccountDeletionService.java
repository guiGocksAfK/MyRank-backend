package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.AccountDeleteRequestDTO;
import br.com.myrank.repository.UserRepository;
import br.com.myrank.service.social.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclusão definitiva da conta (LGPD art. 18, VI). Não é soft delete: depois do
 * DELETE os dados pessoais somem do banco e o email fica livre pra um cadastro novo.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatService chatService;

    public AccountDeletionService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  ChatService chatService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.chatService = chatService;
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

        Long userId = user.getId();
        chatService.releaseForAccountDeletion(userId);
        userRepository.hardDeleteById(userId);
        log.info("Conta {} excluída a pedido do usuário", userId);
    }
}
