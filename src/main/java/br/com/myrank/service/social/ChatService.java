package br.com.myrank.service.social;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.entity.ConversationMember;
import br.com.myrank.domain.entity.Message;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.ConversationMemberRole;
import br.com.myrank.domain.enums.ConversationType;
import br.com.myrank.domain.enums.MessageKind;
import br.com.myrank.dto.chat.*;
import br.com.myrank.repository.ConversationMemberRepository;
import br.com.myrank.repository.ConversationRepository;
import br.com.myrank.repository.FollowRepository;
import br.com.myrank.repository.MessageRepository;
import br.com.myrank.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chat unificado: DM (conversa DIRECT de 2 membros) e grupo (GROUP, N membros, 1 OWNER).
 * Não-lidas por membro via cursor. Real-time por polling.
 * Iniciar DM exige follow mútuo; grupo pode conter qualquer usuário.
 * Mensagens SYSTEM ("fulano criou o grupo"…) são texto pt-BR gerado aqui.
 */
@Service
public class ChatService {

    private static final int BODY_MAX = 2000;
    private static final int NAME_MAX = 80;
    private static final int HISTORY_MAX = 100;
    private static final int GROUP_MAX = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public ChatService(ConversationRepository conversationRepository,
                       ConversationMemberRepository memberRepository,
                       MessageRepository messageRepository,
                       FollowRepository followRepository,
                       UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    // ── Início de conversa ──────────────────────────────────────────────

    @Transactional
    public ChatConversationDTO startDirect(Long me, Long otherId) {
        if (otherId == null || otherId.equals(me)) {
            throw new IllegalArgumentException("Destinatário inválido.");
        }
        userRepository.findById(otherId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        assertMutual(me, otherId);

        Conversation conv = conversationRepository.findDirectBetween(me, otherId).orElseGet(() -> {
            Conversation fresh = conversationRepository.save(
                    new Conversation(ConversationType.DIRECT, null, me));
            memberRepository.save(new ConversationMember(fresh.getId(), me, ConversationMemberRole.MEMBER));
            memberRepository.save(new ConversationMember(fresh.getId(), otherId, ConversationMemberRole.MEMBER));
            return fresh;
        });
        return toConversationDTO(conv.getId(), me);
    }

    @Transactional
    public ChatConversationDTO createGroup(Long me, String nameRaw, List<Long> memberIdsRaw) {
        String name = sanitizeName(nameRaw);

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (memberIdsRaw != null) memberIdsRaw.forEach(id -> { if (id != null) ids.add(id); });
        ids.remove(me);
        // membros são opcionais: dá pra criar só com o dono e convidar depois
        if (ids.size() >= GROUP_MAX) throw new IllegalArgumentException("Máximo de " + GROUP_MAX + " participantes.");

        List<User> found = userRepository.findAllById(ids);
        if (found.size() != ids.size()) throw new IllegalArgumentException("Algum usuário não existe.");

        Conversation conv = conversationRepository.save(
                new Conversation(ConversationType.GROUP, name, me));
        memberRepository.save(new ConversationMember(conv.getId(), me, ConversationMemberRole.OWNER));
        for (Long id : ids) {
            memberRepository.save(new ConversationMember(conv.getId(), id, ConversationMemberRole.MEMBER));
        }
        system(conv.getId(), me, nameOf(me) + " criou o grupo \"" + name + "\"");
        return toConversationDTO(conv.getId(), me);
    }

    // ── Listagem ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatConversationDTO> listConversations(Long me) {
        List<ConversationMember> mine = memberRepository.findByUserId(me);
        if (mine.isEmpty()) return List.of();

        List<Long> convIds = mine.stream().map(ConversationMember::getConversationId).toList();
        Map<Long, Conversation> convs = conversationRepository.findAllById(convIds).stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c));
        Map<Long, List<ConversationMember>> membersByConv = memberRepository.findByConversationIdIn(convIds)
                .stream().collect(Collectors.groupingBy(ConversationMember::getConversationId));
        Map<Long, Message> lastByConv = messageRepository.findLastPerConversation(convIds).stream()
                .collect(Collectors.toMap(Message::getConversationId, m -> m));

        Set<Long> userIds = new HashSet<>();
        for (ConversationMember m : mine) {
            Conversation c = convs.get(m.getConversationId());
            if (c == null) continue;
            if (c.getType() == ConversationType.DIRECT) {
                membersByConv.getOrDefault(c.getId(), List.of()).forEach(cm -> {
                    if (!cm.getUserId().equals(me)) userIds.add(cm.getUserId());
                });
            }
            Message last = lastByConv.get(c.getId());
            if (last != null) userIds.add(last.getSenderId());
        }
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ChatConversationDTO> out = new ArrayList<>();
        for (ConversationMember m : mine) {
            Conversation c = convs.get(m.getConversationId());
            if (c == null) continue;
            List<ConversationMember> members = membersByConv.getOrDefault(c.getId(), List.of());
            Message last = lastByConv.get(c.getId());
            out.add(assemble(c, m, members, last, users, me));
        }
        out.sort(Comparator.comparing(
                (ChatConversationDTO d) -> d.lastAt(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    @Transactional(readOnly = true)
    public long unreadTotal(Long me) {
        return messageRepository.countUnreadTotal(me);
    }

    // ── Mensagens ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> history(Long me, Long convId, int page, int size) {
        assertMember(convId, me);
        int capped = Math.min(Math.max(size, 1), HISTORY_MAX);
        List<Message> rows = messageRepository.findByConversationIdOrderByIdDesc(
                convId, PageRequest.of(Math.max(page, 0), capped));

        Map<Long, User> users = userRepository.findAllById(
                        rows.stream().map(Message::getSenderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ChatMessageDTO> out = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            out.add(toMessageDTO(rows.get(i), me, users));
        }
        return out;
    }

    @Transactional
    public ChatMessageDTO send(Long me, Long convId, String bodyRaw) {
        ConversationMember member = assertMember(convId, me);
        String body = bodyRaw == null ? "" : bodyRaw.trim();
        if (body.isEmpty()) throw new IllegalArgumentException("A mensagem está vazia.");
        if (body.length() > BODY_MAX) throw new IllegalArgumentException("A mensagem passa de 2000 caracteres.");

        Message saved = messageRepository.save(new Message(convId, me, MessageKind.USER, body));
        member.setLastReadMessageId(saved.getId());
        memberRepository.save(member);

        Map<Long, User> users = userRepository.findById(me)
                .map(u -> Map.of(u.getId(), u)).orElse(Map.of());
        return toMessageDTO(saved, me, users);
    }

    @Transactional
    public void markRead(Long me, Long convId) {
        ConversationMember member = assertMember(convId, me);
        messageRepository.findFirstByConversationIdOrderByIdDesc(convId).ifPresent(last -> {
            if (member.getLastReadMessageId() == null || last.getId() > member.getLastReadMessageId()) {
                member.setLastReadMessageId(last.getId());
                memberRepository.save(member);
            }
        });
    }

    // ── Membros ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConversationMemberDTO> listMembers(Long me, Long convId) {
        assertMember(convId, me);
        List<ConversationMember> members = memberRepository.findByConversationId(convId);
        Map<Long, User> users = userRepository.findAllById(
                        members.stream().map(ConversationMember::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return members.stream()
                .sorted(Comparator
                        .comparing((ConversationMember m) -> m.getRole() == ConversationMemberRole.OWNER ? 0 : 1)
                        .thenComparing(m -> {
                            User u = users.get(m.getUserId());
                            return u != null ? u.getUsername().toLowerCase() : "";
                        }))
                .map(m -> {
                    User u = users.get(m.getUserId());
                    return new ConversationMemberDTO(
                            m.getUserId(),
                            u != null ? u.getUsername() : "Usuário",
                            u != null ? u.getAvatarUrl() : null,
                            m.getRole().name());
                })
                .toList();
    }

    @Transactional
    public List<ConversationMemberDTO> addMembers(Long me, Long convId, List<Long> userIdsRaw) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        requireOwner(myMembership, "Só o dono pode adicionar participantes.");

        Set<Long> existing = new HashSet<>(memberRepository.findMemberIds(convId));
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (userIdsRaw != null) userIdsRaw.forEach(id -> { if (id != null) ids.add(id); });
        ids.removeAll(existing);
        ids.remove(me);
        if (ids.isEmpty()) return listMembers(me, convId);
        if (existing.size() + ids.size() > GROUP_MAX) {
            throw new IllegalArgumentException("Máximo de " + GROUP_MAX + " participantes.");
        }

        List<User> found = userRepository.findAllById(ids);
        if (found.size() != ids.size()) throw new IllegalArgumentException("Algum usuário não existe.");

        for (Long id : ids) {
            memberRepository.save(new ConversationMember(convId, id, ConversationMemberRole.MEMBER));
        }
        system(convId, me, nameOf(me) + " adicionou " + humanJoin(
                found.stream().map(User::getUsername).toList()));
        return listMembers(me, convId);
    }

    @Transactional
    public void removeMember(Long me, Long convId, Long targetId) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);

        ConversationMember target = memberRepository.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> new IllegalArgumentException("Esse usuário não está no grupo."));

        boolean leaving = targetId.equals(me);
        if (!leaving) {
            requireOwner(myMembership, "Só o dono pode remover participantes.");
            if (target.getRole() == ConversationMemberRole.OWNER) {
                throw new IllegalArgumentException("Não dá pra remover o dono.");
            }
        }

        memberRepository.delete(target);

        if (leaving) {
            system(convId, me, nameOf(me) + " saiu do grupo");
            if (myMembership.getRole() == ConversationMemberRole.OWNER) {
                List<ConversationMember> remaining = memberRepository.findByConversationId(convId).stream()
                        .sorted(Comparator.comparing(ConversationMember::getJoinedAt))
                        .toList();
                if (remaining.isEmpty()) {
                    conversationRepository.deleteById(convId);
                    return;
                }
                ConversationMember heir = remaining.get(0);
                heir.setRole(ConversationMemberRole.OWNER);
                memberRepository.save(heir);
                system(convId, heir.getUserId(), nameOf(heir.getUserId()) + " agora é o dono do grupo");
            }
        } else {
            system(convId, me, nameOf(me) + " removeu " + nameOf(targetId));
        }
    }

    // ── Grupo: renomear / excluir ──────────────────────────────────────

    @Transactional
    public ChatConversationDTO rename(Long me, Long convId, String nameRaw) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        requireOwner(myMembership, "Só o dono pode renomear o grupo.");

        String name = sanitizeName(nameRaw);
        conv.setName(name);
        conversationRepository.save(conv);
        system(convId, me, nameOf(me) + " renomeou o grupo para \"" + name + "\"");
        return toConversationDTO(convId, me);
    }

    @Transactional
    public void deleteConversation(Long me, Long convId) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        requireOwner(myMembership, "Só o dono pode excluir o grupo.");
        conversationRepository.deleteById(convId);
    }

    // ── DTO builders ───────────────────────────────────────────────────

    private ChatConversationDTO toConversationDTO(Long convId, Long me) {
        Conversation conv = getConversation(convId);
        List<ConversationMember> members = memberRepository.findByConversationId(convId);
        ConversationMember myMembership = members.stream()
                .filter(m -> m.getUserId().equals(me)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Você não participa dessa conversa."));
        Message last = messageRepository.findFirstByConversationIdOrderByIdDesc(convId).orElse(null);

        Set<Long> userIds = new HashSet<>();
        if (conv.getType() == ConversationType.DIRECT) {
            members.forEach(m -> { if (!m.getUserId().equals(me)) userIds.add(m.getUserId()); });
        }
        if (last != null) userIds.add(last.getSenderId());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return assemble(conv, myMembership, members, last, users, me);
    }

    private ChatConversationDTO assemble(Conversation conv,
                                         ConversationMember myMembership,
                                         List<ConversationMember> members,
                                         Message last,
                                         Map<Long, User> users,
                                         Long me) {
        ChatUserDTO peer = null;
        if (conv.getType() == ConversationType.DIRECT) {
            peer = members.stream()
                    .filter(m -> !m.getUserId().equals(me))
                    .findFirst()
                    .map(m -> {
                        User u = users.get(m.getUserId());
                        return new ChatUserDTO(m.getUserId(),
                                u != null ? u.getUsername() : "Usuário",
                                u != null ? u.getAvatarUrl() : null);
                    })
                    .orElse(null);
        }

        String lastSenderName = null;
        boolean lastMine = false;
        String lastKind = null;
        String lastBody = null;
        if (last != null) {
            lastMine = last.getSenderId().equals(me);
            lastKind = last.getKind().name();
            lastBody = last.getBody();
            User su = users.get(last.getSenderId());
            lastSenderName = su != null ? su.getUsername() : null;
        }

        long unread = messageRepository.countUnread(conv.getId(), me, myMembership.getLastReadMessageId());

        return new ChatConversationDTO(
                conv.getId(),
                conv.getType().name(),
                conv.getName(),
                peer,
                members.size(),
                myMembership.getRole().name(),
                lastBody,
                lastSenderName,
                lastMine,
                lastKind,
                last != null ? last.getCreatedAt() : conv.getCreatedAt(),
                unread
        );
    }

    private ChatMessageDTO toMessageDTO(Message m, Long me, Map<Long, User> users) {
        User u = users.get(m.getSenderId());
        return new ChatMessageDTO(
                m.getId(),
                m.getSenderId(),
                u != null ? u.getUsername() : null,
                u != null ? u.getAvatarUrl() : null,
                m.getSenderId().equals(me),
                m.getKind().name(),
                m.getBody(),
                m.getCreatedAt()
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Conversation getConversation(Long convId) {
        return conversationRepository.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversa não encontrada."));
    }

    private ConversationMember assertMember(Long convId, Long me) {
        return memberRepository.findByConversationIdAndUserId(convId, me)
                .orElseThrow(() -> new IllegalArgumentException("Você não participa dessa conversa."));
    }

    private void requireGroup(Conversation conv) {
        if (conv.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Essa ação só vale pra grupos.");
        }
    }

    private void requireOwner(ConversationMember membership, String message) {
        if (membership.getRole() != ConversationMemberRole.OWNER) {
            throw new IllegalArgumentException(message);
        }
    }

    private void assertMutual(Long me, Long other) {
        boolean iFollow = followRepository.existsByFollowerIdAndFollowedId(me, other);
        boolean followsMe = followRepository.existsByFollowerIdAndFollowedId(other, me);
        if (!iFollow || !followsMe) {
            throw new IllegalArgumentException("Vocês precisam se seguir mutuamente pra trocar mensagens.");
        }
    }

    private String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Dê um nome ao grupo.");
        if (name.length() > NAME_MAX) throw new IllegalArgumentException("O nome passa de " + NAME_MAX + " caracteres.");
        return name;
    }

    private void system(Long convId, Long actorId, String text) {
        messageRepository.save(new Message(convId, actorId, MessageKind.SYSTEM, text));
    }

    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("Alguém");
    }

    private static String humanJoin(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " e " + names.get(1);
        if (names.size() == 3) return names.get(0) + ", " + names.get(1) + " e " + names.get(2);
        return names.get(0) + ", " + names.get(1) + " e mais " + (names.size() - 2);
    }
}
