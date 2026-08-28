package br.com.myrank.service;

import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.UserAvatar;
import br.com.myrank.repository.UserAvatarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
public class UserAvatarService {

    private static final long MAX_BYTES = 1_000_000; // 1 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );

    private final UserAvatarRepository avatarRepository;

    public UserAvatarService(UserAvatarRepository avatarRepository) {
        this.avatarRepository = avatarRepository;
    }

    @Transactional
    public void upload(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Nenhum arquivo enviado.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Formato inválido. Use PNG, JPEG ou WebP.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Imagem muito grande. O limite é 1 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler o arquivo enviado.", e);
        }

        UserAvatar avatar = avatarRepository.findById(user.getId())
                .orElseGet(() -> new UserAvatar());
        avatar.setUserId(user.getId());
        avatar.setImage(bytes);
        avatar.setContentType(contentType.toLowerCase());
        avatarRepository.save(avatar);
    }

    @Transactional(readOnly = true)
    public UserAvatar get(Long userId) {
        return avatarRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Avatar não encontrado."));
    }

    @Transactional
    public void delete(Long userId) {
        avatarRepository.deleteById(userId);
    }
}
