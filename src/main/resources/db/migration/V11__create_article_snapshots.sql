CREATE TABLE IF NOT EXISTS article_snapshots (
    snapshot_id BIGSERIAL PRIMARY KEY,
    concept_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    lifecycle VARCHAR(64) NOT NULL,
    compiled_at TIMESTAMPTZ NOT NULL,
    source_paths TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    summary TEXT,
    referential_keywords TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    depends_on TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    related TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    confidence VARCHAR(32),
    review_status VARCHAR(64),
    snapshot_reason VARCHAR(32) NOT NULL DEFAULT 'article_upsert',
    captured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_article_snapshots_concept_id_captured_at
    ON article_snapshots (concept_id, captured_at DESC, snapshot_id DESC);

CREATE INDEX IF NOT EXISTS idx_article_snapshots_captured_at
    ON article_snapshots (captured_at DESC, snapshot_id DESC);

CREATE OR REPLACE FUNCTION capture_article_snapshot()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO article_snapshots (
        concept_id,
        title,
        content,
        lifecycle,
        compiled_at,
        source_paths,
        metadata_json,
        summary,
        referential_keywords,
        depends_on,
        related,
        confidence,
        review_status,
        snapshot_reason,
        captured_at
    )
    VALUES (
        NEW.concept_id,
        NEW.title,
        NEW.content,
        NEW.lifecycle,
        NEW.compiled_at,
        COALESCE(NEW.source_paths, ARRAY[]::TEXT[]),
        COALESCE(NEW.metadata_json, '{}'::JSONB),
        NEW.summary,
        COALESCE(NEW.referential_keywords, ARRAY[]::TEXT[]),
        COALESCE(NEW.depends_on, ARRAY[]::TEXT[]),
        COALESCE(NEW.related, ARRAY[]::TEXT[]),
        NEW.confidence,
        NEW.review_status,
        'article_upsert',
        CURRENT_TIMESTAMP
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_capture_article_snapshot ON articles;

CREATE TRIGGER trg_capture_article_snapshot
AFTER INSERT OR UPDATE ON articles
FOR EACH ROW
EXECUTE FUNCTION capture_article_snapshot();
