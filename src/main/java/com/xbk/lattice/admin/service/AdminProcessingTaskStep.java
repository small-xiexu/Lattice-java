package com.xbk.lattice.admin.service;

/**
 * 当前处理任务步骤。
 *
 * 职责：集中维护编译/入库链路的步骤码与面向用户的步骤文案
 *
 * @author xiexu
 */
public enum AdminProcessingTaskStep {

    TASK_RECEIVED("TASK_RECEIVED", "资料接收"),
    TASK_SUBMITTED("TASK_SUBMITTED", "资料接收"),
    MATCHING("MATCHING", "资料接收"),
    WAIT_CONFIRM("WAIT_CONFIRM", "资料接收"),
    MATERIALIZING("MATERIALIZING", "资料接收"),
    COMPILE_QUEUED("COMPILE_QUEUED", "内容生成"),
    COMPILE_RUNNING("COMPILE_RUNNING", "内容生成"),
    INITIALIZE_JOB("INITIALIZE_JOB", "资料接收"),
    INGEST_SOURCES("INGEST_SOURCES", "资料接收"),
    PERSIST_SOURCE_FILES("PERSIST_SOURCE_FILES", "资料接收"),
    PERSIST_SOURCE_FILE_CHUNKS("PERSIST_SOURCE_FILE_CHUNKS", "资料接收"),
    EXTRACT_AST_GRAPH("EXTRACT_AST_GRAPH", "资料接收"),
    GROUP_SOURCES("GROUP_SOURCES", "资料接收"),
    SPLIT_BATCHES("SPLIT_BATCHES", "内容生成"),
    ANALYZE_BATCHES("ANALYZE_BATCHES", "内容生成"),
    MERGE_CONCEPTS("MERGE_CONCEPTS", "内容生成"),
    ENHANCE_EXISTING_ARTICLES("ENHANCE_EXISTING_ARTICLES", "内容生成"),
    COMPILE_NEW_ARTICLES("COMPILE_NEW_ARTICLES", "内容生成"),
    REVIEW_ARTICLES("REVIEW_ARTICLES", "质量检查"),
    FIX_REVIEW_ISSUES("FIX_REVIEW_ISSUES", "质量检查"),
    PERSIST_ARTICLES("PERSIST_ARTICLES", "写入知识库"),
    REBUILD_ARTICLE_CHUNKS("REBUILD_ARTICLE_CHUNKS", "写入知识库"),
    REFRESH_VECTOR_INDEX("REFRESH_VECTOR_INDEX", "写入知识库"),
    REBUILD_ARTICLE_VECTORS("REBUILD_ARTICLE_VECTORS", "写入知识库"),
    REBUILD_SOURCE_VECTORS("REBUILD_SOURCE_VECTORS", "写入知识库"),
    GENERATE_SYNTHESIS_ARTIFACTS("GENERATE_SYNTHESIS_ARTIFACTS", "写入知识库"),
    CAPTURE_REPO_SNAPSHOT("CAPTURE_REPO_SNAPSHOT", "写入知识库"),
    FINALIZE_JOB("FINALIZE_JOB", "写入知识库");

    private final String code;

    private final String label;

    /**
     * 创建处理步骤。
     *
     * @param code 稳定步骤码
     * @param label 展示文案
     */
    AdminProcessingTaskStep(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按步骤码解析处理步骤。
     *
     * @param value 原始步骤码
     * @return 处理步骤；未知时返回 null
     */
    public static AdminProcessingTaskStep fromCode(String value) {
        String normalizedValue = AdminProcessingTaskDisplayStatus.normalize(value);
        if (normalizedValue == null) {
            return null;
        }
        for (AdminProcessingTaskStep step : values()) {
            if (step.code.equals(normalizedValue)) {
                return step;
            }
        }
        return null;
    }

    /**
     * 判断步骤是否属于质量检查阶段。
     *
     * @param value 原始步骤码
     * @return 是否属于质量检查阶段
     */
    public static boolean isQualityStep(String value) {
        AdminProcessingTaskStep step = fromCode(value);
        return REVIEW_ARTICLES.equals(step) || FIX_REVIEW_ISSUES.equals(step);
    }

    /**
     * 判断步骤是否属于写入知识库阶段。
     *
     * @param value 原始步骤码
     * @return 是否属于写入知识库阶段
     */
    public static boolean isKnowledgeWriteStep(String value) {
        AdminProcessingTaskStep step = fromCode(value);
        return FINALIZE_JOB.equals(step)
                || PERSIST_ARTICLES.equals(step)
                || REBUILD_ARTICLE_CHUNKS.equals(step)
                || REFRESH_VECTOR_INDEX.equals(step)
                || REBUILD_ARTICLE_VECTORS.equals(step)
                || REBUILD_SOURCE_VECTORS.equals(step)
                || GENERATE_SYNTHESIS_ARTIFACTS.equals(step)
                || CAPTURE_REPO_SNAPSHOT.equals(step);
    }

    /**
     * 获取稳定步骤码。
     *
     * @return 稳定步骤码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取展示文案。
     *
     * @return 展示文案
     */
    public String getLabel() {
        return label;
    }
}
