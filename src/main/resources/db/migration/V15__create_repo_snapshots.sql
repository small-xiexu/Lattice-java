CREATE TABLE IF NOT EXISTS repo_snapshots (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trigger_event VARCHAR(64) NOT NULL,
    git_commit VARCHAR(64),
    description TEXT,
    article_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS repo_snapshot_items (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES repo_snapshots (id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_repo_snapshot_items_snapshot
    ON repo_snapshot_items (snapshot_id);

CREATE INDEX IF NOT EXISTS idx_repo_snapshot_items_entity
    ON repo_snapshot_items (entity_type, entity_id);
