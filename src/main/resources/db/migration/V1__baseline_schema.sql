CREATE TABLE IF NOT EXISTS articles (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    article_key VARCHAR(256) NOT NULL,
    concept_id VARCHAR(128) NOT NULL,
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
COMMENT ON COLUMN articles.source_id IS '所属资料源主键 ID';
COMMENT ON COLUMN articles.article_key IS 'source-aware 文章唯一键';
COMMENT ON COLUMN articles.concept_id IS '概念标识（concept slug）';
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

CREATE UNIQUE INDEX IF NOT EXISTS uk_articles_article_key
    ON articles (article_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_articles_source_concept
    ON articles (source_id, concept_id);

CREATE INDEX IF NOT EXISTS idx_articles_concept_id
    ON articles (concept_id);

CREATE TABLE IF NOT EXISTS source_files (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    file_path VARCHAR(512) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    source_sync_run_id BIGINT,
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
COMMENT ON COLUMN source_files.source_id IS '所属资料源主键 ID';
COMMENT ON COLUMN source_files.file_path IS '兼容文件路径';
COMMENT ON COLUMN source_files.relative_path IS '资料源内相对路径';
COMMENT ON COLUMN source_files.source_sync_run_id IS '最近一次写入该记录的同步运行主键';
COMMENT ON COLUMN source_files.content_preview IS '源文件内容预览';
COMMENT ON COLUMN source_files.format IS '源文件格式';
COMMENT ON COLUMN source_files.file_size IS '源文件大小（字节）';
COMMENT ON COLUMN source_files.indexed_at IS '最近一次索引时间';
COMMENT ON COLUMN source_files.content_text IS '源文件全文文本';
COMMENT ON COLUMN source_files.metadata_json IS '源文件扩展元数据 JSON';
COMMENT ON COLUMN source_files.is_verbatim IS '是否为逐字提取内容';
COMMENT ON COLUMN source_files.raw_path IS '源文件原始路径';

CREATE UNIQUE INDEX IF NOT EXISTS uk_source_files_source_relative_path
    ON source_files (source_id, relative_path);

CREATE INDEX IF NOT EXISTS idx_source_files_file_path
    ON source_files (file_path);

CREATE INDEX IF NOT EXISTS idx_source_files_relative_path
    ON source_files (relative_path);

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
    selected_article_keys TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
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
COMMENT ON COLUMN pending_queries.selected_article_keys IS '命中的文章唯一键数组';
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
    source_file_id BIGINT REFERENCES source_files (id) ON DELETE CASCADE,
    file_path VARCHAR(512) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    is_verbatim BOOLEAN NOT NULL DEFAULT FALSE,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE source_file_chunks IS '源文件分块表';
COMMENT ON COLUMN source_file_chunks.id IS '源文件分块主键 ID';
COMMENT ON COLUMN source_file_chunks.source_file_id IS '关联源文件主键 ID';
COMMENT ON COLUMN source_file_chunks.file_path IS '关联源文件路径';
COMMENT ON COLUMN source_file_chunks.chunk_index IS '分块顺序号';
COMMENT ON COLUMN source_file_chunks.chunk_text IS '源文件分块文本';
COMMENT ON COLUMN source_file_chunks.is_verbatim IS '是否为逐字分块';
COMMENT ON COLUMN source_file_chunks.indexed_at IS '分块索引时间';

CREATE INDEX IF NOT EXISTS idx_source_file_chunks_file_path
    ON source_file_chunks (file_path);

CREATE INDEX IF NOT EXISTS idx_source_file_chunks_source_file_id
    ON source_file_chunks (source_file_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_source_file_chunks_source_file_chunk
    ON source_file_chunks (source_file_id, chunk_index);

CREATE TABLE IF NOT EXISTS article_snapshots (
    snapshot_id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    article_key VARCHAR(256),
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
COMMENT ON COLUMN article_snapshots.source_id IS '所属资料源主键 ID';
COMMENT ON COLUMN article_snapshots.article_key IS 'source-aware 文章唯一键';
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

CREATE INDEX IF NOT EXISTS idx_article_snapshots_article_key_captured_at
    ON article_snapshots (article_key, captured_at DESC, snapshot_id DESC);

CREATE INDEX IF NOT EXISTS idx_article_snapshots_captured_at
    ON article_snapshots (captured_at DESC, snapshot_id DESC);

CREATE OR REPLACE FUNCTION capture_article_snapshot()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO article_snapshots (
        source_id,
        article_key,
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
        NEW.source_id,
        NEW.article_key,
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
    source_id BIGINT,
    source_sync_run_id BIGINT,
    root_trace_id VARCHAR(64),
    incremental BOOLEAN NOT NULL DEFAULT FALSE,
    orchestration_mode VARCHAR(32) NOT NULL DEFAULT 'state_graph',
    status VARCHAR(32) NOT NULL,
    worker_id VARCHAR(128),
    last_heartbeat_at TIMESTAMPTZ,
    running_expires_at TIMESTAMPTZ,
    current_step VARCHAR(64),
    progress_current INTEGER NOT NULL DEFAULT 0,
    progress_total INTEGER NOT NULL DEFAULT 0,
    progress_message TEXT,
    progress_updated_at TIMESTAMPTZ,
    error_code VARCHAR(64),
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
COMMENT ON COLUMN compile_jobs.source_id IS '所属资料源主键 ID';
COMMENT ON COLUMN compile_jobs.source_sync_run_id IS '触发该编译的资料源同步运行主键';
COMMENT ON COLUMN compile_jobs.root_trace_id IS '异步编译链路根追踪标识';
COMMENT ON COLUMN compile_jobs.incremental IS '是否为增量编译';
COMMENT ON COLUMN compile_jobs.orchestration_mode IS '编排模式';
COMMENT ON COLUMN compile_jobs.status IS '任务状态';
COMMENT ON COLUMN compile_jobs.worker_id IS '当前持有任务的 worker 标识';
COMMENT ON COLUMN compile_jobs.last_heartbeat_at IS '最近一次运行心跳时间';
COMMENT ON COLUMN compile_jobs.running_expires_at IS '当前运行租约到期时间';
COMMENT ON COLUMN compile_jobs.current_step IS '当前执行步骤';
COMMENT ON COLUMN compile_jobs.progress_current IS '当前已完成子任务数量';
COMMENT ON COLUMN compile_jobs.progress_total IS '当前总子任务数量';
COMMENT ON COLUMN compile_jobs.progress_message IS '当前进度说明';
COMMENT ON COLUMN compile_jobs.progress_updated_at IS '最近一次进度更新时间';
COMMENT ON COLUMN compile_jobs.error_code IS '机器可识别错误码';
COMMENT ON COLUMN compile_jobs.persisted_count IS '已落库文章数量';
COMMENT ON COLUMN compile_jobs.error_message IS '失败错误信息';
COMMENT ON COLUMN compile_jobs.attempt_count IS '已尝试次数';
COMMENT ON COLUMN compile_jobs.requested_at IS '任务请求时间';
COMMENT ON COLUMN compile_jobs.started_at IS '任务开始时间';
COMMENT ON COLUMN compile_jobs.finished_at IS '任务结束时间';

CREATE INDEX IF NOT EXISTS idx_compile_jobs_status_requested_at
    ON compile_jobs (status, requested_at DESC, job_id DESC);

CREATE INDEX IF NOT EXISTS idx_compile_jobs_status_running_expires_at
    ON compile_jobs (status, running_expires_at, job_id);

CREATE TABLE IF NOT EXISTS compile_job_steps (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    step_name VARCHAR(64) NOT NULL,
    step_execution_id VARCHAR(64) NOT NULL,
    sequence_no INTEGER NOT NULL,
    agent_role VARCHAR(32),
    model_route VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    summary TEXT,
    input_summary TEXT,
    output_summary TEXT,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);

COMMENT ON TABLE compile_job_steps IS '编译步骤执行日志表';
COMMENT ON COLUMN compile_job_steps.id IS '步骤日志主键 ID';
COMMENT ON COLUMN compile_job_steps.job_id IS '所属编译任务标识';
COMMENT ON COLUMN compile_job_steps.step_name IS 'Graph 节点名称';
COMMENT ON COLUMN compile_job_steps.step_execution_id IS '单次步骤执行唯一标识';
COMMENT ON COLUMN compile_job_steps.sequence_no IS '本次作业内步骤写入顺序';
COMMENT ON COLUMN compile_job_steps.agent_role IS '步骤命中的 Agent 角色';
COMMENT ON COLUMN compile_job_steps.model_route IS '步骤命中的模型路由';
COMMENT ON COLUMN compile_job_steps.status IS '步骤状态';
COMMENT ON COLUMN compile_job_steps.summary IS '步骤摘要';
COMMENT ON COLUMN compile_job_steps.input_summary IS '输入摘要';
COMMENT ON COLUMN compile_job_steps.output_summary IS '输出摘要';
COMMENT ON COLUMN compile_job_steps.error_message IS '错误信息';
COMMENT ON COLUMN compile_job_steps.started_at IS '步骤开始时间';
COMMENT ON COLUMN compile_job_steps.finished_at IS '步骤结束时间';

CREATE INDEX IF NOT EXISTS idx_compile_job_steps_job_id_sequence_no
    ON compile_job_steps (job_id, sequence_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_compile_job_steps_step_execution_id
    ON compile_job_steps (step_execution_id);

CREATE INDEX IF NOT EXISTS idx_compile_job_steps_job_id_step_execution_id
    ON compile_job_steps (job_id, step_execution_id);

CREATE TABLE IF NOT EXISTS llm_provider_connections (
    id BIGSERIAL PRIMARY KEY,
    connection_code VARCHAR(64) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    api_key_mask VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(512),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE llm_provider_connections IS 'LLM Provider 连接配置表';
COMMENT ON COLUMN llm_provider_connections.connection_code IS '连接编码';
COMMENT ON COLUMN llm_provider_connections.provider_type IS 'Provider 类型';
COMMENT ON COLUMN llm_provider_connections.base_url IS 'Provider 基础地址';
COMMENT ON COLUMN llm_provider_connections.api_key_ciphertext IS '加密后的 API Key';
COMMENT ON COLUMN llm_provider_connections.api_key_mask IS '脱敏展示值';
COMMENT ON COLUMN llm_provider_connections.enabled IS '是否启用';
COMMENT ON COLUMN llm_provider_connections.remarks IS '备注';
COMMENT ON COLUMN llm_provider_connections.created_by IS '创建人';
COMMENT ON COLUMN llm_provider_connections.updated_by IS '更新人';
COMMENT ON COLUMN llm_provider_connections.created_at IS '创建时间';
COMMENT ON COLUMN llm_provider_connections.updated_at IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_llm_provider_connections_connection_code
    ON llm_provider_connections (connection_code);

CREATE INDEX IF NOT EXISTS idx_llm_provider_connections_enabled
    ON llm_provider_connections (enabled, id DESC);

CREATE TABLE IF NOT EXISTS llm_model_profiles (
    id BIGSERIAL PRIMARY KEY,
    model_code VARCHAR(64) NOT NULL,
    connection_id BIGINT NOT NULL REFERENCES llm_provider_connections (id),
    model_name VARCHAR(128) NOT NULL,
    model_kind VARCHAR(16) NOT NULL DEFAULT 'CHAT',
    expected_dimensions INTEGER,
    supports_dimension_override BOOLEAN NOT NULL DEFAULT FALSE,
    temperature NUMERIC(4, 2),
    max_tokens INTEGER,
    timeout_seconds INTEGER,
    input_price_per_1k_tokens NUMERIC(12, 6),
    output_price_per_1k_tokens NUMERIC(12, 6),
    extra_options_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(512),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE llm_model_profiles IS 'LLM 模型配置表';
COMMENT ON COLUMN llm_model_profiles.model_code IS '模型配置编码';
COMMENT ON COLUMN llm_model_profiles.connection_id IS '关联连接配置 ID';
COMMENT ON COLUMN llm_model_profiles.model_name IS '实际模型名称';
COMMENT ON COLUMN llm_model_profiles.model_kind IS '模型类型：CHAT / EMBEDDING';
COMMENT ON COLUMN llm_model_profiles.expected_dimensions IS 'Embedding 模型期望维度';
COMMENT ON COLUMN llm_model_profiles.supports_dimension_override IS '是否支持运行时维度覆盖';
COMMENT ON COLUMN llm_model_profiles.temperature IS '温度参数';
COMMENT ON COLUMN llm_model_profiles.max_tokens IS '最大输出 token';
COMMENT ON COLUMN llm_model_profiles.timeout_seconds IS '超时秒数';
COMMENT ON COLUMN llm_model_profiles.input_price_per_1k_tokens IS '输入单价（美元/1k token）';
COMMENT ON COLUMN llm_model_profiles.output_price_per_1k_tokens IS '输出单价（美元/1k token）';
COMMENT ON COLUMN llm_model_profiles.extra_options_json IS 'Provider 扩展参数';
COMMENT ON COLUMN llm_model_profiles.enabled IS '是否启用';
COMMENT ON COLUMN llm_model_profiles.remarks IS '备注';
COMMENT ON COLUMN llm_model_profiles.created_by IS '创建人';
COMMENT ON COLUMN llm_model_profiles.updated_by IS '更新人';
COMMENT ON COLUMN llm_model_profiles.created_at IS '创建时间';
COMMENT ON COLUMN llm_model_profiles.updated_at IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_llm_model_profiles_model_code
    ON llm_model_profiles (model_code);

CREATE INDEX IF NOT EXISTS idx_llm_model_profiles_connection_enabled
    ON llm_model_profiles (connection_id, enabled, id DESC);

CREATE TABLE IF NOT EXISTS document_parse_connections (
    id BIGSERIAL PRIMARY KEY,
    connection_code VARCHAR(64) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    credential_ciphertext TEXT NOT NULL DEFAULT '',
    credential_mask VARCHAR(255) NOT NULL DEFAULT '',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document_parse_connections IS '文档解析连接配置表';
COMMENT ON COLUMN document_parse_connections.connection_code IS '连接编码';
COMMENT ON COLUMN document_parse_connections.provider_type IS '供应商类型';
COMMENT ON COLUMN document_parse_connections.base_url IS '基础地址';
COMMENT ON COLUMN document_parse_connections.credential_ciphertext IS '加密后的凭证 JSON';
COMMENT ON COLUMN document_parse_connections.credential_mask IS '凭证脱敏展示';
COMMENT ON COLUMN document_parse_connections.config_json IS '供应商配置 JSON';
COMMENT ON COLUMN document_parse_connections.enabled IS '是否启用';
COMMENT ON COLUMN document_parse_connections.created_by IS '创建人';
COMMENT ON COLUMN document_parse_connections.updated_by IS '更新人';
COMMENT ON COLUMN document_parse_connections.created_at IS '创建时间';
COMMENT ON COLUMN document_parse_connections.updated_at IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_parse_connections_connection_code
    ON document_parse_connections (connection_code);

CREATE INDEX IF NOT EXISTS idx_document_parse_connections_enabled
    ON document_parse_connections (enabled, id DESC);

CREATE TABLE IF NOT EXISTS document_parse_route_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_scope VARCHAR(32) NOT NULL,
    image_connection_id BIGINT REFERENCES document_parse_connections (id),
    scanned_pdf_connection_id BIGINT REFERENCES document_parse_connections (id),
    cleanup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cleanup_model_profile_id BIGINT REFERENCES llm_model_profiles (id),
    fallback_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document_parse_route_policies IS '文档解析路由策略表';
COMMENT ON COLUMN document_parse_route_policies.policy_scope IS '策略作用域';
COMMENT ON COLUMN document_parse_route_policies.image_connection_id IS '图片 OCR 默认连接';
COMMENT ON COLUMN document_parse_route_policies.scanned_pdf_connection_id IS '扫描 PDF OCR 默认连接';
COMMENT ON COLUMN document_parse_route_policies.cleanup_enabled IS '是否启用 OCR 后整理';
COMMENT ON COLUMN document_parse_route_policies.cleanup_model_profile_id IS '后整理模型档案';
COMMENT ON COLUMN document_parse_route_policies.fallback_policy_json IS '降级策略 JSON';
COMMENT ON COLUMN document_parse_route_policies.created_by IS '创建人';
COMMENT ON COLUMN document_parse_route_policies.updated_by IS '更新人';
COMMENT ON COLUMN document_parse_route_policies.created_at IS '创建时间';
COMMENT ON COLUMN document_parse_route_policies.updated_at IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_parse_route_policies_scope
    ON document_parse_route_policies (policy_scope);

CREATE INDEX IF NOT EXISTS idx_document_parse_route_policies_image_connection
    ON document_parse_route_policies (image_connection_id);

CREATE INDEX IF NOT EXISTS idx_document_parse_route_policies_scanned_connection
    ON document_parse_route_policies (scanned_pdf_connection_id);

CREATE TABLE IF NOT EXISTS source_credentials (
    id BIGSERIAL PRIMARY KEY,
    credential_code VARCHAR(64) NOT NULL,
    credential_type VARCHAR(32) NOT NULL,
    secret_ciphertext TEXT NOT NULL DEFAULT '',
    secret_mask VARCHAR(128) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_source_credentials_credential_code
    ON source_credentials (credential_code);

CREATE TABLE IF NOT EXISTS knowledge_sources (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    content_profile VARCHAR(32) NOT NULL DEFAULT 'DOCUMENT',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    visibility VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    default_sync_mode VARCHAR(32) NOT NULL DEFAULT 'AUTO',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    latest_manifest_hash VARCHAR(128),
    last_sync_run_id BIGINT,
    last_sync_status VARCHAR(32),
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_sources_source_code
    ON knowledge_sources (source_code);

CREATE TABLE IF NOT EXISTS source_sync_runs (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT REFERENCES knowledge_sources (id),
    source_type VARCHAR(32) NOT NULL DEFAULT 'UPLOAD',
    manifest_hash VARCHAR(128),
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    resolver_mode VARCHAR(32) NOT NULL DEFAULT 'RULE_ONLY',
    resolver_decision VARCHAR(32),
    sync_action VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    matched_source_id BIGINT REFERENCES knowledge_sources (id),
    compile_job_id VARCHAR(64) REFERENCES compile_jobs (job_id),
    evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_source_sync_runs_source_status
    ON source_sync_runs (source_id, status, requested_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_source_sync_runs_manifest_hash
    ON source_sync_runs (manifest_hash, requested_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_source_sync_runs_manifest_hash_prelock
    ON source_sync_runs (manifest_hash)
    WHERE source_id IS NULL
      AND manifest_hash IS NOT NULL
      AND status IN ('QUEUED', 'MATCHING', 'MATERIALIZING', 'COMPILE_QUEUED', 'RUNNING');

CREATE TABLE IF NOT EXISTS source_snapshots (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    sync_run_id BIGINT REFERENCES source_sync_runs (id) ON DELETE SET NULL,
    manifest_hash VARCHAR(128) NOT NULL,
    summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_snapshots_source_created_at
    ON source_snapshots (source_id, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS article_source_refs (
    id BIGSERIAL PRIMARY KEY,
    article_key VARCHAR(256) NOT NULL,
    source_id BIGINT NOT NULL REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    source_file_id BIGINT NOT NULL REFERENCES source_files (id) ON DELETE CASCADE,
    ref_type VARCHAR(32) NOT NULL,
    ref_label VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_article_source_refs_article_key
    ON article_source_refs (article_key, created_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_article_source_refs_article_source_file_ref_type
    ON article_source_refs (article_key, source_file_id, ref_type);

CREATE TABLE IF NOT EXISTS graph_entities (
    id VARCHAR(512) PRIMARY KEY,
    canonical_name VARCHAR(512) NOT NULL,
    simple_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    system_label VARCHAR(128) NOT NULL,
    source_file_id BIGINT NOT NULL REFERENCES source_files (id) ON DELETE CASCADE,
    anchor_ref VARCHAR(512) NOT NULL,
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'RESOLVED',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE graph_entities IS 'AST 图谱实体表';
COMMENT ON COLUMN graph_entities.id IS '稳定实体业务主键';
COMMENT ON COLUMN graph_entities.canonical_name IS '实体全名';
COMMENT ON COLUMN graph_entities.simple_name IS '实体短名';
COMMENT ON COLUMN graph_entities.entity_type IS '实体类型';
COMMENT ON COLUMN graph_entities.system_label IS '业务系统标签';
COMMENT ON COLUMN graph_entities.source_file_id IS '关联源文件主键';
COMMENT ON COLUMN graph_entities.anchor_ref IS '源码锚点';
COMMENT ON COLUMN graph_entities.resolution_status IS '符号解析状态';
COMMENT ON COLUMN graph_entities.metadata_json IS '实体扩展元数据 JSON';

CREATE INDEX IF NOT EXISTS idx_graph_entities_simple_name
    ON graph_entities (simple_name);

CREATE INDEX IF NOT EXISTS idx_graph_entities_canonical_name
    ON graph_entities (canonical_name);

CREATE INDEX IF NOT EXISTS idx_graph_entities_source_file_id
    ON graph_entities (source_file_id);

CREATE TABLE IF NOT EXISTS graph_facts (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(512) NOT NULL REFERENCES graph_entities (id) ON DELETE CASCADE,
    predicate VARCHAR(64) NOT NULL,
    value TEXT NOT NULL,
    source_ref VARCHAR(512) NOT NULL,
    source_start_line INTEGER NOT NULL DEFAULT 0,
    source_end_line INTEGER NOT NULL DEFAULT 0,
    evidence_excerpt TEXT NOT NULL DEFAULT '',
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    extractor VARCHAR(64) NOT NULL,
    asserted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    superseded_by BIGINT,
    tombstoned BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE graph_facts IS 'AST 图谱事实表';
COMMENT ON COLUMN graph_facts.entity_id IS '实体主键';
COMMENT ON COLUMN graph_facts.predicate IS '事实谓词';
COMMENT ON COLUMN graph_facts.value IS '事实值';
COMMENT ON COLUMN graph_facts.source_ref IS '证据源路径';
COMMENT ON COLUMN graph_facts.source_start_line IS '证据起始行号';
COMMENT ON COLUMN graph_facts.source_end_line IS '证据结束行号';
COMMENT ON COLUMN graph_facts.evidence_excerpt IS '证据摘录';
COMMENT ON COLUMN graph_facts.confidence IS '置信度';
COMMENT ON COLUMN graph_facts.extractor IS '抽取器标识';
COMMENT ON COLUMN graph_facts.asserted_at IS '断言时间';
COMMENT ON COLUMN graph_facts.superseded_by IS '被后续记录替代的主键';
COMMENT ON COLUMN graph_facts.tombstoned IS '是否逻辑删除';

CREATE INDEX IF NOT EXISTS idx_graph_facts_entity_id
    ON graph_facts (entity_id, asserted_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_graph_facts_source_ref
    ON graph_facts (source_ref, asserted_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_facts_active
    ON graph_facts (entity_id, predicate, value, source_ref, source_start_line, source_end_line)
    WHERE tombstoned = FALSE
      AND superseded_by IS NULL;

CREATE TABLE IF NOT EXISTS graph_relations (
    id BIGSERIAL PRIMARY KEY,
    src_id VARCHAR(512) NOT NULL REFERENCES graph_entities (id) ON DELETE CASCADE,
    edge_type VARCHAR(64) NOT NULL,
    dst_id VARCHAR(512) NOT NULL,
    source_ref VARCHAR(512) NOT NULL,
    source_start_line INTEGER NOT NULL DEFAULT 0,
    source_end_line INTEGER NOT NULL DEFAULT 0,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    extractor VARCHAR(64) NOT NULL,
    asserted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    superseded_by BIGINT,
    tombstoned BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE graph_relations IS 'AST 图谱关系表';
COMMENT ON COLUMN graph_relations.src_id IS '源实体主键';
COMMENT ON COLUMN graph_relations.edge_type IS '关系类型';
COMMENT ON COLUMN graph_relations.dst_id IS '目标实体或符号';
COMMENT ON COLUMN graph_relations.source_ref IS '证据源路径';
COMMENT ON COLUMN graph_relations.source_start_line IS '证据起始行号';
COMMENT ON COLUMN graph_relations.source_end_line IS '证据结束行号';
COMMENT ON COLUMN graph_relations.confidence IS '置信度';
COMMENT ON COLUMN graph_relations.extractor IS '抽取器标识';
COMMENT ON COLUMN graph_relations.asserted_at IS '断言时间';
COMMENT ON COLUMN graph_relations.superseded_by IS '被后续记录替代的主键';
COMMENT ON COLUMN graph_relations.tombstoned IS '是否逻辑删除';

CREATE INDEX IF NOT EXISTS idx_graph_relations_src_id
    ON graph_relations (src_id, asserted_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_graph_relations_source_ref
    ON graph_relations (source_ref, asserted_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_graph_relations_active
    ON graph_relations (src_id, edge_type, dst_id, source_ref, source_start_line, source_end_line)
    WHERE tombstoned = FALSE
      AND superseded_by IS NULL;

INSERT INTO knowledge_sources (
    source_code,
    name,
    source_type,
    content_profile,
    status,
    visibility,
    default_sync_mode,
    config_json,
    metadata_json
)
VALUES (
    'legacy-default',
    'Legacy Default Source',
    'UPLOAD',
    'DOCUMENT',
    'ACTIVE',
    'NORMAL',
    'FULL',
    '{}'::jsonb,
    '{"legacyDefault":true}'::jsonb
)
ON CONFLICT (source_code) DO NOTHING;

ALTER TABLE source_files
    ADD CONSTRAINT fk_source_files_source
    FOREIGN KEY (source_id) REFERENCES knowledge_sources (id);

ALTER TABLE source_files
    ADD CONSTRAINT fk_source_files_source_sync_run
    FOREIGN KEY (source_sync_run_id) REFERENCES source_sync_runs (id);

ALTER TABLE articles
    ADD CONSTRAINT fk_articles_source
    FOREIGN KEY (source_id) REFERENCES knowledge_sources (id);

ALTER TABLE article_snapshots
    ADD CONSTRAINT fk_article_snapshots_source
    FOREIGN KEY (source_id) REFERENCES knowledge_sources (id);

ALTER TABLE compile_jobs
    ADD CONSTRAINT fk_compile_jobs_source
    FOREIGN KEY (source_id) REFERENCES knowledge_sources (id);

ALTER TABLE compile_jobs
    ADD CONSTRAINT fk_compile_jobs_source_sync_run
    FOREIGN KEY (source_sync_run_id) REFERENCES source_sync_runs (id);

ALTER TABLE knowledge_sources
    ADD CONSTRAINT fk_knowledge_sources_last_sync_run
    FOREIGN KEY (last_sync_run_id) REFERENCES source_sync_runs (id);

CREATE TABLE IF NOT EXISTS query_vector_settings (
    config_scope VARCHAR(32) PRIMARY KEY,
    vector_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    embedding_model_profile_id BIGINT NOT NULL REFERENCES llm_model_profiles (id),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE query_vector_settings IS 'Query 向量检索配置表';
COMMENT ON COLUMN query_vector_settings.config_scope IS '配置作用域，当前固定为 default';
COMMENT ON COLUMN query_vector_settings.vector_enabled IS '是否启用向量检索';
COMMENT ON COLUMN query_vector_settings.embedding_model_profile_id IS '当前选中的 embedding profile 主键';
COMMENT ON COLUMN query_vector_settings.created_by IS '创建人';
COMMENT ON COLUMN query_vector_settings.updated_by IS '更新人';
COMMENT ON COLUMN query_vector_settings.created_at IS '创建时间';
COMMENT ON COLUMN query_vector_settings.updated_at IS '更新时间';

CREATE TABLE IF NOT EXISTS query_retrieval_settings (
    id BIGINT PRIMARY KEY DEFAULT 1,
    parallel_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    fts_weight NUMERIC NOT NULL DEFAULT 1.0,
    source_weight NUMERIC NOT NULL DEFAULT 1.0,
    contribution_weight NUMERIC NOT NULL DEFAULT 1.0,
    graph_weight NUMERIC NOT NULL DEFAULT 0.9,
    article_vector_weight NUMERIC NOT NULL DEFAULT 0.6,
    chunk_vector_weight NUMERIC NOT NULL DEFAULT 1.2,
    rrf_k INTEGER NOT NULL DEFAULT 60,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE query_retrieval_settings IS 'Query 检索融合配置表';
COMMENT ON COLUMN query_retrieval_settings.parallel_enabled IS '是否启用并行召回';
COMMENT ON COLUMN query_retrieval_settings.fts_weight IS 'FTS 召回权重';
COMMENT ON COLUMN query_retrieval_settings.source_weight IS 'Source 召回权重';
COMMENT ON COLUMN query_retrieval_settings.contribution_weight IS 'Contribution 召回权重';
COMMENT ON COLUMN query_retrieval_settings.graph_weight IS 'Graph 召回权重';
COMMENT ON COLUMN query_retrieval_settings.article_vector_weight IS '文章级向量召回权重';
COMMENT ON COLUMN query_retrieval_settings.chunk_vector_weight IS 'Chunk 级向量召回权重';
COMMENT ON COLUMN query_retrieval_settings.rrf_k IS 'RRF 融合 K 值';
COMMENT ON COLUMN query_retrieval_settings.updated_at IS '更新时间';

INSERT INTO query_retrieval_settings DEFAULT VALUES ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS query_answer_audits (
    audit_id BIGSERIAL PRIMARY KEY,
    query_id VARCHAR(64) NOT NULL,
    answer_version INTEGER NOT NULL,
    question TEXT NOT NULL,
    answer_markdown TEXT NOT NULL,
    answer_outcome VARCHAR(32),
    generation_mode VARCHAR(32),
    review_status VARCHAR(32),
    citation_coverage NUMERIC(5, 4) NOT NULL DEFAULT 0,
    unsupported_claim_count INTEGER NOT NULL DEFAULT 0,
    verified_citation_count INTEGER NOT NULL DEFAULT 0,
    demoted_citation_count INTEGER NOT NULL DEFAULT 0,
    skipped_citation_count INTEGER NOT NULL DEFAULT 0,
    cacheable BOOLEAN NOT NULL DEFAULT FALSE,
    route_type VARCHAR(32) NOT NULL,
    model_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE query_answer_audits IS 'Query 最终答案审计主表';
COMMENT ON COLUMN query_answer_audits.query_id IS '查询标识';
COMMENT ON COLUMN query_answer_audits.answer_version IS '答案版本';
COMMENT ON COLUMN query_answer_audits.question IS '用户问题';
COMMENT ON COLUMN query_answer_audits.answer_markdown IS '最终答案 Markdown';
COMMENT ON COLUMN query_answer_audits.answer_outcome IS '答案语义';
COMMENT ON COLUMN query_answer_audits.generation_mode IS '生成模式';
COMMENT ON COLUMN query_answer_audits.review_status IS '审查状态';
COMMENT ON COLUMN query_answer_audits.citation_coverage IS '引用覆盖率';
COMMENT ON COLUMN query_answer_audits.unsupported_claim_count IS '无法支撑 claim 数';
COMMENT ON COLUMN query_answer_audits.verified_citation_count IS '已验证引用数';
COMMENT ON COLUMN query_answer_audits.demoted_citation_count IS '被降级引用数';
COMMENT ON COLUMN query_answer_audits.skipped_citation_count IS '跳过核验引用数';
COMMENT ON COLUMN query_answer_audits.cacheable IS '是否允许写缓存';
COMMENT ON COLUMN query_answer_audits.route_type IS '路由类型';
COMMENT ON COLUMN query_answer_audits.model_snapshot_json IS '模型快照 JSON';

CREATE INDEX IF NOT EXISTS idx_query_answer_audits_query_id
    ON query_answer_audits (query_id, created_at DESC, audit_id DESC);

CREATE TABLE IF NOT EXISTS query_answer_claims (
    claim_id BIGSERIAL PRIMARY KEY,
    audit_id BIGINT NOT NULL REFERENCES query_answer_audits (audit_id) ON DELETE CASCADE,
    claim_index INTEGER NOT NULL,
    claim_text TEXT NOT NULL,
    claim_status VARCHAR(32) NOT NULL,
    citation_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE query_answer_claims IS 'Query claim 审计明细表';
COMMENT ON COLUMN query_answer_claims.audit_id IS '答案审计主键';
COMMENT ON COLUMN query_answer_claims.claim_index IS 'claim 顺序号';
COMMENT ON COLUMN query_answer_claims.claim_text IS 'claim 文本';
COMMENT ON COLUMN query_answer_claims.claim_status IS 'claim 核验状态';
COMMENT ON COLUMN query_answer_claims.citation_count IS 'claim 对应引用数量';

CREATE UNIQUE INDEX IF NOT EXISTS uk_query_answer_claims_audit_claim_index
    ON query_answer_claims (audit_id, claim_index);

CREATE TABLE IF NOT EXISTS query_answer_citations (
    citation_id BIGSERIAL PRIMARY KEY,
    audit_id BIGINT NOT NULL REFERENCES query_answer_audits (audit_id) ON DELETE CASCADE,
    claim_id BIGINT REFERENCES query_answer_claims (claim_id) ON DELETE CASCADE,
    citation_ordinal INTEGER NOT NULL,
    citation_literal TEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(512) NOT NULL,
    validation_status VARCHAR(32) NOT NULL,
    overlap_score NUMERIC(5, 4) NOT NULL DEFAULT 0,
    matched_excerpt TEXT NOT NULL DEFAULT '',
    reason VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE query_answer_citations IS 'Query citation 审计明细表';
COMMENT ON COLUMN query_answer_citations.audit_id IS '答案审计主键';
COMMENT ON COLUMN query_answer_citations.claim_id IS 'claim 主键';
COMMENT ON COLUMN query_answer_citations.citation_ordinal IS '引用顺序号';
COMMENT ON COLUMN query_answer_citations.citation_literal IS '原始引用字面量';
COMMENT ON COLUMN query_answer_citations.source_type IS '引用来源类型';
COMMENT ON COLUMN query_answer_citations.target_key IS '引用目标键';
COMMENT ON COLUMN query_answer_citations.validation_status IS '核验状态';
COMMENT ON COLUMN query_answer_citations.overlap_score IS '重叠分';
COMMENT ON COLUMN query_answer_citations.matched_excerpt IS '命中摘录';
COMMENT ON COLUMN query_answer_citations.reason IS '核验原因';

CREATE UNIQUE INDEX IF NOT EXISTS uk_query_answer_citations_audit_ordinal
    ON query_answer_citations (audit_id, citation_ordinal);

CREATE TABLE IF NOT EXISTS deep_research_runs (
    run_id BIGSERIAL PRIMARY KEY,
    query_id VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    route_reason VARCHAR(255) NOT NULL,
    plan_json JSONB NOT NULL,
    layer_count INTEGER NOT NULL,
    task_count INTEGER NOT NULL,
    llm_call_count INTEGER NOT NULL,
    citation_coverage NUMERIC(5, 4) NOT NULL DEFAULT 0,
    partial_answer BOOLEAN NOT NULL DEFAULT FALSE,
    has_conflicts BOOLEAN NOT NULL DEFAULT FALSE,
    final_answer_audit_id BIGINT REFERENCES query_answer_audits (audit_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE deep_research_runs IS 'Deep Research 运行审计主表';
COMMENT ON COLUMN deep_research_runs.query_id IS '查询标识';
COMMENT ON COLUMN deep_research_runs.question IS '查询问题';
COMMENT ON COLUMN deep_research_runs.route_reason IS '路由原因';
COMMENT ON COLUMN deep_research_runs.plan_json IS '研究计划 JSON';
COMMENT ON COLUMN deep_research_runs.layer_count IS '层数';
COMMENT ON COLUMN deep_research_runs.task_count IS '任务数';
COMMENT ON COLUMN deep_research_runs.llm_call_count IS 'LLM 调用数';
COMMENT ON COLUMN deep_research_runs.citation_coverage IS '引用覆盖率';
COMMENT ON COLUMN deep_research_runs.partial_answer IS '是否部分答案';
COMMENT ON COLUMN deep_research_runs.has_conflicts IS '是否存在冲突';
COMMENT ON COLUMN deep_research_runs.final_answer_audit_id IS '最终答案审计主键';

CREATE INDEX IF NOT EXISTS idx_deep_research_runs_query_id
    ON deep_research_runs (query_id, created_at DESC, run_id DESC);

CREATE TABLE IF NOT EXISTS deep_research_evidence_cards (
    card_id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES deep_research_runs (run_id) ON DELETE CASCADE,
    evidence_id VARCHAR(32) NOT NULL,
    layer_index INTEGER NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    scope TEXT NOT NULL,
    findings_json JSONB NOT NULL,
    gaps_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    related_leads_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE deep_research_evidence_cards IS 'Deep Research 证据卡审计表';
COMMENT ON COLUMN deep_research_evidence_cards.run_id IS '运行主键';
COMMENT ON COLUMN deep_research_evidence_cards.evidence_id IS '证据卡标识';
COMMENT ON COLUMN deep_research_evidence_cards.layer_index IS '层序号';
COMMENT ON COLUMN deep_research_evidence_cards.task_id IS '任务标识';
COMMENT ON COLUMN deep_research_evidence_cards.scope IS '研究范围';
COMMENT ON COLUMN deep_research_evidence_cards.findings_json IS '证据发现 JSON';
COMMENT ON COLUMN deep_research_evidence_cards.gaps_json IS '证据缺口 JSON';
COMMENT ON COLUMN deep_research_evidence_cards.related_leads_json IS '后续线索 JSON';

CREATE UNIQUE INDEX IF NOT EXISTS uk_deep_research_evidence_cards_run_evidence
    ON deep_research_evidence_cards (run_id, evidence_id);

CREATE TABLE IF NOT EXISTS agent_model_bindings (
    id BIGSERIAL PRIMARY KEY,
    scene VARCHAR(32) NOT NULL,
    agent_role VARCHAR(32) NOT NULL,
    primary_model_profile_id BIGINT NOT NULL REFERENCES llm_model_profiles (id),
    fallback_model_profile_id BIGINT REFERENCES llm_model_profiles (id),
    route_label VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remarks VARCHAR(512),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE agent_model_bindings IS 'Agent 角色与模型绑定表';
COMMENT ON COLUMN agent_model_bindings.scene IS '场景';
COMMENT ON COLUMN agent_model_bindings.agent_role IS 'Agent 角色';
COMMENT ON COLUMN agent_model_bindings.primary_model_profile_id IS '主模型配置 ID';
COMMENT ON COLUMN agent_model_bindings.fallback_model_profile_id IS '备用模型配置 ID（V1 仅预留）';
COMMENT ON COLUMN agent_model_bindings.route_label IS '稳定路由标签';
COMMENT ON COLUMN agent_model_bindings.enabled IS '是否启用';
COMMENT ON COLUMN agent_model_bindings.remarks IS '备注';
COMMENT ON COLUMN agent_model_bindings.created_by IS '创建人';
COMMENT ON COLUMN agent_model_bindings.updated_by IS '更新人';
COMMENT ON COLUMN agent_model_bindings.created_at IS '创建时间';
COMMENT ON COLUMN agent_model_bindings.updated_at IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_model_bindings_scene_role
    ON agent_model_bindings (scene, agent_role);

CREATE INDEX IF NOT EXISTS idx_agent_model_bindings_scene_enabled
    ON agent_model_bindings (scene, enabled, id DESC);

CREATE TABLE IF NOT EXISTS execution_llm_snapshots (
    id BIGSERIAL PRIMARY KEY,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    agent_role VARCHAR(32) NOT NULL,
    binding_id BIGINT NOT NULL REFERENCES agent_model_bindings (id),
    model_profile_id BIGINT NOT NULL REFERENCES llm_model_profiles (id),
    connection_id BIGINT NOT NULL REFERENCES llm_provider_connections (id),
    route_label VARCHAR(128) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    temperature NUMERIC(4, 2),
    max_tokens INTEGER,
    timeout_seconds INTEGER,
    extra_options_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_price_per_1k_tokens NUMERIC(12, 6),
    output_price_per_1k_tokens NUMERIC(12, 6),
    snapshot_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE execution_llm_snapshots IS '运行时 LLM 快照表';
COMMENT ON COLUMN execution_llm_snapshots.scope_type IS '作用域类型';
COMMENT ON COLUMN execution_llm_snapshots.scope_id IS '作用域标识';
COMMENT ON COLUMN execution_llm_snapshots.scene IS '运行场景';
COMMENT ON COLUMN execution_llm_snapshots.agent_role IS 'Agent 角色';
COMMENT ON COLUMN execution_llm_snapshots.binding_id IS '命中的绑定 ID';
COMMENT ON COLUMN execution_llm_snapshots.model_profile_id IS '命中的模型配置 ID';
COMMENT ON COLUMN execution_llm_snapshots.connection_id IS '命中的连接配置 ID';
COMMENT ON COLUMN execution_llm_snapshots.route_label IS '任务内稳定路由标签';
COMMENT ON COLUMN execution_llm_snapshots.provider_type IS 'Provider 类型';
COMMENT ON COLUMN execution_llm_snapshots.base_url IS '快照时命中的基础地址';
COMMENT ON COLUMN execution_llm_snapshots.model_name IS '快照时命中的模型名称';
COMMENT ON COLUMN execution_llm_snapshots.temperature IS '快照时命中的温度参数';
COMMENT ON COLUMN execution_llm_snapshots.max_tokens IS '快照时命中的最大输出 token';
COMMENT ON COLUMN execution_llm_snapshots.timeout_seconds IS '快照时命中的超时秒数';
COMMENT ON COLUMN execution_llm_snapshots.extra_options_json IS '快照时命中的扩展参数';
COMMENT ON COLUMN execution_llm_snapshots.input_price_per_1k_tokens IS '快照输入单价';
COMMENT ON COLUMN execution_llm_snapshots.output_price_per_1k_tokens IS '快照输出单价';
COMMENT ON COLUMN execution_llm_snapshots.snapshot_version IS '快照版本';
COMMENT ON COLUMN execution_llm_snapshots.created_at IS '创建时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_execution_llm_snapshots_scope_scene_role
    ON execution_llm_snapshots (scope_type, scope_id, scene, agent_role);

CREATE INDEX IF NOT EXISTS idx_execution_llm_snapshots_scope_scene
    ON execution_llm_snapshots (scope_type, scope_id, scene, id DESC);

DO $$
DECLARE vector_type_name TEXT;
BEGIN
    vector_type_name := coalesce(to_regtype('vector')::text, to_regtype('public.vector')::text);
    IF vector_type_name IS NOT NULL THEN
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS article_vector_index (
                article_key VARCHAR(256) PRIMARY KEY REFERENCES articles (article_key) ON DELETE CASCADE,
                concept_id VARCHAR(128) NOT NULL,
                model_profile_id BIGINT NOT NULL REFERENCES llm_model_profiles (id),
                embedding_dimensions INTEGER NOT NULL,
                index_version VARCHAR(64) NOT NULL,
                content_hash VARCHAR(64) NOT NULL,
                embedding %s(1536) NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            vector_type_name
        );

        CREATE INDEX IF NOT EXISTS idx_article_vector_index_updated_at
            ON article_vector_index (updated_at DESC);

        CREATE INDEX IF NOT EXISTS idx_article_vector_index_concept_id
            ON article_vector_index (concept_id);

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS article_chunk_vector_index (
                article_chunk_id BIGINT PRIMARY KEY REFERENCES article_chunks (id) ON DELETE CASCADE,
                article_id BIGINT NOT NULL REFERENCES articles (id) ON DELETE CASCADE,
                concept_id VARCHAR(128) NOT NULL,
                chunk_index INTEGER NOT NULL,
                model_profile_id BIGINT NOT NULL REFERENCES llm_model_profiles (id),
                content_hash VARCHAR(64) NOT NULL,
                embedding %s(1536) NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            vector_type_name
        );

        CREATE INDEX IF NOT EXISTS idx_article_chunk_vector_index_concept
            ON article_chunk_vector_index (concept_id, chunk_index);

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS graph_entity_vectors (
                entity_id VARCHAR(512) PRIMARY KEY REFERENCES graph_entities (id) ON DELETE CASCADE,
                model_profile_id BIGINT NOT NULL REFERENCES llm_model_profiles (id),
                embedding_dimensions INTEGER NOT NULL,
                content_hash VARCHAR(64) NOT NULL,
                embedding %s(1536) NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            vector_type_name
        );

        CREATE INDEX IF NOT EXISTS idx_graph_entity_vectors_updated_at
            ON graph_entity_vectors (updated_at DESC);

    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass(current_schema() || '.article_vector_index') IS NOT NULL THEN
        EXECUTE 'COMMENT ON TABLE article_vector_index IS ''文章向量索引表''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.article_key IS ''关联文章唯一键''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.concept_id IS ''关联文章概念标识''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.model_profile_id IS ''Embedding profile 主键''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.embedding_dimensions IS ''向量维度''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.index_version IS ''索引版本''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.content_hash IS ''正文内容哈希''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.embedding IS ''文章向量嵌入''';
        EXECUTE 'COMMENT ON COLUMN article_vector_index.updated_at IS ''向量索引更新时间''';
    END IF;
    IF to_regclass(current_schema() || '.article_chunk_vector_index') IS NOT NULL THEN
        EXECUTE 'COMMENT ON TABLE article_chunk_vector_index IS ''文章分块向量索引表''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.article_chunk_id IS ''关联文章分块主键 ID''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.article_id IS ''关联文章主键 ID''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.concept_id IS ''关联文章概念唯一标识''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.chunk_index IS ''分块顺序号''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.model_profile_id IS ''Embedding profile 主键''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.content_hash IS ''Chunk 内容哈希''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.embedding IS ''Chunk 向量嵌入''';
        EXECUTE 'COMMENT ON COLUMN article_chunk_vector_index.updated_at IS ''向量索引更新时间''';
    END IF;
    IF to_regclass(current_schema() || '.graph_entity_vectors') IS NOT NULL THEN
        EXECUTE 'COMMENT ON TABLE graph_entity_vectors IS ''图谱实体向量索引表''';
        EXECUTE 'COMMENT ON COLUMN graph_entity_vectors.entity_id IS ''关联图谱实体主键''';
        EXECUTE 'COMMENT ON COLUMN graph_entity_vectors.model_profile_id IS ''Embedding profile 主键''';
        EXECUTE 'COMMENT ON COLUMN graph_entity_vectors.embedding_dimensions IS ''向量维度''';
        EXECUTE 'COMMENT ON COLUMN graph_entity_vectors.content_hash IS ''图谱 grounding 内容哈希''';
        EXECUTE 'COMMENT ON COLUMN graph_entity_vectors.embedding IS ''图谱实体向量嵌入''';
        EXECUTE 'COMMENT ON COLUMN graph_entity_vectors.updated_at IS ''向量索引更新时间''';
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
