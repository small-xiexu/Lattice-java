package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依赖图服务
 *
 * 职责：从文章 frontmatter 与正文 wiki-links 构建传播依赖图
 *
 * @author xiexu
 */
@Service
public class DependencyGraphService {

    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建依赖图服务。
     *
     * @param articleJdbcRepository 文章仓储
     */
    public DependencyGraphService(ArticleJdbcRepository articleJdbcRepository) {
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 构建当前依赖图快照。
     *
     * @return 依赖图快照
     */
    public DependencyGraphSnapshot snapshot() {
        if (articleJdbcRepository == null) {
            return new DependencyGraphSnapshot(List.of(), Map.of());
        }
        List<ArticleRecord> articles = articleJdbcRepository.findAll();
        Map<String, String> titlesByNodeId = new LinkedHashMap<String, String>();
        Map<String, Long> sourceIdsByNodeId = new LinkedHashMap<String, Long>();
        Map<String, String> articleKeysByNodeId = new LinkedHashMap<String, String>();
        Map<String, String> conceptIdsByNodeId = new LinkedHashMap<String, String>();
        Map<String, Map<String, ArticleRecord>> scopedTargets = new LinkedHashMap<String, Map<String, ArticleRecord>>();
        for (ArticleRecord article : articles) {
            String nodeId = resolveNodeId(article);
            titlesByNodeId.put(nodeId, article.getTitle());
            sourceIdsByNodeId.put(nodeId, article.getSourceId());
            articleKeysByNodeId.put(nodeId, article.getArticleKey());
            conceptIdsByNodeId.put(nodeId, article.getConceptId());
            indexScopedTargets(scopedTargets, article);
        }

        Map<String, DependencyGraphEdge> uniqueEdges = new LinkedHashMap<String, DependencyGraphEdge>();
        for (ArticleRecord article : articles) {
            addDependsOnEdges(article, scopedTargets, uniqueEdges);
            addRelatedEdges(article, scopedTargets, uniqueEdges);
            addWikiLinkEdges(article, scopedTargets, uniqueEdges);
        }
        return new DependencyGraphSnapshot(
                new ArrayList<DependencyGraphEdge>(uniqueEdges.values()),
                titlesByNodeId,
                sourceIdsByNodeId,
                articleKeysByNodeId,
                conceptIdsByNodeId
        );
    }

    private void addDependsOnEdges(
            ArticleRecord article,
            Map<String, Map<String, ArticleRecord>> scopedTargets,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        for (String upstream : article.getDependsOn()) {
            addEdge(upstream, article, "depends_on", scopedTargets, uniqueEdges);
        }
    }

    private void addRelatedEdges(
            ArticleRecord article,
            Map<String, Map<String, ArticleRecord>> scopedTargets,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        for (String relatedConceptId : article.getRelated()) {
            addEdge(relatedConceptId, article, "related", scopedTargets, uniqueEdges);
            ArticleRecord relatedArticle = resolveTargetArticle(article, relatedConceptId, scopedTargets);
            if (relatedArticle != null) {
                addEdge(resolveNodeId(article), article, relatedArticle, "related", uniqueEdges);
            }
        }
    }

    private void addWikiLinkEdges(
            ArticleRecord article,
            Map<String, Map<String, ArticleRecord>> scopedTargets,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        Matcher matcher = WIKI_LINK_PATTERN.matcher(article.getContent() == null ? "" : article.getContent());
        while (matcher.find()) {
            String linkedConceptId = normalizeWikiLinkTarget(matcher.group(1));
            addEdge(linkedConceptId, article, "wiki_link", scopedTargets, uniqueEdges);
        }
    }

    private String normalizeWikiLinkTarget(String rawTarget) {
        if (rawTarget == null) {
            return "";
        }
        String normalized = rawTarget.trim();
        int pipeIndex = normalized.indexOf('|');
        if (pipeIndex >= 0) {
            normalized = normalized.substring(0, pipeIndex);
        }
        int headingIndex = normalized.indexOf('#');
        if (headingIndex >= 0) {
            normalized = normalized.substring(0, headingIndex);
        }
        return normalized.trim();
    }

    private void addEdge(
            String upstreamReference,
            ArticleRecord downstreamArticle,
            String relationType,
            Map<String, Map<String, ArticleRecord>> scopedTargets,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        if (upstreamReference == null || upstreamReference.isBlank()) {
            return;
        }
        ArticleRecord upstreamArticle = resolveTargetArticle(downstreamArticle, upstreamReference, scopedTargets);
        if (upstreamArticle == null) {
            return;
        }
        addEdge(resolveNodeId(upstreamArticle), upstreamArticle, downstreamArticle, relationType, uniqueEdges);
    }

    private void addEdge(
            String upstreamNodeId,
            ArticleRecord upstreamArticle,
            ArticleRecord downstreamArticle,
            String relationType,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        String downstreamNodeId = resolveNodeId(downstreamArticle);
        if (upstreamNodeId.equals(downstreamNodeId)) {
            return;
        }
        String key = upstreamNodeId + "->" + downstreamNodeId + ":" + relationType;
        uniqueEdges.put(key, new DependencyGraphEdge(
                upstreamNodeId,
                downstreamNodeId,
                upstreamArticle.getSourceId(),
                downstreamArticle.getSourceId(),
                upstreamArticle.getArticleKey(),
                downstreamArticle.getArticleKey(),
                upstreamArticle.getConceptId(),
                downstreamArticle.getConceptId(),
                relationType
        ));
    }

    private void indexScopedTargets(
            Map<String, Map<String, ArticleRecord>> scopedTargets,
            ArticleRecord article
    ) {
        String scopeKey = resolveScopeKey(article.getSourceId());
        Map<String, ArticleRecord> scopedMap = scopedTargets.computeIfAbsent(
                scopeKey,
                ignored -> new LinkedHashMap<String, ArticleRecord>()
        );
        scopedMap.put(article.getConceptId(), article);
        if (article.getArticleKey() != null && !article.getArticleKey().isBlank()) {
            scopedMap.put(article.getArticleKey(), article);
        }
    }

    private ArticleRecord resolveTargetArticle(
            ArticleRecord article,
            String targetReference,
            Map<String, Map<String, ArticleRecord>> scopedTargets
    ) {
        Map<String, ArticleRecord> scopedMap = scopedTargets.get(resolveScopeKey(article.getSourceId()));
        if (scopedMap == null) {
            return null;
        }
        return scopedMap.get(targetReference);
    }

    private String resolveNodeId(ArticleRecord article) {
        if (article.getArticleKey() != null && !article.getArticleKey().isBlank()) {
            return article.getArticleKey();
        }
        return article.getConceptId();
    }

    private String resolveScopeKey(Long sourceId) {
        return sourceId == null ? "__null__" : String.valueOf(sourceId.longValue());
    }
}
