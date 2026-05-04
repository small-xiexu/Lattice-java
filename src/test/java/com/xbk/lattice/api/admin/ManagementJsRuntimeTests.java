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
                const articleUi = sandbox.__LATTICE_ADMIN_TEST__.article;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(runs, "missing __LATTICE_ADMIN_TEST__.runs export");
                assert(sourceUi, "missing __LATTICE_ADMIN_TEST__.source export");
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
                assert(runtimeSnapshot.includes("编译态"),
                    "runtime snapshot should expose derived status");
                assert(runtimeSnapshot.includes("质量检查"),
                    "runtime snapshot should expose current step label");
                assert(runtimeSnapshot.includes("2 / 6"),
                    "runtime snapshot should expose current progress");
                assert(runtimeSnapshot.includes("原因摘要"),
                    "runtime snapshot should expose reason summary");
                const progressStrip = runs.buildRunProgressStrip(stalledRun, {
                        label: "失败",
                        nextStep: "查看最近推进时间并重新同步资料源",
                        tone: "danger"
                    });
                assert(progressStrip.includes("run-progress-detail"),
                    "progress strip should expose detail copy for current real sub-step");
                assert(progressStrip.includes("质量检查"),
                    "progress strip should show current real compile step under grouped stage");
                assert(progressStrip.includes("正在审查文章草稿"),
                    "progress strip should keep cleaned detail copy");
                assert(!progressStrip.includes("细分状态"),
                    "progress strip should not render redundant detail label");
                assert(runs.buildRunReasonSummary(stalledRun).includes("长时间没有新的心跳"),
                    "stalled run should explain stalled reason");
                assert(runs.shouldRenderRunReasonSummary(stalledRun),
                    "stalled run should render actionable reason summary");
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
                assert(promotedRunMarkup.includes("当前阶段"),
                    "promoted completion run should expose full task highlights");

                const failedRun = {
                    status: "FAILED",
                    reasonSummary: "调用模型时发生链路异常，请检查网络、路由配置或模型服务可用性。",
                    compileErrorCode: "LLM_TRANSPORT_ERROR"
                };
                assert(runs.buildRunReasonSummary(failedRun).includes("链路异常"),
                    "failed run should prefer backend-provided reason summary");
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
                    updatedAt: "2026-05-02T15:08:00+08:00",
                    compiledAt: "2026-05-10T22:30:00+08:00"
                });
                assert(elementState["article-detail-meta"].textContent.includes("入库时间：05/02 15:08"),
                    "article detail should render stored updatedAt as ingestion time");
                assert(!elementState["article-detail-meta"].textContent.includes("05/10 22:30"),
                    "article detail should not render compiledAt as ingestion time");

                const sourceFile = {
                    relativePath: "docs/payment/order-guide.md",
                    format: "md",
                    fileSize: 2048,
                    parseMode: "NATIVE",
                    parseProvider: "default-parser"
                };
                const compactFileMarkup = sourceUi.renderSourceFileListItem(sourceFile, true);
                assert(compactFileMarkup.includes("order-guide.md"),
                    "source file list should render file base name");
                assert(!compactFileMarkup.includes("run-runtime-grid"),
                    "source file list row should stay compact");
                const fileDetailMarkup = sourceUi.buildSourceFileDetailCard(sourceFile);
                assert(fileDetailMarkup.includes("完整路径"),
                    "selected source file detail should expose full relative path");
                assert(sourceUi.isUploadSource({ sourceType: "UPLOAD" }),
                    "upload source helper should identify upload sources");
                assert(!sourceUi.isUploadSource({ sourceType: "GIT" }),
                    "upload source helper should ignore non-upload sources");
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
