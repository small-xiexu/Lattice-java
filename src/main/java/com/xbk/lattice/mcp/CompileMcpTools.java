package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.compiler.service.CompileResult;
import com.xbk.lattice.compiler.service.DocumentSectionSelector;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.LintIssue;
import com.xbk.lattice.governance.LintFixResult;
import com.xbk.lattice.governance.LintFixService;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.governance.LintService;
import com.xbk.lattice.governance.InspectService;
import com.xbk.lattice.governance.InspectionAnswerImportService;
import com.xbk.lattice.governance.InspectionImportResult;
import com.xbk.lattice.governance.InspectionQuestion;
import com.xbk.lattice.governance.InspectionReport;
import com.xbk.lattice.governance.CoverageReport;
import com.xbk.lattice.governance.CoverageTrackingService;
import com.xbk.lattice.governance.domain.LifecycleItem;
import com.xbk.lattice.governance.domain.LifecycleReport;
import com.xbk.lattice.governance.domain.LifecycleTransitionResult;
import com.xbk.lattice.governance.LifecycleService;
import com.xbk.lattice.governance.LinkEnhancementItem;
import com.xbk.lattice.governance.LinkEnhancementReport;
import com.xbk.lattice.governance.LinkEnhancementService;
import com.xbk.lattice.governance.OmissionReport;
import com.xbk.lattice.governance.OmissionTrackingService;
import com.xbk.lattice.governance.PropagationItem;
import com.xbk.lattice.governance.PropagationExecutionResult;
import com.xbk.lattice.governance.PropagationReport;
import com.xbk.lattice.governance.PropagationService;
import com.xbk.lattice.governance.PropagateExecutionService;
import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.QualityMetricsService;
import com.xbk.lattice.governance.QualityMetricsTrend;
import com.xbk.lattice.governance.RollbackResult;
import com.xbk.lattice.governance.HistoryReport;
import com.xbk.lattice.governance.HistoryService;
import com.xbk.lattice.governance.SnapshotReport;
import com.xbk.lattice.governance.SnapshotService;
import com.xbk.lattice.governance.StatusService;
import com.xbk.lattice.governance.StatusSnapshot;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.KnowledgeLookupResult;
import com.xbk.lattice.query.service.KnowledgeLookupService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.PendingQueryManager;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryFacadeService;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceService;
import com.xbk.lattice.source.service.SourceSyncWorkflowService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 编译工具实现。
 *
 * 职责：承接编译触发工具逻辑。
 *
 * @author xiexu
 */
abstract class CompileMcpTools extends SourceMcpTools {

    /**
     * 触发知识库编译。
     *
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @return JSON 字符串，包含 persistedCount / jobId / mode
     * @throws IOException IO 异常
     */
    @McpTool(name = "lattice_compile", description = "Compile a source directory into the knowledge base, optionally in incremental mode")
    protected String compile(
            @McpToolParam(description = "The source directory to compile") String sourceDir,
            @McpToolParam(description = "Whether to run incremental compile") boolean incremental
    ) throws IOException {
        Path compileSourceDir = Path.of(sourceDir);
        CompileResult compileResult = requireCompileApplicationFacade().compile(compileSourceDir, incremental, null);
        return "{"
                + "\"persistedCount\":" + compileResult.getPersistedCount() + ","
                + "\"jobId\":" + jsonString(compileResult.getJobId()) + ","
                + "\"mode\":" + jsonString(incremental ? "incremental" : "full")
                + "}";
    }
}
