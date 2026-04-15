ALTER TABLE source_files
    ADD COLUMN IF NOT EXISTS content_text TEXT NOT NULL DEFAULT '';

ALTER TABLE source_files
    ADD COLUMN IF NOT EXISTS metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB;

ALTER TABLE source_files
    ADD COLUMN IF NOT EXISTS is_verbatim BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE source_files
    ADD COLUMN IF NOT EXISTS raw_path VARCHAR(512) NOT NULL DEFAULT '';

UPDATE source_files
SET content_text = content_preview
WHERE content_text = '';

UPDATE source_files
SET raw_path = file_path
WHERE raw_path = '';
