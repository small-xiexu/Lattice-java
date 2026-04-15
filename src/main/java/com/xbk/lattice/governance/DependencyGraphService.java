package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@Profile("jdbc")
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
        Map<String, String> conceptTitles = new LinkedHashMap<String, String>();
        Set<String> existingConceptIds = new LinkedHashSet<String>();
        for (ArticleRecord article : articles) {
            conceptTitles.put(article.getConceptId(), article.getTitle());
            existingConceptIds.add(article.getConceptId());
        }

        Map<String, DependencyGraphEdge> uniqueEdges = new LinkedHashMap<String, DependencyGraphEdge>();
        for (ArticleRecord article : articles) {
            addDependsOnEdges(article, existingConceptIds, uniqueEdges);
            addRelatedEdges(article, existingConceptIds, uniqueEdges);
            addWikiLinkEdges(article, existingConceptIds, uniqueEdges);
        }
        return new DependencyGraphSnapshot(new ArrayList<DependencyGraphEdge>(uniqueEdges.values()), conceptTitles);
    }

    private void addDependsOnEdges(
            ArticleRecord article,
            Set<String> existingConceptIds,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        for (String upstream : article.getDependsOn()) {
            addEdge(upstream, article.getConceptId(), "depends_on", existingConceptIds, uniqueEdges);
        }
    }

    private void addRelatedEdges(
            ArticleRecord article,
            Set<String> existingConceptIds,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        for (String relatedConceptId : article.getRelated()) {
            addEdge(relatedConceptId, article.getConceptId(), "related", existingConceptIds, uniqueEdges);
            addEdge(article.getConceptId(), relatedConceptId, "related", existingConceptIds, uniqueEdges);
        }
    }

    private void addWikiLinkEdges(
            ArticleRecord article,
            Set<String> existingConceptIds,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        Matcher matcher = WIKI_LINK_PATTERN.matcher(article.getContent());
        while (matcher.find()) {
            String linkedConceptId = normalizeWikiLinkTarget(matcher.group(1));
            addEdge(linkedConceptId, article.getConceptId(), "wiki_link", existingConceptIds, uniqueEdges);
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
            String upstreamConceptId,
            String downstreamConceptId,
            String relationType,
            Set<String> existingConceptIds,
            Map<String, DependencyGraphEdge> uniqueEdges
    ) {
        if (upstreamConceptId == null || upstreamConceptId.isBlank()) {
            return;
        }
        if (!existingConceptIds.contains(upstreamConceptId)) {
            return;
        }
        if (upstreamConceptId.equals(downstreamConceptId)) {
            return;
        }
        String key = upstreamConceptId + "->" + downstreamConceptId + ":" + relationType;
        uniqueEdges.put(key, new DependencyGraphEdge(upstreamConceptId, downstreamConceptId, relationType));
    }
}
