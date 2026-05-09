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
 * MCP 资料源工具实现。
 *
 * 职责：承接文档目录、章节读取、资料源列表和资料源同步工具逻辑。
 *
 * @author xiexu
 */
abstract class SourceMcpTools extends GovernanceMcpTools {

    /**
     * 返回源文件目录。
     *
     * @param path 源文件路径
     * @return JSON 字符串，包含章节标题、层级与行号
     */
    @McpTool(name = "lattice_doc_toc", description = "Return heading hierarchy and line numbers for a source document")
    protected String docToc(@McpToolParam(description = "The source file path to inspect") String path) {
        SourceFileRecord sourceFileRecord = requireSourceFileJdbcRepository().findByPath(path)
                .orElseThrow(() -> new IllegalArgumentException("source file not found: " + path));
        List<DocumentSectionSelector.DocumentHeading> headings = requireDocumentSectionSelector().toc(sourceFileRecord.getContentText());
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        for (int index = 0; index < headings.size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            DocumentSectionSelector.DocumentHeading heading = headings.get(index);
            itemsBuilder.append("{")
                    .append("\"heading\":").append(jsonString(heading.getHeading())).append(",")
                    .append("\"level\":").append(heading.getLevel()).append(",")
                    .append("\"line\":").append(heading.getLine())
                    .append("}");
        }
        itemsBuilder.append("]");
        return "{"
                + "\"path\":" + jsonString(path) + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 读取源文件指定章节。
     *
     * @param path 源文件路径
     * @param heading 章节标题
     * @return JSON 字符串，包含章节标题、行号与正文
     */
    @McpTool(name = "lattice_doc_read", description = "Read a specific heading section from a source document")
    protected String docRead(
            @McpToolParam(description = "The source file path to inspect") String path,
            @McpToolParam(description = "The heading title to read") String heading
    ) {
        SourceFileRecord sourceFileRecord = requireSourceFileJdbcRepository().findByPath(path)
                .orElseThrow(() -> new IllegalArgumentException("source file not found: " + path));
        DocumentSectionSelector.DocumentSection section = requireDocumentSectionSelector()
                .readSection(sourceFileRecord.getContentText(), heading);
        return "{"
                + "\"path\":" + jsonString(path) + ","
                + "\"heading\":" + jsonString(section.getHeading()) + ","
                + "\"line\":" + section.getLine() + ","
                + "\"content\":" + jsonString(section.getContent())
                + "}";
    }
    /**
     * 返回资料源列表。
     *
     * @param limit 返回数量
     * @return JSON 字符串，包含 count / items
     */
    @McpTool(name = "lattice_source_list", description = "List knowledge sources with status, type, and last sync summary")
    protected String sourceList(
            @McpToolParam(description = "The max number of sources to return") int limit
    ) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        KnowledgeSourcePage page = requireSourceService().listSources(null, null, null, 1, safeLimit);
        StringBuilder itemsBuilder = new StringBuilder();
        itemsBuilder.append("[");
        List<KnowledgeSource> items = page.getItems();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                itemsBuilder.append(",");
            }
            itemsBuilder.append(toSourceSummaryJson(items.get(index)));
        }
        itemsBuilder.append("]");
        return "{"
                + "\"count\":" + items.size() + ","
                + "\"total\":" + page.getTotal() + ","
                + "\"items\":" + itemsBuilder
                + "}";
    }
    /**
     * 对资料源发起一次同步。
     *
     * @param sourceId 资料源主键
     * @return JSON 字符串，包含 runId / status / sourceId / compileJobStatus 等字段
     * @throws IOException IO 异常
     */
    @McpTool(name = "lattice_source_sync", description = "Trigger a source sync run and return the run detail")
    protected String sourceSync(
            @McpToolParam(description = "The sourceId returned by lattice_source_list") long sourceId
    ) throws IOException {
        SourceSyncRunDetail detail = requireSourceSyncWorkflowService().syncSource(sourceId);
        return toSourceRunJson(detail);
    }
}
