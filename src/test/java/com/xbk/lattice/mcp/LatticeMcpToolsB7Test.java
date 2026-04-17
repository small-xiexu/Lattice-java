package com.xbk.lattice.mcp;

import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.compiler.service.CompileResult;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.LintIssue;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.governance.LintService;
import com.xbk.lattice.governance.InspectService;
import com.xbk.lattice.governance.InspectionAnswerImportService;
import com.xbk.lattice.governance.InspectionImportResult;
import com.xbk.lattice.governance.InspectionQuestion;
import com.xbk.lattice.governance.InspectionReport;
import com.xbk.lattice.governance.CoverageReport;
import com.xbk.lattice.governance.CoverageTrackingService;
import com.xbk.lattice.governance.LifecycleItem;
import com.xbk.lattice.governance.LifecycleReport;
import com.xbk.lattice.governance.LifecycleService;
import com.xbk.lattice.governance.LifecycleTransitionResult;
import com.xbk.lattice.governance.LinkEnhancementItem;
import com.xbk.lattice.governance.LinkEnhancementReport;
import com.xbk.lattice.governance.LinkEnhancementService;
import com.xbk.lattice.governance.OmissionReport;
import com.xbk.lattice.governance.OmissionTrackingService;
import com.xbk.lattice.governance.PropagationItem;
import com.xbk.lattice.governance.PropagationReport;
import com.xbk.lattice.governance.PropagationService;
import com.xbk.lattice.governance.QualityMetricsReport;
import com.xbk.lattice.governance.QualityMetricsService;
import com.xbk.lattice.governance.HistoryReport;
import com.xbk.lattice.governance.HistoryService;
import com.xbk.lattice.governance.SnapshotReport;
import com.xbk.lattice.governance.SnapshotService;
import com.xbk.lattice.governance.StatusService;
import com.xbk.lattice.governance.StatusSnapshot;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.query.service.KnowledgeLookupResult;
import com.xbk.lattice.query.service.KnowledgeLookupService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.PendingQueryManager;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LatticeMcpTools B7 扩展工具测试
 *
 * 职责：验证 search/get/status/lint/quality/compile 工具的输出格式
 *
 * @author xiexu
 */
class LatticeMcpToolsB7Test {

    /**
     * 验证 lattice_search 会返回融合命中列表 JSON。
     */
    @Test
    void searchShouldReturnHitListJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                new FixedKnowledgeSearchService(List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "payment/context.md#0",
                                "payment/context.md",
                                "retry=3",
                                "{}",
                                List.of("payment/context.md"),
                                3.5D
                        )
                )),
                null,
                null,
                null,
                null,
                null
        );

        String result = tools.search("retry=3", 5);

        assertThat(result).contains("\"count\":1");
        assertThat(result).contains("\"evidenceType\":\"SOURCE\"");
        assertThat(result).contains("\"title\":\"payment/context.md\"");
    }

    /**
     * 验证 lattice_get 会返回知识详情 JSON。
     */
    @Test
    void getShouldReturnKnowledgeJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                new FixedKnowledgeLookupService(
                        new KnowledgeLookupResult(
                                true,
                                "article",
                                "payment-timeout",
                                "Payment Timeout",
                                "# Payment Timeout",
                                List.of("payment/a.md"),
                                "{}"
                        )
                ),
                null,
                null,
                null,
                null
        );

        String result = tools.get("payment-timeout");

        assertThat(result).contains("\"status\":\"found\"");
        assertThat(result).contains("\"type\":\"article\"");
        assertThat(result).contains("\"title\":\"Payment Timeout\"");
    }

    /**
     * 验证 lattice_status 会返回状态汇总 JSON。
     */
    @Test
    void statusShouldReturnSnapshotJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                new FixedStatusService(new StatusSnapshot(2, 3, 4, 5, 1)),
                null,
                null,
                null
        );

        String result = tools.status();

        assertThat(result).contains("\"articleCount\":2");
        assertThat(result).contains("\"pendingQueryCount\":5");
        assertThat(result).contains("\"reviewPendingArticleCount\":1");
    }

    /**
     * 验证 lattice_lint 会返回检查维度与问题列表 JSON。
     */
    @Test
    void lintShouldReturnIssueJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                new FixedLintService(
                        new LintReport(
                                List.of("grounding", "referential"),
                                List.of(new LintIssue("grounding", "payment-timeout", "缺少 source_paths"))
                        )
                ),
                null,
                null
        );

        String result = tools.lint();

        assertThat(result).contains("\"checkedDimensions\":[\"grounding\",\"referential\"]");
        assertThat(result).contains("\"totalIssues\":1");
        assertThat(result).contains("缺少 source_paths");
    }

    /**
     * 验证 lattice_quality 会返回质量指标 JSON。
     */
    @Test
    void qualityShouldReturnMetricsJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                new FixedQualityMetricsService(new QualityMetricsReport(6, 4, 1, 1, 3, 8)),
                null
        );

        String result = tools.quality();

        assertThat(result).contains("\"totalArticles\":6");
        assertThat(result).contains("\"passedArticles\":4");
        assertThat(result).contains("\"sourceFileCount\":8");
    }

    /**
     * 验证 lattice_coverage 会返回覆盖率汇总 JSON。
     */
    @Test
    void coverageShouldReturnSummaryJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedCoverageTrackingService(new CoverageReport(
                        4,
                        3,
                        1,
                        0.75D,
                        List.of("payment/a.md", "payment/b.md", "payment/c.md")
                )),
                null
        );

        String result = tools.coverage();

        assertThat(result).contains("\"totalSourceFileCount\":4");
        assertThat(result).contains("\"coverageRatio\":0.75");
        assertThat(result).contains("\"coveredSourcePaths\":[\"payment/a.md\",\"payment/b.md\",\"payment/c.md\"]");
    }

    /**
     * 验证 lattice_omissions 会返回遗漏源文件 JSON。
     */
    @Test
    void omissionsShouldReturnListJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedOmissionTrackingService(new OmissionReport(
                        5,
                        List.of("payment/d.md", "payment/e.md")
                ))
        );

        String result = tools.omissions();

        assertThat(result).contains("\"totalSourceFileCount\":5");
        assertThat(result).contains("\"count\":2");
        assertThat(result).contains("\"items\":[\"payment/d.md\",\"payment/e.md\"]");
    }

    /**
     * 验证 lattice_lifecycle 会返回生命周期汇总 JSON。
     */
    @Test
    void lifecycleShouldReturnSummaryJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedLifecycleService(
                        new LifecycleReport(
                                3,
                                1,
                                1,
                                1,
                                0,
                                List.of(
                                        new LifecycleItem(
                                                "payment-timeout-v1",
                                                "Payment Timeout V1",
                                                "deprecated",
                                                "passed",
                                                "由 v2 取代",
                                                "architect",
                                                "2026-04-15T22:40:00+08:00"
                                        )
                                )
                        ),
                        null,
                        null,
                        null
                )
        );

        String result = tools.lifecycle();

        assertThat(result).contains("\"totalArticles\":3");
        assertThat(result).contains("\"deprecatedCount\":1");
        assertThat(result).contains("\"reason\":\"由 v2 取代\"");
    }

    /**
     * 验证 lattice_lifecycle_deprecate 会返回状态切换 JSON。
     */
    @Test
    void lifecycleDeprecateShouldReturnTransitionJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedLifecycleService(
                        null,
                        new LifecycleTransitionResult(
                                "payment-timeout",
                                "Payment Timeout",
                                "deprecated",
                                "由 v2 取代",
                                "architect",
                                "2026-04-15T22:41:00+08:00"
                        ),
                        null,
                        null
                )
        );

        String result = tools.lifecycleDeprecate("payment-timeout", "由 v2 取代", "architect");

        assertThat(result).contains("\"conceptId\":\"payment-timeout\"");
        assertThat(result).contains("\"lifecycle\":\"deprecated\"");
        assertThat(result).contains("\"updatedBy\":\"architect\"");
    }

    /**
     * 验证 lattice_link_enhance 会返回增强摘要 JSON。
     */
    @Test
    void linkEnhanceShouldReturnReportJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedLinkEnhancementService(new LinkEnhancementReport(
                        4,
                        4,
                        1,
                        1,
                        2,
                        1,
                        List.of(
                                new LinkEnhancementItem(
                                        "refund-manual-review",
                                        "Refund Manual Review",
                                        true,
                                        1,
                                        2,
                                        List.of("Unknown Legacy Doc")
                                )
                        )
                ))
        );

        String result = tools.linkEnhance(true);

        assertThat(result).contains("\"processedArticleCount\":4");
        assertThat(result).contains("\"updatedArticleCount\":1");
        assertThat(result).contains("\"fixedLinkCount\":1");
        assertThat(result).contains("\"unresolvedLinks\":[\"Unknown Legacy Doc\"]");
    }

    /**
     * 验证 lattice_inspect 会返回标准化 inspection 问题 JSON。
     */
    @Test
    void inspectShouldReturnInspectionQuestionsJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedInspectService(new InspectionReport(List.of(
                        new InspectionQuestion(
                                "pending:query-1",
                                "pending_query",
                                "retry=3 是什么意思",
                                "请确认答案是否准确，必要时给出最终答案",
                                "retry=3 表示最多重试三次",
                                List.of("payment/a.md"),
                                "PASSED",
                                "2026-04-15T20:00:00+08:00",
                                "2026-04-22T20:00:00+08:00"
                        )
                ))),
                null
        );

        String result = tools.inspect();

        assertThat(result).contains("\"count\":1");
        assertThat(result).contains("\"type\":\"pending_query\"");
        assertThat(result).contains("\"id\":\"pending:query-1\"");
    }

    /**
     * 验证 lattice_import_answers 会返回导入结果 JSON。
     */
    @Test
    void importAnswersShouldReturnImportResultJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedInspectionAnswerImportService(new InspectionImportResult(1, List.of("pending:query-1")))
        );

        String result = tools.importAnswers("pending:query-1", "retry=3 表示最多重试三次", "reviewer");

        assertThat(result).contains("\"importedCount\":1");
        assertThat(result).contains("\"resolvedIds\":[\"pending:query-1\"]");
    }

    /**
     * 验证 lattice_correct 会返回纠错预览 JSON。
     */
    @Test
    void correctKnowledgeShouldReturnCorrectionPreviewJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedArticleCorrectionService(new ArticleCorrectionResult(
                        "payment-config",
                        "# Payment Config\n\n已修正重试策略。",
                        List.of("payment-timeout", "refund-manual-review"),
                        true
                )),
                new FixedPropagationService(new PropagationReport(
                        "payment-config",
                        "更新重试策略",
                        List.of(
                                new PropagationItem("payment-timeout", "Payment Timeout", 1, List.of("depends_on")),
                                new PropagationItem("refund-manual-review", "Refund Manual Review", 2, List.of("wiki_link"))
                        )
                ))
        );

        String result = tools.correctKnowledge("payment-config", "更新重试策略");

        assertThat(result).contains("\"conceptId\":\"payment-config\"");
        assertThat(result).contains("\"downstreamCount\":2");
        assertThat(result).contains("\"evidenceSupported\":true");
        assertThat(result).contains("\"downstreamIds\":[\"payment-timeout\",\"refund-manual-review\"]");
    }

    /**
     * 验证 lattice_snapshot 会返回最近快照 JSON。
     */
    @Test
    void snapshotShouldReturnRecentSnapshotJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedSnapshotService(new SnapshotReport(List.of(
                        snapshotRecord(2L, "payment-timeout", "passed", "2026-04-15T20:05:00+08:00")
                ))),
                null
        );

        String result = tools.snapshot(10);

        assertThat(result).contains("\"count\":1");
        assertThat(result).contains("\"snapshotId\":2");
        assertThat(result).contains("\"conceptId\":\"payment-timeout\"");
    }

    /**
     * 验证 lattice_history 会返回指定概念的历史 JSON。
     */
    @Test
    void historyShouldReturnConceptHistoryJson() {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new FixedHistoryService(new HistoryReport(
                        "payment-timeout",
                        List.of(snapshotRecord(3L, "payment-timeout", "needs_human_review", "2026-04-15T20:10:00+08:00"))
                ))
        );

        String result = tools.history("payment-timeout", 10);

        assertThat(result).contains("\"conceptId\":\"payment-timeout\"");
        assertThat(result).contains("\"count\":1");
        assertThat(result).contains("\"reviewStatus\":\"needs_human_review\"");
    }

    /**
     * 验证 lattice_compile 会返回编译结果 JSON。
     *
     * @throws IOException IO 异常
     */
    @Test
    void compileShouldReturnCompileResultJson() throws IOException {
        LatticeMcpTools tools = new LatticeMcpTools(
                null,
                new UnsupportedPendingQueryManager(),
                null,
                null,
                null,
                null,
                null,
                new FixedCompileApplicationFacade(new CompileResult(2, "job-001"))
        );

        String result = tools.compile("/tmp/kb", true);

        assertThat(result).contains("\"persistedCount\":2");
        assertThat(result).contains("\"jobId\":\"job-001\"");
        assertThat(result).contains("\"mode\":\"incremental\"");
    }

    private ArticleSnapshotRecord snapshotRecord(
            long snapshotId,
            String conceptId,
            String reviewStatus,
            String compiledAt
    ) {
        return new ArticleSnapshotRecord(
                snapshotId,
                conceptId,
                "Payment Timeout",
                "# Payment Timeout",
                "active",
                java.time.OffsetDateTime.parse(compiledAt),
                List.of("payment/a.md"),
                "{}",
                "summary",
                List.of("retry=3"),
                List.of(),
                List.of(),
                "medium",
                reviewStatus,
                "article_upsert",
                java.time.OffsetDateTime.parse(compiledAt).plusSeconds(5)
        );
    }

    private static class FixedKnowledgeSearchService extends KnowledgeSearchService {

        private final List<QueryArticleHit> hits;

        private FixedKnowledgeSearchService(List<QueryArticleHit> hits) {
            super(null, null, null, null, null);
            this.hits = hits;
        }

        @Override
        public List<QueryArticleHit> search(String question, int limit) {
            return hits;
        }
    }

    private static class FixedKnowledgeLookupService extends KnowledgeLookupService {

        private final KnowledgeLookupResult result;

        private FixedKnowledgeLookupService(KnowledgeLookupResult result) {
            super(null, null);
            this.result = result;
        }

        @Override
        public KnowledgeLookupResult get(String id) {
            return result;
        }
    }

    private static class FixedStatusService extends StatusService {

        private final StatusSnapshot snapshot;

        private FixedStatusService(StatusSnapshot snapshot) {
            super(null, null, null, null);
            this.snapshot = snapshot;
        }

        @Override
        public StatusSnapshot snapshot() {
            return snapshot;
        }
    }

    private static class FixedLintService extends LintService {

        private final LintReport report;

        private FixedLintService(LintReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public LintReport lint() {
            return report;
        }
    }

    private static class FixedQualityMetricsService extends QualityMetricsService {

        private final QualityMetricsReport report;

        private FixedQualityMetricsService(QualityMetricsReport report) {
            super(null, null, null);
            this.report = report;
        }

        @Override
        public QualityMetricsReport measure() {
            return report;
        }
    }

    private static class FixedCompileApplicationFacade extends CompileApplicationFacade {

        private final CompileResult compileResult;

        private FixedCompileApplicationFacade(CompileResult compileResult) {
            super(null);
            this.compileResult = compileResult;
        }

        @Override
        public CompileResult compile(Path sourceDir, boolean incremental, String orchestrationMode) {
            return compileResult;
        }
    }

    private static class FixedInspectService extends InspectService {

        private final InspectionReport report;

        private FixedInspectService(InspectionReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public InspectionReport inspect() {
            return report;
        }
    }

    private static class FixedInspectionAnswerImportService extends InspectionAnswerImportService {

        private final InspectionImportResult result;

        private FixedInspectionAnswerImportService(InspectionImportResult result) {
            super(null, null);
            this.result = result;
        }

        @Override
        public InspectionImportResult importAnswer(String inspectionId, String finalAnswer, String confirmedBy) {
            return result;
        }
    }

    private static class FixedArticleCorrectionService extends ArticleCorrectionService {

        private final ArticleCorrectionResult result;

        private FixedArticleCorrectionService(ArticleCorrectionResult result) {
            super(null, null, null, null, null);
            this.result = result;
        }

        @Override
        public ArticleCorrectionResult correct(String conceptId, String correctionSummary) {
            return result;
        }
    }

    private static class FixedPropagationService extends PropagationService {

        private final PropagationReport report;

        private FixedPropagationService(PropagationReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public PropagationReport propagate(String rootConceptId, String correctionSummary) {
            return report;
        }

        @Override
        public void markDownstream(String rootConceptId, String correctionSummary, List<String> downstreamIds) {
            // 无操作：本测试只验证 lattice_correct 的返回结构
        }
    }

    private static class FixedSnapshotService extends SnapshotService {

        private final SnapshotReport report;

        private FixedSnapshotService(SnapshotReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public SnapshotReport snapshot(int limit) {
            return report;
        }
    }

    private static class FixedHistoryService extends HistoryService {

        private final HistoryReport report;

        private FixedHistoryService(HistoryReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public HistoryReport history(String conceptId, int limit) {
            return report;
        }
    }

    private static class FixedLifecycleService extends LifecycleService {

        private final LifecycleReport report;

        private final LifecycleTransitionResult deprecatedResult;

        private final LifecycleTransitionResult archivedResult;

        private final LifecycleTransitionResult activatedResult;

        private FixedLifecycleService(
                LifecycleReport report,
                LifecycleTransitionResult deprecatedResult,
                LifecycleTransitionResult archivedResult,
                LifecycleTransitionResult activatedResult
        ) {
            super(null);
            this.report = report;
            this.deprecatedResult = deprecatedResult;
            this.archivedResult = archivedResult;
            this.activatedResult = activatedResult;
        }

        @Override
        public LifecycleReport report() {
            return report;
        }

        @Override
        public LifecycleTransitionResult deprecate(String conceptId, String reason, String updatedBy) {
            return deprecatedResult;
        }

        @Override
        public LifecycleTransitionResult archive(String conceptId, String reason, String updatedBy) {
            return archivedResult;
        }

        @Override
        public LifecycleTransitionResult activate(String conceptId, String reason, String updatedBy) {
            return activatedResult;
        }
    }

    private static class FixedLinkEnhancementService extends LinkEnhancementService {

        private final LinkEnhancementReport report;

        private FixedLinkEnhancementService(LinkEnhancementReport report) {
            super(null);
            this.report = report;
        }

        @Override
        public LinkEnhancementReport enhance(boolean persist) {
            return report;
        }
    }

    private static class FixedCoverageTrackingService extends CoverageTrackingService {

        private final CoverageReport report;

        private FixedCoverageTrackingService(CoverageReport report) {
            super(null, null);
            this.report = report;
        }

        @Override
        public CoverageReport measure() {
            return report;
        }
    }

    private static class FixedOmissionTrackingService extends OmissionTrackingService {

        private final OmissionReport report;

        private FixedOmissionTrackingService(OmissionReport report) {
            super(null, null);
            this.report = report;
        }

        @Override
        public OmissionReport track() {
            return report;
        }
    }

    private static class UnsupportedPendingQueryManager implements PendingQueryManager {

        @Override
        public com.xbk.lattice.infra.persistence.PendingQueryRecord createPendingQuery(
                String question,
                com.xbk.lattice.api.query.QueryResponse queryResponse
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.xbk.lattice.infra.persistence.PendingQueryRecord correct(String queryId, String correction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void confirm(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void discard(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.xbk.lattice.infra.persistence.PendingQueryRecord findPendingQuery(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.xbk.lattice.infra.persistence.PendingQueryRecord> listPendingQueries() {
            throw new UnsupportedOperationException();
        }
    }
}
