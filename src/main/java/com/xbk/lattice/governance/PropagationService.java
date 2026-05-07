package com.xbk.lattice.governance;

import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 传播服务
 *
 * 职责：根据依赖图计算某次纠错的 downstream 影响范围
 *
 * @author xiexu
 */
@Service
public class PropagationService {

    private final DependencyGraphService dependencyGraphService;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    /**
     * 创建传播服务。
     *
     * @param dependencyGraphService 依赖图服务
     * @param articleJdbcRepository 文章仓储
     */
    @Autowired
    public PropagationService(
            DependencyGraphService dependencyGraphService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver
    ) {
        this.dependencyGraphService = dependencyGraphService;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
    }

    /**
     * 创建仅分析影响范围的传播服务。
     *
     * @param dependencyGraphService 依赖图服务
     */
    public PropagationService(DependencyGraphService dependencyGraphService) {
        this(dependencyGraphService, null, null);
    }

    /**
     * 创建兼容旧构造方式的传播服务。
     *
     * @param dependencyGraphService 依赖图服务
     * @param articleJdbcRepository 文章仓储
     */
    public PropagationService(
            DependencyGraphService dependencyGraphService,
            ArticleJdbcRepository articleJdbcRepository
    ) {
        this(
                dependencyGraphService,
                articleJdbcRepository,
                articleJdbcRepository == null ? null : new ArticleIdentityResolver(articleJdbcRepository)
        );
    }

    /**
     * 计算某个概念纠错后的下游影响范围。
     *
     * @param rootConceptId 根概念标识
     * @param correctionSummary 纠错摘要
     * @return 传播报告
     */
    public PropagationReport analyzeImpact(String rootConceptId, String correctionSummary) {
        if (dependencyGraphService == null) {
            return new PropagationReport(rootConceptId, correctionSummary, List.of());
        }
        ArticleRecord rootArticle = resolveRootArticle(rootConceptId);
        DependencyGraphSnapshot snapshot = dependencyGraphService.snapshot();
        Map<String, List<DependencyGraphEdge>> downstreamAdjacency = buildAdjacency(snapshot.getEdges());
        Queue<PropagationNode> queue = new ArrayDeque<PropagationNode>();
        Set<String> visited = new LinkedHashSet<String>();
        List<PropagationItem> items = new ArrayList<PropagationItem>();

        String rootNodeId = rootArticle == null ? rootConceptId : resolveNodeId(rootArticle);
        visited.add(rootNodeId);
        queue.add(new PropagationNode(rootNodeId, 0));

        while (!queue.isEmpty()) {
            PropagationNode current = queue.poll();
            for (DependencyGraphEdge edge : downstreamAdjacency.getOrDefault(current.nodeId, List.of())) {
                if (!visited.add(edge.getDownstreamNodeId())) {
                    continue;
                }
                int depth = current.depth + 1;
                items.add(new PropagationItem(
                        snapshot.findSourceId(edge.getDownstreamNodeId()),
                        snapshot.findArticleKey(edge.getDownstreamNodeId()),
                        snapshot.findConceptId(edge.getDownstreamNodeId()),
                        snapshot.findTitle(edge.getDownstreamNodeId()),
                        depth,
                        List.of(edge.getRelationType())
                ));
                queue.add(new PropagationNode(edge.getDownstreamNodeId(), depth));
            }
        }
        if (rootArticle == null) {
            return new PropagationReport(rootConceptId, correctionSummary, items);
        }
        return new PropagationReport(rootArticle.getSourceId(), rootArticle.getArticleKey(), rootArticle.getConceptId(), correctionSummary, items);
    }

    /**
     * 兼容旧调用的传播分析入口。
     *
     * @param rootConceptId 根概念标识
     * @param correctionSummary 纠错摘要
     * @return 传播报告
     */
    public PropagationReport propagate(String rootConceptId, String correctionSummary) {
        return analyzeImpact(rootConceptId, correctionSummary);
    }

    /**
     * 为下游文章追加上游纠错标记。
     *
     * @param rootConceptId 根概念标识
     * @param correctionSummary 纠错摘要
     * @param downstreamIds 下游概念标识
     */
    public void markDownstream(String rootConceptId, String correctionSummary, List<String> downstreamIds) {
        if (articleJdbcRepository == null || downstreamIds == null || downstreamIds.isEmpty()) {
            return;
        }
        ArticleRecord rootArticle = resolveRootArticle(rootConceptId);
        if (rootArticle == null) {
            return;
        }
        Set<String> uniqueDownstreamIds = new LinkedHashSet<String>(downstreamIds);
        for (String downstreamId : uniqueDownstreamIds) {
            if (downstreamId == null || downstreamId.isBlank()) {
                continue;
            }
            ArticleRecord downstreamArticle = resolveDownstreamArticle(rootArticle, downstreamId);
            if (downstreamArticle == null) {
                continue;
            }
            articleJdbcRepository.appendUpstreamCorrection(downstreamArticle, rootArticle, correctionSummary);
        }
    }

    private Map<String, List<DependencyGraphEdge>> buildAdjacency(List<DependencyGraphEdge> edges) {
        Map<String, List<DependencyGraphEdge>> adjacency = new LinkedHashMap<String, List<DependencyGraphEdge>>();
        for (DependencyGraphEdge edge : edges) {
            adjacency.computeIfAbsent(edge.getUpstreamNodeId(), ignored -> new ArrayList<DependencyGraphEdge>())
                    .add(edge);
        }
        return adjacency;
    }

    private ArticleRecord resolveRootArticle(String rootConceptId) {
        if (articleIdentityResolver == null || rootConceptId == null || rootConceptId.isBlank()) {
            return null;
        }
        return articleIdentityResolver.resolve(rootConceptId).orElse(null);
    }

    private ArticleRecord resolveDownstreamArticle(ArticleRecord rootArticle, String downstreamId) {
        if (articleIdentityResolver == null) {
            return null;
        }
        ArticleRecord articleRecord = articleIdentityResolver.resolve(downstreamId, rootArticle.getSourceId()).orElse(null);
        if (articleRecord != null) {
            return articleRecord;
        }
        return articleIdentityResolver.resolve(downstreamId).orElse(null);
    }

    private String resolveNodeId(ArticleRecord articleRecord) {
        if (articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
            return articleRecord.getArticleKey();
        }
        return articleRecord.getConceptId();
    }

    private static class PropagationNode {

        private final String nodeId;

        private final int depth;

        private PropagationNode(String nodeId, int depth) {
            this.nodeId = nodeId;
            this.depth = depth;
        }
    }
}
