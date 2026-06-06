const fs = require("fs");
const path = require("path");
const vm = require("vm");

(async function () {

const commonSource = fs.readFileSync(process.argv[2], "utf8");
const moduleDir = process.argv[3];
const modulePrefix = process.argv[4];
function parsePart(name) {
    const moduleSource = fs.readFileSync(path.join(moduleDir, name), "utf8");
    const matched = moduleSource.match(/export default (.*);\s*$/s);
    if (!matched) {
        throw new Error("invalid runtime module: " + name);
    }
    return JSON.parse(matched[1]);
}

const runtimeModuleNames = fs.readdirSync(moduleDir)
    .filter(function (name) { return name.startsWith(modulePrefix + "-runtime-part-"); })
    .sort();
const lastName = runtimeModuleNames[runtimeModuleNames.length - 1];
const namesWithoutLast = runtimeModuleNames.slice(0, -1);
const runtimeParts = namesWithoutLast.map(parsePart);

let historyPart = "";
try {
    const historyModuleSource = fs.readFileSync(path.join(moduleDir, "management-history-part.js"), "utf8");
    const historyMatched = historyModuleSource.match(/export default (.*);\s*$/s);
    if (historyMatched) {
        historyPart = JSON.parse(historyMatched[1]);
    }
} catch (e) {}

runtimeParts.push(parsePart(lastName));
if (historyPart) {
    runtimeParts.push(historyPart);
}
const source = runtimeParts.join("\n");
const documentEventListeners = {};
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
        addEventListener: function (eventName, handler) {
            if (!documentEventListeners[eventName]) {
                documentEventListeners[eventName] = [];
            }
            documentEventListeners[eventName].push(handler);
        },
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

vm.runInNewContext(commonSource, sandbox, { filename: "admin-common.js" });
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
assert(typeof runs.selectRecentRunBoardItems === "function",
    "missing selectRecentRunBoardItems export");

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
    nextStepHint: "检查处理提示后重新同步资料源",
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
    nextStep: "检查处理提示后重新同步资料源",
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
const olderSucceededRun = Object.assign({}, succeededRun, {
    title: "较早完成任务.md",
    updatedAt: "2026-05-01T15:08:00+08:00",
    requestedAt: "2026-05-01T15:00:00+08:00"
});
const latestSucceededRun = Object.assign({}, succeededRun, {
    title: "最新完成任务.md",
    updatedAt: "2026-05-02T15:08:00+08:00",
    requestedAt: "2026-05-02T15:00:00+08:00"
});
const selectedCompletionItems = runs.selectRecentRunBoardItems([olderSucceededRun, latestSucceededRun]);
assert(selectedCompletionItems.length === 1,
    "run board should show only the latest completion when there is no active task");
assert(selectedCompletionItems[0].title === "最新完成任务.md",
    "run board should keep the newest completion notice only");
const operationalNoteMarkup = runs.renderRecentRunBoardItem({
    status: "RUNNING",
    displayStatus: "RUNNING",
    sourceType: "UPLOAD",
    title: "正在处理的资料.md",
    progressText: "正在整理资料",
    operationalNote: "请等待本轮处理完成后再提问",
    requestedAt: "2026-05-02T16:08:00+08:00"
});
assert(operationalNoteMarkup.includes("请等待本轮处理完成后再提问"),
    "operational note should still render useful action copy");
assert(!operationalNoteMarkup.includes("任务线索"),
    "operational note should not expose the old task-clue label");
const waitConfirmSnapshot = runs.buildRunRuntimeSnapshot({
    status: "WAIT_CONFIRM",
    displayStatus: "WAIT_CONFIRM",
    progressText: "等待人工确认",
    reasonSummary: "需要选择这批资料的归并方式"
});
assert(waitConfirmSnapshot.includes("处理提示"),
    "runtime snapshot should use user-facing processing hint label");
assert(!waitConfirmSnapshot.includes("原因摘要"),
    "runtime snapshot should not expose engineering-style reason summary label");
const waitConfirmMarkup = runs.renderRecentRunBoardItem({
    status: "WAIT_CONFIRM",
    displayStatus: "WAIT_CONFIRM",
    title: "待确认资料.md",
    reasonSummary: "需要选择这批资料的归并方式"
});
assert(waitConfirmMarkup.includes("待人工确认"),
    "wait-confirm status should render the unified human-confirmation label");
assert(!waitConfirmMarkup.includes(">待确认<"),
    "wait-confirm status should not render the old short confirmation label");
const publishReviewMarkup = runs.renderRecentRunBoardItem({
    taskType: "SOURCE_SYNC",
    status: "SUCCEEDED",
    displayStatus: "SUCCEEDED",
    displayStatusLabel: "待人工确认",
    requiresManualAction: true,
    sourceType: "UPLOAD",
    syncAction: "UPDATE",
    title: "待人工确认任务",
    reasonSummary: "质量检查已完成，等待人工确认后决定是否入库",
    pendingHumanReviewCount: 2,
    requestedAt: "2026-05-02T16:08:00+08:00"
});
assert(publishReviewMarkup.includes("待人工确认草稿"),
    "task card should distinguish draft count from top-level task count");
assert(publishReviewMarkup.includes("2 篇"),
    "task card should render pending draft count");

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
    "java.net.SocketTimeoutException: Read timed out\n at com.example.Test"
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
// buildArticleRiskSummary: new terms, no old internal terms
var hotspotRiskSummary = articleUi.buildArticleRiskSummary({
    riskLevel: "medium",
    riskReasons: [],
    isHotspot: true,
    requiresResultVerification: true
});
assert(hotspotRiskSummary.includes("高频问题相关"),
    "risk summary should use 高频问题相关 not 高频热点");
assert(!hotspotRiskSummary.includes("高频热点"),
    "risk summary should not expose internal term 高频热点");
assert(hotspotRiskSummary.includes("需关注"),
    "risk summary should use 需关注 not 需要结果抽检");
assert(!hotspotRiskSummary.includes("需要结果抽检"),
    "risk summary should not expose internal term 需要结果抽检");
assert(!hotspotRiskSummary.includes("抽检"),
    "risk summary should not expose internal term 抽检");
var lowRiskNoFlags = articleUi.buildArticleRiskSummary({
    riskLevel: "low",
    riskReasons: [],
    isHotspot: false,
    requiresResultVerification: false
});
assert(lowRiskNoFlags.includes("暂无额外关注原因"),
    "low risk summary should use 暂无额外关注原因 not 暂无额外抽检原因");
assert(!lowRiskNoFlags.includes("抽检"),
    "low risk summary should not expose 抽检");
var hotspotFnSrc = String(articleUi.buildArticleRiskSummary);
assert(!hotspotFnSrc.includes("高频热点"),
    "buildArticleRiskSummary source should not contain 高频热点");
assert(!hotspotFnSrc.includes("需要结果抽检"),
    "buildArticleRiskSummary source should not contain 需要结果抽检");
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
assert(elementState["article-review-history"].innerHTML.includes("review-history-row"),
    "review history should render compact timeline row structure");
assert(elementState["article-review-history"].innerHTML.includes("review-history-action"),
    "review history should render action label");
assert(elementState["article-review-history"].innerHTML.includes("review-history-status"),
    "review history should render status change");
assert(elementState["article-review-history"].innerHTML.includes("review-history-time"),
    "review history should render timestamp");
assert(!elementState["article-review-history"].innerHTML.includes("review-history-head"),
    "review history should not use old dark gray card structure");
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
// 二次渲染回归：验证 closest(".detail-section") 路径连续两次渲染
// 不会产生 details 嵌套、id 丢失或 TypeError
delete elementState["article-metadata"];
var metadataSectionState = { innerHTML: "" };
var technicalInfoState = { innerHTML: "" };
sandbox.document.getElementById = function (id) {
    if (id === "article-technical-info") {
        elementState[id] = technicalInfoState;
        return technicalInfoState;
    }
    if (!elementState[id]) {
        var el = {
            textContent: "",
            innerHTML: "",
            hidden: false
        };
        if (id === "article-metadata") {
            el.closest = function (selector) {
                if (selector === ".detail-section") {
                    return metadataSectionState;
                }
                return null;
            };
        }
        elementState[id] = el;
    }
    return elementState[id];
};
articleUi.renderArticleDetail({
    articleKey: "article-010",
    conceptId: "article-010",
    title: "二次渲染测试 A",
    content: "正文",
    lifecycle: "ACTIVE",
    summary: "摘要",
    sourceCount: 1,
    sourcePaths: ["docs/a.md"],
    updatedAt: "2026-05-02T16:00:00+08:00",
    metadataJson: "a",
    isHotspot: true,
    requiresResultVerification: true
});
var _round1Html = metadataSectionState.innerHTML;
assert(_round1Html.includes("article-metadata-toggle"),
    "first render should wrap metadata in details");
assert(_round1Html.includes("a"),
    "first render should contain first metadata text");
articleUi.renderArticleDetail({
    articleKey: "article-011",
    conceptId: "article-011",
    title: "二次渲染测试 B",
    content: "正文B",
    lifecycle: "ACTIVE",
    summary: "摘要B",
    sourceCount: 1,
    sourcePaths: ["docs/b.md"],
    updatedAt: "2026-05-02T17:00:00+08:00",
    metadataJson: "b"
});
var _round2Html = metadataSectionState.innerHTML;
assert(_round2Html.includes("article-metadata-toggle"),
    "second render should still wrap metadata in details");
assert(_round2Html.includes("b"),
    "second render should show second metadata text");
var _toggleCount = (_round2Html.match(/article-metadata-toggle/g) || []).length;
assert(_toggleCount === 1,
    "second render should not nest multiple article-metadata-toggle, got " + _toggleCount);
assert(elementState["article-metadata"] !== undefined,
    "article-metadata element should still exist after two renders");
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
        humanReviewDraftPendingCount: 2,
        highRiskArticleCount: 2,
        hotspotPendingVerificationCount: 1,
        userReportedAnswerCount: 1,
        answerFeedbackPendingCount: 2
    }
};
knowledgeUi.renderSummary(sandbox.__LATTICE_ADMIN_TEST_STATE__.overview, {});
assert(summaryElements["summary-cards"].innerHTML.includes("summary-primary-grid"),
    "summary cards should render primary metric section");
assert(summaryElements["summary-cards"].innerHTML.includes("summary-secondary-panel"),
    "summary cards should fold lower-priority governance metrics");
assert(summaryElements["summary-cards"].innerHTML.includes("待人工确认草稿"),
    "summary cards should expose compile review queue pending draft count");
assert(summaryElements["summary-cards"].innerHTML.includes("质量检查需要人工确认的草稿"),
    "summary card should distinguish unpublished compile review drafts");
assert(summaryElements["summary-cards"].innerHTML.includes("答案反馈待处理"),
    "summary cards should expose answer feedback pending count with user-facing copy");
assert(summaryElements["summary-cards"].innerHTML.includes("已确认修正"),
    "summary cards should rename feedback contribution to confirmed fixes");
assert(summaryElements["summary-cards"].innerHTML.includes("待分析提问"),
    "summary cards should rename pending query backlog");
assert(summaryElements["summary-cards"].innerHTML.includes("已入库待复核"),
    "summary cards should keep article review backlog as a secondary governance metric");
assert(summaryElements["summary-cards"].innerHTML.includes("高风险内容"),
    "summary cards should expose high risk count");
assert(summaryElements["summary-cards"].innerHTML.includes("复核状态筛选"),
    "summary card should guide to review status filter");
assert(!summaryElements["summary-cards"].innerHTML.includes("反馈沉淀"),
    "summary cards should avoid old engineering-style contribution copy");
assert(!summaryElements["summary-cards"].innerHTML.includes("待处理反馈"),
    "summary cards should avoid ambiguous pending feedback copy");
assert(!summaryElements["summary-cards"].innerHTML.includes("结果反馈待处理"),
    "summary cards should use clearer answer feedback copy");

const metricCardWithAction = knowledgeUi.renderMetricCard({
    label: "待人工确认草稿",
    value: 2,
    action: "{\"tab\":\"knowledge-runs\",\"scrollTo\":\"review-queue-list\"}",
    actionHint: "去处理 →"
});
assert(metricCardWithAction.includes("<button type='button'"),
    "metric card with action should render as button element");
assert(metricCardWithAction.includes("data-metric-action="),
    "metric card with action should render data-metric-action attribute");
assert(metricCardWithAction.includes("clickable"),
    "metric card with action should have clickable class");
assert(metricCardWithAction.includes("action-hint"),
    "metric card with actionHint should render action-hint span");
assert(metricCardWithAction.includes("去处理 →"),
    "metric card action hint should be visible");

const metricCardWithoutAction = knowledgeUi.renderMetricCard({
    label: "知识条目",
    value: 100
});
assert(metricCardWithoutAction.includes("<div"),
    "metric card without action should render as div");
assert(!metricCardWithoutAction.includes("<button"),
    "metric card without action should not contain button tag");
assert(!metricCardWithoutAction.includes("data-metric-action="),
    "metric card without action should not render data-metric-action attribute");
assert(!metricCardWithoutAction.includes("clickable"),
    "metric card without action should not have clickable class");
assert(!metricCardWithoutAction.includes("action-hint"),
    "metric card without actionHint should not render action-hint span");

const metricCardZeroValue = knowledgeUi.renderMetricCard({
    label: "待分析提问",
    value: 0,
    action: undefined,
    actionHint: undefined
});
assert(metricCardZeroValue.includes("<div"),
    "metric card with undefined action should render as div");
assert(!metricCardZeroValue.includes("<button"),
    "metric card with undefined action should not contain button tag");
assert(!metricCardZeroValue.includes("data-metric-action="),
    "metric card with undefined action should not render data-metric-action");
assert(!metricCardZeroValue.includes("clickable"),
    "metric card with undefined action should not have clickable class");
assert(!metricCardZeroValue.includes("action-hint"),
    "metric card with undefined actionHint should not render action-hint span");

const summaryHtml = summaryElements["summary-cards"].innerHTML;
const actionAttrCount = (summaryHtml.match(/data-metric-action='/g) || []).length;
assert(actionAttrCount === 6,
    "summary should render exactly 6 clickable metric cards (pendingQueryCount=0 excluded), got " + actionAttrCount);

const expectedLabels = ["待人工确认草稿", "答案反馈待处理", "待分析提问",
    "已入库待复核", "高风险内容", "关注内容", "用户反馈风险"];
expectedLabels.forEach(function (label) {
    assert(summaryHtml.indexOf(label) !== -1,
        "summary should include metric card label: " + label);
});

var _prevGetElementById = sandbox.document.getElementById;
var filterEl = { value: "" };
sandbox.document.getElementById = function (id) {
    if (id === "article-review-status" || id === "article-risk-filter"
        || id === "query-feedback-status-filter") {
        return filterEl;
    }
    if (id === "search-articles" || id === "refresh-query-feedback") {
        return { click: function () {} };
    }
    return _prevGetElementById(id);
};
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-articles\",\"filters\":{\"article-review-status\":\"pending\"}}");
assert(filterEl.value === "pending",
    "handleMetricCardAction should set article-review-status filter");

knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-feedback\",\"filters\":{\"query-feedback-status-filter\":\"PENDING\"}}");
assert(filterEl.value === "PENDING",
    "handleMetricCardAction should set query-feedback-status-filter filter");

knowledgeUi.handleMetricCardAction("not-json");
assert(true, "handleMetricCardAction should not throw on invalid JSON");

knowledgeUi.handleMetricCardAction("{}");
assert(true, "handleMetricCardAction should not throw on empty config");

const recentRunOverview = { innerHTML: "", dataset: {} };
summaryElements["recent-run-overview"] = recentRunOverview;
runs.renderRecentRunOverview({
    cards: [{
        label: "待确认",
        value: 1,
        note: "仍有任务等待人工处理",
        tone: "warning"
    }, {
        label: "已完成",
        value: 3,
        note: "最近已有资料任务处理结束",
        tone: "success"
    }]
});
assert(recentRunOverview.innerHTML.includes("待人工确认任务"),
    "processing-task overview should express waiting count as task count");
assert(!recentRunOverview.innerHTML.includes(">待确认<"),
    "processing-task overview should not keep the old short waiting label");
const helpState = knowledgeUi.deriveKnowledgeHelpState();
assert(helpState.description.includes("待人工确认"),
    "help state should guide to compile review queue before article review backlog");
assert(helpState.actions[0].action === "knowledge-runs",
    "help state should route compile review queue backlog to current processing tasks");
sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.humanReviewDraftPendingCount = 0;
const articleReviewHelpState = knowledgeUi.deriveKnowledgeHelpState();
assert(articleReviewHelpState.description.includes("复核状态筛选"),
    "help state should guide to article review status filter after compile drafts are cleared");
sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.reviewPendingArticleCount = 0;
const feedbackHelpState = knowledgeUi.deriveKnowledgeHelpState();
assert(feedbackHelpState.actions[0].action === "knowledge-feedback",
    "help state should route to answer feedback queue when only result feedback is pending");
sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.answerFeedbackPendingCount = 0;
const hotspotHelpState = knowledgeUi.deriveKnowledgeHelpState();
assert(hotspotHelpState.title.includes("高频问题"),
    "help state should expose hotspot verification entry before generic high-risk entry");
assert(hotspotHelpState.description.includes("需关注"),
    "hotspot help state should guide to result verification filter");
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
const uploadedSource = {
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
assert(sourceUi.resolveSourceDisplayName(uploadedSource) === "卡券三期-迁移方案",
    "source display name should prefer bundle file-oriented display name");
assert(sourceUi.resolveSourceDocumentTitle(uploadedSource) === "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
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
}, uploadedSource)]);
assert(sourceListContainer.innerHTML.includes("卡券三期-迁移方案"),
    "source list should render source-level display name");
assert(sourceListContainer.innerHTML.includes("source-document-title"),
    "source list should keep document title as secondary copy");
const uploadedSourceDetailElements = {};
sandbox.document.getElementById = function (id) {
    if (!uploadedSourceDetailElements[id]) {
        uploadedSourceDetailElements[id] = {
            textContent: "",
            innerHTML: "",
            hidden: false,
            closest: function () {
                return { hidden: false };
            }
        };
    }
    return uploadedSourceDetailElements[id];
};
sourceUi.renderSourceDetail(Object.assign({
    id: 1,
    status: "ACTIVE",
    sourceType: "UPLOAD",
    contentProfile: "DOCUMENT",
    defaultSyncMode: "AUTO",
    configJson: "{}",
    lastSyncAt: "2026-05-05T16:54:00+08:00"
}, uploadedSource), [], []);
assert(uploadedSourceDetailElements["source-detail-title"].textContent === "卡券三期-迁移方案",
    "source detail title should render source-level display name");
assert(uploadedSourceDetailElements["source-detail-meta"].textContent.includes("文档标题：SRKIT/SVC 卡履约链路从 FC 平移至 DPFM"),
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

const historyUi = sandbox.__LATTICE_ADMIN_TEST__.history;
assert(historyUi, "missing __LATTICE_ADMIN_TEST__.history export");
assert(typeof historyUi.loadProcessingHistory === "function",
    "missing loadProcessingHistory export");
assert(typeof historyUi.applyHistoryFilterAndRender === "function",
    "missing applyHistoryFilterAndRender export");
assert(typeof historyUi.renderHistoryItem === "function",
    "missing renderHistoryItem export");
assert(typeof historyUi.formatElapsed === "function",
    "missing formatElapsed export");

assert(historyUi.formatElapsed(null, null) === "\u2014",
    "formatElapsed should return em-dash for null arguments");

const historyItemMarkup = historyUi.renderHistoryItem({
    sourceName: "测试资料.md",
    title: "测试资料.md",
    sourceType: "UPLOAD",
    displayStatus: "SUCCEEDED",
    requestedAt: "2026-05-20T08:00:00+08:00",
    updatedAt: "2026-05-20T08:05:30+08:00",
    persistedArticleCount: 3,
    sourceId: 42
});
assert(historyItemMarkup.includes("测试资料.md"),
    "history item should render source name");
assert(historyItemMarkup.includes("资料同步"),
    "history item should show source type label");
assert(historyItemMarkup.includes("测试资料"),
    "history item should expose a readable display title");
assert(historyItemMarkup.includes("更新于"),
    "history item should show updated-at label");
assert(historyItemMarkup.includes("data-history-source-id"),
    "history item should render detail button with source id");

const noSourceItemMarkup = historyUi.renderHistoryItem({
    sourceName: "独立编译任务.md",
    title: "独立编译任务.md",
    sourceType: "DIRECT_COMPILE",
    displayStatus: "FAILED",
    requestedAt: "2026-05-20T08:00:00+08:00",
    updatedAt: "2026-05-20T08:05:30+08:00",
    persistedArticleCount: 0
});
assert(noSourceItemMarkup.includes("独立编译"),
    "history item should show standalone compile type");
assert(!noSourceItemMarkup.includes("data-history-source-id"),
    "history item without sourceId should not render detail button");

const loadFnSource = String(historyUi.loadProcessingHistory);
assert(loadFnSource.includes("/api/v1/admin/processing-tasks"),
    "loadProcessingHistory should fetch processing-tasks endpoint");
assert(loadFnSource.includes("status=terminal"),
    "loadProcessingHistory should include status=terminal filter");
assert(loadFnSource.includes("limit=50"),
    "loadProcessingHistory should include limit=50");

const historyModuleSourceText = fs.readFileSync(path.join(moduleDir, "management-history-part.js"), "utf8");
assert(historyModuleSourceText.includes("processing-history-panel"),
    "history module should bind the collapsed processing history panel");
assert(historyModuleSourceText.includes("historyPanel.open"),
    "history module should load terminal tasks when the history panel is opened");

// Verify renderHistoryItem produces balanced div tags
var singleItemMarkup = historyUi.renderHistoryItem({
    sourceName: "测试.md",
    title: "测试.md",
    sourceType: "UPLOAD",
    displayStatus: "SUCCEEDED",
    requestedAt: "2026-05-20T08:00:00+08:00",
    updatedAt: "2026-05-20T08:05:30+08:00",
    persistedArticleCount: 3,
    sourceId: 42
});
var openDivs = (singleItemMarkup.match(/<div/g) || []).length;
var closeDivs = (singleItemMarkup.match(/<\/div>/g) || []).length;
assert(openDivs === closeDivs,
    "renderHistoryItem should produce balanced div tags, got " + openDivs + " opens vs " + closeDivs + " closes");

// Verify two history items render as siblings, not nested
var item1 = historyUi.renderHistoryItem({
    sourceName: "资料A.md",
    title: "资料A.md",
    sourceType: "UPLOAD",
    displayStatus: "SUCCEEDED",
    requestedAt: "2026-05-20T08:00:00+08:00",
    updatedAt: "2026-05-20T08:05:30+08:00",
    persistedArticleCount: 2,
    sourceId: 10
});
var item2 = historyUi.renderHistoryItem({
    sourceName: "资料B.md",
    title: "资料B.md",
    sourceType: "DIRECT_COMPILE",
    displayStatus: "FAILED",
    requestedAt: "2026-05-21T09:00:00+08:00",
    updatedAt: "2026-05-21T09:10:00+08:00",
    persistedArticleCount: 0,
    sourceId: 20
});
var joinedHtml = item1 + item2;
var historyListItemCount = (joinedHtml.match(/class='list-item history-list-item'/g) || []).length;
assert(historyListItemCount === 2,
    "two history items should produce two list-item divs, got " + historyListItemCount);
var firstItemEnd = joinedHtml.indexOf(item2);
var firstItemOnly = joinedHtml.substring(0, firstItemEnd);
assert(firstItemOnly.indexOf("class='list-item history-list-item'") >= 0,
    "item2 should not be nested inside item1's unclosed div");

// History panel should always call loadProcessingHistory when opened (no _historyLoaded guard)
assert(!loadFnSource.includes("_historyLoaded"),
    "loadProcessingHistory should not gate on _historyLoaded; opened panel always loads");

// History empty state rendering
var emptyHistoryContainer = { innerHTML: "" };
var prevGetEl5 = sandbox.document.getElementById;
sandbox.document.getElementById = function (id) {
    if (id === "history-list") { return emptyHistoryContainer; }
    if (id === "history-status") { return { textContent: "" }; }
    return prevGetEl5 ? prevGetEl5(id) : null;
};
sandbox.__LATTICE_ADMIN_TEST__.history.applyHistoryFilterAndRender();
assert(emptyHistoryContainer.innerHTML.includes("暂无已结束的处理任务"),
    "history empty state should explain how tasks end up here");
sandbox.document.getElementById = prevGetEl5;

// History module should initialize on DOMContentLoaded and load terminal tasks when panel opens
const domReadyHandlers = documentEventListeners["DOMContentLoaded"] || [];
assert(domReadyHandlers.length > 0,
    "management runtime should register DOMContentLoaded handlers");

let activatedGroup = null;
let activatedTab = null;
let activatedOptions = null;
const fetchedUrls = [];
let articleFilterChanged = false;
const historyItems = [{
    sourceName: "处理完成资料.md",
    title: "处理完成资料.md",
    sourceType: "UPLOAD",
    displayStatus: "SUCCEEDED",
    requestedAt: "2026-05-20T08:00:00+08:00",
    updatedAt: "2026-05-20T08:05:30+08:00",
    persistedArticleCount: 2,
    sourceId: 88
}];
function createEventfulElement(id) {
    return {
        id: id,
        innerHTML: "",
        textContent: "",
        value: "",
        open: false,
        hidden: false,
        dataset: {},
        _listeners: {},
        addEventListener: function (eventName, handler) {
            if (!this._listeners[eventName]) {
                this._listeners[eventName] = [];
            }
            this._listeners[eventName].push(handler);
        },
        dispatchEvent: function (event) {
            const handlers = this._listeners[event.type] || [];
            handlers.forEach(function (handler) {
                handler(event);
            });
        },
        querySelectorAll: function () { return []; },
        classList: {
            toggle: function () {},
            add: function () {},
            remove: function () {}
        }
    };
}

const historyPanel = createEventfulElement("processing-history-panel");
const refreshHistoryButton = createEventfulElement("refresh-history");
const historyListElement = createEventfulElement("history-list");
historyListElement.querySelectorAll = function (selector) {
    if (selector !== "[data-history-source-id]") {
        return [];
    }
    return [{
        dataset: { historySourceId: "88" },
        addEventListener: function (eventName, handler) {
            if (eventName === "click") {
                this._click = handler;
            }
        },
        click: function () {
            if (this._click) {
                this._click();
            }
        }
    }];
};
const historyStatusElement = createEventfulElement("history-status");
const articleSourceFilter = createEventfulElement("article-source-filter");
articleSourceFilter.dispatchEvent = function (event) {
    if (event.type === "change") {
        articleFilterChanged = true;
    }
};
const filterButtons = ["all", "succeeded", "failed", "skipped"].map(function (filter) {
    return {
        dataset: { historyFilter: filter },
        _active: filter === "all",
        hasAttribute: function (name) {
            return name === "data-history-filter";
        },
        getAttribute: function (name) {
            return name === "data-history-filter" ? this.dataset.historyFilter : null;
        },
        closest: function (selector) {
            return selector === "[data-history-filter]" ? this : null;
        },
        classList: {
            toggle: function (_className, enabled) {
                if (_className === "active") {
                    this._owner._active = !!enabled;
                }
            },
            _owner: null
        }
    };
});
filterButtons.forEach(function (button) {
    button.classList._owner = button;
});
const historyFilterBar = createEventfulElement("history-filter-bar");
historyFilterBar.querySelectorAll = function () {
    return filterButtons;
};
const prevFetch = sandbox.fetch;
const prevGetEl6 = sandbox.document.getElementById;
const prevQuerySelector = sandbox.document.querySelector;
const prevAdminTabs = sandbox.window.AdminTabs;
const prevEventCtor = sandbox.Event;
sandbox.fetch = function (url) {
    fetchedUrls.push(url);
    return Promise.resolve({
        ok: true,
        headers: { get: function () { return "application/json"; } },
        json: function () {
            return Promise.resolve({ items: historyItems });
        }
    });
};
sandbox.window.AdminTabs = {
    activate: function (groupName, tabName, options) {
        activatedGroup = groupName;
        activatedTab = tabName;
        activatedOptions = options;
    }
};
sandbox.Event = function (type, init) {
    this.type = type;
    this.bubbles = !!(init && init.bubbles);
};
sandbox.document.getElementById = function (id) {
    if (id === "processing-history-panel") { return historyPanel; }
    if (id === "refresh-history") { return refreshHistoryButton; }
    if (id === "history-list") { return historyListElement; }
    if (id === "history-status") { return historyStatusElement; }
    if (id === "article-source-filter") { return articleSourceFilter; }
    return prevGetEl6 ? prevGetEl6(id) : null;
};
sandbox.document.querySelector = function (selector) {
    if (selector === ".history-filter-bar") {
        return historyFilterBar;
    }
    return prevQuerySelector ? prevQuerySelector(selector) : null;
};
domReadyHandlers.forEach(function (handler) {
    handler();
});
assert((historyPanel._listeners.toggle || []).length > 0,
    "history panel should bind toggle listener during DOMContentLoaded");
historyPanel.open = true;
historyPanel.dispatchEvent({ type: "toggle" });
await Promise.resolve();
await Promise.resolve();
await new Promise(function (resolve) {
    setImmediate(resolve);
});
assert(fetchedUrls.some(function (url) {
    return String(url || "").includes("/api/v1/admin/processing-tasks?limit=50&status=terminal");
}),
    "opening history panel should request terminal processing tasks");
assert(historyListElement.innerHTML.includes("处理完成资料.md"),
    "history panel should render fetched terminal task list");
assert(historyStatusElement.textContent.includes("共 1 条"),
    "history panel should show visible count after loading all terminal tasks");
const clickHandlers = historyFilterBar._listeners.click || [];
assert(clickHandlers.length > 0,
    "history filter bar should bind click listener during DOMContentLoaded");
clickHandlers[0]({ target: filterButtons[1] });
assert(filterButtons[1]._active === true,
    "clicked history filter button should become active");
assert(filterButtons[0]._active === false,
    "previous history filter button should lose active state");
assert(historyStatusElement.textContent.includes("已入库 1 / 1 条"),
    "history panel should describe current filter and counts after filtering");
historyUi.viewHistoryDetail(88);
assert(activatedGroup === "knowledge-console" && activatedTab === "knowledge-articles",
    "viewHistoryDetail should switch to knowledge articles tab");
assert(activatedOptions && activatedOptions.scroll === true,
    "viewHistoryDetail should keep scroll navigation");
assert(articleSourceFilter.value === "88",
    "viewHistoryDetail should prefill article source filter");
assert(articleFilterChanged,
    "viewHistoryDetail should trigger article source filter change event");
sandbox.fetch = prevFetch;
sandbox.document.getElementById = prevGetEl6;
sandbox.document.querySelector = prevQuerySelector;
sandbox.window.AdminTabs = prevAdminTabs;
sandbox.Event = prevEventCtor;

// Hotspot copy: old internal terms must be absent from runtime
var summarySrc = String(knowledgeUi.renderSummary);
assert(!summarySrc.includes("抽检"),
    "renderSummary should not expose internal term: 抽检");
assert(!summarySrc.includes("待验证"),
    "renderSummary should not expose internal term: 待验证");
assert(!summarySrc.includes("刷新热点"),
    "renderSummary should not expose internal term: 刷新热点");
assert(!summarySrc.includes("待结果抽检"),
    "renderSummary should not expose internal term: 待结果抽检");

// Hotspot copy: new user-facing terms present
assert(summarySrc.includes("关注内容") || summarySrc.includes("需关注") || summarySrc.includes("高频问题相关"),
    "renderSummary should use user-facing hotspot terms (关注内容/需关注/高频问题相关)");

// Part-04: old terms absent from buildArticleRiskSummary
var part04RiskSrc = String(articleUi.buildArticleRiskSummary);
assert(!part04RiskSrc.includes("结果抽检"),
    "part-04 should not expose internal term: 结果抽检");
assert(!part04RiskSrc.includes("需要结果抽检"),
    "part-04 should not expose internal term: 需要结果抽检");
assert(!part04RiskSrc.includes("暂无额外抽检原因"),
    "part-04 should not expose internal term: 暂无额外抽检原因");
assert(!part04RiskSrc.includes("高频热点"),
    "part-04 should not expose internal term: 高频热点");
assert(part04RiskSrc.includes("高频问题相关"),
    "buildArticleRiskSummary should use 高频问题相关");
assert(part04RiskSrc.includes("暂无额外关注原因"),
    "buildArticleRiskSummary should use 暂无额外关注原因");

// Part-04: review history compact timeline structure
var _part04ReviewHistorySrc = String(articleUi.renderArticleReviewHistory);
assert(_part04ReviewHistorySrc.includes("review-history-row"),
    "renderArticleReviewHistoryItem should use compact timeline row class");
assert(_part04ReviewHistorySrc.includes("review-history-action"),
    "renderArticleReviewHistoryItem should render action label");
assert(_part04ReviewHistorySrc.includes("review-history-meta"),
    "renderArticleReviewHistoryItem should use metadata row for comment/reviewer");
assert(!_part04ReviewHistorySrc.includes("review-history-head"),
    "renderArticleReviewHistoryItem should not use old dark gray card class");

// getBadgeLabel HOTSPOT_UNVERIFIED verified via risk summary output:
// hotspot flag → "高频问题相关" (not "高频热点" or "热点未验证")
var hotspotOnlySummary = articleUi.buildArticleRiskSummary({
    riskLevel: "low",
    riskReasons: [],
    isHotspot: true,
    requiresResultVerification: false
});
assert(hotspotOnlySummary.includes("高频问题相关"),
    "getBadgeLabel via riskSummary should map hotspot to 高频问题相关");
assert(!hotspotOnlySummary.includes("高频热点"),
    "riskSummary should not expose 高频热点");

// isTechKeyword tests
assert(typeof articleUi.isTechKeyword === "function",
    "missing isTechKeyword export");
assert(articleUi.isTechKeyword("docs/readme.md") === true,
    "isTechKeyword should detect file extension");
assert(articleUi.isTechKeyword("app.config.key") === true,
    "isTechKeyword should detect dotted config key");
assert(articleUi.isTechKeyword("my_variable_name") === true,
    "isTechKeyword should detect snake_case");
assert(articleUi.isTechKeyword("/path/to/file") === true,
    "isTechKeyword should detect path with slash");
assert(articleUi.isTechKeyword("foo=bar") === true,
    "isTechKeyword should detect key=value");
assert(articleUi.isTechKeyword("https://example.com") === true,
    "isTechKeyword should detect URL");
assert(articleUi.isTechKeyword("机器学习") === false,
    "isTechKeyword should not flag Chinese text");
assert(articleUi.isTechKeyword("payment") === false,
    "isTechKeyword should not flag simple word");
assert(articleUi.isTechKeyword("order-processing") === false,
    "isTechKeyword should not flag kebab-case");
assert(articleUi.isTechKeyword("") === true,
    "isTechKeyword should treat empty string as tech");

// Verify normalizeArticleKeywords is exported
assert(typeof articleUi.normalizeArticleKeywords === "function",
    "missing normalizeArticleKeywords export");

// Mock page-notice element to verify setStatus in handleMetricCardAction
var _pageNoticeEl = {
    hidden: true,
    textContent: "",
    className: ""
};
var _prevGetElementById3 = sandbox.document.getElementById;
sandbox.document.getElementById = function (id) {
    if (id === "page-notice") {
        return _pageNoticeEl;
    }
    if (id === "article-review-status" || id === "article-risk-filter"
        || id === "query-feedback-status-filter") {
        return filterEl;
    }
    if (id === "search-articles" || id === "refresh-query-feedback") {
        return { click: function () {} };
    }
    return _prevGetElementById3(id);
};
filterEl.value = "";
_pageNoticeEl.textContent = "";
_pageNoticeEl.className = "";
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-articles\",\"filters\":{\"article-review-status\":\"pending\"}}");
assert(_pageNoticeEl.hidden === false,
    "handleMetricCardAction should show page-notice for articles tab");
assert(_pageNoticeEl.textContent !== "",
    "handleMetricCardAction should set status message for articles tab");

_pageNoticeEl.textContent = "";
_pageNoticeEl.className = "";
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-feedback\",\"filters\":{\"query-feedback-status-filter\":\"PENDING\"}}");
assert(_pageNoticeEl.hidden === false,
    "handleMetricCardAction should show page-notice for feedback tab");
assert(_pageNoticeEl.textContent !== "",
    "handleMetricCardAction should set status message for feedback tab");

// resolveArticleMetricFilterMessage tests
assert(typeof knowledgeUi.resolveArticleMetricFilterMessage === "function",
    "missing resolveArticleMetricFilterMessage export");
var highRiskMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-risk-filter": "riskLevel:high"});
assert(highRiskMsg.includes("高风险"),
    "resolveArticleMetricFilterMessage should mention high risk for riskLevel:high");
var hotspotMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-risk-filter": "requiresResultVerification:true"});
assert(hotspotMsg.includes("高频问题相关内容"),
    "resolveArticleMetricFilterMessage should mention hotspot verification for requiresResultVerification:true");
assert(hotspotMsg.includes("仅用于查看"),
    "resolveArticleMetricFilterMessage should clarify view-only intent for hotspot verification");
var userReportedMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-risk-filter": "riskReason:user_reported"});
assert(userReportedMsg.includes("用户反馈风险"),
    "resolveArticleMetricFilterMessage should mention user reported for riskReason:user_reported");
var reviewMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-review-status": "pending"});
assert(reviewMsg.includes("复核状态"),
    "resolveArticleMetricFilterMessage should mention review status filter");
var emptyMsg = knowledgeUi.resolveArticleMetricFilterMessage(null);
assert(emptyMsg.includes("已切换到已入库内容"),
    "resolveArticleMetricFilterMessage should return default message for null filters");
assert(emptyMsg.includes("如列表为空"),
    "resolveArticleMetricFilterMessage should include empty-result hint");

// HandleMetricCardAction for hotspot (requiresResultVerification)
_pageNoticeEl.textContent = "";
_pageNoticeEl.className = "";
filterEl.value = "";
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-articles\",\"filters\":{\"article-risk-filter\":\"requiresResultVerification:true\"}}");
assert(_pageNoticeEl.textContent.includes("高频问题相关内容"),
    "handleMetricCardAction should show hotspot-specific status for requiresResultVerification:true");
assert(_pageNoticeEl.textContent.includes("仅用于查看"),
    "handleMetricCardAction should include view-only hint for hotspot without processing closure");

// HandleMetricCardAction for user-reported risk
_pageNoticeEl.textContent = "";
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-articles\",\"filters\":{\"article-risk-filter\":\"riskReason:user_reported\"}}");
assert(_pageNoticeEl.textContent.includes("用户反馈风险"),
    "handleMetricCardAction should show user-reported-specific status");

// HandleMetricCardAction for high risk
_pageNoticeEl.textContent = "";
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-articles\",\"filters\":{\"article-risk-filter\":\"riskLevel:high\"}}");
assert(_pageNoticeEl.textContent.includes("高风险"),
    "handleMetricCardAction should show high-risk-specific status");

// pendingQueryCount > 0 but no data-metric-action
var pendingCardMarkup = knowledgeUi.renderMetricCard({
    label: "待分析提问",
    value: 27,
    action: undefined,
    actionHint: undefined
});
assert(pendingCardMarkup.includes("<div"),
    "pendingQuery card with count>0 should render as div when action is undefined");
assert(!pendingCardMarkup.includes("data-metric-action="),
    "pendingQuery card with count>0 should not render data-metric-action");

// normalizeArticleKeywords test: verify keywords from raw data (no DOM scanning)
var _prevGetEl4 = sandbox.document.getElementById;
var _articleRelationsEl = { innerHTML: "" };
sandbox.document.getElementById = function (id) {
    if (id === "article-relations") {
        return _articleRelationsEl;
    }
    if (id === "page-notice") {
        return _pageNoticeEl;
    }
    if (id === "article-review-status" || id === "article-risk-filter"
        || id === "query-feedback-status-filter") {
        return filterEl;
    }
    if (id === "search-articles" || id === "refresh-query-feedback") {
        return { click: function () {} };
    }
    return _prevGetEl4 ? _prevGetEl4(id) : null;
};
// Simulate article with keywords including tech keywords
sandbox.__LATTICE_ADMIN_TEST_STATE__._articleKeywordData = {
    keywords: ["机器学习", "支付系统", "订单处理", "用户认证", "数据同步", "缓存策略", "消息队列"],
    dependsOn: ["docs/readme.md"],
    related: ["app.config.key", "my_variable_name"]
};
articleUi.normalizeArticleKeywords();
var keywordHtml = _articleRelationsEl.innerHTML;
assert(keywordHtml.includes("article-keyword-section"),
    "normalizeArticleKeywords should render keyword section");
assert(keywordHtml.includes("article-keyword-visible"),
    "normalizeArticleKeywords should render visible keyword area");
// Should show max 6 visible normal keywords
assert(keywordHtml.includes("机器学习"),
    "normalizeArticleKeywords should include Chinese keyword");
// Should NOT include "关键词:" prefix (clean text only)
assert(!keywordHtml.includes("关键词: 机器学习"),
    "normalizeArticleKeywords should NOT prefix with '关键词: '");
// "还有 N 个关键词" should be in a details/summary, not a pill
assert(keywordHtml.includes("article-keyword-toggle"),
    "normalizeArticleKeywords should render expandable toggle for extra keywords");
assert(keywordHtml.includes("还有 "),
    "toggle should include '还有 N 个关键词' label");
// The count should include tech + overflow keywords
assert(keywordHtml.includes("article-relations-aux"),
    "normalizeArticleKeywords should render auxiliary relations section");
assert(keywordHtml.includes("关联信息"),
    "auxiliary section should include '关联信息' label");

// Verify pendingQueryCount card has no dev-facing copy
assert(!pendingCardMarkup.includes("去处理"),
    "pendingQuery card with count>0 should not contain '去处理' action hint");
assert(!pendingCardMarkup.includes("待开放"),
    "pendingQuery card should not contain dev-facing '待开放' copy");
assert(!summaryHtml.includes("处理入口待开放"),
    "summary should not contain dev-facing '处理入口待开放' copy");

// Verify actionHint semantics: only cards with backend closure use "去处理"
assert(summaryHtml.includes("去确认 \u2192"),
    "draft card should use '去确认' action hint");
assert(summaryHtml.includes("查看反馈 \u2192"),
    "feedback card should use '查看反馈' action hint");
// 3 cards have backend processing closures: 已入库待复核, 高风险内容, 用户反馈风险
var quChuLiCount = (summaryHtml.match(/去处理 \u2192/g) || []).length;
assert(quChuLiCount === 3,
    "exactly 3 cards (manualReview, highRisk, userReported) should use 去处理, found: " + quChuLiCount);
assert(summaryHtml.includes("查看内容 \u2192"),
    "hotspot card should use '查看内容' action hint");

// handleMetricCardAction scrollTo for articles and feedback tabs
var scrollTargetEl = { scrollIntoViewCalled: false, scrollIntoView: function () { this.scrollIntoViewCalled = true; } };
sandbox.document.getElementById = function (id) {
    if (id === "article-list" || id === "query-feedback-list") {
        return scrollTargetEl;
    }
    if (id === "page-notice") {
        return _pageNoticeEl;
    }
    if (id === "article-review-status" || id === "article-risk-filter"
        || id === "query-feedback-status-filter") {
        return filterEl;
    }
    if (id === "search-articles" || id === "refresh-query-feedback") {
        return { click: function () {} };
    }
    return _prevGetEl4 ? _prevGetEl4(id) : null;
};
scrollTargetEl.scrollIntoViewCalled = false;
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-articles\",\"filters\":{\"article-review-status\":\"pending\"},\"scrollTo\":\"article-list\"}");
// scrollTo is async (setTimeout 200ms), so assert no immediate error
assert(true, "handleMetricCardAction with articles scrollTo should not throw");

scrollTargetEl.scrollIntoViewCalled = false;
knowledgeUi.handleMetricCardAction(
    "{\"tab\":\"knowledge-feedback\",\"filters\":{\"query-feedback-status-filter\":\"PENDING\"},\"scrollTo\":\"query-feedback-list\"}");
assert(true, "handleMetricCardAction with feedback scrollTo should not throw");

// Verify article detail metadata h4 renamed to dev-facing "开发诊断信息"
assert(metadataSectionState.innerHTML.includes("开发诊断信息"),
    "metadata section h4 should use '开发诊断信息' not '技术元数据'");
assert(!metadataSectionState.innerHTML.includes("技术元数据"),
    "metadata section should not contain old '技术元数据' copy");
assert(!metadataSectionState.innerHTML.includes("技术信息"),
    "metadata summary should not contain old '技术信息' copy");
// details should be closed by default (no 'open' attribute)
assert(!metadataSectionState.innerHTML.includes("<details open"),
    "metadata details should be closed by default");
// article-technical-info now rendered inside 开发诊断信息 details
assert(metadataSectionState.innerHTML.includes("article-technical-info"),
    "article-technical-info div should exist inside metadata details");
assert(metadataSectionState.innerHTML.includes("article-metadata-toggle"),
    "metadata section should still wrap details toggle");
// Verify renderArticleDetail source still references article-technical-info
// (now rendered inside the metadata collapsible section, not standalone)
var renderDetailSrc = String(articleUi.renderArticleDetail);
assert(renderDetailSrc.includes("article-technical-info"),
    "renderArticleDetail should render technical info inside metadata details");

// clearArticleDetail null-guard: should not throw when article-technical-info is missing
var _prevGetElForClear = sandbox.document.getElementById;
sandbox.document.getElementById = function (id) {
    if (id === "article-technical-info") {
        return null;
    }
    if (!elementState[id]) {
        elementState[id] = {
            textContent: "",
            innerHTML: "",
            hidden: false
        };
    }
    return elementState[id];
};
var clearErr = null;
try {
    articleUi.clearArticleDetail();
} catch (e) {
    clearErr = e;
}
assert(clearErr === null,
    "clearArticleDetail should not throw when article-technical-info is missing, got: " + (clearErr && clearErr.message));
// Verify clearArticleDetail source contains null guard
var clearDetailSrc = String(articleUi.clearArticleDetail);
assert(clearDetailSrc.includes("_techInfo"),
    "clearArticleDetail should null-guard article-technical-info write");
sandbox.document.getElementById = _prevGetEl4;

console.log("management-js-runtime-tests:ok");

})().catch(function (error) {
    console.error(error);
    process.exit(1);
});
