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
 * MCP 工具依赖校验支持。
 *
 * 职责：在可选依赖缺失时给出稳定错误，避免工具方法散落空指针判断。
 *
 * @author xiexu
 */
abstract class LatticeMcpDependencySupport extends LatticeMcpJsonSupport {

    /**
     * 获取源文件仓储。
     *
     * @return 源文件仓储
     */
    protected SourceFileJdbcRepository requireSourceFileJdbcRepository() {
        if (sourceFileJdbcRepository == null) {
            throw new UnsupportedOperationException("sourceFileJdbcRepository not configured");
        }
        return sourceFileJdbcRepository;
    }
    /**
     * 获取文档章节选择器。
     *
     * @return 文档章节选择器
     */
    protected DocumentSectionSelector requireDocumentSectionSelector() {
        if (documentSectionSelector == null) {
            throw new UnsupportedOperationException("documentSectionSelector not configured");
        }
        return documentSectionSelector;
    }
    /**
     * 获取知识检索服务。
     *
     * @return 知识检索服务
     */
    protected KnowledgeSearchService requireKnowledgeSearchService() {
        if (knowledgeSearchService == null) {
            throw new UnsupportedOperationException("knowledgeSearchService not configured");
        }
        return knowledgeSearchService;
    }
    /**
     * 获取知识详情服务。
     *
     * @return 知识详情服务
     */
    protected KnowledgeLookupService requireKnowledgeLookupService() {
        if (knowledgeLookupService == null) {
            throw new UnsupportedOperationException("knowledgeLookupService not configured");
        }
        return knowledgeLookupService;
    }
    /**
     * 获取状态服务。
     *
     * @return 状态服务
     */
    protected StatusService requireStatusService() {
        if (statusService == null) {
            throw new UnsupportedOperationException("statusService not configured");
        }
        return statusService;
    }
    /**
     * 获取 Lint 服务。
     *
     * @return Lint 服务
     */
    protected LintService requireLintService() {
        if (lintService == null) {
            throw new UnsupportedOperationException("lintService not configured");
        }
        return lintService;
    }
    /**
     * 获取 lint 自动修复服务。
     *
     * @return lint 自动修复服务
     */
    protected LintFixService requireLintFixService() {
        if (lintFixService == null) {
            throw new UnsupportedOperationException("lintFixService not configured");
        }
        return lintFixService;
    }
    /**
     * 获取质量指标服务。
     *
     * @return 质量指标服务
     */
    protected QualityMetricsService requireQualityMetricsService() {
        if (qualityMetricsService == null) {
            throw new UnsupportedOperationException("qualityMetricsService not configured");
        }
        return qualityMetricsService;
    }
    /**
     * 获取覆盖率跟踪服务。
     *
     * @return 覆盖率跟踪服务
     */
    protected CoverageTrackingService requireCoverageTrackingService() {
        if (coverageTrackingService == null) {
            throw new UnsupportedOperationException("coverageTrackingService not configured");
        }
        return coverageTrackingService;
    }
    /**
     * 获取遗漏跟踪服务。
     *
     * @return 遗漏跟踪服务
     */
    protected OmissionTrackingService requireOmissionTrackingService() {
        if (omissionTrackingService == null) {
            throw new UnsupportedOperationException("omissionTrackingService not configured");
        }
        return omissionTrackingService;
    }
    /**
     * 获取生命周期服务。
     *
     * @return 生命周期服务
     */
    protected LifecycleService requireLifecycleService() {
        if (lifecycleService == null) {
            throw new UnsupportedOperationException("lifecycleService not configured");
        }
        return lifecycleService;
    }
    /**
     * 获取链接增强服务。
     *
     * @return 链接增强服务
     */
    protected LinkEnhancementService requireLinkEnhancementService() {
        if (linkEnhancementService == null) {
            throw new UnsupportedOperationException("linkEnhancementService not configured");
        }
        return linkEnhancementService;
    }
    /**
     * 获取统一编译应用门面。
     *
     * @return 编译应用门面
     */
    protected CompileApplicationFacade requireCompileApplicationFacade() {
        if (compileApplicationFacade == null) {
            throw new UnsupportedOperationException("compileApplicationFacade not configured");
        }
        return compileApplicationFacade;
    }
    /**
     * 获取 inspect 服务。
     *
     * @return inspect 服务
     */
    protected InspectService requireInspectService() {
        if (inspectService == null) {
            throw new UnsupportedOperationException("inspectService not configured");
        }
        return inspectService;
    }
    /**
     * 获取 inspection 答案导入服务。
     *
     * @return inspection 答案导入服务
     */
    protected InspectionAnswerImportService requireInspectionAnswerImportService() {
        if (inspectionAnswerImportService == null) {
            throw new UnsupportedOperationException("inspectionAnswerImportService not configured");
        }
        return inspectionAnswerImportService;
    }
    /**
     * 获取文章纠错服务。
     *
     * @return 文章纠错服务
     */
    protected ArticleCorrectionService requireArticleCorrectionService() {
        if (articleCorrectionService == null) {
            throw new UnsupportedOperationException("articleCorrectionService not configured");
        }
        return articleCorrectionService;
    }
    /**
     * 获取传播执行服务。
     *
     * @return 传播执行服务
     */
    protected PropagateExecutionService requirePropagateExecutionService() {
        if (propagateExecutionService == null) {
            throw new UnsupportedOperationException("propagateExecutionService not configured");
        }
        return propagateExecutionService;
    }
    /**
     * 获取传播服务。
     *
     * @return 传播服务
     */
    protected PropagationService requirePropagationService() {
        if (propagationService == null) {
            throw new UnsupportedOperationException("propagationService not configured");
        }
        return propagationService;
    }
    /**
     * 获取快照服务。
     *
     * @return 快照服务
     */
    protected SnapshotService requireSnapshotService() {
        if (snapshotService == null) {
            throw new UnsupportedOperationException("snapshotService not configured");
        }
        return snapshotService;
    }
    /**
     * 获取历史服务。
     *
     * @return 历史服务
     */
    protected HistoryService requireHistoryService() {
        if (historyService == null) {
            throw new UnsupportedOperationException("historyService not configured");
        }
        return historyService;
    }
    /**
     * 获取资料源服务。
     *
     * @return 资料源服务
     */
    protected SourceService requireSourceService() {
        if (sourceService == null) {
            throw new UnsupportedOperationException("sourceService not configured");
        }
        return sourceService;
    }
    /**
     * 获取资料源同步工作流服务。
     *
     * @return 资料源同步工作流服务
     */
    protected SourceSyncWorkflowService requireSourceSyncWorkflowService() {
        if (sourceSyncWorkflowService == null) {
            throw new UnsupportedOperationException("sourceSyncWorkflowService not configured");
        }
        return sourceSyncWorkflowService;
    }
}
