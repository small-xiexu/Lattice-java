package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
public class PropagationService {

    private final DependencyGraphService dependencyGraphService;

    private final ArticleJdbcRepository articleJdbcRepository;

    /**
     * 创建传播服务。
     *
     * @param dependencyGraphService 依赖图服务
     * @param articleJdbcRepository 文章仓储
     */
    @Autowired
    public PropagationService(
            DependencyGraphService dependencyGraphService,
            ArticleJdbcRepository articleJdbcRepository
    ) {
        this.dependencyGraphService = dependencyGraphService;
        this.articleJdbcRepository = articleJdbcRepository;
    }

    /**
     * 创建仅分析影响范围的传播服务。
     *
     * @param dependencyGraphService 依赖图服务
     */
    public PropagationService(DependencyGraphService dependencyGraphService) {
        this(dependencyGraphService, null);
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
        DependencyGraphSnapshot snapshot = dependencyGraphService.snapshot();
        Map<String, List<DependencyGraphEdge>> downstreamAdjacency = buildAdjacency(snapshot.getEdges());
        Queue<PropagationNode> queue = new ArrayDeque<PropagationNode>();
        Set<String> visited = new LinkedHashSet<String>();
        List<PropagationItem> items = new ArrayList<PropagationItem>();

        visited.add(rootConceptId);
        queue.add(new PropagationNode(rootConceptId, 0));

        while (!queue.isEmpty()) {
            PropagationNode current = queue.poll();
            for (DependencyGraphEdge edge : downstreamAdjacency.getOrDefault(current.conceptId, List.of())) {
                if (!visited.add(edge.getDownstreamConceptId())) {
                    continue;
                }
                int depth = current.depth + 1;
                items.add(new PropagationItem(
                        edge.getDownstreamConceptId(),
                        snapshot.findTitle(edge.getDownstreamConceptId()),
                        depth,
                        List.of(edge.getRelationType())
                ));
                queue.add(new PropagationNode(edge.getDownstreamConceptId(), depth));
            }
        }
        return new PropagationReport(rootConceptId, correctionSummary, items);
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
        Set<String> uniqueDownstreamIds = new LinkedHashSet<String>(downstreamIds);
        for (String downstreamId : uniqueDownstreamIds) {
            if (downstreamId == null || downstreamId.isBlank()) {
                continue;
            }
            articleJdbcRepository.appendUpstreamCorrection(downstreamId, rootConceptId, correctionSummary);
        }
    }

    private Map<String, List<DependencyGraphEdge>> buildAdjacency(List<DependencyGraphEdge> edges) {
        Map<String, List<DependencyGraphEdge>> adjacency = new LinkedHashMap<String, List<DependencyGraphEdge>>();
        for (DependencyGraphEdge edge : edges) {
            adjacency.computeIfAbsent(edge.getUpstreamConceptId(), ignored -> new ArrayList<DependencyGraphEdge>())
                    .add(edge);
        }
        return adjacency;
    }

    private static class PropagationNode {

        private final String conceptId;

        private final int depth;

        private PropagationNode(String conceptId, int depth) {
            this.conceptId = conceptId;
            this.depth = depth;
        }
    }
}
