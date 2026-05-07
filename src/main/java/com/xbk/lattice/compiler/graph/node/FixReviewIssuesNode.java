package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.graph.ReviewPartition;
import com.xbk.lattice.compiler.service.ArticleCompileSupport;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 修复审查问题节点
 *
 * 职责：仅对当前待修复子集执行自动修复并更新修复轮次
 *
 * @author xiexu
 */
@Component
public class FixReviewIssuesNode extends AbstractCompileGraphNode {

    private final ArticleCompileSupport articleCompileSupport;

    /**
     * 创建修复审查问题节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articleCompileSupport 文章编译支撑服务
     */
    public FixReviewIssuesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticleCompileSupport articleCompileSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articleCompileSupport = articleCompileSupport;
    }

    /**
     * 执行自动修复。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        ReviewPartition reviewPartition = loadReviewPartition(state.getReviewPartitionRef());
        List<ArticleReviewEnvelope> reviewedArticles = articleCompileSupport.fixReviewedArticles(
                reviewPartition.getFixable(),
                state.getJobId(),
                ExecutionLlmSnapshotService.COMPILE_SCENE
        );
        state.setFixAttemptCount(state.getFixAttemptCount() + 1);
        state.setReviewedArticlesRef(workingSetStore().saveReviewedArticles(state.getJobId(), reviewedArticles));
        state.setPendingReviewCount(reviewedArticles.size());
        return delta(state);
    }
}
