package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.model.MergedConcept;
import com.xbk.lattice.compiler.service.ArticleCompileSupport;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 编译新文章节点
 *
 * 职责：为待编译概念生成新的文章草稿并并入当前工作集
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CompileNewArticlesNode extends AbstractCompileGraphNode {

    private final ArticleCompileSupport articleCompileSupport;

    /**
     * 创建编译新文章节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articleCompileSupport 文章编译支撑服务
     */
    public CompileNewArticlesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticleCompileSupport articleCompileSupport
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.articleCompileSupport = articleCompileSupport;
    }

    /**
     * 生成新文章草稿。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<ArticleRecord> currentDrafts = loadDraftArticles(state.getDraftArticlesRef());
        List<MergedConcept> conceptsToCompile = resolveConceptsToCompile(state);
        if (!conceptsToCompile.isEmpty()) {
            currentDrafts.addAll(articleCompileSupport.compileDraftArticles(
                    conceptsToCompile,
                    Path.of(state.getSourceDir()),
                    state.getJobId(),
                    ExecutionLlmSnapshotService.COMPILE_SCENE
            ));
        }
        state.setDraftArticlesRef(workingSetStore().saveDraftArticles(state.getJobId(), currentDrafts));
        return delta(state);
    }
}
