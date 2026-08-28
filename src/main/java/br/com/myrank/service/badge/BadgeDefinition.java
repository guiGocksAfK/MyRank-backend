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
    JOGO_NOTA_10("jogo", "Platina", "Dê nota 10 a um jogo", "🏆", 1, false,
            c -> c.hasTenOfType("jogo") ? 1 : 0),
    JOGO_MARATONA("jogo", "RPG interminável", "Avalie um jogo com 100h+ registradas", "🐉", 1, false,
            c -> c.maxSingleHoursOfType("jogo") >= 100 ? 1 : 0),

    // ─────────────────────────── 🎬 FILMES ──────────────────────────
    FILME_10("filme", "Sessão pipoca", "Avalie 10 filmes", "🎟️", 10, true,
            c -> c.countType("filme")),
    FILME_50("filme", "Cinéfilo", "Avalie 50 filmes", "🎬", 50, true,
            c -> c.countType("filme")),
    FILME_CLASSICO("filme", "Preto e branco", "Avalie um filme lançado antes de 1970", "🎞️", 1, false,
            c -> c.hasWorkBefore("filme", 1970) ? 1 : 0),
    FILME_NOTA_10("filme", "Obra-prima", "Dê nota 10 a um filme", "⭐", 1, false,
            c -> c.hasTenOfType("filme") ? 1 : 0),
    FILME_DIRETORES_10("filme", "Conhece o catálogo", "Avalie filmes de 10 criadores diferentes", "🎥", 10, true,
            c -> c.distinctCreators("filme")),

    // ─────────────────────────── 📺 SÉRIES ──────────────────────────
    SERIE_5("serie", "Só mais um episódio", "Avalie 5 séries", "📺", 5, true,
            c -> c.countType("serie")),
    SERIE_20("serie", "Rei do sofá", "Avalie 20 séries", "🛋️", 20, true,
            c -> c.countType("serie")),
    SERIE_HORAS_300("serie", "Binge-watcher", "Acumule 300h em séries", "📡", 300, true,
            c -> c.hoursOfType("serie")),
    SERIE_NOTA_10("serie", "Perto da perfeição", "Dê nota 10 a uma série", "🌟", 1, false,
            c -> c.hasTenOfType("serie") ? 1 : 0),
    SERIE_MEDIA_8("serie", "Bom gosto pra série", "Média ≥ 8,0 com pelo menos 10 séries", "🧠", 1, false,
            c -> c.countType("serie") >= 10 && c.avgOfType("serie") >= 8.0 ? 1 : 0),

    // ─────────────────────────── 📚 LIVROS ──────────────────────────
    LIVRO_5("livro", "Marcador de página", "Avalie 5 livros", "🔖", 5, true,
            c -> c.countType("livro")),
    LIVRO_20("livro", "Rato de biblioteca", "Avalie 20 livros", "📚", 20, true,
            c -> c.countType("livro")),
    LIVRO_AUTORES_10("livro", "Estante variada", "Avalie livros de 10 autores diferentes", "✍️", 10, true,
            c -> c.distinctCreators("livro")),
    LIVRO_NOTA_10("livro", "Livro de cabeceira", "Dê nota 10 a um livro", "📖", 1, false,
            c -> c.hasTenOfType("livro") ? 1 : 0),
    LIVRO_CLASSICO("livro", "Cânone literário", "Avalie um livro publicado antes de 1950", "🏛️", 1, false,
            c -> c.hasWorkBefore("livro", 1950) ? 1 : 0),

    // ─────────────────────────── 🌸 ANIMES ──────────────────────────
    ANIME_5("anime", "Isekai iniciante", "Avalie 5 animes", "🍥", 5, true,
            c -> c.countType("anime")),
    ANIME_25("anime", "Otaku assumido", "Avalie 25 animes", "⛩️", 25, true,
            c -> c.countType("anime")),
    ANIME_HORAS_200("anime", "Filler incluso", "Acumule 200h em animes", "🌀", 200, true,
            c -> c.hoursOfType("anime")),
    ANIME_NOTA_10("anime", "Peak fiction", "Dê nota 10 a um anime", "🎇", 1, false,
            c -> c.hasTenOfType("anime") ? 1 : 0),
    ANIME_ESTUDIOS_5("anime", "Conhece os estúdios", "Avalie animes de 5 criadores/estúdios diferentes", "🎨", 5, true,
            c -> c.distinctCreators("anime")),

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
    SOFRI_ATE_O_FIM("geral", "Eu assisti até o fim", "Avalie uma obra com nota ≤ 2,0", "💀", 1, false,
            c -> c.hasScoreLte(2.0) ? 1 : 0),
    ARQUEOLOGO("geral", "Arqueólogo", "Avalie qualquer obra lançada antes de 1950", "🦴", 1, false,
            c -> c.hasAnyWorkBefore(1950) ? 1 : 0),
    TOP_HEAVY("geral", "Cume alto", "Tenha 10 obras com nota ≥ 9,0", "🗻", 10, true,
            c -> c.countScoreGte(9.0)),

    // ─────────────────────────── 🚀 USAR O SITE ─────────────────────
    PRIMEIRA_OBRA("site", "Primeiro passo", "Adicione sua primeira obra", "🌱", 1, false,
            c -> c.totalWorks() >= 1 ? 1 : 0),
    PERFIL_COMPLETO("site", "Cartão de visita", "Tenha foto de perfil e bio preenchida", "📇", 1, false,
            c -> c.hasAvatar() && c.hasBio() ? 1 : 0),
    CATEGORIA_CUSTOM("site", "Do seu jeito", "Crie uma categoria personalizada (além das padrão)", "🗂️", 1, false,
            c -> c.hasCustomCategory() ? 1 : 0),
    TABELA_MAE("site", "Visão unificada", "Crie um grupo de tabela-mãe", "🧬", 1, false,
            c -> c.hasMasterGroup() ? 1 : 0),
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
