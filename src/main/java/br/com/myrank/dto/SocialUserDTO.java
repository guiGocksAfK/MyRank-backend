package br.com.myrank.dto;

/** Card de usuário nas listas (seguindo, sugestões, busca, solicitações). */
public record SocialUserDTO(
        Long id,
        String username,
        String avatarUrl,
        String bio,
        String plan,
        int worksCount,
        double avgScore,
        boolean following,
        boolean followsYou,
        boolean isPublic,
        /** eu já pedi pra seguir este usuário (pendente). */
        boolean requested,
        /** obras com o mesmo título nos dois rankings (0 se não dá pra ver as obras). */
        int sharedCount,
        /** % de gosto parecido; null com menos de 5 obras em comum (ver TasteMatch). */
        Integer matchPct,
        /** a obra em que as notas ficam mais longe ("brigam em..."); null sem obras em comum. */
        String fightTitle,
        Double fightMine,
        Double fightTheirs
) {}
