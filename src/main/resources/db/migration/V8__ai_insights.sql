-- =========================================================
-- MyRank — AI Insights (análise de perfil gerada por IA / Gemini)
-- payload = JSON estruturado devolvido pelo modelo (summaryTitle, traits,
-- tasteProfile, recommendation...). Cacheado por (user_id, selection_hash):
-- mesma seleção de obras + mesmas notas + mesmo modelo => reaproveita a linha.
-- =========================================================

CREATE TABLE ai_insights (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    selection_hash  VARCHAR(64)  NOT NULL,   -- SHA-256 de (modelo + obras ordenadas + notas)
    model           VARCHAR(60)  NOT NULL,
    work_count      INT          NOT NULL,
    payload         JSONB        NOT NULL,
    -- chat de follow-up sobre esta análise: array de {role:'USER'|'AI', content, at}.
    -- Limitado a 3 turnos do usuário (ver InsightService.CHAT_LIMIT).
    chat_log        JSONB        NOT NULL DEFAULT '[]',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_ai_insights_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_insights_selection UNIQUE (user_id, selection_hash)
);

CREATE INDEX idx_ai_insights_user_created ON ai_insights (user_id, created_at DESC);
