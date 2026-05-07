package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 增强既有文章节点
 *
 * 职责：为增量编译场景生成既有文章的增强草稿
 *
 * @author xiexu
 */
@Component
public class EnhanceExistingArticlesNode extends AbstractCompileGraphNode {

    private final SourceIngestSupport sourceIngestSupport;

    /**
     * 创建增强既有文章节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param sourceIngestSupport 源文件摄入支撑服务
     */
    public EnhanceExistingArticlesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            SourceIngestSupport sourceIngestSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.sourceIngestSupport = sourceIngestSupport;
    }

    /**
     * 生成增强文章草稿。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<ArticleRecord> enhancedDrafts = sourceIngestSupport.enhanceExistingArticles(
                workingSetStore().loadEnhancementConcepts(state.getEnhancementConceptsRef())
        );
        state.setDraftArticlesRef(workingSetStore().saveDraftArticles(state.getJobId(), enhancedDrafts));
        return delta(state);
    }
}
