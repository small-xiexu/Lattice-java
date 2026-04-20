package com.xbk.lattice.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.governance.domain.LifecycleItem;
import com.xbk.lattice.governance.domain.LifecycleReport;
import com.xbk.lattice.governance.domain.LifecycleTransitionResult;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 生命周期服务
 *
 * 职责：提供知识文章生命周期汇总与 deprecate/archive/activate 最小闭环
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LifecycleService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    /**
     * 创建生命周期服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param articleIdentityResolver 文章身份解析服务
     */
    @Autowired
    public LifecycleService(
            ArticleJdbcRepository articleJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
    }

    /**
     * 创建兼容旧构造方式的生命周期服务。
     *
     * @param articleJdbcRepository 文章仓储
     */
    public LifecycleService(ArticleJdbcRepository articleJdbcRepository) {
        this(articleJdbcRepository, new ArticleIdentityResolver(articleJdbcRepository));
    }

    /**
     * 汇总当前生命周期分布。
     *
     * @return 生命周期报告
     */
    public LifecycleReport report() {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        int activeCount = 0;
        int deprecatedCount = 0;
        int archivedCount = 0;
        int otherCount = 0;
        List<LifecycleItem> items = new ArrayList<LifecycleItem>();

        for (ArticleRecord articleRecord : articleRecords) {
            String lifecycle = normalizeLifecycle(articleRecord.getLifecycle());
            if ("active".equals(lifecycle)) {
                activeCount++;
            }
            else if ("deprecated".equals(lifecycle)) {
                deprecatedCount++;
            }
            else if ("archived".equals(lifecycle)) {
                archivedCount++;
            }
            else {
                otherCount++;
            }
            items.add(toLifecycleItem(articleRecord, lifecycle));
        }

        return new LifecycleReport(
                articleRecords.size(),
                activeCount,
                deprecatedCount,
                archivedCount,
                otherCount,
                items
        );
    }

    /**
     * 将文章标记为 deprecated。
     *
     * @param conceptId 概念标识
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleTransitionResult deprecate(String conceptId, String reason, String updatedBy) {
        return deprecate(conceptId, null, reason, updatedBy);
    }

    /**
     * 将文章标记为 deprecated。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleTransitionResult deprecate(String articleId, Long sourceId, String reason, String updatedBy) {
        return transition(articleId, sourceId, "deprecated", reason, updatedBy);
    }

    /**
     * 将文章标记为 archived。
     *
     * @param conceptId 概念标识
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleTransitionResult archive(String conceptId, String reason, String updatedBy) {
        return archive(conceptId, null, reason, updatedBy);
    }

    /**
     * 将文章标记为 archived。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleTransitionResult archive(String articleId, Long sourceId, String reason, String updatedBy) {
        return transition(articleId, sourceId, "archived", reason, updatedBy);
    }

    /**
     * 将文章恢复为 active。
     *
     * @param conceptId 概念标识
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleTransitionResult activate(String conceptId, String reason, String updatedBy) {
        return activate(conceptId, null, reason, updatedBy);
    }

    /**
     * 将文章恢复为 active。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LifecycleTransitionResult activate(String articleId, Long sourceId, String reason, String updatedBy) {
        return transition(articleId, sourceId, "active", reason, updatedBy);
    }

    /**
     * 执行单篇文章生命周期切换。
     *
     * @param conceptId 概念标识
     * @param lifecycle 目标生命周期
     * @param reason 原因
     * @param updatedBy 更新人
     * @return 生命周期切换结果
     */
    private LifecycleTransitionResult transition(
            String articleId,
            Long sourceId,
            String lifecycle,
            String reason,
            String updatedBy
    ) {
        ArticleRecord articleRecord = articleIdentityResolver.require(articleId, sourceId);
        OffsetDateTime updatedAt = OffsetDateTime.now();
        String metadataJson = mergeLifecycleMetadata(
                articleRecord.getMetadataJson(),
                lifecycle,
                reason,
                updatedBy,
                updatedAt
        );
        ArticleRecord updatedRecord = articleRecord.copy(
                articleRecord.getTitle(),
                articleRecord.getContent(),
                lifecycle,
                articleRecord.getCompiledAt(),
                articleRecord.getSourcePaths(),
                metadataJson,
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                articleRecord.getReviewStatus()
        );
        articleJdbcRepository.upsert(updatedRecord);

        return new LifecycleTransitionResult(
                updatedRecord.getSourceId(),
                updatedRecord.getArticleKey(),
                updatedRecord.getConceptId(),
                updatedRecord.getTitle(),
                lifecycle,
                normalizeText(reason),
                normalizeText(updatedBy),
                updatedAt.toString()
        );
    }

    /**
     * 合并生命周期留痕到 metadata JSON。
     *
     * @param metadataJson 原始 metadata JSON
     * @param lifecycle 生命周期
     * @param reason 原因
     * @param updatedBy 更新人
     * @param updatedAt 更新时间
     * @return 更新后的 metadata JSON
     */
    private String mergeLifecycleMetadata(
            String metadataJson,
            String lifecycle,
            String reason,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        ObjectNode rootNode = parseMetadataObject(metadataJson);
        ObjectNode lifecycleNode = rootNode.putObject("lifecycle");
        lifecycleNode.put("status", lifecycle);
        lifecycleNode.put("updatedAt", updatedAt.toString());

        String normalizedReason = normalizeText(reason);
        if (normalizedReason == null) {
            lifecycleNode.remove("reason");
        }
        else {
            lifecycleNode.put("reason", normalizedReason);
        }

        String normalizedUpdatedBy = normalizeText(updatedBy);
        if (normalizedUpdatedBy == null) {
            lifecycleNode.remove("updatedBy");
        }
        else {
            lifecycleNode.put("updatedBy", normalizedUpdatedBy);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(rootNode);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to write lifecycle metadata", ex);
        }
    }

    /**
     * 将文章记录转换为生命周期条目。
     *
     * @param articleRecord 文章记录
     * @param lifecycle 规范化生命周期
     * @return 生命周期条目
     */
    private LifecycleItem toLifecycleItem(ArticleRecord articleRecord, String lifecycle) {
        JsonNode lifecycleNode = readLifecycleNode(articleRecord.getMetadataJson());
        return new LifecycleItem(
                articleRecord.getSourceId(),
                articleRecord.getArticleKey(),
                articleRecord.getConceptId(),
                articleRecord.getTitle(),
                lifecycle,
                articleRecord.getReviewStatus(),
                readLifecycleText(lifecycleNode, "reason"),
                readLifecycleText(lifecycleNode, "updatedBy"),
                readLifecycleText(lifecycleNode, "updatedAt")
        );
    }

    /**
     * 读取 metadata 中的 lifecycle 节点。
     *
     * @param metadataJson metadata JSON
     * @return lifecycle 节点
     */
    private JsonNode readLifecycleNode(String metadataJson) {
        ObjectNode rootNode = parseMetadataObject(metadataJson);
        JsonNode lifecycleNode = rootNode.get("lifecycle");
        if (lifecycleNode == null || !lifecycleNode.isObject()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return lifecycleNode;
    }

    /**
     * 从 lifecycle 节点读取文本字段。
     *
     * @param lifecycleNode lifecycle 节点
     * @param fieldName 字段名
     * @return 文本字段
     */
    private String readLifecycleText(JsonNode lifecycleNode, String fieldName) {
        JsonNode fieldNode = lifecycleNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        String value = fieldNode.asText();
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 解析 metadata JSON 为对象节点。
     *
     * @param metadataJson metadata JSON
     * @return 对象节点
     */
    private ObjectNode parseMetadataObject(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            if (rootNode != null && rootNode.isObject()) {
                return (ObjectNode) rootNode.deepCopy();
            }
        }
        catch (JsonProcessingException ex) {
            // ignore invalid legacy metadata and fall back to empty object
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    /**
     * 规范化生命周期文本。
     *
     * @param lifecycle 生命周期文本
     * @return 规范化生命周期
     */
    private String normalizeLifecycle(String lifecycle) {
        String normalizedLifecycle = normalizeText(lifecycle);
        return normalizedLifecycle == null ? "active" : normalizedLifecycle.toLowerCase();
    }

    /**
     * 规范化普通文本。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }
}
