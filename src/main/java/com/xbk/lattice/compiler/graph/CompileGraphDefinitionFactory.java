package com.xbk.lattice.compiler.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.xbk.lattice.compiler.graph.node.AnalyzeBatchesNode;
import com.xbk.lattice.compiler.graph.node.CaptureRepoSnapshotNode;
import com.xbk.lattice.compiler.graph.node.CompileNewArticlesNode;
import com.xbk.lattice.compiler.graph.node.EnhanceExistingArticlesNode;
import com.xbk.lattice.compiler.graph.node.ExtractAstGraphNode;
import com.xbk.lattice.compiler.graph.node.FinalizeJobNode;
import com.xbk.lattice.compiler.graph.node.FixReviewIssuesNode;
import com.xbk.lattice.compiler.graph.node.GenerateSynthesisArtifactsNode;
import com.xbk.lattice.compiler.graph.node.GroupSourcesNode;
import com.xbk.lattice.compiler.graph.node.IngestSourcesNode;
import com.xbk.lattice.compiler.graph.node.InitializeJobNode;
import com.xbk.lattice.compiler.graph.node.MergeConceptsNode;
import com.xbk.lattice.compiler.graph.node.PersistArticlesNode;
import com.xbk.lattice.compiler.graph.node.PersistSourceFileChunksNode;
import com.xbk.lattice.compiler.graph.node.PersistSourceFilesNode;
import com.xbk.lattice.compiler.graph.node.PlanChangesNode;
import com.xbk.lattice.compiler.graph.node.RebuildArticleChunksNode;
import com.xbk.lattice.compiler.graph.node.RefreshVectorIndexNode;
import com.xbk.lattice.compiler.graph.node.ReviewArticlesNode;
import com.xbk.lattice.compiler.graph.node.SplitBatchesNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 编译图定义工厂
 *
 * 职责：集中注册编译图节点、顺序边与条件边
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CompileGraphDefinitionFactory {

    private final InitializeJobNode initializeJobNode;

    private final IngestSourcesNode ingestSourcesNode;

    private final PersistSourceFilesNode persistSourceFilesNode;

    private final PersistSourceFileChunksNode persistSourceFileChunksNode;

    private final GroupSourcesNode groupSourcesNode;

    private final ExtractAstGraphNode extractAstGraphNode;

    private final SplitBatchesNode splitBatchesNode;

    private final AnalyzeBatchesNode analyzeBatchesNode;

    private final MergeConceptsNode mergeConceptsNode;

    private final PlanChangesNode planChangesNode;

    private final EnhanceExistingArticlesNode enhanceExistingArticlesNode;

    private final CompileNewArticlesNode compileNewArticlesNode;

    private final ReviewArticlesNode reviewArticlesNode;

    private final FixReviewIssuesNode fixReviewIssuesNode;

    private final PersistArticlesNode persistArticlesNode;

    private final RebuildArticleChunksNode rebuildArticleChunksNode;

    private final RefreshVectorIndexNode refreshVectorIndexNode;

    private final GenerateSynthesisArtifactsNode generateSynthesisArtifactsNode;

    private final CaptureRepoSnapshotNode captureRepoSnapshotNode;

    private final FinalizeJobNode finalizeJobNode;

    private final CompileGraphConditions compileGraphConditions;

    private final CompileGraphStateMapper compileGraphStateMapper;

    private final CompileWorkingSetStore compileWorkingSetStore;

    private final ReviewDecisionPolicy reviewDecisionPolicy;

    /**
     * 创建编译图定义工厂。
     *
     * @param initializeJobNode 初始化作业节点
     * @param ingestSourcesNode 摄入源文件节点
     * @param persistSourceFilesNode 落盘源文件节点
     * @param persistSourceFileChunksNode 落盘源文件分块节点
     * @param groupSourcesNode 源文件分组节点
     * @param extractAstGraphNode AST 图谱抽取节点
     * @param splitBatchesNode 批次切分节点
     * @param analyzeBatchesNode 批次分析节点
     * @param mergeConceptsNode 概念归并节点
     * @param planChangesNode 增量规划节点
     * @param enhanceExistingArticlesNode 增强既有文章节点
     * @param compileNewArticlesNode 编译新文章节点
     * @param reviewArticlesNode 审查文章节点
     * @param fixReviewIssuesNode 修复审查问题节点
     * @param persistArticlesNode 落库文章节点
     * @param rebuildArticleChunksNode 重建文章分块节点
     * @param refreshVectorIndexNode 刷新向量索引节点
     * @param generateSynthesisArtifactsNode 生成合成产物节点
     * @param captureRepoSnapshotNode 捕获仓库快照节点
     * @param finalizeJobNode 结束作业节点
     * @param compileGraphConditions 编译图条件路由
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param reviewDecisionPolicy 审查决策策略
     */
    public CompileGraphDefinitionFactory(
            InitializeJobNode initializeJobNode,
            IngestSourcesNode ingestSourcesNode,
            PersistSourceFilesNode persistSourceFilesNode,
            PersistSourceFileChunksNode persistSourceFileChunksNode,
            GroupSourcesNode groupSourcesNode,
            ExtractAstGraphNode extractAstGraphNode,
            SplitBatchesNode splitBatchesNode,
            AnalyzeBatchesNode analyzeBatchesNode,
            MergeConceptsNode mergeConceptsNode,
            PlanChangesNode planChangesNode,
            EnhanceExistingArticlesNode enhanceExistingArticlesNode,
            CompileNewArticlesNode compileNewArticlesNode,
            ReviewArticlesNode reviewArticlesNode,
            FixReviewIssuesNode fixReviewIssuesNode,
            PersistArticlesNode persistArticlesNode,
            RebuildArticleChunksNode rebuildArticleChunksNode,
            RefreshVectorIndexNode refreshVectorIndexNode,
            GenerateSynthesisArtifactsNode generateSynthesisArtifactsNode,
            CaptureRepoSnapshotNode captureRepoSnapshotNode,
            FinalizeJobNode finalizeJobNode,
            CompileGraphConditions compileGraphConditions,
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ReviewDecisionPolicy reviewDecisionPolicy
    ) {
        this.initializeJobNode = initializeJobNode;
        this.ingestSourcesNode = ingestSourcesNode;
        this.persistSourceFilesNode = persistSourceFilesNode;
        this.persistSourceFileChunksNode = persistSourceFileChunksNode;
        this.groupSourcesNode = groupSourcesNode;
        this.extractAstGraphNode = extractAstGraphNode;
        this.splitBatchesNode = splitBatchesNode;
        this.analyzeBatchesNode = analyzeBatchesNode;
        this.mergeConceptsNode = mergeConceptsNode;
        this.planChangesNode = planChangesNode;
        this.enhanceExistingArticlesNode = enhanceExistingArticlesNode;
        this.compileNewArticlesNode = compileNewArticlesNode;
        this.reviewArticlesNode = reviewArticlesNode;
        this.fixReviewIssuesNode = fixReviewIssuesNode;
        this.persistArticlesNode = persistArticlesNode;
        this.rebuildArticleChunksNode = rebuildArticleChunksNode;
        this.refreshVectorIndexNode = refreshVectorIndexNode;
        this.generateSynthesisArtifactsNode = generateSynthesisArtifactsNode;
        this.captureRepoSnapshotNode = captureRepoSnapshotNode;
        this.finalizeJobNode = finalizeJobNode;
        this.compileGraphConditions = compileGraphConditions;
        this.compileGraphStateMapper = compileGraphStateMapper;
        this.compileWorkingSetStore = compileWorkingSetStore;
        this.reviewDecisionPolicy = reviewDecisionPolicy;
    }

    /**
     * 构建编译图定义。
     *
     * @return StateGraph 定义
     * @throws Exception 构建异常
     */
    public StateGraph build() throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("initialize_job", AsyncNodeAction.node_async(initializeJobNode::execute));
        stateGraph.addNode("ingest_sources", AsyncNodeAction.node_async(ingestSourcesNode::execute));
        stateGraph.addNode("persist_source_files", AsyncNodeAction.node_async(persistSourceFilesNode::execute));
        stateGraph.addNode("persist_source_file_chunks", AsyncNodeAction.node_async(persistSourceFileChunksNode::execute));
        stateGraph.addNode("extract_ast_graph", AsyncNodeAction.node_async(extractAstGraphNode::execute));
        stateGraph.addNode("group_sources", AsyncNodeAction.node_async(groupSourcesNode::execute));
        stateGraph.addNode("split_batches", AsyncNodeAction.node_async(splitBatchesNode::execute));
        stateGraph.addNode("analyze_batches", AsyncNodeAction.node_async(analyzeBatchesNode::execute));
        stateGraph.addNode("merge_concepts", AsyncNodeAction.node_async(mergeConceptsNode::execute));
        stateGraph.addNode("plan_changes", AsyncNodeAction.node_async(planChangesNode::execute));
        stateGraph.addNode("enhance_existing_articles", AsyncNodeAction.node_async(enhanceExistingArticlesNode::execute));
        stateGraph.addNode("compile_new_articles", AsyncNodeAction.node_async(compileNewArticlesNode::execute));
        stateGraph.addNode("review_articles", AsyncNodeAction.node_async(reviewArticlesNode::execute));
        stateGraph.addNode("fix_review_issues", AsyncNodeAction.node_async(fixReviewIssuesNode::execute));
        stateGraph.addNode("persist_articles", AsyncNodeAction.node_async(persistArticlesNode::execute));
        stateGraph.addNode("rebuild_article_chunks", AsyncNodeAction.node_async(rebuildArticleChunksNode::execute));
        stateGraph.addNode("refresh_vector_index", AsyncNodeAction.node_async(refreshVectorIndexNode::execute));
        stateGraph.addNode("generate_synthesis_artifacts", AsyncNodeAction.node_async(generateSynthesisArtifactsNode::execute));
        stateGraph.addNode("capture_repo_snapshot", AsyncNodeAction.node_async(captureRepoSnapshotNode::execute));
        stateGraph.addNode("finalize_job", AsyncNodeAction.node_async(finalizeJobNode::execute));

        stateGraph.addEdge(StateGraph.START, "initialize_job");
        stateGraph.addEdge("initialize_job", "ingest_sources");
        stateGraph.addEdge("ingest_sources", "persist_source_files");
        stateGraph.addEdge("persist_source_files", "persist_source_file_chunks");
        stateGraph.addEdge("persist_source_file_chunks", "extract_ast_graph");
        stateGraph.addEdge("extract_ast_graph", "group_sources");
        stateGraph.addEdge("group_sources", "split_batches");
        stateGraph.addEdge("split_batches", "analyze_batches");
        stateGraph.addEdge("analyze_batches", "merge_concepts");
        stateGraph.addConditionalEdges(
                "merge_concepts",
                AsyncEdgeAction.edge_async(state -> compileGraphConditions.routeAfterMerge(
                        compileGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "plan_changes", "plan_changes",
                        "compile_new_articles", "compile_new_articles"
                )
        );
        stateGraph.addConditionalEdges(
                "plan_changes",
                AsyncEdgeAction.edge_async(state -> compileGraphConditions.routeAfterPlanChanges(
                        compileGraphStateMapper.fromMap(state.data())
                )),
                Map.of(
                        "finalize_job", "finalize_job",
                        "enhance_existing_articles", "enhance_existing_articles",
                        "compile_new_articles", "compile_new_articles"
                )
        );
        stateGraph.addEdge("enhance_existing_articles", "compile_new_articles");
        stateGraph.addEdge("compile_new_articles", "review_articles");
        stateGraph.addConditionalEdges(
                "review_articles",
                AsyncEdgeAction.edge_async(state -> reviewDecisionPolicy.decide(
                        compileGraphStateMapper.fromMap(state.data()),
                        reviewPartition(state.data())
                )),
                Map.of(
                        "persist_articles", "persist_articles",
                        "fix_review_issues", "fix_review_issues"
                )
        );
        stateGraph.addEdge("fix_review_issues", "review_articles");
        stateGraph.addEdge("persist_articles", "rebuild_article_chunks");
        stateGraph.addEdge("rebuild_article_chunks", "refresh_vector_index");
        stateGraph.addEdge("refresh_vector_index", "generate_synthesis_artifacts");
        stateGraph.addEdge("generate_synthesis_artifacts", "capture_repo_snapshot");
        stateGraph.addEdge("capture_repo_snapshot", "finalize_job");
        stateGraph.addEdge("finalize_job", StateGraph.END);
        return stateGraph;
    }

    /**
     * 解析当前审查分区。
     *
     * @param stateMap 图状态 Map
     * @return 审查分区
     */
    private ReviewPartition reviewPartition(Map<String, Object> stateMap) {
        CompileGraphState state = compileGraphStateMapper.fromMap(stateMap);
        if (state.getReviewPartitionRef() == null) {
            return new ReviewPartition();
        }
        return compileWorkingSetStore.loadReviewPartition(state.getReviewPartitionRef());
    }
}
