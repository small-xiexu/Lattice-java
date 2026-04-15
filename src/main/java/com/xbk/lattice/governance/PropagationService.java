package com.xbk.lattice.governance;

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

    /**
     * 创建传播服务。
     *
     * @param dependencyGraphService 依赖图服务
     */
    public PropagationService(DependencyGraphService dependencyGraphService) {
        this.dependencyGraphService = dependencyGraphService;
    }

    /**
     * 计算某个概念纠错后的下游影响范围。
     *
     * @param rootConceptId 根概念标识
     * @param correctionSummary 纠错摘要
     * @return 传播报告
     */
    public PropagationReport propagate(String rootConceptId, String correctionSummary) {
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
