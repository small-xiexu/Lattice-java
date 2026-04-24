package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.ast.domain.AstSourceFile;
import com.xbk.lattice.compiler.ast.service.AstGraphExtractService;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AST 图谱抽取节点
 *
 * 职责：把 Java 源文件抽取为图谱实体、事实与关系
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class ExtractAstGraphNode extends AbstractCompileGraphNode {

    private final AstGraphExtractService astGraphExtractService;

    /**
     * 创建 AST 图谱抽取节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     * @param astGraphExtractService AST 图谱抽取服务
     */
    public ExtractAstGraphNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore,
            AstGraphExtractService astGraphExtractService
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
        this.astGraphExtractService = astGraphExtractService;
    }

    /**
     * 执行 AST 图谱抽取。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<RawSource> rawSources = workingSetStore().loadRawSources(state.getRawSourcesRef());
        List<AstSourceFile> astSourceFiles = new ArrayList<AstSourceFile>();
        for (RawSource rawSource : rawSources) {
            if (!rawSource.getRelativePath().endsWith(".java")) {
                continue;
            }
            AstSourceFile astSourceFile = new AstSourceFile();
            astSourceFile.setSourceFileId(state.getSourceFileIdsByPath().get(rawSource.getRelativePath()));
            astSourceFile.setRelativePath(rawSource.getRelativePath());
            astSourceFile.setContent(rawSource.getContent());
            astSourceFile.setSystemLabel(state.getSourceCode());
            astSourceFiles.add(astSourceFile);
        }
        AstGraphExtractReport astGraphExtractReport = astGraphExtractService.extract(state.getSourceDir(), astSourceFiles);
        state.setAstExtractReportRef(workingSetStore().saveAstExtractReport(state.getJobId(), astGraphExtractReport));
        state.setGraphEntityUpsertCount(astGraphExtractReport.getEntityUpsertCount());
        state.setGraphFactUpsertCount(astGraphExtractReport.getFactUpsertCount());
        state.setGraphRelationUpsertCount(astGraphExtractReport.getRelationUpsertCount());
        return delta(state);
    }
}
