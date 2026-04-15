CREATE TABLE IF NOT EXISTS compile_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    source_dir VARCHAR(1024) NOT NULL,
    incremental BOOLEAN NOT NULL DEFAULT FALSE,
    orchestration_mode VARCHAR(32) NOT NULL DEFAULT 'service',
    status VARCHAR(32) NOT NULL,
    persisted_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_compile_jobs_status_requested_at
    ON compile_jobs (status, requested_at DESC, job_id DESC);
