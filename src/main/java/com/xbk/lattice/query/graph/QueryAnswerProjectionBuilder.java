package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query 答案投影构建器
 *
 * 职责：把简单问答答案中的可见 citation 映射为 TOP_K 约束下的 projection 白名单
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class QueryAnswerProjectionBuilder {

    private final CitationExtractor citationExtractor;

    /**
     * 创建 Query 答案投影构建器。
     *
     * @param citationExtractor Citation 提取器
     */
    public QueryAnswerProjectionBuilder(CitationExtractor citationExtractor) {
        this.citationExtractor = citationExtractor;
    }

    /**
     * 基于最终答案与融合命中构建 projection 白名单。
     *
     * @param answerMarkdown 最终答案
     * @param fusedHits 融合命中
     * @return projection 白名单
     */
    public AnswerProjectionBundle build(String answerMarkdown, List<QueryArticleHit> fusedHits) {
        Map<String, AnswerProjection> activeProjectionByLiteral = new LinkedHashMap<String, AnswerProjection>();
        Set<String> articleTargetKeys = collectArticleTargetKeys(fusedHits);
        Set<String> sourceTargetKeys = collectSourceTargetKeys(fusedHits);
        List<ClaimSegment> claimSegments = citationExtractor.extractClaims(answerMarkdown);
        int projectionOrdinal = 1;
        for (ClaimSegment claimSegment : claimSegments) {
            for (Citation citation : claimSegment.getCitations()) {
                AnswerProjection answerProjection = toAnswerProjection(
                        citation,
                        articleTargetKeys,
                        sourceTargetKeys,
                        projectionOrdinal
                );
                if (answerProjection == null) {
                    continue;
                }
                if (!activeProjectionByLiteral.containsKey(answerProjection.getCitationLiteral())) {
                    activeProjectionByLiteral.put(answerProjection.getCitationLiteral(), answerProjection);
                    projectionOrdinal++;
                }
            }
        }
        return new AnswerProjectionBundle(
                answerMarkdown,
                new ArrayList<AnswerProjection>(activeProjectionByLiteral.values())
        );
    }

    /**
     * 将单条 citation 映射为 projection。
     *
     * @param citation 答案中的 citation
     * @param articleTargetKeys 可用文章目标键
     * @param sourceTargetKeys 可用源文件目标键
     * @param projectionOrdinal projection 顺序号
     * @return projection，无法映射时返回 null
     */
    private AnswerProjection toAnswerProjection(
            Citation citation,
            Set<String> articleTargetKeys,
            Set<String> sourceTargetKeys,
            int projectionOrdinal
    ) {
        if (citation == null
                || citation.getLiteral() == null
                || citation.getLiteral().isBlank()
                || citation.getTargetKey() == null
                || citation.getTargetKey().isBlank()) {
            return null;
        }
        if (citation.getSourceType() == CitationSourceType.ARTICLE
                && articleTargetKeys.contains(citation.getTargetKey())) {
            return new AnswerProjection(
                    projectionOrdinal,
                    buildAnchorId(ProjectionCitationFormat.ARTICLE, citation.getTargetKey()),
                    ProjectionCitationFormat.ARTICLE,
                    citation.getLiteral(),
                    citation.getTargetKey(),
                    ProjectionStatus.ACTIVE,
                    0,
                    null
            );
        }
        if (citation.getSourceType() == CitationSourceType.SOURCE_FILE
                && sourceTargetKeys.contains(citation.getTargetKey())) {
            return new AnswerProjection(
                    projectionOrdinal,
                    buildAnchorId(ProjectionCitationFormat.SOURCE_FILE, citation.getTargetKey()),
                    ProjectionCitationFormat.SOURCE_FILE,
                    citation.getLiteral(),
                    citation.getTargetKey(),
                    ProjectionStatus.ACTIVE,
                    0,
                    null
            );
        }
        return null;
    }

    /**
     * 收集可作为 ARTICLE projection 的目标键。
     *
     * @param fusedHits 融合命中
     * @return articleKey / conceptId 集合
     */
    private Set<String> collectArticleTargetKeys(List<QueryArticleHit> fusedHits) {
        Set<String> articleTargetKeys = new LinkedHashSet<String>();
        if (fusedHits == null) {
            return articleTargetKeys;
        }
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit == null || fusedHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
                continue;
            }
            addIfPresent(articleTargetKeys, fusedHit.getArticleKey());
            addIfPresent(articleTargetKeys, fusedHit.getConceptId());
        }
        return articleTargetKeys;
    }

    /**
     * 收集可作为 SOURCE_FILE projection 的目标键。
     *
     * @param fusedHits 融合命中
     * @return 源文件路径集合
     */
    private Set<String> collectSourceTargetKeys(List<QueryArticleHit> fusedHits) {
        Set<String> sourceTargetKeys = new LinkedHashSet<String>();
        if (fusedHits == null) {
            return sourceTargetKeys;
        }
        for (QueryArticleHit fusedHit : fusedHits) {
            if (fusedHit == null || fusedHit.getSourcePaths() == null) {
                continue;
            }
            for (String sourcePath : fusedHit.getSourcePaths()) {
                addIfPresent(sourceTargetKeys, normalizeSourcePath(sourcePath));
            }
        }
        return sourceTargetKeys;
    }

    /**
     * 在值非空时加入集合。
     *
     * @param values 目标集合
     * @param value 候选值
     */
    private void addIfPresent(Set<String> values, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        values.add(value.trim());
    }

    /**
     * 归一化源文件路径。
     *
     * @param sourcePath 原始路径
     * @return 去除行号描述后的路径
     */
    private String normalizeSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalizedPath = sourcePath.trim();
        if (normalizedPath.startsWith("[") && normalizedPath.endsWith("]")) {
            normalizedPath = normalizedPath.substring(1, normalizedPath.length() - 1).trim();
        }
        int commaIndex = normalizedPath.indexOf(',');
        if (commaIndex > 0) {
            return normalizedPath.substring(0, commaIndex).trim();
        }
        return normalizedPath;
    }

    /**
     * 构造 Query TOP_K projection 的内部 anchorId。
     *
     * @param projectionCitationFormat projection 格式
     * @param targetKey 目标键
     * @return anchorId
     */
    private String buildAnchorId(ProjectionCitationFormat projectionCitationFormat, String targetKey) {
        return "query-top-k:" + projectionCitationFormat.name() + ":" + targetKey;
    }
}
