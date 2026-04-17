CREATE TABLE IF NOT EXISTS articles (
    id BIGSERIAL PRIMARY KEY,
    concept_id VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    lifecycle VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    compiled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_paths TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    summary TEXT NOT NULL DEFAULT '',
    referential_keywords TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    depends_on TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    related TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    confidence VARCHAR(16) NOT NULL DEFAULT 'medium',
    review_status VARCHAR(32) NOT NULL DEFAULT 'pending'
);

COMMENT ON TABLE articles IS '知识文章主表';
COMMENT ON COLUMN articles.id IS '文章主键 ID';
COMMENT ON COLUMN articles.concept_id IS '概念唯一标识（concept slug）';
COMMENT ON COLUMN articles.title IS '文章标题';
COMMENT ON COLUMN articles.content IS '文章 Markdown 正文';
COMMENT ON COLUMN articles.lifecycle IS '文章生命周期状态';
COMMENT ON COLUMN articles.compiled_at IS '最近一次编译完成时间';
COMMENT ON COLUMN articles.created_at IS '记录创建时间';
COMMENT ON COLUMN articles.updated_at IS '记录更新时间';
COMMENT ON COLUMN articles.source_paths IS '生成该文章的源文件路径数组';
COMMENT ON COLUMN articles.metadata_json IS '文章扩展元数据 JSON';
COMMENT ON COLUMN articles.summary IS '文章摘要';
COMMENT ON COLUMN articles.referential_keywords IS '文章中的明确性关键词数组（业务码、枚举值、端口等）';
COMMENT ON COLUMN articles.depends_on IS '文章依赖的上游概念 ID 数组';
COMMENT ON COLUMN articles.related IS '文章关联概念 ID 数组';
COMMENT ON COLUMN articles.confidence IS '内容置信度等级';
COMMENT ON COLUMN articles.review_status IS '审查状态';

CREATE TABLE IF NOT EXISTS source_files (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(512) NOT NULL UNIQUE,
    content_preview TEXT NOT NULL,
    format VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_text TEXT NOT NULL DEFAULT '',
    metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    is_verbatim BOOLEAN NOT NULL DEFAULT FALSE,
    raw_path VARCHAR(512) NOT NULL DEFAULT ''
);

COMMENT ON TABLE source_files IS '源文件主表';
COMMENT ON COLUMN source_files.id IS '源文件主键 ID';
COMMENT ON COLUMN source_files.file_path IS '源文件相对路径';
COMMENT ON COLUMN source_files.content_preview IS '源文件内容预览';
COMMENT ON COLUMN source_files.format IS '源文件格式';
COMMENT ON COLUMN source_files.file_size IS '源文件大小（字节）';
COMMENT ON COLUMN source_files.indexed_at IS '最近一次索引时间';
COMMENT ON COLUMN source_files.content_text IS '源文件全文文本';
COMMENT ON COLUMN source_files.metadata_json IS '源文件扩展元数据 JSON';
COMMENT ON COLUMN source_files.is_verbatim IS '是否为逐字提取内容';
COMMENT ON COLUMN source_files.raw_path IS '源文件原始路径';

CREATE TABLE IF NOT EXISTS article_chunks (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES articles (id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE article_chunks IS '文章分块表';
COMMENT ON COLUMN article_chunks.id IS '文章分块主键 ID';
COMMENT ON COLUMN article_chunks.article_id IS '关联文章主键 ID';
COMMENT ON COLUMN article_chunks.chunk_text IS '文章分块文本';
COMMENT ON COLUMN article_chunks.chunk_index IS '分块顺序号';
COMMENT ON COLUMN article_chunks.created_at IS '分块创建时间';

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

COMMENT ON TABLE pending_queries IS '待确认问答表';
COMMENT ON COLUMN pending_queries.query_id IS '待确认查询唯一标识';
COMMENT ON COLUMN pending_queries.question IS '用户问题';
COMMENT ON COLUMN pending_queries.answer IS '待确认答案';
COMMENT ON COLUMN pending_queries.selected_concept_ids IS '命中的概念 ID 数组';
COMMENT ON COLUMN pending_queries.source_file_paths IS '引用的源文件路径数组';
COMMENT ON COLUMN pending_queries.corrections IS '审查修正记录 JSON 数组';
COMMENT ON COLUMN pending_queries.review_status IS '答案审查状态';
COMMENT ON COLUMN pending_queries.created_at IS '待确认记录创建时间';
COMMENT ON COLUMN pending_queries.expires_at IS '待确认记录过期时间';

CREATE TABLE IF NOT EXISTS contributions (
    id UUID PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    corrections JSONB NOT NULL DEFAULT '[]'::JSONB,
    confirmed_by VARCHAR(100),
    confirmed_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE contributions IS '用户贡献表';
COMMENT ON COLUMN contributions.id IS '贡献主键 UUID';
COMMENT ON COLUMN contributions.question IS '已确认问答对应的问题';
COMMENT ON COLUMN contributions.answer IS '已确认的最终答案';
COMMENT ON COLUMN contributions.corrections IS '人工修正记录 JSON 数组';
COMMENT ON COLUMN contributions.confirmed_by IS '确认人';
COMMENT ON COLUMN contributions.confirmed_at IS '确认时间';

CREATE TABLE IF NOT EXISTS synthesis_artifacts (
    id BIGSERIAL PRIMARY KEY,
    artifact_type VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    compiled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE synthesis_artifacts IS '合成产物表';
COMMENT ON COLUMN synthesis_artifacts.id IS '合成产物主键 ID';
COMMENT ON COLUMN synthesis_artifacts.artifact_type IS '产物类型';
COMMENT ON COLUMN synthesis_artifacts.title IS '产物标题';
COMMENT ON COLUMN synthesis_artifacts.content IS '产物内容';
COMMENT ON COLUMN synthesis_artifacts.compiled_at IS '产物编译时间';

CREATE TABLE IF NOT EXISTS source_file_chunks (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(512) NOT NULL REFERENCES source_files (file_path) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    is_verbatim BOOLEAN NOT NULL DEFAULT FALSE,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_source_file_chunks_file_path_chunk_index UNIQUE (file_path, chunk_index)
);

COMMENT ON TABLE source_file_chunks IS '源文件分块表';
COMMENT ON COLUMN source_file_chunks.id IS '源文件分块主键 ID';
COMMENT ON COLUMN source_file_chunks.file_path IS '关联源文件路径';
COMMENT ON COLUMN source_file_chunks.chunk_index IS '分块顺序号';
COMMENT ON COLUMN source_file_chunks.chunk_text IS '源文件分块文本';
COMMENT ON COLUMN source_file_chunks.is_verbatim IS '是否为逐字分块';
COMMENT ON COLUMN source_file_chunks.indexed_at IS '分块索引时间';

CREATE INDEX IF NOT EXISTS idx_source_file_chunks_file_path
    ON source_file_chunks (file_path);

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

COMMENT ON TABLE article_snapshots IS '文章快照历史表';
COMMENT ON COLUMN article_snapshots.snapshot_id IS '快照主键 ID';
COMMENT ON COLUMN article_snapshots.concept_id IS '文章概念唯一标识';
COMMENT ON COLUMN article_snapshots.title IS '快照标题';
COMMENT ON COLUMN article_snapshots.content IS '快照正文';
COMMENT ON COLUMN article_snapshots.lifecycle IS '快照对应的生命周期状态';
COMMENT ON COLUMN article_snapshots.compiled_at IS '快照对应的编译完成时间';
COMMENT ON COLUMN article_snapshots.source_paths IS '快照中的源文件路径数组';
COMMENT ON COLUMN article_snapshots.metadata_json IS '快照扩展元数据 JSON';
COMMENT ON COLUMN article_snapshots.summary IS '快照摘要';
COMMENT ON COLUMN article_snapshots.referential_keywords IS '快照中的明确性关键词数组';
COMMENT ON COLUMN article_snapshots.depends_on IS '快照依赖的上游概念 ID 数组';
COMMENT ON COLUMN article_snapshots.related IS '快照关联概念 ID 数组';
COMMENT ON COLUMN article_snapshots.confidence IS '快照置信度等级';
COMMENT ON COLUMN article_snapshots.review_status IS '快照审查状态';
COMMENT ON COLUMN article_snapshots.snapshot_reason IS '生成快照的原因';
COMMENT ON COLUMN article_snapshots.captured_at IS '快照采集时间';

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

COMMENT ON FUNCTION capture_article_snapshot() IS '在 articles 插入或更新后自动写入 article_snapshots 的触发器函数';

DROP TRIGGER IF EXISTS trg_capture_article_snapshot ON articles;

CREATE TRIGGER trg_capture_article_snapshot
AFTER INSERT OR UPDATE ON articles
FOR EACH ROW
EXECUTE FUNCTION capture_article_snapshot();

COMMENT ON TRIGGER trg_capture_article_snapshot ON articles IS '文章插入或更新后自动生成快照';

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

COMMENT ON TABLE compile_jobs IS '编译任务表';
COMMENT ON COLUMN compile_jobs.job_id IS '编译任务唯一标识';
COMMENT ON COLUMN compile_jobs.source_dir IS '编译输入目录';
COMMENT ON COLUMN compile_jobs.incremental IS '是否为增量编译';
COMMENT ON COLUMN compile_jobs.orchestration_mode IS '编排模式';
COMMENT ON COLUMN compile_jobs.status IS '任务状态';
COMMENT ON COLUMN compile_jobs.persisted_count IS '已落库文章数量';
COMMENT ON COLUMN compile_jobs.error_message IS '失败错误信息';
COMMENT ON COLUMN compile_jobs.attempt_count IS '已尝试次数';
COMMENT ON COLUMN compile_jobs.requested_at IS '任务请求时间';
COMMENT ON COLUMN compile_jobs.started_at IS '任务开始时间';
COMMENT ON COLUMN compile_jobs.finished_at IS '任务结束时间';

CREATE INDEX IF NOT EXISTS idx_compile_jobs_status_requested_at
    ON compile_jobs (status, requested_at DESC, job_id DESC);

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

DO $$
BEGIN
    IF to_regclass(current_schema() || '.article_vector_index') IS NOT NULL THEN
        EXECUTE 'COMMENT ON TABLE article_vector_index IS ''文章向量索引表''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.concept_id IS ''关联文章概念唯一标识''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.model_name IS ''Embedding 模型名称''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.content_hash IS ''正文内容哈希''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.embedding IS ''文章向量嵌入''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.updated_at IS ''向量索引更新时间''';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS quality_metrics_history (
    id BIGSERIAL PRIMARY KEY,
    measured_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_articles INT NOT NULL DEFAULT 0,
    passed_articles INT NOT NULL DEFAULT 0,
    pending_articles INT NOT NULL DEFAULT 0,
    needs_review INT NOT NULL DEFAULT 0,
    contributions INT NOT NULL DEFAULT 0,
    source_count INT NOT NULL DEFAULT 0,
    review_pass_rate NUMERIC(5, 2),
    grounding_rate NUMERIC(5, 2),
    referential_rate NUMERIC(5, 2)
);

COMMENT ON TABLE quality_metrics_history IS '质量指标历史表';
COMMENT ON COLUMN quality_metrics_history.id IS '质量指标记录主键 ID';
COMMENT ON COLUMN quality_metrics_history.measured_at IS '指标采集时间';
COMMENT ON COLUMN quality_metrics_history.total_articles IS '文章总数';
COMMENT ON COLUMN quality_metrics_history.passed_articles IS '审查通过文章数';
COMMENT ON COLUMN quality_metrics_history.pending_articles IS '待审文章数';
COMMENT ON COLUMN quality_metrics_history.needs_review IS '需人工复核文章数';
COMMENT ON COLUMN quality_metrics_history.contributions IS '用户贡献总数';
COMMENT ON COLUMN quality_metrics_history.source_count IS '源文件总数';
COMMENT ON COLUMN quality_metrics_history.review_pass_rate IS '审查通过率（百分比）';
COMMENT ON COLUMN quality_metrics_history.grounding_rate IS '来源可追溯率（含 source_paths 的文章占比，百分比）';
COMMENT ON COLUMN quality_metrics_history.referential_rate IS '明确性关键词覆盖率（含 referential_keywords 的文章占比，百分比）';

CREATE INDEX IF NOT EXISTS idx_quality_history_measured_at
    ON quality_metrics_history (measured_at DESC);

CREATE TABLE IF NOT EXISTS repo_snapshots (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trigger_event VARCHAR(64) NOT NULL,
    git_commit VARCHAR(64),
    description TEXT,
    article_count INTEGER NOT NULL DEFAULT 0
);

COMMENT ON TABLE repo_snapshots IS '整库快照表';
COMMENT ON COLUMN repo_snapshots.id IS '整库快照主键 ID';
COMMENT ON COLUMN repo_snapshots.created_at IS '整库快照创建时间';
COMMENT ON COLUMN repo_snapshots.trigger_event IS '触发快照的事件类型';
COMMENT ON COLUMN repo_snapshots.git_commit IS '快照对应的 Git 提交哈希';
COMMENT ON COLUMN repo_snapshots.description IS '快照描述';
COMMENT ON COLUMN repo_snapshots.article_count IS '快照中的文章数量';

CREATE TABLE IF NOT EXISTS repo_snapshot_items (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES repo_snapshots (id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL
);

COMMENT ON TABLE repo_snapshot_items IS '整库快照明细表';
COMMENT ON COLUMN repo_snapshot_items.id IS '快照明细主键 ID';
COMMENT ON COLUMN repo_snapshot_items.snapshot_id IS '关联整库快照主键 ID';
COMMENT ON COLUMN repo_snapshot_items.entity_type IS '快照实体类型';
COMMENT ON COLUMN repo_snapshot_items.entity_id IS '实体唯一标识';
COMMENT ON COLUMN repo_snapshot_items.content_hash IS '实体内容哈希';
COMMENT ON COLUMN repo_snapshot_items.payload IS '实体快照载荷 JSON';

CREATE INDEX IF NOT EXISTS idx_repo_snapshot_items_snapshot
    ON repo_snapshot_items (snapshot_id);

CREATE INDEX IF NOT EXISTS idx_repo_snapshot_items_entity
    ON repo_snapshot_items (entity_type, entity_id);

DO $$
BEGIN
    IF to_regclass(current_schema() || '.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'COMMENT ON TABLE flyway_schema_history IS ''Flyway 迁移历史表''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.installed_rank IS ''迁移执行顺序''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.version IS ''迁移版本号''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.description IS ''迁移描述''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.type IS ''迁移类型''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.script IS ''迁移脚本名称''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.checksum IS ''迁移校验和''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.installed_by IS ''执行迁移的数据库用户''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.installed_on IS ''迁移安装时间''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.execution_time IS ''迁移执行耗时（毫秒）''';
        EXECUTE 'COMMENT ON COLUMN flyway_schema_history.success IS ''迁移是否执行成功''';
    END IF;
END $$;
