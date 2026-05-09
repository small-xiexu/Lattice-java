package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.compiler.service.ArticlePersistSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 重建文章分块节点
 *
 * 职责：保留编译图恢复边界；文章 chunk 已由 ArticleAtomicWriteService 随文章落库原子写入
 *
 * @author xiexu
 */
@Component
public class RebuildArticleChunksNode extends AbstractCompileGraphNode {

    /**
     * 创建重建文章分块节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     */
    @Autowired
    public RebuildArticleChunksNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
    }

    /**
     * 创建测试替身可注入的重建文章分块节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param articlePersistSupport 文章落库支撑服务
     */
    public RebuildArticleChunksNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            ArticlePersistSupport articlePersistSupport
    ) {
        this(compileGraphStateMapper, compileWorkingSetStore);
    }

    /**
     * 确认文章分块已随文章原子写入完成。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        return delta(state);
    }
}
