package br.com.myrank.service.social;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.entity.ConversationJoinRequest;
import br.com.myrank.domain.entity.ConversationMember;
import br.com.myrank.domain.entity.Message;
import br.com.myrank.domain.entity.MessageReaction;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.ConversationAccess;
import br.com.myrank.domain.enums.ConversationMemberRole;
import br.com.myrank.domain.enums.ConversationType;
import br.com.myrank.domain.enums.MessageKind;
import br.com.myrank.dto.chat.*;
import br.com.myrank.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chat unificado: DM (DIRECT, 2 membros) e grupo (GROUP).
 * Grupo tem foto, acesso (OPEN/REQUEST/CLOSED), cargos (OWNER > ADMIN > MOD > MEMBER)
 * e fila de pedidos de entrada. Mensagens podem ser respondidas, editadas, apagadas
 * (vira lápide) e reagidas (1 emoji por pessoa). Real-time por polling.
 * Textos SYSTEM são pt-BR gerados aqui (igual às notificações).
 */
@Service
public class ChatService {

    private static final int BODY_MAX = 2000;
    private static final int NAME_MAX = 80;
    private static final int DESC_MAX = 300;
    private static final int HISTORY_MAX = 100;
    private static final int GROUP_MAX = 50;
    private static final int IMAGE_URL_MAX = 1000;
    private static final int EXCERPT_MAX = 80;
    private static final int DIRECTORY_PAGE = 30;

    private static final Set<String> EMOJIS = Set.of(
            "👍", "❤️", "😂", "😮", "😢",
            "😠", "🎉", "🔥", "👀", "🙏");

    private static final SecureRandom RNG = new SecureRandom();

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final ConversationJoinRequestRepository joinRequestRepository;
    private final MessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ChatService(ConversationRepository conversationRepository,
                       ConversationMemberRepository memberRepository,
                       ConversationJoinRequestRepository joinRequestRepository,
                       MessageRepository messageRepository,
                       MessageReactionRepository reactionRepository,
                       FollowRepository followRepository,
                       UserRepository userRepository,
                       NotificationService notificationService) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ── Início de conversa ──────────────────────────────────────────────

    @Transactional
    public ChatConversationDTO startDirect(Long me, Long otherId) {
        if (otherId == null || otherId.equals(me)) {
            throw new IllegalArgumentException("Destinatário inválido.");
        }
        userRepository.findById(otherId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        // serializa criações concorrentes do mesmo par (evita DM duplicada numa corrida)
        conversationRepository.lockDirectPair(
                (int) Math.min(me, otherId), (int) Math.max(me, otherId));

        Conversation conv = conversationRepository
                .findDirectsBetween(ConversationType.DIRECT, me, otherId).stream().findFirst()
                .orElseGet(() -> {
                    Conversation fresh = conversationRepository.save(
                            new Conversation(ConversationType.DIRECT, null, me));
                    memberRepository.save(new ConversationMember(fresh.getId(), me, ConversationMemberRole.MEMBER));
                    memberRepository.save(new ConversationMember(fresh.getId(), otherId, ConversationMemberRole.MEMBER));
                    return fresh;
                });
        return toConversationDTO(conv.getId(), me);
    }

    @Transactional
    public ChatConversationDTO createGroup(Long me, String nameRaw, List<Long> memberIdsRaw,
                                           String accessRaw, String imageUrlRaw, String descriptionRaw) {
        String name = sanitizeName(nameRaw);
        ConversationAccess access = accessRaw == null || accessRaw.isBlank()
                ? ConversationAccess.REQUEST : parseAccess(accessRaw);
        String imageUrl = sanitizeImageUrl(imageUrlRaw);
        String description = sanitizeDescription(descriptionRaw);

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (memberIdsRaw != null) memberIdsRaw.forEach(id -> { if (id != null) ids.add(id); });
        ids.remove(me);
        if (ids.size() >= GROUP_MAX) throw new IllegalArgumentException("Máximo de " + GROUP_MAX + " participantes.");

        List<User> found = userRepository.findAllById(ids);
        if (found.size() != ids.size()) throw new IllegalArgumentException("Algum usuário não existe.");

        Conversation conv = new Conversation(ConversationType.GROUP, name, me);
        conv.setAccess(access);
        conv.setImageUrl(imageUrl);
        conv.setDescription(description);
        conv = conversationRepository.save(conv);

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
        Map<Long, Long> pendingByConv = joinRequestRepository.findByConversationIdIn(convIds).stream()
                .collect(Collectors.groupingBy(ConversationJoinRequest::getConversationId, Collectors.counting()));

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
            int pending = m.getRole().canModerate()
                    ? Math.toIntExact(pendingByConv.getOrDefault(c.getId(), 0L)) : 0;
            out.add(assemble(c, m, members, last, users, me, pending));
        }
        out.sort(Comparator.comparing(
                ChatConversationDTO::lastAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    @Transactional(readOnly = true)
    public long unreadTotal(Long me) {
        return messageRepository.countUnreadTotal(me);
    }

    // ── Diretório de grupos ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GroupDirectoryEntryDTO> directory(Long me, String qRaw, int page) {
        String q = qRaw == null ? "" : qRaw.trim();
        // a query já exclui grupos em que `me` é membro — aqui só resta NONE/PENDING
        List<Conversation> convs = conversationRepository.searchDirectory(
                ConversationType.GROUP, ConversationAccess.CLOSED,
                q, me, PageRequest.of(Math.max(page, 0), DIRECTORY_PAGE));
        if (convs.isEmpty()) return List.of();

        List<Long> ids = convs.stream().map(Conversation::getId).toList();
        Map<Long, Long> counts = memberRepository.findByConversationIdIn(ids).stream()
                .collect(Collectors.groupingBy(ConversationMember::getConversationId, Collectors.counting()));
        Set<Long> myPending = joinRequestRepository.findByUserId(me).stream()
                .map(ConversationJoinRequest::getConversationId).collect(Collectors.toSet());

        return convs.stream().map(c -> {
            String membership = myPending.contains(c.getId()) ? "PENDING" : "NONE";
            return new GroupDirectoryEntryDTO(
                    c.getId(),
                    c.getName(),
                    c.getDescription(),
                    c.getImageUrl(),
                    c.getAccess().name(),
                    Math.toIntExact(counts.getOrDefault(c.getId(), 0L)),
                    membership);
        }).toList();
    }

    @Transactional
    public String joinOrRequest(Long me, Long convId) {
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        if (memberRepository.existsByConversationIdAndUserId(convId, me)) {
            throw new IllegalArgumentException("Você já está no grupo.");
        }
        switch (conv.getAccess()) {
            case OPEN -> {
                if (memberRepository.countByConversationId(convId) >= GROUP_MAX) {
                    throw new IllegalArgumentException("O grupo está cheio.");
                }
                joinRequestRepository.findByConversationIdAndUserId(convId, me)
                        .ifPresent(joinRequestRepository::delete);
                memberRepository.save(new ConversationMember(convId, me, ConversationMemberRole.MEMBER));
                system(convId, me, nameOf(me) + " entrou no grupo");
                return "JOINED";
            }
            case REQUEST -> {
                if (joinRequestRepository.existsByConversationIdAndUserId(convId, me)) {
                    throw new IllegalArgumentException("Você já pediu pra entrar.");
                }
                joinRequestRepository.save(new ConversationJoinRequest(convId, me));
                return "REQUESTED";
            }
            default -> throw new IllegalArgumentException("Esse grupo é fechado.");
        }
    }

    @Transactional
    public void cancelJoinRequest(Long me, Long convId) {
        joinRequestRepository.findByConversationIdAndUserId(convId, me)
                .ifPresent(joinRequestRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestDTO> listJoinRequests(Long me, Long convId) {
        ConversationMember myMembership = assertMember(convId, me);
        requireGroup(getConversation(convId));
        if (!myMembership.getRole().canModerate()) {
            throw new IllegalArgumentException("Você não modera esse grupo.");
        }
        List<ConversationJoinRequest> reqs = joinRequestRepository.findByConversationId(convId);
        if (reqs.isEmpty()) return List.of();
        Map<Long, User> users = userRepository.findAllById(
                        reqs.stream().map(ConversationJoinRequest::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return reqs.stream()
                .sorted(Comparator.comparing(ConversationJoinRequest::getCreatedAt))
                .map(r -> {
                    User u = users.get(r.getUserId());
                    return new JoinRequestDTO(
                            r.getUserId(),
                            u != null ? u.getUsername() : "Usuário",
                            u != null ? u.getAvatarUrl() : null,
                            r.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public void resolveJoinRequest(Long me, Long convId, Long targetId, boolean approve) {
        ConversationMember myMembership = assertMember(convId, me);
        requireGroup(getConversation(convId));
        if (!myMembership.getRole().canModerate()) {
            throw new IllegalArgumentException("Você não modera esse grupo.");
        }
        ConversationJoinRequest req = joinRequestRepository.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        joinRequestRepository.delete(req);

        if (approve && !memberRepository.existsByConversationIdAndUserId(convId, targetId)) {
            if (memberRepository.countByConversationId(convId) >= GROUP_MAX) {
                throw new IllegalArgumentException("O grupo está cheio.");
            }
            memberRepository.save(new ConversationMember(convId, targetId, ConversationMemberRole.MEMBER));
            system(convId, me, nameOf(targetId) + " entrou no grupo");
            notificationService.onJoinRequestApproved(targetId, me, convId);
        }
    }

    // ── Mensagens ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> history(Long me, Long convId, int page, int size) {
        assertMember(convId, me);
        int capped = Math.min(Math.max(size, 1), HISTORY_MAX);
        List<Message> rows = messageRepository.findByConversationIdOrderByIdDesc(
                convId, PageRequest.of(Math.max(page, 0), capped));
        if (rows.isEmpty()) return List.of();

        List<Long> replyIds = rows.stream().map(Message::getReplyToId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Message> replyMsgs = replyIds.isEmpty() ? Map.of()
                : messageRepository.findByIdIn(replyIds).stream()
                        .collect(Collectors.toMap(Message::getId, m -> m));

        Set<Long> userIds = new HashSet<>();
        rows.forEach(m -> userIds.add(m.getSenderId()));
        replyMsgs.values().forEach(m -> userIds.add(m.getSenderId()));
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<MessageReaction>> reactionsByMsg = reactionRepository
                .findByMessageIdIn(rows.stream().map(Message::getId).toList()).stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));

        List<ChatMessageDTO> out = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            Message m = rows.get(i);
            out.add(buildMessageDTO(m, me, users, replyMsgs, users,
                    reactionsByMsg.getOrDefault(m.getId(), List.of())));
        }
        return out;
    }

    @Transactional
    public ChatMessageDTO send(Long me, Long convId, String bodyRaw, Long replyToId) {
        ConversationMember member = assertMember(convId, me);
        String body = bodyRaw == null ? "" : bodyRaw.trim();
        if (body.isEmpty()) throw new IllegalArgumentException("A mensagem está vazia.");
        if (body.length() > BODY_MAX) throw new IllegalArgumentException("A mensagem passa de 2000 caracteres.");

        if (replyToId != null) {
            Message target = messageRepository.findById(replyToId)
                    .orElseThrow(() -> new IllegalArgumentException("A mensagem respondida não existe."));
            if (!target.getConversationId().equals(convId)
                    || target.getKind() != MessageKind.USER
                    || target.getDeletedAt() != null) {
                throw new IllegalArgumentException("Não dá pra responder essa mensagem.");
            }
        }

        Message msg = new Message(convId, me, MessageKind.USER, body);
        msg.setReplyToId(replyToId);
        Message saved = messageRepository.save(msg);
        member.setLastReadMessageId(saved.getId());
        memberRepository.save(member);

        return oneMessageDTO(saved, me);
    }

    @Transactional
    public ChatMessageDTO editMessage(Long me, Long msgId, String bodyRaw) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada."));
        assertMember(msg.getConversationId(), me);
        if (!msg.getSenderId().equals(me)) {
            throw new IllegalArgumentException("Você só edita suas mensagens.");
        }
        if (msg.getKind() != MessageKind.USER || msg.getDeletedAt() != null) {
            throw new IllegalArgumentException("Essa mensagem não pode ser editada.");
        }
        String body = bodyRaw == null ? "" : bodyRaw.trim();
        if (body.isEmpty()) throw new IllegalArgumentException("A mensagem está vazia.");
        if (body.length() > BODY_MAX) throw new IllegalArgumentException("A mensagem passa de 2000 caracteres.");

        msg.setBody(body);
        msg.setEditedAt(LocalDateTime.now());
        messageRepository.save(msg);
        return oneMessageDTO(msg, me);
    }

    @Transactional
    public ChatMessageDTO deleteMessage(Long me, Long msgId) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada."));
        ConversationMember member = assertMember(msg.getConversationId(), me);
        if (msg.getKind() != MessageKind.USER) {
            throw new IllegalArgumentException("Não dá pra apagar avisos do sistema.");
        }
        boolean allowed = msg.getSenderId().equals(me) || member.getRole().canModerate();
        if (!allowed) throw new IllegalArgumentException("Você não pode apagar essa mensagem.");

        if (msg.getDeletedAt() == null) {
            msg.setDeletedAt(LocalDateTime.now());
            msg.setBody("");
            messageRepository.save(msg);
            reactionRepository.deleteByMessageId(msgId);
        }
        return oneMessageDTO(msg, me);
    }

    @Transactional
    public ChatMessageDTO react(Long me, Long msgId, String emojiRaw) {
        String emoji = emojiRaw == null ? "" : emojiRaw.trim();
        if (!EMOJIS.contains(emoji)) throw new IllegalArgumentException("Emoji inválido.");

        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada."));
        assertMember(msg.getConversationId(), me);
        if (msg.getKind() != MessageKind.USER || msg.getDeletedAt() != null) {
            throw new IllegalArgumentException("Não dá pra reagir a essa mensagem.");
        }

        reactionRepository.findByMessageIdAndUserId(msgId, me).ifPresentOrElse(existing -> {
            if (existing.getEmoji().equals(emoji)) {
                reactionRepository.delete(existing);          // toggle off
            } else {
                existing.setEmoji(emoji);
                reactionRepository.save(existing);
            }
        }, () -> reactionRepository.save(new MessageReaction(msgId, me, emoji)));

        return oneMessageDTO(msg, me);
    }

    /** Marca lido até a última mensagem. Retorna o id marcado, ou null se nada mudou. */
    @Transactional
    public Long markRead(Long me, Long convId) {
        ConversationMember member = assertMember(convId, me);
        Message last = messageRepository.findFirstByConversationIdOrderByIdDesc(convId).orElse(null);
        if (last == null) return null;
        if (member.getLastReadMessageId() == null || last.getId() > member.getLastReadMessageId()) {
            member.setLastReadMessageId(last.getId());
            memberRepository.save(member);
            return last.getId();
        }
        return null;
    }

    /** Nome de quem está numa conversa (valida que participa). Pro "digitando…". */
    @Transactional(readOnly = true)
    public String memberName(Long me, Long convId) {
        assertMember(convId, me);
        return userRepository.findById(me).map(User::getUsername).orElse("Alguém");
    }

    /** Heartbeat de presença — chamado no poll de não-lidas (~60s). */
    @Transactional
    public void heartbeat(Long me) {
        try {
            userRepository.updateLastSeen(me, LocalDateTime.now());
        } catch (Exception ignored) {
            // presença é best-effort
        }
    }

    // ── Membros / cargos ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConversationMemberDTO> listMembers(Long me, Long convId) {
        assertMember(convId, me);
        List<ConversationMember> members = memberRepository.findByConversationId(convId);
        Map<Long, User> users = userRepository.findAllById(
                        members.stream().map(ConversationMember::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return members.stream()
                .sorted(Comparator
                        .comparingInt((ConversationMember m) -> m.getRole().rank())
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
        requireGroup(getConversation(convId));
        if (!myMembership.getRole().canManageRoles()) {
            throw new IllegalArgumentException("Só o dono ou admins adicionam participantes.");
        }

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
            joinRequestRepository.findByConversationIdAndUserId(convId, id)
                    .ifPresent(joinRequestRepository::delete);
            memberRepository.save(new ConversationMember(convId, id, ConversationMemberRole.MEMBER));
            notificationService.onAddedToGroup(id, me, convId);
        }
        system(convId, me, nameOf(me) + " adicionou " + humanJoin(
                found.stream().map(User::getUsername).toList()));
        return listMembers(me, convId);
    }

    @Transactional
    public void removeMember(Long me, Long convId, Long targetId) {
        ConversationMember myMembership = assertMember(convId, me);
        requireGroup(getConversation(convId));

        ConversationMember target = memberRepository.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> new IllegalArgumentException("Esse usuário não está no grupo."));

        boolean leaving = targetId.equals(me);
        if (!leaving) {
            if (!myMembership.getRole().canModerate()) {
                throw new IllegalArgumentException("Você não pode expulsar ninguém desse grupo.");
            }
            if (myMembership.getRole().rank() >= target.getRole().rank()) {
                throw new IllegalArgumentException("Você só expulsa quem tem cargo abaixo do seu.");
            }
        }

        memberRepository.delete(target);

        if (leaving) {
            system(convId, me, nameOf(me) + " saiu do grupo");
            if (myMembership.getRole() == ConversationMemberRole.OWNER) {
                List<ConversationMember> remaining = memberRepository.findByConversationId(convId).stream()
                        .sorted(Comparator.comparingInt((ConversationMember cm) -> cm.getRole().rank())
                                .thenComparing(ConversationMember::getJoinedAt))
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

    @Transactional
    public List<ConversationMemberDTO> setRole(Long me, Long convId, Long targetId, String roleRaw) {
        ConversationMember myMembership = assertMember(convId, me);
        requireGroup(getConversation(convId));
        if (!myMembership.getRole().canManageRoles()) {
            throw new IllegalArgumentException("Só o dono ou admins mexem em cargos.");
        }
        ConversationMemberRole newRole = parseAssignableRole(roleRaw);
        ConversationMember target = memberRepository.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> new IllegalArgumentException("Esse usuário não está no grupo."));
        if (target.getRole() == ConversationMemberRole.OWNER) {
            throw new IllegalArgumentException("Não dá pra mudar o cargo do dono.");
        }
        if (target.getRole() == newRole) return listMembers(me, convId);

        boolean promote = newRole.rank() < target.getRole().rank();
        target.setRole(newRole);
        memberRepository.save(target);
        system(convId, me, nameOf(me) + (promote ? " promoveu " : " rebaixou ")
                + nameOf(targetId) + (promote ? " a " : " para ") + roleLabel(newRole));
        return listMembers(me, convId);
    }

    // ── Grupo: editar / excluir ────────────────────────────────────────

    @Transactional
    public ChatConversationDTO updateGroup(Long me, Long convId, UpdateGroupDTO dto) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        if (!myMembership.getRole().canEditGroup()) {
            throw new IllegalArgumentException("Só o dono ou admins editam o grupo.");
        }

        if (dto.name() != null) {
            String name = sanitizeName(dto.name());
            if (!name.equals(conv.getName())) {
                conv.setName(name);
                system(convId, me, nameOf(me) + " renomeou o grupo para \"" + name + "\"");
            }
        }
        if (dto.imageUrl() != null) {
            String v = dto.imageUrl().trim();
            if (v.isEmpty()) {
                if (conv.getImageUrl() != null) {
                    conv.setImageUrl(null);
                    system(convId, me, nameOf(me) + " removeu a foto do grupo");
                }
            } else {
                String url = sanitizeImageUrl(v);
                if (!url.equals(conv.getImageUrl())) {
                    conv.setImageUrl(url);
                    system(convId, me, nameOf(me) + " atualizou a foto do grupo");
                }
            }
        }
        if (dto.description() != null) {
            String v = dto.description().trim();
            if (v.isEmpty()) {
                if (conv.getDescription() != null) {
                    conv.setDescription(null);
                    system(convId, me, nameOf(me) + " removeu a descrição do grupo");
                }
            } else {
                String desc = sanitizeDescription(v);
                if (!desc.equals(conv.getDescription())) {
                    conv.setDescription(desc);
                    system(convId, me, nameOf(me) + " atualizou a descrição do grupo");
                }
            }
        }
        if (dto.access() != null) {
            ConversationAccess access = parseAccess(dto.access());
            if (access != conv.getAccess()) {
                conv.setAccess(access);
                if (access != ConversationAccess.REQUEST) {
                    joinRequestRepository.findByConversationId(convId).forEach(joinRequestRepository::delete);
                }
                system(convId, me, nameOf(me) + " mudou o acesso do grupo para " + accessLabel(access));
            }
        }
        conversationRepository.save(conv);
        return toConversationDTO(convId, me);
    }

    // ── Grupo: link de convite (reutilizável + revogável) ──────────────

    @Transactional(readOnly = true)
    public String getInviteToken(Long me, Long convId) {
        Conversation conv = requireInviteManager(me, convId, "veem");
        return conv.getInviteToken();
    }

    @Transactional
    public String rotateInviteToken(Long me, Long convId) {
        Conversation conv = requireInviteManager(me, convId, "geram");
        String token = newInviteToken();
        conv.setInviteToken(token);
        conversationRepository.save(conv);
        system(convId, me, nameOf(me) + " gerou um novo link de convite");
        return token;
    }

    @Transactional
    public void revokeInviteToken(Long me, Long convId) {
        Conversation conv = requireInviteManager(me, convId, "revogam");
        if (conv.getInviteToken() != null) {
            conv.setInviteToken(null);
            conversationRepository.save(conv);
            system(convId, me, nameOf(me) + " revogou o link de convite");
        }
    }

    @Transactional
    public ChatConversationDTO acceptInvite(Long me, String tokenRaw) {
        String token = tokenRaw == null ? "" : tokenRaw.trim();
        if (token.isEmpty()) throw new IllegalArgumentException("Convite inválido.");
        Conversation conv = conversationRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Esse convite não vale mais."));
        requireGroup(conv);

        if (!memberRepository.existsByConversationIdAndUserId(conv.getId(), me)) {
            if (memberRepository.countByConversationId(conv.getId()) >= GROUP_MAX) {
                throw new IllegalArgumentException("O grupo está cheio.");
            }
            joinRequestRepository.findByConversationIdAndUserId(conv.getId(), me)
                    .ifPresent(joinRequestRepository::delete);
            memberRepository.save(new ConversationMember(conv.getId(), me, ConversationMemberRole.MEMBER));
            system(conv.getId(), me, nameOf(me) + " entrou no grupo pelo link de convite");
        }
        return toConversationDTO(conv.getId(), me);
    }

    private Conversation requireInviteManager(Long me, Long convId, String verb) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        if (!myMembership.getRole().canEditGroup()) {
            throw new IllegalArgumentException("Só o dono ou admins " + verb + " o link de convite.");
        }
        return conv;
    }

    private static String newInviteToken() {
        byte[] buf = new byte[18];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf); // 24 chars
    }

    @Transactional
    public void deleteConversation(Long me, Long convId) {
        ConversationMember myMembership = assertMember(convId, me);
        Conversation conv = getConversation(convId);
        requireGroup(conv);
        if (myMembership.getRole() != ConversationMemberRole.OWNER) {
            throw new IllegalArgumentException("Só o dono pode excluir o grupo.");
        }
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

        int pending = myMembership.getRole().canModerate()
                ? Math.toIntExact(joinRequestRepository.countByConversationId(convId)) : 0;
        return assemble(conv, myMembership, members, last, users, me, pending);
    }

    private ChatConversationDTO assemble(Conversation conv,
                                         ConversationMember myMembership,
                                         List<ConversationMember> members,
                                         Message last,
                                         Map<Long, User> users,
                                         Long me,
                                         int pendingRequests) {
        ChatUserDTO peer = null;
        Long peerLastReadId = null;
        LocalDateTime peerLastSeenAt = null;
        if (conv.getType() == ConversationType.DIRECT) {
            ConversationMember peerMember = members.stream()
                    .filter(m -> !m.getUserId().equals(me))
                    .findFirst()
                    .orElse(null);
            if (peerMember != null) {
                User u = users.get(peerMember.getUserId());
                peer = new ChatUserDTO(peerMember.getUserId(),
                        u != null ? u.getUsername() : "Usuário",
                        u != null ? u.getAvatarUrl() : null);
                peerLastReadId = peerMember.getLastReadMessageId();
                peerLastSeenAt = u != null ? u.getLastSeenAt() : null;
            }
        }

        String lastSenderName = null;
        boolean lastMine = false;
        String lastKind = null;
        String lastBody = null;
        if (last != null) {
            lastMine = last.getSenderId().equals(me);
            lastKind = last.getKind().name();
            lastBody = last.getDeletedAt() != null ? "Mensagem apagada" : last.getBody();
            User su = users.get(last.getSenderId());
            lastSenderName = su != null ? su.getUsername() : null;
        }

        long unread = messageRepository.countUnread(conv.getId(), me, myMembership.getLastReadMessageId());

        return new ChatConversationDTO(
                conv.getId(),
                conv.getType().name(),
                conv.getName(),
                conv.getDescription(),
                conv.getImageUrl(),
                conv.getAccess().name(),
                peer,
                members.size(),
                myMembership.getRole().name(),
                pendingRequests,
                lastBody,
                lastSenderName,
                lastMine,
                lastKind,
                last != null ? last.getCreatedAt() : conv.getCreatedAt(),
                unread,
                myMembership.getLastReadMessageId(),
                peerLastReadId,
                peerLastSeenAt
        );
    }

    /** Constrói o DTO de uma mensagem fazendo os loads pontuais (send/edit/delete/react). */
    private ChatMessageDTO oneMessageDTO(Message m, Long me) {
        Set<Long> uids = new HashSet<>(List.of(m.getSenderId(), me));
        Map<Long, Message> replyMsgs = Map.of();
        if (m.getReplyToId() != null) {
            Message rt = messageRepository.findById(m.getReplyToId()).orElse(null);
            if (rt != null) {
                replyMsgs = Map.of(rt.getId(), rt);
                uids.add(rt.getSenderId());
            }
        }
        Map<Long, User> users = userRepository.findAllById(uids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<MessageReaction> reactions = m.getDeletedAt() != null
                ? List.of() : reactionRepository.findByMessageId(m.getId());
        return buildMessageDTO(m, me, users, replyMsgs, users, reactions);
    }

    private ChatMessageDTO buildMessageDTO(Message m, Long me,
                                           Map<Long, User> users,
                                           Map<Long, Message> replyMsgs,
                                           Map<Long, User> replyUsers,
                                           List<MessageReaction> reactions) {
        boolean deleted = m.getDeletedAt() != null;
        User sender = users.get(m.getSenderId());

        ReplyPreviewDTO reply = null;
        if (m.getReplyToId() != null) {
            Message rt = replyMsgs.get(m.getReplyToId());
            if (rt != null) {
                boolean rtDeleted = rt.getDeletedAt() != null;
                User ru = replyUsers.get(rt.getSenderId());
                reply = new ReplyPreviewDTO(
                        rt.getId(),
                        ru != null ? ru.getUsername() : null,
                        rtDeleted ? null : excerpt(rt.getBody()),
                        rtDeleted);
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        String mineEmoji = null;
        for (MessageReaction r : reactions) {
            counts.merge(r.getEmoji(), 1, Integer::sum);
            if (r.getUserId().equals(me)) mineEmoji = r.getEmoji();
        }
        final String fMine = mineEmoji;
        List<ReactionCountDTO> rx = counts.entrySet().stream()
                .map(e -> new ReactionCountDTO(e.getKey(), e.getValue(), e.getKey().equals(fMine)))
                .toList();

        return new ChatMessageDTO(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                sender != null ? sender.getUsername() : null,
                sender != null ? sender.getAvatarUrl() : null,
                m.getSenderId().equals(me),
                m.getKind().name(),
                deleted ? null : m.getBody(),
                m.getEditedAt() != null,
                deleted,
                reply,
                rx,
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

    /** Usuários que você segue de volta e ainda não tem DM — pra sugerir "diga oi". */
    @Transactional(readOnly = true)
    public List<ChatUserDTO> suggestedDirects(Long me) {
        Set<Long> iFollow = new HashSet<>(followRepository.findFollowedIds(me));
        Set<Long> followMe = new HashSet<>(followRepository.findFollowerIds(me));
        iFollow.retainAll(followMe); // mútuos
        iFollow.remove(me);
        if (iFollow.isEmpty()) return List.of();

        iFollow.removeAll(conversationRepository.findDirectPeerIds(me, ConversationType.DIRECT));
        if (iFollow.isEmpty()) return List.of();

        return userRepository.findAllById(iFollow).stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase()))
                .map(u -> new ChatUserDTO(u.getId(), u.getUsername(), u.getAvatarUrl()))
                .toList();
    }

    private String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Dê um nome ao grupo.");
        if (name.length() > NAME_MAX) throw new IllegalArgumentException("O nome passa de " + NAME_MAX + " caracteres.");
        return name;
    }

    /** null = sem descrição; senão valida tamanho. */
    private String sanitizeDescription(String raw) {
        if (raw == null) return null;
        String desc = raw.trim();
        if (desc.isEmpty()) return null;
        if (desc.length() > DESC_MAX) {
            throw new IllegalArgumentException("A descrição passa de " + DESC_MAX + " caracteres.");
        }
        return desc;
    }

    /** null = sem foto; senão valida https e tamanho. */
    private String sanitizeImageUrl(String raw) {
        if (raw == null) return null;
        String url = raw.trim();
        if (url.isEmpty()) return null;
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("A URL da foto precisa começar com https://");
        }
        if (url.length() > IMAGE_URL_MAX) {
            throw new IllegalArgumentException("A URL da foto é longa demais.");
        }
        return url;
    }

    private ConversationAccess parseAccess(String raw) {
        try {
            return ConversationAccess.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Acesso inválido.");
        }
    }

    private ConversationMemberRole parseAssignableRole(String raw) {
        ConversationMemberRole r;
        try {
            r = ConversationMemberRole.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Cargo inválido.");
        }
        if (r == ConversationMemberRole.OWNER) {
            throw new IllegalArgumentException("Não dá pra definir o cargo de dono.");
        }
        return r;
    }

    private void system(Long convId, Long actorId, String text) {
        messageRepository.save(new Message(convId, actorId, MessageKind.SYSTEM, text));
    }

    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("Alguém");
    }

    private static String excerpt(String s) {
        if (s == null) return "";
        return s.length() > EXCERPT_MAX ? s.substring(0, EXCERPT_MAX) + "…" : s;
    }

    private static String accessLabel(ConversationAccess a) {
        return switch (a) {
            case OPEN -> "Aberto";
            case REQUEST -> "Por convite";
            case CLOSED -> "Fechado";
        };
    }

    private static String roleLabel(ConversationMemberRole r) {
        return switch (r) {
            case OWNER -> "Dono";
            case ADMIN -> "Admin";
            case MOD -> "Moderador";
            case MEMBER -> "Membro";
        };
    }

    private static String humanJoin(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " e " + names.get(1);
        if (names.size() == 3) return names.get(0) + ", " + names.get(1) + " e " + names.get(2);
        return names.get(0) + ", " + names.get(1) + " e mais " + (names.size() - 2);
    }
}
