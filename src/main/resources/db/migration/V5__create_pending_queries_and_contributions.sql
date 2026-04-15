CREATE TABLE IF NOT EXISTS pending_queries (
    query_id VARCHAR(64) PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    selected_concept_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    source_file_paths TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    corrections JSONB NOT NULL DEFAULT '[]'::JSONB,
    review_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS contributions (
    id UUID PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    corrections JSONB NOT NULL DEFAULT '[]'::JSONB,
    confirmed_by VARCHAR(100),
    confirmed_at TIMESTAMPTZ NOT NULL
);
