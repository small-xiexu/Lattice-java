CREATE TABLE IF NOT EXISTS quality_metrics_history (
    id               BIGSERIAL PRIMARY KEY,
    measured_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_articles   INT         NOT NULL DEFAULT 0,
    passed_articles  INT         NOT NULL DEFAULT 0,
    pending_articles INT         NOT NULL DEFAULT 0,
    needs_review     INT         NOT NULL DEFAULT 0,
    contributions    INT         NOT NULL DEFAULT 0,
    source_count     INT         NOT NULL DEFAULT 0,
    review_pass_rate NUMERIC(5,2),
    grounding_rate   NUMERIC(5,2),
    referential_rate NUMERIC(5,2)
);

CREATE INDEX IF NOT EXISTS idx_quality_history_measured_at
    ON quality_metrics_history (measured_at DESC);
