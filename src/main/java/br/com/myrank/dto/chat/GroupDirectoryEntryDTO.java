package br.com.myrank.dto.chat;

/** Item do diretório de grupos. `membership` = MEMBER | PENDING | NONE. */
public record GroupDirectoryEntryDTO(
        Long id,
        String name,
        String imageUrl,
        String access,        // OPEN | REQUEST
        int memberCount,
        String membership
) {}
