package br.com.myrank.service.badge;

import java.util.function.ToIntFunction;

/**
 * Catálogo de badges + regra de cálculo de cada uma. O nome do enum é o
 * {@code code} persistido na tabela {@code badges}. A UI mostra barra de
 * progresso quando {@code hasProgress} é true; senão é só liga/desliga.
 *
 * A função retorna o "progresso bruto"; a badge conta como conquistada
 * quando esse valor alcança {@code target}.
 */
public enum BadgeDefinition {

    // ─────────────────────────── 🎮 JOGOS ───────────────────────────
    JOGO_5("jogo", "Começou a jogar", "Avalie 5 jogos", "🕹️", 5, true,
            c -> c.countType("jogo")),
    JOGO_25("jogo", "Gamer de carteirinha", "Avalie 25 jogos", "🎮", 25, true,
            c -> c.countType("jogo")),
    JOGO_HORAS_500("jogo", "Sem vida social", "Acumule 500h em jogos", "⏳", 500, true,
            c -> c.hoursOfType("jogo")),
    JOGO_DAY_ONE("jogo", "Day one", "Avalie um jogo no mesmo ano em que ele saiu", "🚀", 1, false,
            c -> c.hasWorkInReleaseYear("jogo") ? 1 : 0),
    JOGO_MARATONA("jogo", "Interminável", "Avalie um jogo com 100h+ registradas", "🐉", 1, false,
            c -> c.maxSingleHoursOfType("jogo") >= 100 ? 1 : 0),
    JOGO_ESTUDIOS_10("jogo", "De indie a AAA", "Avalie jogos de 10 estúdios/criadores diferentes", "🎲", 10, true,
            c -> c.distinctCreators("jogo")),

    // ─────────────────────────── 🎬 FILMES ──────────────────────────
    FILME_10("filme", "Sessão pipoca", "Avalie 10 filmes", "🎟️", 10, true,
            c -> c.countType("filme")),
    FILME_50("filme", "Cinéfilo", "Avalie 50 filmes", "🎬", 50, true,
            c -> c.countType("filme")),
    FILME_CLASSICO("filme", "Preto e branco", "Avalie um filme lançado antes de 1970", "🎞️", 1, false,
            c -> c.hasWorkBefore("filme", 1970) ? 1 : 0),
    FILME_MESMO_DIRETOR("filme", "Fã do diretor", "Avalie 3 filmes do mesmo diretor", "🎥", 3, true,
            c -> c.maxWorksBySameCreator("filme")),
    FILME_DIRETORES_10("filme", "Conhece o catálogo", "Avalie filmes de 10 criadores diferentes", "📽️", 10, true,
            c -> c.distinctCreators("filme")),
    FILME_LONGO("filme", "Sessão épica", "Avalie um filme com 3h+ registradas", "🍿", 1, false,
            c -> c.maxSingleHoursOfType("filme") >= 3 ? 1 : 0),

    // ─────────────────────────── 📺 SÉRIES ──────────────────────────
    SERIE_5("serie", "Só mais um episódio", "Avalie 5 séries", "📺", 5, true,
            c -> c.countType("serie")),
    SERIE_20("serie", "Rei do sofá", "Avalie 20 séries", "🛋️", 20, true,
            c -> c.countType("serie")),
    SERIE_HORAS_300("serie", "Binge-watcher", "Acumule 300h em séries", "📡", 300, true,
            c -> c.hoursOfType("serie")),
    SERIE_CLASSICA("serie", "Clássico da TV", "Avalie uma série lançada antes de 1990", "📻", 1, false,
            c -> c.hasWorkBefore("serie", 1990) ? 1 : 0),
    SERIE_MINISSERIE("serie", "Minissérie", "Avalie uma série com até 10h registradas", "🎬", 1, false,
            c -> c.hasShortWorkOfType("serie", 10) ? 1 : 0),
    SERIE_VICIANTE("serie", "Não consigo parar", "Avalie uma série com 100h+ registradas", "📼", 1, false,
            c -> c.maxSingleHoursOfType("serie") >= 100 ? 1 : 0),

    // ─────────────────────────── 📚 LIVROS ──────────────────────────
    LIVRO_5("livro", "Marcador de página", "Avalie 5 livros", "🔖", 5, true,
            c -> c.countType("livro")),
    LIVRO_20("livro", "Rato de biblioteca", "Avalie 20 livros", "📚", 20, true,
            c -> c.countType("livro")),
    LIVRO_AUTORES_10("livro", "Estante variada", "Avalie livros de 10 autores diferentes", "✍️", 10, true,
            c -> c.distinctCreators("livro")),
    LIVRO_MESMO_AUTOR("livro", "Leitor fiel", "Avalie 5 livros do mesmo autor", "📖", 5, true,
            c -> c.maxWorksBySameCreator("livro")),
    LIVRO_CLASSICO("livro", "Cânone literário", "Avalie um livro publicado antes de 1950", "🏛️", 1, false,
            c -> c.hasWorkBefore("livro", 1950) ? 1 : 0),
    LIVRO_50("livro", "Devorador de livros", "Avalie 50 livros", "📕", 50, true,
            c -> c.countType("livro")),

    // ─────────────────────────── 🌸 ANIMES ──────────────────────────
    ANIME_5("anime", "Isekai iniciante", "Avalie 5 animes", "🍥", 5, true,
            c -> c.countType("anime")),
    ANIME_25("anime", "Otaku assumido", "Avalie 25 animes", "⛩️", 25, true,
            c -> c.countType("anime")),
    ANIME_HORAS_200("anime", "Filler incluso", "Acumule 200h em animes", "🌀", 200, true,
            c -> c.hoursOfType("anime")),
    ANIME_EM_DIA("anime", "Temporada em dia", "Avalie um anime no mesmo ano em que ele saiu", "📅", 1, false,
            c -> c.hasWorkInReleaseYear("anime") ? 1 : 0),
    ANIME_ESTUDIOS_5("anime", "Conhece os estúdios", "Avalie animes de 5 criadores/estúdios diferentes", "🎨", 5, true,
            c -> c.distinctCreators("anime")),
    ANIME_CLASSICO("anime", "Clássico do Japão", "Avalie um anime lançado antes de 2000", "🎞️", 1, false,
            c -> c.hasWorkBefore("anime", 2000) ? 1 : 0),

    // ─────────────────────────── 📖 MANGÁS ──────────────────────────
    MANGA_5("manga", "Primeiro volume", "Avalie 5 mangás", "📘", 5, true,
            c -> c.countType("manga")),
    MANGA_25("manga", "Colecionador de volumes", "Avalie 25 mangás", "📚", 25, true,
            c -> c.countType("manga")),
    MANGA_AUTORES_5("manga", "Conhece os mangakás", "Avalie mangás de 5 autores diferentes", "🖋️", 5, true,
            c -> c.distinctCreators("manga")),
    MANGA_MESMO_AUTOR("manga", "Leitor de um mangaká só", "Avalie 3 mangás do mesmo autor", "✒️", 3, true,
            c -> c.maxWorksBySameCreator("manga")),
    MANGA_CLASSICO("manga", "Clássico do papel", "Avalie um mangá lançado antes de 1990", "📜", 1, false,
            c -> c.hasWorkBefore("manga", 1990) ? 1 : 0),
    MANGA_DECADAS("manga", "Gerações", "Avalie mangás de 3 décadas diferentes", "🕰️", 3, true,
            c -> c.distinctDecadesOfType("manga")),

    // ─────────────────────────── 🎵 MÚSICAS ─────────────────────────
    MUSICA_10("musica", "Playlist começando", "Avalie 10 músicas", "🎵", 10, true,
            c -> c.countType("musica")),
    MUSICA_50("musica", "Repeat infinito", "Avalie 50 músicas", "🔁", 50, true,
            c -> c.countType("musica")),
    MUSICA_ARTISTAS_10("musica", "Ouvido eclético", "Avalie músicas de 10 artistas diferentes", "🎧", 10, true,
            c -> c.distinctCreators("musica")),
    MUSICA_MESMO_ARTISTA("musica", "Artista do coração", "Avalie 5 músicas do mesmo artista", "🎤", 5, true,
            c -> c.maxWorksBySameCreator("musica")),
    MUSICA_CLASSICA("musica", "Toca-discos", "Avalie uma música lançada antes de 1980", "📻", 1, false,
            c -> c.hasWorkBefore("musica", 1980) ? 1 : 0),
    MUSICA_DECADAS("musica", "Rádio do tempo", "Avalie músicas de 4 décadas diferentes", "📡", 4, true,
            c -> c.distinctDecadesOfType("musica")),

    // ─────────────────────────── 💿 ÁLBUNS ──────────────────────────
    ALBUM_5("album", "Lado A", "Avalie 5 álbuns", "💿", 5, true,
            c -> c.countType("album")),
    ALBUM_25("album", "Discoteca", "Avalie 25 álbuns", "🪩", 25, true,
            c -> c.countType("album")),
    ALBUM_ARTISTAS_10("album", "Garimpeiro de discos", "Avalie álbuns de 10 artistas diferentes", "🔍", 10, true,
            c -> c.distinctCreators("album")),
    ALBUM_MESMO_ARTISTA("album", "Fã de carteirinha", "Avalie 3 álbuns do mesmo artista", "🎸", 3, true,
            c -> c.maxWorksBySameCreator("album")),
    ALBUM_CLASSICO("album", "Era do vinil", "Avalie um álbum lançado antes de 1980", "📀", 1, false,
            c -> c.hasWorkBefore("album", 1980) ? 1 : 0),
    ALBUM_LANCAMENTO("album", "Lançamento quente", "Avalie um álbum no mesmo ano em que ele saiu", "🔥", 1, false,
            c -> c.hasWorkInReleaseYear("album") ? 1 : 0),

    // ─────────────────────────── 📦 OUTROS ──────────────────────────
    OUTRO_5("outro", "Fora da caixa", "Avalie 5 obras em tabelas personalizadas", "📦", 5, true,
            c -> c.countType("outro")),
    OUTRO_25("outro", "Curador", "Avalie 25 obras em tabelas personalizadas", "🗄️", 25, true,
            c -> c.countType("outro")),
    OUTRO_HORAS_50("outro", "Hobby sério", "Acumule 50h em obras personalizadas", "⏱️", 50, true,
            c -> c.hoursOfType("outro")),
    OUTRO_DEDICACAO("outro", "Dedicação", "Avalie uma obra personalizada com 20h+ registradas", "🔨", 1, false,
            c -> c.maxSingleHoursOfType("outro") >= 20 ? 1 : 0),
    OUTRO_TABELAS_3("outro", "Multitarefa", "Tenha obras personalizadas em 3 tabelas diferentes", "🧰", 3, true,
            c -> c.distinctTablesOfType("outro")),
    OUTRO_NICHO("outro", "Fã de nicho", "Tenha 10 obras personalizadas com nota 9,0 ou mais", "💠", 10, true,
            c -> c.countScoreGteOfType("outro", 9.0)),

    // ─────────────────────────── 🌐 GERAIS ──────────────────────────
    TOTAL_50("geral", "Colecionador", "Avalie 50 obras no total", "📈", 50, true,
            BadgeContext::totalWorks),
    TOTAL_100("geral", "Centurião", "Avalie 100 obras no total", "💯", 100, true,
            BadgeContext::totalWorks),
    HORAS_1000("geral", "Mil horas", "Acumule 1000h no total", "⌛", 1000, true,
            BadgeContext::totalHours),
    CRITICO_RIGOROSO("geral", "Crítico rigoroso", "50+ obras e menos de 5 delas com nota 10", "⚖️", 1, false,
            c -> c.totalWorks() >= 50 && c.tensTotal() < 5 ? 1 : 0),
    POLIVALENTE("geral", "Polivalente", "Ao menos 1 obra avaliada em cada tipo (jogo, filme, série, livro, anime)", "🧩", 1, false,
            c -> c.allCoreTypesCovered() ? 1 : 0),
    GENEROSO("geral", "Coração mole", "30+ obras e média geral ≥ 8,5", "🎁", 1, false,
            c -> c.totalWorks() >= 30 && c.avgAll() >= 8.5 ? 1 : 0),
    SOFRI_ATE_O_FIM("geral", "Fui até o fim", "Avalie uma obra com nota ≤ 2,0", "💀", 1, false,
            c -> c.hasScoreLte(2.0) ? 1 : 0),
    ARQUEOLOGO_1900("geral", "Arqueólogo", "Avalie qualquer obra lançada antes de 1900", "🦴", 1, false,
            c -> c.hasAnyWorkBefore(1900) ? 1 : 0),
    SEM_PAPAS("geral", "Sem papas na língua", "Tenha 5 obras com nota ≤ 5,0", "🗣️", 5, true,
            c -> c.countScoreLte(5.0)),
    TOTAL_250("geral", "Acervo respeitável", "Avalie 250 obras no total", "🗃️", 250, true,
            BadgeContext::totalWorks),
    NOTA_PERFEITA_10("geral", "Dez de dez", "Dê nota 10 a 10 obras diferentes", "💎", 10, true,
            c -> c.countScoreGte(9.999)),
    DECADAS("geral", "Viajante do tempo", "Avalie obras de 5 décadas de lançamento diferentes", "🕰️", 5, true,
            BadgeContext::distinctDecades),

    // ─────────────────────────── 🚀 USAR O SITE ─────────────────────
    PRIMEIRA_OBRA("site", "Primeiro passo", "Adicione sua primeira obra", "🌱", 1, false,
            c -> c.totalWorks() >= 1 ? 1 : 0),
    PERFIL_COMPLETO("site", "Cartão de visita", "Tenha foto de perfil e bio preenchida", "📇", 1, false,
            c -> c.hasAvatar() && c.hasBio() ? 1 : 0),
    CATEGORIA_CUSTOM("site", "Do seu jeito", "Crie uma categoria personalizada (além das padrão)", "🗂️", 1, false,
            c -> c.hasCustomCategory() ? 1 : 0),
    REVISOR("site", "Mudei de ideia", "Edite a nota de uma obra já cadastrada", "✏️", 1, false,
            c -> c.hasEditedWork() ? 1 : 0),
    VETERANO("site", "Um ano de casa", "Conta criada há mais de 1 ano", "🎂", 1, false,
            c -> c.accountAgeDays() > 365 ? 1 : 0),
    MADRUGADOR("site", "Constante", "Adicione obras em 7 dias diferentes", "🌙", 7, true,
            BadgeContext::distinctAddDays);

    private final String bucket;
    private final String displayName;
    private final String description;
    private final String icon;
    private final int target;
    private final boolean hasProgress;
    private final ToIntFunction<BadgeContext> rule;

    BadgeDefinition(String bucket, String displayName, String description, String icon,
                    int target, boolean hasProgress, ToIntFunction<BadgeContext> rule) {
        this.bucket = bucket;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.target = target;
        this.hasProgress = hasProgress;
        this.rule = rule;
    }

    public String code() { return name(); }
    public String bucket() { return bucket; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String icon() { return icon; }
    public int target() { return target; }
    public boolean hasProgress() { return hasProgress; }

    /** Progresso bruto (pode passar do target). */
    public int rawProgress(BadgeContext ctx) {
        return Math.max(0, rule.applyAsInt(ctx));
    }
}
