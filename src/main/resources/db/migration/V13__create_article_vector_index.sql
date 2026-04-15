DO $$
DECLARE vector_type_name TEXT;
BEGIN
    vector_type_name := coalesce(to_regtype('vector')::text, to_regtype('public.vector')::text);
    IF vector_type_name IS NOT NULL THEN
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS article_vector_index (
                concept_id VARCHAR(128) PRIMARY KEY REFERENCES articles (concept_id) ON DELETE CASCADE,
                model_name VARCHAR(128) NOT NULL,
                content_hash VARCHAR(64) NOT NULL,
                embedding %s(1536) NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            vector_type_name
        );

        CREATE INDEX IF NOT EXISTS idx_article_vector_index_updated_at
            ON article_vector_index (updated_at DESC);
    END IF;
END $$;
