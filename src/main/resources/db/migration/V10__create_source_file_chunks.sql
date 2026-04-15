CREATE TABLE IF NOT EXISTS source_file_chunks (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(512) NOT NULL REFERENCES source_files (file_path) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    is_verbatim BOOLEAN NOT NULL DEFAULT FALSE,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_source_file_chunks_file_path_chunk_index UNIQUE (file_path, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_source_file_chunks_file_path
    ON source_file_chunks (file_path);
