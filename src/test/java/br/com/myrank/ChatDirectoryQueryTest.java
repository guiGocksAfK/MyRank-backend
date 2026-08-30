package br.com.myrank;

import br.com.myrank.domain.entity.Conversation;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.ConversationAccess;
import br.com.myrank.domain.enums.ConversationType;
import br.com.myrank.repository.ConversationRepository;
import br.com.myrank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ChatDirectoryQueryTest {

    @Autowired
    ConversationRepository repo;
    @Autowired
    UserRepository userRepository;

    @Test
    @Transactional
    void directoryReturnsNonClosedGroups() {
        User u = new User();
        u.setUsername("seed_owner_" + System.nanoTime());
        u = userRepository.save(u);
        Long owner = u.getId();

        Conversation open = new Conversation(ConversationType.GROUP, "Grupo Aberto", owner);
        open.setAccess(ConversationAccess.OPEN);
        Conversation request = new Conversation(ConversationType.GROUP, "Grupo Convite", owner);
        request.setAccess(ConversationAccess.REQUEST);
        Conversation closed = new Conversation(ConversationType.GROUP, "Grupo Fechado", owner);
        closed.setAccess(ConversationAccess.CLOSED);
        repo.save(open);
        repo.save(request);
        repo.save(closed);
        repo.flush();

        List<Conversation> all = repo.searchDirectory(
                ConversationType.GROUP, ConversationAccess.CLOSED, "", PageRequest.of(0, 30));
        assertEquals(2, all.size(), "deve trazer OPEN e REQUEST, não o CLOSED");
        assertTrue(all.stream().noneMatch(c -> c.getAccess() == ConversationAccess.CLOSED));

        List<Conversation> byName = repo.searchDirectory(
                ConversationType.GROUP, ConversationAccess.CLOSED, "aberto", PageRequest.of(0, 30));
        assertEquals(1, byName.size());
        assertEquals("Grupo Aberto", byName.get(0).getName());
    }
}
