package br.com.myrank.service.social;

import br.com.myrank.domain.entity.Message;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.ChatConversationDTO;
import br.com.myrank.dto.ChatMessageDTO;
import br.com.myrank.repository.FollowRepository;
import br.com.myrank.repository.MessageRepository;
import br.com.myrank.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DM 1:1. Só habilitado entre quem se segue mutuamente.
 * Sem real-time no servidor: o front faz polling do contador (igual ao sininho).
 */
@Service
public class ChatService {

    private static final int BODY_MAX = 2000;
    private static final int HISTORY_MAX = 100;
    private static final int INBOX_SCAN = 400;

    private final MessageRepository messageRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public ChatService(MessageRepository messageRepository,
                       FollowRepository followRepository,
                       UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    // ── Envio ───────────────────────────────────────────────────────────

    @Transactional
    public ChatMessageDTO send(Long me, Long recipientId, String bodyRaw) {
        if (recipientId == null) throw new IllegalArgumentException("Destinatário inválido.");
        String body = bodyRaw == null ? "" : bodyRaw.trim();
        if (body.isEmpty()) throw new IllegalArgumentException("A mensagem está vazia.");
        if (body.length() > BODY_MAX) throw new IllegalArgumentException("A mensagem passa de 2000 caracteres.");

        userRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        assertMutual(me, recipientId);

        Message saved = messageRepository.save(new Message(me, recipientId, body));
        return toDTO(saved, me);
    }

    // ── Leitura ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> conversation(Long me, Long otherId, int page, int size) {
        assertMutual(me, otherId);
        int capped = Math.min(Math.max(size, 1), HISTORY_MAX);
        List<Message> rows = messageRepository.findConversation(
                me, otherId, PageRequest.of(Math.max(page, 0), capped));

        // repo devolve desc; a tela quer asc (mais antiga no topo)
        List<ChatMessageDTO> out = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            out.add(toDTO(rows.get(i), me));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> inbox(Long me) {
        List<Message> recent = messageRepository.findRecentForUser(me, PageRequest.of(0, INBOX_SCAN));
        if (recent.isEmpty()) return List.of();

        // 1 linha por interlocutor: a primeira ocorrência já é a mais recente
        LinkedHashMap<Long, Message> lastByPeer = new LinkedHashMap<>();
        for (Message m : recent) {
            Long peer = m.getSenderId().equals(me) ? m.getRecipientId() : m.getSenderId();
            lastByPeer.putIfAbsent(peer, m);
        }

        Map<Long, User> users = userRepository.findAllById(lastByPeer.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, Long> unreadByPeer = new HashMap<>();
        for (Object[] row : messageRepository.unreadCountsBySender(me)) {
            unreadByPeer.put((Long) row[0], (Long) row[1]);
        }

        return lastByPeer.entrySet().stream().map(e -> {
            Long peerId = e.getKey();
            Message m = e.getValue();
            User u = users.get(peerId);
            return new ChatConversationDTO(
                    peerId,
                    u != null ? u.getUsername() : "Usuário",
                    u != null ? u.getAvatarUrl() : null,
                    m.getBody(),
                    m.getSenderId().equals(me),
                    m.getCreatedAt(),
                    unreadByPeer.getOrDefault(peerId, 0L)
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public long unreadTotal(Long me) {
        return messageRepository.countByRecipientIdAndReadAtIsNull(me);
    }

    @Transactional
    public int markRead(Long me, Long otherId) {
        return messageRepository.markConversationRead(me, otherId, LocalDateTime.now());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void assertMutual(Long me, Long other) {
        if (me.equals(other)) {
            throw new IllegalArgumentException("Você não pode conversar consigo mesmo.");
        }
        boolean iFollow = followRepository.existsByFollowerIdAndFollowedId(me, other);
        boolean followsMe = followRepository.existsByFollowerIdAndFollowedId(other, me);
        if (!iFollow || !followsMe) {
            throw new IllegalArgumentException("Vocês precisam se seguir mutuamente pra trocar mensagens.");
        }
    }

    private ChatMessageDTO toDTO(Message m, Long me) {
        return new ChatMessageDTO(
                m.getId(),
                m.getSenderId(),
                m.getSenderId().equals(me),
                m.getBody(),
                m.getCreatedAt(),
                m.getReadAt()
        );
    }
}
