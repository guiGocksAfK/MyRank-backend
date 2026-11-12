package br.com.myrank.service;

import br.com.myrank.domain.entity.CreatorPhoto;
import br.com.myrank.domain.entity.UserCreator;
import br.com.myrank.dto.creator.CreatorProfileDTO;
import br.com.myrank.dto.creator.CreatorProfilesRequestDTO;
import br.com.myrank.dto.creator.CreatorUpdateDTO;
import br.com.myrank.repository.CreatorPhotoRepository;
import br.com.myrank.repository.UserCreatorRepository;
import br.com.myrank.service.external.TmdbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Foto e tipo dos criadores da tela de Criadores.
 * - Foto automática: TMDB (pessoa = foto de perfil, estúdio = logo), guardada em
 *   creator_photos e compartilhada entre todos. Cada pedido procura no máximo
 *   {@link #LOOKUPS_PER_REQUEST} nomes novos; o resto volta "pending" e a tela pede de novo.
 * - O que o usuário editou (foto própria, tipo) fica em user_creators e vale só pra ele.
 */
@Service
public class CreatorProfileService {

    private static final Logger log = LoggerFactory.getLogger(CreatorProfileService.class);

    static final int LOOKUPS_PER_REQUEST = 10;
    /** "Procurou e não achou" vale por 30 dias; depois procura de novo (a TMDB cresce). */
    static final int RETRY_NOT_FOUND_DAYS = 30;

    private final CreatorPhotoRepository photoRepository;
    private final UserCreatorRepository userCreatorRepository;
    private final TmdbService tmdbService;
    private final Clock clock;

    public CreatorProfileService(CreatorPhotoRepository photoRepository, UserCreatorRepository userCreatorRepository,
                                 TmdbService tmdbService, Clock clock) {
        this.photoRepository = photoRepository;
        this.userCreatorRepository = userCreatorRepository;
        this.tmdbService = tmdbService;
        this.clock = clock;
    }

    public List<CreatorProfileDTO> profiles(Long userId, CreatorProfilesRequestDTO request) {
        // um pedido por chave (o mesmo nome pode vir repetido)
        Map<String, CreatorProfilesRequestDTO.Item> byKey = new LinkedHashMap<>();
        for (CreatorProfilesRequestDTO.Item item : request.creators()) {
            String key = CreatorNames.key(item.name());
            if (!key.isEmpty()) byKey.putIfAbsent(key, item);
        }
        if (byKey.isEmpty()) return List.of();

        Map<String, UserCreator> edited = userCreatorRepository.findByUserIdAndCreatorKeyIn(userId, byKey.keySet())
                .stream().collect(Collectors.toMap(UserCreator::getCreatorKey, Function.identity()));
        List<CreatorPhoto.Id> ids = byKey.entrySet().stream()
                .map(e -> new CreatorPhoto.Id(e.getValue().kind(), e.getKey())).toList();
        Map<CreatorPhoto.Id, CreatorPhoto> cached = new HashMap<>(photoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CreatorPhoto::getId, Function.identity())));

        record LookupTask(CreatorPhoto.Id id, String name, CreatorPhoto previous) {}
        List<LookupTask> toLookup = new ArrayList<>();
        Map<String, Boolean> pendingByKey = new HashMap<>();

        for (Map.Entry<String, CreatorProfilesRequestDTO.Item> entry : byKey.entrySet()) {
            String key = entry.getKey();
            CreatorProfilesRequestDTO.Item item = entry.getValue();
            UserCreator mine = edited.get(key);
            String custom = mine != null ? mine.getPhotoUrl() : null;
            CreatorPhoto.Id id = new CreatorPhoto.Id(item.kind(), key);
            CreatorPhoto photo = cached.get(id);

            if (custom == null && needsLookup(photo)) {
                if (toLookup.size() < LOOKUPS_PER_REQUEST) {
                    toLookup.add(new LookupTask(id, item.name(), photo));
                } else {
                    pendingByKey.put(key, true);
                }
            }
        }

        if (!toLookup.isEmpty()) {
            List<CompletableFuture<CreatorPhoto>> futures = toLookup.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> fetchFromTmdb(task.id(), task.name(), task.previous())))
                    .toList();
            for (CompletableFuture<CreatorPhoto> future : futures) {
                CreatorPhoto fetched = future.join();
                if (fetched != null) {
                    cached.put(fetched.getId(), photoRepository.save(fetched));
                }
            }
        }

        List<CreatorProfileDTO> out = new ArrayList<>(byKey.size());
        for (Map.Entry<String, CreatorProfilesRequestDTO.Item> entry : byKey.entrySet()) {
            String key = entry.getKey();
            CreatorProfilesRequestDTO.Item item = entry.getValue();
            UserCreator mine = edited.get(key);
            String custom = mine != null ? mine.getPhotoUrl() : null;
            CreatorPhoto photo = cached.get(new CreatorPhoto.Id(item.kind(), key));
            String auto = photo != null ? photo.getPhotoUrl() : null;
            boolean pending = pendingByKey.getOrDefault(key, false);
            out.add(new CreatorProfileDTO(key, custom != null ? custom : auto, custom != null,
                    mine != null ? mine.getCreatorType() : null, pending));
        }
        return out;
    }

    /** Salva a foto e/ou o tipo escolhidos; os dois vazios apagam a edição (volta tudo pro automático). */
    public CreatorProfileDTO update(Long userId, CreatorUpdateDTO dto) {
        String key = CreatorNames.key(dto.name());
        if (key.isEmpty()) throw new IllegalArgumentException("Informe o criador.");
        String photoUrl = dto.photoUrl() == null ? null : dto.photoUrl().trim();
        if (photoUrl != null && photoUrl.isEmpty()) photoUrl = null;
        if (photoUrl != null && !photoUrl.startsWith("https://")) {
            throw new IllegalArgumentException("O link da foto precisa começar com https://");
        }

        UserCreator mine = userCreatorRepository.findByUserIdAndCreatorKey(userId, key).orElse(null);
        if (photoUrl == null && dto.type() == null) {
            if (mine != null) userCreatorRepository.delete(mine);
            mine = null;
        } else {
            if (mine == null) mine = new UserCreator(userId, key);
            mine.setPhotoUrl(photoUrl);
            mine.setCreatorType(dto.type());
            mine.setUpdatedAt(LocalDateTime.now(clock));
            mine = userCreatorRepository.save(mine);
        }

        String kind = dto.kind() != null ? dto.kind() : "PERSON";
        String auto = photoRepository.findById(new CreatorPhoto.Id(kind, key)).map(CreatorPhoto::getPhotoUrl).orElse(null);
        String custom = mine != null ? mine.getPhotoUrl() : null;
        return new CreatorProfileDTO(key, custom != null ? custom : auto, custom != null,
                mine != null ? mine.getCreatorType() : null, false);
    }

    private boolean needsLookup(CreatorPhoto photo) {
        if (photo == null) return true;
        return photo.getPhotoUrl() == null
                && photo.getFetchedAt().isBefore(LocalDateTime.now(clock).minusDays(RETRY_NOT_FOUND_DAYS));
    }

    /** Procura na TMDB e monta a entidade atualizada (inclusive "não achou"). Se a TMDB falhar, devolve null. */
    private CreatorPhoto fetchFromTmdb(CreatorPhoto.Id id, String name, CreatorPhoto previous) {
        try {
            String url = "COMPANY".equals(id.getKind()) ? tmdbService.findCompanyLogo(name) : tmdbService.findPersonPhoto(name);
            CreatorPhoto photo = previous != null ? previous : new CreatorPhoto(id, null, null);
            photo.setPhotoUrl(url);
            photo.setFetchedAt(LocalDateTime.now(clock));
            return photo;
        } catch (RestClientException e) {
            log.warn("Não deu pra buscar a foto de '{}' na TMDB: {}", name, e.getMessage());
            return null;
        }
    }
}
