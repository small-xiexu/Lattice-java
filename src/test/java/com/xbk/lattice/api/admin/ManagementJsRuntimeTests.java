package com.xbk.lattice.api.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * management.js 运行态回归测试
 *
 * 职责：通过 Node 执行前端测试钩子，验证运行态回退、疑似卡住提示与错误文案收口
 *
 * @author xiexu
 */
class ManagementJsRuntimeTests {

    /**
     * 验证运行态回退、疑似卡住提示、稳定错误文案与重新同步入口都会按预期工作。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldVerifyRunFallbackAndErrorPresentationViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path managementJsPath = Path.of(userDir, "src/main/resources/static/admin/management.js");
        assertThat(Files.exists(managementJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("management-js-runtime-test.js");
        Files.writeString(harnessScriptPath, buildHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder versionBuilder = new ProcessBuilder("node", "--version");
        versionBuilder.redirectErrorStream(true);
        Process versionProcess = versionBuilder.start();
        String versionOutput = readProcessOutput(versionProcess);
        int versionExitCode = versionProcess.waitFor();
        assertThat(versionExitCode).isZero();
        assertThat(versionOutput).startsWith("v");

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                managementJsPath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as(output)
                .isZero();
        assertThat(output).contains("management-js-runtime-tests:ok");
    }

    /**
     * 构造 Node 侧测试脚本。
     *
     * @return 测试脚本文本
     */
    private String buildHarnessScript() {
        return """
                const fs = require("fs");
                const vm = require("vm");

                const source = fs.readFileSync(process.argv[2], "utf8");
                const sandbox = {
                    console: console,
                    URLSearchParams: URLSearchParams,
                    setTimeout: function () { return 0; },
                    clearTimeout: function () {},
                    window: {
                        setTimeout: function () { return 0; },
                        clearTimeout: function () {},
                        location: { search: "" }
                    },
                    document: {
                        addEventListener: function () {},
                        getElementById: function () { return null; },
                        querySelector: function () { return null; },
                        querySelectorAll: function () { return []; },
                        body: {}
                    },
                    navigator: {},
                    fetch: function () {
                        return Promise.reject(new Error("fetch not available in test harness"));
                    },
                    globalThis: null,
                    __LATTICE_ADMIN_TEST__: {}
                };

                sandbox.window.document = sandbox.document;
                sandbox.globalThis = sandbox;

                vm.runInNewContext(source, sandbox, { filename: "management.js" });

                const runs = sandbox.__LATTICE_ADMIN_TEST__.runs;
                const sourceUi = sandbox.__LATTICE_ADMIN_TEST__.source;
                const knowledgeUi = sandbox.__LATTICE_ADMIN_TEST__.knowledge;
                const feedbackUi = sandbox.__LATTICE_ADMIN_TEST__.feedback;
                const articleUi = sandbox.__LATTICE_ADMIN_TEST__.article;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(runs, "missing __LATTICE_ADMIN_TEST__.runs export");
                assert(sourceUi, "missing __LATTICE_ADMIN_TEST__.source export");
                assert(knowledgeUi, "missing __LATTICE_ADMIN_TEST__.knowledge export");
                assert(feedbackUi, "missing __LATTICE_ADMIN_TEST__.feedback export");
                assert(articleUi, "missing __LATTICE_ADMIN_TEST__.article export");
                assert(typeof sourceUi.focusSourceRunDetail === "function",
                    "missing focusSourceRunDetail export");

                const fallbackRun = { status: "RUNNING", currentStepLabel: "写入知识库", progressText: "等待下一步刷新" };
                assert(runs.resolveRunDisplayStatus(fallbackRun) === "RUNNING",
                    "should fallback to base run status when derived fields are absent");
                assert(runs.resolveRunStepLabel(fallbackRun) === "写入知识库",
                    "should prefer backend-provided current step label");
                assert(runs.resolveRunProgressText({ progressText: "等待后台 worker 领取" }) === "等待后台 worker 领取",
                    "should prefer backend-provided progress text");
                const duplicateSummaryRun = {
                    status: "RUNNING",
                    currentStepLabel: "质量检查",
                    progressText: "13 / 15 · 正在修复文章（13/15）：卡券三期-迁移方案-场景8-星礼包退款链路",
                    reasonSummary: "正在修复文章（13/15）：卡券三期-迁移方案-场景8-星礼包退款链路"
                };
                assert(runs.resolveRunSpotlightSummaryText(duplicateSummaryRun) === "",
                    "spotlight summary should be hidden when it duplicates current progress");
                assert(runs.resolveRunNextStepText({
                    nextStepHint: "继续等待当前真实步骤推进",
                    progressText: "13 / 15 · 正在修复文章（13/15）：卡券三期-迁移方案-场景8-星礼包退款链路"
                }, {
                    nextStep: "继续等待当前真实步骤推进"
                }) === "",
                    "placeholder next step should be hidden");
                const runningDuplicateReasonRun = {
                    status: "RUNNING",
                    displayStatus: "RUNNING",
                    currentStepLabel: "内容生成",
                    progressText: "1 / 20 · 正在生成文章（1/20）：卡券三期-迁移方案-srkit-svc-卡履约链路从-fc-平移至-dpfm",
                    reasonSummary: "正在生成文章（1/20）：卡券三期-迁移方案-srkit-svc-卡履约链路从-fc-平移至-dpfm"
                };
                const runningDuplicateSnapshot = runs.buildRunRuntimeSnapshot(runningDuplicateReasonRun);
                assert(!runs.shouldRenderRunReasonSummary(runningDuplicateReasonRun),
                    "running duplicated progress reason should be hidden");
                assert(runningDuplicateSnapshot.includes("当前进度"),
                    "running snapshot should keep current progress");
                assert(!runningDuplicateSnapshot.includes("原因摘要"),
                    "running duplicated reason should not render reason summary");
                assert(runs.shouldRenderRunAsBoardFocus(runningDuplicateReasonRun),
                    "running task should stay as the focused processing task card");
                const runningWarningProgressStrip = runs.buildRunProgressStrip({
                    status: "RUNNING",
                    displayStatus: "RUNNING",
                    compileDerivedStatus: "RUNNING",
                    displayTone: "warning",
                    progressSteps: [{
                        key: "TASK_RECEIVED",
                        label: "资料接收",
                        status: "COMPLETED",
                        detail: ""
                    }, {
                        key: "COMPILE_NEW_ARTICLES",
                        label: "内容生成",
                        status: "ACTIVE",
                        detail: "正在生成文章草稿"
                    }, {
                        key: "REVIEW_ARTICLES",
                        label: "质量检查",
                        status: "PENDING",
                        detail: ""
                    }]
                }, {
                    label: "进行中",
                    nextStep: "继续等待当前真实步骤推进",
                    tone: "warning"
                });
                assert(runningWarningProgressStrip.includes("run-progress-step active"),
                    "running warning tone should keep the active progress step");
                assert(!runningWarningProgressStrip.includes("run-progress-status-mark warning"),
                    "running warning tone should not be rendered as stalled");
                assert(!runningWarningProgressStrip.includes(">卡住<"),
                    "running warning tone should not show stalled copy");

                const stalledRun = {
                    status: "RUNNING",
                    displayStatus: "STALLED",
                    currentStepLabel: "质量检查",
                    progressText: "2 / 6 · 正在审查第 2 篇文章",
                    reasonSummary: "任务长时间没有新的心跳或进度更新，建议重新同步资料源。",
                    compileDerivedStatus: "STALLED",
                    compileCurrentStep: "review_articles",
                    compileProgressCurrent: 2,
                    compileProgressTotal: 6,
                    compileProgressMessage: "正在审查第 2 篇文章",
                    compileLastHeartbeatAt: "2026-04-24T08:00:00+08:00",
                    sourceNames: ["docs/payment/order-guide.md", "docs/payment/retry.md"],
                    sourceId: 12,
                    progressSteps: [{
                        key: "INITIALIZE_JOB",
                        label: "资料接收",
                        status: "COMPLETED",
                        detail: ""
                    }, {
                        key: "INGEST_SOURCES",
                        label: "资料接收",
                        status: "COMPLETED",
                        detail: ""
                    }, {
                        key: "REVIEW_ARTICLES",
                        label: "质量检查",
                        status: "FAILED",
                        detail: "细分状态：正在审查文章草稿"
                    }],
                    actions: [{
                        actionKey: "RESYNC_SOURCE",
                        label: "重新同步当前资料源",
                        buttonClass: "secondary-btn",
                        runId: 12,
                        sourceId: 12,
                        uploadRetry: false
                    }]
                };
                const runtimeSnapshot = runs.buildRunRuntimeSnapshot(stalledRun);
                assert(!runtimeSnapshot.includes("编译态"),
                    "runtime snapshot should hide duplicate derived status");
                assert(!runtimeSnapshot.includes("当前步骤"),
                    "runtime snapshot should hide current step when progress strip is present");
                assert(runtimeSnapshot.includes("2 / 6"),
                    "runtime snapshot should expose current progress");
                assert(!runtimeSnapshot.includes("原因摘要"),
                    "runtime snapshot should hide reason summary when failure panel already covers it");
                const progressStrip = runs.buildRunProgressStrip(stalledRun, {
                        label: "失败",
                        nextStep: "查看最近推进时间并重新同步资料源",
                        tone: "danger"
                    });
                assert(progressStrip.includes("run-progress-detail"),
                    "progress strip should expose detail copy for current real sub-step");
                assert(progressStrip.includes("质量检查"),
                    "progress strip should show current real compile step under grouped stage");
                assert(progressStrip.includes("run-progress-status-mark warning"),
                    "progress strip should expose an explicit stalled status mark");
                assert(progressStrip.includes(">卡住<"),
                    "progress strip should show stalled copy only for STALLED status");
                assert(!progressStrip.includes("run-progress-status-mark failed"),
                    "stalled progress strip should not be mislabeled as a generic failure");
                assert(progressStrip.includes("正在审查文章草稿"),
                    "progress strip should keep cleaned detail copy");
                assert(!progressStrip.includes("细分状态"),
                    "progress strip should not render redundant detail label");
                assert(runs.buildRunReasonSummary(stalledRun).includes("长时间没有新的心跳"),
                    "stalled run should explain stalled reason");
                assert(!runs.shouldRenderRunReasonSummary(stalledRun),
                    "stalled run should hide duplicate reason summary when failure panel covers it");
                assert(runs.shouldRenderRunAsBoardFocus(stalledRun),
                    "stalled run should stay as a focused processing task card");
                const compactRunMarkup = runs.renderSourceRunListItem(stalledRun, true);
                assert(compactRunMarkup.includes("detail-compact-item active"),
                    "source run list should render compact active rows");
                assert(!compactRunMarkup.includes("run-runtime-grid"),
                    "source run list row should stay compact and not inline runtime snapshot");
                assert(compactRunMarkup.includes("docs/payment/order-guide.md"),
                    "upload run title should prefer current imported file name");
                const runDetailMarkup = runs.buildSourceRunDetailCard(stalledRun, {
                    label: "失败",
                    nextStep: "查看最近推进时间并重新同步资料源",
                    tone: "danger"
                });
                assert(runDetailMarkup.includes("run-runtime-summary"),
                    "selected source run detail should render compact runtime summary");
                assert(runDetailMarkup.includes("本次文件"),
                    "selected source run detail should expose processed file summary");
                assert(runDetailMarkup.includes("最近更新时间"),
                    "selected source run detail should merge timestamps into updated-at copy");
                assert(runDetailMarkup.includes("card-actions"),
                    "selected source run detail should keep action buttons");
                const structuredActionMarkup = runs.buildSourceRunDetailCard({
                    status: "FAILED",
                    displayStatus: "FAILED",
                    displayStatusLabel: "失败",
                    nextStepHint: "检查原因摘要后重新同步资料源",
                    reasonSummary: "编译执行过程中出现异常",
                    actions: [{
                        actionKey: "RESYNC_SOURCE",
                        label: "重新同步当前资料源",
                        buttonClass: "secondary-btn",
                        runId: 12,
                        sourceId: 99,
                        uploadRetry: false
                    }]
                }, {
                    label: "处理失败",
                    nextStep: "检查原因摘要后重新同步资料源",
                    tone: "danger"
                });
                assert(structuredActionMarkup.includes("data-resync-source='99'"),
                    "detail card should render backend-provided structured action");

                const succeededRun = {
                    status: "SUCCEEDED",
                    displayStatus: "SUCCEEDED",
                    sourceType: "UPLOAD",
                    resolverDecision: "NEW_SOURCE",
                    title: "卡券三期-迁移方案.md",
                    message: "处理成功，资料已写入知识库",
                    updatedAt: "2026-05-02T15:08:00+08:00"
                };
                assert(!runs.shouldRenderRunAsBoardFocus(succeededRun),
                    "succeeded run should not occupy the strong current-task card");
                assert(runs.shouldRenderRunAsCompletionNotice(succeededRun),
                    "succeeded run should render as lightweight completion notice");
                const succeededRunMarkup = runs.renderRecentRunBoardItem(succeededRun);
                assert(succeededRunMarkup.includes("run-completion-notice"),
                    "succeeded run should use the completion notice presentation");
                assert(!succeededRunMarkup.includes("run-progress-strip"),
                    "succeeded completion notice should not duplicate full history details");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.recentRuns = [succeededRun];
                assert(runs.shouldPromoteCompletionRunAsBoardFocus(succeededRun),
                    "latest completion run should be promoted when there are no active focus tasks");
                const promotedRunMarkup = runs.renderRecentRunBoardItem(succeededRun);
                assert(promotedRunMarkup.includes("run-spotlight-card"),
                    "promoted completion run should render as a spotlight card");
                assert(!promotedRunMarkup.includes("当前阶段"),
                    "promoted completion run should hide duplicated stage highlight");

                const failedRun = {
                    status: "FAILED",
                    reasonSummary: "调用模型时发生链路异常，请检查网络、路由配置或模型服务可用性。",
                    compileErrorCode: "LLM_TRANSPORT_ERROR"
                };
                assert(runs.buildRunReasonSummary(failedRun).includes("链路异常"),
                    "failed run should prefer backend-provided reason summary");
                assert(!runs.shouldRenderRunReasonSummary(failedRun),
                    "failed run should hide duplicate reason summary when failure panel covers it");
                const sanitized = runs.sanitizeDisplayMessage(
                    "java.net.SocketTimeoutException: Read timed out\\n at com.example.Test"
                );
                assert(sanitized === "Read timed out",
                    "sanitizeDisplayMessage should strip exception class and stack trace");

                const conflictMessage = runs.resolveHttpErrorDisplayMessage({
                    payload: {
                        code: "SOURCE_SYNC_CONFLICT",
                        message: "java.lang.IllegalStateException: conflict"
                    },
                    message: "boom"
                });
                assert(conflictMessage.includes("已经有运行中的同步任务"),
                    "should use stable conflict message instead of raw backend message");

                const elementState = {};
                sandbox.document.getElementById = function (id) {
                    if (!elementState[id]) {
                        elementState[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false
                        };
                    }
                    return elementState[id];
                };
                articleUi.renderArticleDetail({
                    articleKey: "article-001",
                    conceptId: "article-001",
                    title: "入库时间测试",
                    content: "正文",
                    lifecycle: "ACTIVE",
                    reviewStatus: "passed",
                    summary: "摘要",
                    sourceCount: 1,
                    sourcePaths: ["docs/demo.md"],
                    riskLevel: "low",
                    riskReasons: [],
                    updatedAt: "2026-05-02T15:08:00+08:00",
                    compiledAt: "2026-05-10T22:30:00+08:00"
                });
                assert(elementState["article-detail-meta"].textContent.includes("入库时间：05/02 15:08"),
                    "article detail should render stored updatedAt as ingestion time");
                assert(!elementState["article-detail-meta"].textContent.includes("05/10 22:30"),
                    "article detail should not render compiledAt as ingestion time");
                assert(articleUi.buildArticleListRequestUrl("订单", "ACTIVE", "12", "needs_human_review")
                        === "/api/v1/admin/articles?query=%E8%AE%A2%E5%8D%95&lifecycle=ACTIVE&sourceId=12&reviewStatus=needs_human_review",
                    "article list request should include generic reviewStatus filter");
                assert(articleUi.buildArticleListRequestUrl("订单", "ACTIVE", "12", "passed", "riskReason:user_reported")
                        === "/api/v1/admin/articles?query=%E8%AE%A2%E5%8D%95&lifecycle=ACTIVE&sourceId=12&reviewStatus=passed&riskReason=user_reported",
                    "article list request should include generic risk filter");
                assert(articleUi.buildArticleRiskSummary({
                    riskLevel: "high",
                    riskReasons: ["source_conflict", "low_traceability"],
                    isHotspot: true,
                    requiresResultVerification: true
                }).includes("来源冲突"),
                    "risk summary should render generic risk reasons");
                assert(articleUi.shouldShowArticleReviewPanel({ reviewStatus: "needs_human_review" }),
                    "needs_human_review article should show manual review panel");
                assert(articleUi.shouldShowArticleReviewPanel({ reviewStatus: "needs_review" }),
                    "needs_review article should show manual review panel");
                assert(!articleUi.shouldShowArticleReviewPanel({ reviewStatus: "passed" }),
                    "passed article should hide manual review panel");
                assert(articleUi.buildArticleReviewNote({ reviewStatus: "needs_review" }).includes("提交过修正"),
                    "needs_review note should explain correction state");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedArticleId = "article-001";
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedArticleSourceId = 7;
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedArticleReviewStatus = "needs_human_review";
                elementState["article-reviewer"].value = "";
                elementState["article-review-comment"].value = "证据稳定";
                elementState["article-correction-summary"].value = "补充来源";
                const articleReviewRequest = articleUi.buildArticleReviewRequest(true);
                assert(articleReviewRequest.sourceId === 7,
                    "manual review request should keep selected source id");
                assert(articleReviewRequest.reviewedBy === "admin",
                    "manual review request should default reviewer");
                assert(articleReviewRequest.expectedReviewStatus === "needs_human_review",
                    "manual review request should carry expected status");
                assert(articleReviewRequest.correctionSummary === "补充来源",
                    "request-changes payload should include correction summary");
                articleUi.renderArticleDetail({
                    articleKey: "article-002",
                    conceptId: "article-002",
                    title: "人工复核测试",
                    content: "正文",
                    lifecycle: "ACTIVE",
                    reviewStatus: "needs_human_review",
                    summary: "摘要",
                    sourceCount: 1,
                    sourcePaths: ["docs/review.md"],
                    riskLevel: "medium",
                    riskReasons: ["user_reported"],
                    isHotspot: true,
                    requiresResultVerification: true,
                    updatedAt: "2026-05-05T10:00:00+08:00"
                }, {
                    items: [{
                        action: "approve",
                        previousReviewStatus: "needs_human_review",
                        nextReviewStatus: "passed",
                        reviewedBy: "reviewer",
                        reviewedAt: "2026-05-05T10:20:00+08:00",
                        comment: "确认通过"
                    }]
                });
                assert(elementState["article-review-panel"].hidden === false,
                    "manual review panel should be visible for needs_human_review detail");
                assert(elementState["article-reviewer"].value === "admin",
                    "manual review panel should set default reviewer");
                assert(elementState["article-review-history"].innerHTML.includes("确认通过"),
                    "review history should render readable approve action");
                assert(elementState["article-risk-summary"].innerHTML.includes("用户反馈"),
                    "detail should render readable risk notice");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.sourceFiles = [{
                    sourceId: 7,
                    relativePath: "docs/review.md",
                    format: "md",
                    contentPreview: "preview text"
                }];
                const sourceReferenceMarkup = articleUi.renderArticleSourceReferences({
                    sourceId: 7,
                    sourcePaths: ["docs/review.md"]
                });
                assert(sourceReferenceMarkup.includes("data-article-source-path"),
                    "source references should expose preview trigger for matching source file");
                articleUi.renderArticleSourcePreview({
                    relativePath: "docs/review.md",
                    format: "md",
                    contentPreview: "preview text"
                });
                assert(elementState["article-source-preview"].hidden === false,
                    "source preview panel should become visible");
                assert(elementState["article-source-preview"].innerHTML.includes("preview text"),
                    "source preview panel should render contentPreview only");
                const summaryElements = {};
                sandbox.document.getElementById = function (id) {
                    if (!summaryElements[id]) {
                        summaryElements[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false,
                            dataset: {},
                            setAttribute: function (name, value) {
                                this[name] = value;
                            },
                            querySelectorAll: function () { return []; }
                        };
                    }
                    return summaryElements[id];
                };
                sandbox.__LATTICE_ADMIN_TEST_STATE__.sources = [];
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview = {
                    status: {
                        articleCount: 3,
                        sourceFileCount: 2,
                        contributionCount: 0,
                        pendingQueryCount: 0,
                        reviewPendingArticleCount: 1,
                        highRiskArticleCount: 2,
                        hotspotPendingVerificationCount: 1,
                        userReportedAnswerCount: 1,
                        answerFeedbackPendingCount: 2
                    }
                };
                knowledgeUi.renderSummary(sandbox.__LATTICE_ADMIN_TEST_STATE__.overview, {});
                assert(summaryElements["summary-cards"].innerHTML.includes("需复核内容"),
                    "summary cards should expose manual review count");
                assert(summaryElements["summary-cards"].innerHTML.includes("高风险内容"),
                    "summary cards should expose high risk count");
                assert(summaryElements["summary-cards"].innerHTML.includes("复核状态筛选"),
                    "summary card should guide to review status filter");
                assert(summaryElements["summary-cards"].innerHTML.includes("结果反馈待处理"),
                    "summary cards should expose answer feedback pending count");
                const helpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(helpState.description.includes("复核状态筛选"),
                    "help state should guide to article review status filter instead of all-article review");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.reviewPendingArticleCount = 0;
                const feedbackHelpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(feedbackHelpState.actions[0].action === "knowledge-feedback",
                    "help state should route to answer feedback queue when only result feedback is pending");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.answerFeedbackPendingCount = 0;
                const hotspotHelpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(hotspotHelpState.title.includes("高频热点"),
                    "help state should expose hotspot verification entry before generic high-risk entry");
                assert(hotspotHelpState.description.includes("待结果抽检"),
                    "hotspot help state should guide to result verification filter");
                assert(articleUi.buildHotspotRefreshStatusText({
                    hotspotCandidateCount: 2,
                    updatedArticleCount: 1,
                    heatScoreThreshold: 3
                }).includes("更新 1"), "hotspot refresh status should include updated article count");
                articleUi.renderHotspotRefreshStatus({
                    hotspotCandidateCount: 2,
                    updatedArticleCount: 1,
                    heatScoreThreshold: 3
                });
                assert(summaryElements["hotspot-refresh-status"].dataset.status === "refreshed",
                    "hotspot refresh status should record refreshed state");
                assert(feedbackUi.buildQueryFeedbackListRequestUrl("PENDING", 20)
                    === "/api/v1/admin/query-feedback?status=PENDING&limit=20",
                    "feedback list request should include generic status filter");
                const feedbackMarkup = feedbackUi.renderQueryFeedbackListItem({
                    id: 9,
                    status: "PENDING",
                    feedbackType: "answer_problem",
                    question: "接口用途是什么",
                    answerSummary: "答案混入了不相关内容",
                    queryId: "query-9",
                    reportedBy: "reviewer",
                    createdAt: "2026-05-05T11:20:00+08:00"
                });
                assert(feedbackMarkup.includes("答案有问题"),
                    "feedback list should render readable feedback type");
                summaryElements["query-feedback-handler"] = { value: "handler" };
                summaryElements["query-feedback-resolution-comment"] = { value: "已补充回归" };
                const feedbackHandleRequest = feedbackUi.buildQueryFeedbackHandleRequest();
                assert(feedbackHandleRequest.handledBy === "handler",
                    "feedback handle request should keep handler");
                assert(feedbackHandleRequest.comment === "已补充回归",
                    "feedback handle request should keep resolution comment");

                const sourceFile = {
                    relativePath: "docs/payment/order-guide.md",
                    format: "md",
                    fileSize: 2048,
                    parseMode: "text_read",
                    parseProvider: "filesystem"
                };
                const compactFileMarkup = sourceUi.renderSourceFileListItem(sourceFile, true);
                assert(compactFileMarkup.includes("order-guide.md"),
                    "source file list should render file base name");
                assert(compactFileMarkup.includes("Markdown"),
                    "source file list should render readable file format");
                assert(compactFileMarkup.includes("文本读取"),
                    "source file list should render readable parse mode");
                assert(!compactFileMarkup.includes(">text_read<"),
                    "source file list should not expose raw parse mode badges");
                assert(!compactFileMarkup.includes("run-runtime-grid"),
                    "source file list row should stay compact");
                const fileDetailMarkup = sourceUi.buildSourceFileDetailCard(sourceFile);
                assert(fileDetailMarkup.includes("完整路径"),
                    "selected source file detail should expose full relative path");
                assert(fileDetailMarkup.includes("本地文件系统"),
                    "selected source file detail should render readable parse provider");
                const legacySource = {
                    name: "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
                    sourceCode: "srkit-svc-fc-dpfm",
                    primaryDocumentTitle: "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
                    metadataJson: JSON.stringify({
                        bundleSummary: {
                            displayName: "卡券三期-迁移方案",
                            relativePathsSample: ["卡券三期-迁移方案.md"],
                            titleHints: ["SRKIT/SVC 卡履约链路从 FC 平移至 DPFM"]
                        }
                    })
                };
                assert(sourceUi.resolveSourceDisplayName(legacySource) === "卡券三期-迁移方案",
                    "source display name should prefer bundle file-oriented display name");
                assert(sourceUi.resolveSourceDocumentTitle(legacySource) === "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
                    "source document title should stay available as secondary metadata");
                const sourceListContainer = { innerHTML: "", querySelectorAll: function () { return []; } };
                sandbox.document.getElementById = function (id) {
                    if (id === "source-list") {
                        return sourceListContainer;
                    }
                    return null;
                };
                sourceUi.renderSourceList([Object.assign({
                    id: 1,
                    status: "ACTIVE",
                    sourceType: "UPLOAD",
                    contentProfile: "DOCUMENT",
                    defaultSyncMode: "AUTO",
                    lastSyncStatus: "RUNNING",
                    lastSyncAt: "2026-05-05T16:54:00+08:00"
                }, legacySource)]);
                assert(sourceListContainer.innerHTML.includes("卡券三期-迁移方案"),
                    "source list should render source-level display name");
                assert(sourceListContainer.innerHTML.includes("source-document-title"),
                    "source list should keep document title as secondary copy");
                const legacySourceDetailElements = {};
                sandbox.document.getElementById = function (id) {
                    if (!legacySourceDetailElements[id]) {
                        legacySourceDetailElements[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false,
                            closest: function () {
                                return { hidden: false };
                            }
                        };
                    }
                    return legacySourceDetailElements[id];
                };
                sourceUi.renderSourceDetail(Object.assign({
                    id: 1,
                    status: "ACTIVE",
                    sourceType: "UPLOAD",
                    contentProfile: "DOCUMENT",
                    defaultSyncMode: "AUTO",
                    configJson: "{}",
                    lastSyncAt: "2026-05-05T16:54:00+08:00"
                }, legacySource), [], []);
                assert(legacySourceDetailElements["source-detail-title"].textContent === "卡券三期-迁移方案",
                    "source detail title should render source-level display name");
                assert(legacySourceDetailElements["source-detail-meta"].textContent.includes("文档标题：SRKIT/SVC 卡履约链路从 FC 平移至 DPFM"),
                    "source detail meta should keep document title separately");
                assert(sourceUi.isUploadSource({ sourceType: "UPLOAD" }),
                    "upload source helper should identify upload sources");
                assert(!sourceUi.isUploadSource({ sourceType: "GIT" }),
                    "upload source helper should ignore non-upload sources");
                assert(sourceUi.resolveSourceProcessingHistoryItems({
                    items: [{ taskId: "compile-job:1" }]
                })[0].taskId === "compile-job:1",
                    "source detail should accept unified processing task list response");
                assert(sourceUi.resolveSourceProcessingHistoryItems([{ runId: 1 }])[0].runId === 1,
                    "source detail should remain compatible with source run arrays");
                assert(sourceUi.shouldFollowLatestSourceRun([], "latest"),
                    "empty source runs should always follow latest");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunKey = null;
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunMode = "auto";
                const failedRunAt = {
                    runId: 11,
                    requestedAt: "2026-05-04T08:05:54+08:00",
                    updatedAt: "2026-05-04T08:15:19+08:00"
                };
                const succeededRunAt = {
                    runId: 12,
                    requestedAt: "2026-05-04T09:38:33+08:00",
                    updatedAt: "2026-05-04T10:10:07+08:00"
                };
                assert(sourceUi.shouldFollowLatestSourceRun([succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(succeededRunAt)),
                    "auto mode should follow the newest run");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunKey = sourceUi.resolveSourceRunKey(failedRunAt);
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunMode = "manual";
                sandbox.__LATTICE_ADMIN_TEST_STATE__.latestSourceRunKey = sourceUi.resolveSourceRunKey(succeededRunAt);
                assert(sourceUi.shouldFollowLatestSourceRun([succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(succeededRunAt)) === false,
                    "manual mode should keep current selection when latest run itself has not changed");
                assert(sourceUi.shouldFollowLatestSourceRun([succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(succeededRunAt)) === false,
                    "manual mode should not jump back to current latest when latest run itself has not changed");
                const newerSucceededRunAt = {
                    runId: 13,
                    requestedAt: "2026-05-04T11:38:33.848574+08:00",
                    updatedAt: "2026-05-04T12:10:07.834603+08:00"
                };
                assert(sourceUi.shouldFollowLatestSourceRun([newerSucceededRunAt, succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(newerSucceededRunAt)),
                    "manual mode should follow latest only when a newer run actually arrives");
                const detailElement = {
                    hidden: false,
                    scrollIntoViewCalled: false,
                    scrollIntoView: function () {
                        this.scrollIntoViewCalled = true;
                    }
                };
                sandbox.document.getElementById = function (id) {
                    if (id === "source-run-detail") {
                        return detailElement;
                    }
                    return null;
                };
                sourceUi.focusSourceRunDetail();
                assert(detailElement.scrollIntoViewCalled,
                    "clicking source run rows should focus the detail panel");

                console.log("management-js-runtime-tests:ok");
                """;
    }

    /**
     * 读取子进程输出。
     *
     * @param process 子进程
     * @return 输出文本
     * @throws IOException IO 异常
     */
    private String readProcessOutput(Process process) throws IOException {
        InputStream inputStream = process.getInputStream();
        byte[] content = inputStream.readAllBytes();
        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
