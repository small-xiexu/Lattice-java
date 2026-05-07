package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.infra.persistence.mapper.ArticleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Article JDBC 仓储
 *
 * 职责：提供最小文章落盘与读取能力
 *
 * @author xiexu
 */
@Repository
public class ArticleJdbcRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ArticleMapper articleMapper;

    /**
     * 创建 Article JDBC 仓储。
     *
     * @param articleMapper 文章 Mapper
     */
    @Autowired
    public ArticleJdbcRepository(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    /**
     * 创建测试替身使用的 Article 仓储。
     *
     * @param ignored 旧测试替身占位参数
     */
    protected ArticleJdbcRepository(Object ignored) {
        this.articleMapper = null;
    }

    /**
     * 保存或更新文章。
     *
     * @param articleRecord 文章记录
     */
    public void upsert(ArticleRecord articleRecord) {
        ArticleRecord normalizedArticleRecord = ArticleMarkdownSupport.synchronizeArticleRecord(
                articleRecord,
                articleRecord.getContent(),
                articleRecord.getReviewStatus()
        );
        String searchText = buildArticleSearchText(normalizedArticleRecord);
        String refkeyText = buildRefkeyText(normalizedArticleRecord);
        articleMapper.upsert(normalizedArticleRecord, searchText, refkeyText);
    }

    /**
     * 按概念标识查询文章。
     *
     * @param conceptId 概念标识
     * @return 文章记录
     */
    public Optional<ArticleRecord> findByConceptId(String conceptId) {
        return Optional.ofNullable(articleMapper.findByConceptId(conceptId));
    }

    /**
     * 按文章唯一键查询文章。
     *
     * @param articleKey 文章唯一键
     * @return 文章记录
     */
    public Optional<ArticleRecord> findByArticleKey(String articleKey) {
        return Optional.ofNullable(articleMapper.findByArticleKey(articleKey));
    }

    /**
     * 按资料源与概念标识查询文章。
     *
     * @param sourceId 资料源主键
     * @param conceptId 概念标识
     * @return 文章记录
     */
    public Optional<ArticleRecord> findBySourceIdAndConceptId(Long sourceId, String conceptId) {
        return Optional.ofNullable(articleMapper.findBySourceIdAndConceptId(sourceId, conceptId));
    }

    /**
     * 追加一条上游纠错标记。
     *
     * @param conceptId 下游概念标识
     * @param fromConceptId 上游概念标识
     * @param correctionSummary 纠错摘要
     */
    public void appendUpstreamCorrection(String conceptId, String fromConceptId, String correctionSummary) {
        articleMapper.appendUpstreamCorrection(conceptId, fromConceptId, correctionSummary);
    }

    /**
     * 追加 source-aware 上游纠错标记。
     *
     * @param downstreamArticle 下游文章
     * @param upstreamArticle 上游文章
     * @param correctionSummary 纠错摘要
     */
    public void appendUpstreamCorrection(
            ArticleRecord downstreamArticle,
            ArticleRecord upstreamArticle,
            String correctionSummary
    ) {
        if (downstreamArticle == null || upstreamArticle == null) {
            return;
        }
        ObjectNode metadataNode = readMetadata(downstreamArticle.getMetadataJson());
        ArrayNode correctionsNode = ensureCorrectionsNode(metadataNode);
        ObjectNode correctionNode = OBJECT_MAPPER.createObjectNode();
        correctionNode.put("from", upstreamArticle.getConceptId());
        correctionNode.put("summary", correctionSummary);
        correctionNode.put("marked_at", OffsetDateTime.now().toString());
        if (upstreamArticle.getArticleKey() != null && !upstreamArticle.getArticleKey().isBlank()) {
            correctionNode.put("fromArticleKey", upstreamArticle.getArticleKey());
        }
        if (upstreamArticle.getSourceId() != null) {
            correctionNode.put("fromSourceId", upstreamArticle.getSourceId().longValue());
        }
        correctionsNode.add(correctionNode);
        upsert(downstreamArticle.copy(
                downstreamArticle.getTitle(),
                downstreamArticle.getContent(),
                downstreamArticle.getLifecycle(),
                downstreamArticle.getCompiledAt(),
                downstreamArticle.getSourcePaths(),
                metadataNode.toString(),
                downstreamArticle.getSummary(),
                downstreamArticle.getReferentialKeywords(),
                downstreamArticle.getDependsOn(),
                downstreamArticle.getRelated(),
                downstreamArticle.getConfidence(),
                downstreamArticle.getReviewStatus()
        ));
    }

    /**
     * 查询带有指定上游纠错标记的下游文章。
     *
     * @param fromConceptId 上游概念标识
     * @return 下游文章列表
     */
    public List<ArticleRecord> findWithUpstreamCorrections(String fromConceptId) {
        return articleMapper.findWithUpstreamCorrections(fromConceptId);
    }

    /**
     * 查询带有指定上游纠错标记的下游文章。
     *
     * @param upstreamArticle 上游文章
     * @return 下游文章列表
     */
    public List<ArticleRecord> findWithUpstreamCorrections(ArticleRecord upstreamArticle) {
        if (upstreamArticle == null) {
            return List.of();
        }
        List<ArticleRecord> candidates = articleMapper.findUpstreamCorrectionCandidates();
        List<ArticleRecord> matchedRecords = new ArrayList<ArticleRecord>();
        for (ArticleRecord candidate : candidates) {
            if (containsUpstreamCorrection(candidate.getMetadataJson(), upstreamArticle)) {
                matchedRecords.add(candidate);
            }
        }
        return matchedRecords;
    }

    /**
     * 清理指定下游文章中来自特定上游的纠错标记。
     *
     * @param downstreamConceptId 下游概念标识
     * @param fromConceptId 上游概念标识
     */
    public void clearUpstreamCorrection(String downstreamConceptId, String fromConceptId) {
        articleMapper.clearUpstreamCorrection(downstreamConceptId, fromConceptId);
    }

    /**
     * 清理指定下游文章中来自特定上游的 source-aware 纠错标记。
     *
     * @param downstreamArticle 下游文章
     * @param upstreamArticle 上游文章
     */
    public void clearUpstreamCorrection(ArticleRecord downstreamArticle, ArticleRecord upstreamArticle) {
        if (downstreamArticle == null || upstreamArticle == null) {
            return;
        }
        ObjectNode metadataNode = readMetadata(downstreamArticle.getMetadataJson());
        ArrayNode correctionsNode = ensureCorrectionsNode(metadataNode);
        ArrayNode filteredNode = OBJECT_MAPPER.createArrayNode();
        for (JsonNode correctionNode : correctionsNode) {
            if (matchesUpstreamCorrection(correctionNode, upstreamArticle)) {
                continue;
            }
            filteredNode.add(correctionNode);
        }
        metadataNode.set("upstream_corrections", filteredNode);
        upsert(downstreamArticle.copy(
                downstreamArticle.getTitle(),
                downstreamArticle.getContent(),
                downstreamArticle.getLifecycle(),
                downstreamArticle.getCompiledAt(),
                downstreamArticle.getSourcePaths(),
                metadataNode.toString(),
                downstreamArticle.getSummary(),
                downstreamArticle.getReferentialKeywords(),
                downstreamArticle.getDependsOn(),
                downstreamArticle.getRelated(),
                downstreamArticle.getConfidence(),
                downstreamArticle.getReviewStatus()
        ));
    }

    /**
     * 查询全部文章。
     *
     * @return 文章记录列表
     */
    public List<ArticleRecord> findAll() {
        return articleMapper.findAll();
    }

    /**
     * 批量标记热点待抽检文章。
     *
     * @param articleKeys 热点文章唯一键
     * @param riskReason 风险原因
     * @return 更新记录数
     */
    public int markHotspotPendingVerification(List<String> articleKeys, String riskReason) {
        if (articleKeys == null || articleKeys.isEmpty()) {
            return 0;
        }
        List<String> normalizedArticleKeys = normalizeKeys(articleKeys);
        if (normalizedArticleKeys.isEmpty()) {
            return 0;
        }
        String normalizedRiskReason = normalizeRiskReason(riskReason);
        return articleMapper.markHotspotPendingVerification(normalizedArticleKeys, normalizedRiskReason);
    }

    /**
     * 清空全部文章与级联受管数据。
     */
    public void deleteAll() {
        articleMapper.deleteAll();
    }

    /**
     * 构建文章检索文本。
     *
     * @param articleRecord 文章记录
     * @return 检索文本
     */
    private String buildArticleSearchText(ArticleRecord articleRecord) {
        return String.join(
                " ",
                safeText(articleRecord.getTitle()),
                safeText(articleRecord.getSummary()),
                safeText(articleRecord.getContent()),
                safeText(articleRecord.getMetadataJson())
        ).trim();
    }

    /**
     * 构建明确性关键词检索文本。
     *
     * @param articleRecord 文章记录
     * @return 明确性关键词检索文本
     */
    private String buildRefkeyText(ArticleRecord articleRecord) {
        return String.join(
                " ",
                safeText(articleRecord.getConceptId()),
                safeText(articleRecord.getTitle()),
                String.join(" ", articleRecord.getReferentialKeywords())
        ).trim();
    }

    /**
     * 返回非空文本。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * 归一文章唯一键列表。
     *
     * @param articleKeys 原始文章唯一键
     * @return 去重后的文章唯一键
     */
    private List<String> normalizeKeys(List<String> articleKeys) {
        LinkedHashSet<String> normalizedKeys = new LinkedHashSet<String>();
        for (String articleKey : articleKeys) {
            if (articleKey == null || articleKey.isBlank()) {
                continue;
            }
            normalizedKeys.add(articleKey.trim());
        }
        return new ArrayList<String>(normalizedKeys);
    }

    /**
     * 归一风险原因。
     *
     * @param riskReason 原始风险原因
     * @return 风险原因
     */
    private String normalizeRiskReason(String riskReason) {
        if (riskReason == null || riskReason.isBlank()) {
            return "hotspot_unverified";
        }
        return riskReason.trim();
    }

    private ObjectNode readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            if (rootNode instanceof ObjectNode objectNode) {
                return objectNode;
            }
        }
        catch (Exception ignored) {
            // 回退为空对象
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private ArrayNode ensureCorrectionsNode(ObjectNode metadataNode) {
        JsonNode correctionsNode = metadataNode.path("upstream_corrections");
        if (correctionsNode instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        metadataNode.set("upstream_corrections", arrayNode);
        return arrayNode;
    }

    private boolean containsUpstreamCorrection(String metadataJson, ArticleRecord upstreamArticle) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return false;
        }
        try {
            JsonNode correctionsNode = OBJECT_MAPPER.readTree(metadataJson).path("upstream_corrections");
            if (!correctionsNode.isArray()) {
                return false;
            }
            for (JsonNode correctionNode : correctionsNode) {
                if (matchesUpstreamCorrection(correctionNode, upstreamArticle)) {
                    return true;
                }
            }
            return false;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesUpstreamCorrection(JsonNode correctionNode, ArticleRecord upstreamArticle) {
        String fromArticleKey = correctionNode.path("fromArticleKey").asText("");
        if (!fromArticleKey.isBlank()
                && upstreamArticle.getArticleKey() != null
                && upstreamArticle.getArticleKey().equals(fromArticleKey)) {
            return true;
        }
        String fromConceptId = correctionNode.path("from").asText("");
        if (!upstreamArticle.getConceptId().equals(fromConceptId)) {
            return false;
        }
        JsonNode fromSourceIdNode = correctionNode.get("fromSourceId");
        if (fromSourceIdNode == null || fromSourceIdNode.isNull() || upstreamArticle.getSourceId() == null) {
            return true;
        }
        return fromSourceIdNode.asLong() == upstreamArticle.getSourceId().longValue();
    }
}
