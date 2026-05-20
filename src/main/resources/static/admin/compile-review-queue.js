(function () {
    const AdminCommon = window.AdminCommon || {};
    const fetchJson = AdminCommon.fetchJson;
    const escapeHtml = AdminCommon.escapeHtml || function (value) {
        return String(value == null ? "" : value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#39;");
    };
    const formatDateTime = AdminCommon.formatDateTime || function (value) {
        return value ? String(value) : "暂无";
    };

    const state = {
        items: [],
        selectedId: null,
        detail: null
    };

    document.addEventListener("DOMContentLoaded", function () {
        const list = document.getElementById("review-queue-list");
        if (!list || typeof fetchJson !== "function") {
            return;
        }
        bindEvents();
        loadReviewQueue();
    });

    function bindEvents() {
        bindIfPresent("refresh-review-queue", "click", loadReviewQueue);
        const list = document.getElementById("review-queue-list");
        if (list) {
            list.addEventListener("click", handleListClick);
        }
        const detail = document.getElementById("review-queue-detail");
        if (detail) {
            detail.addEventListener("click", handleDetailClick);
        }
    }

    function bindIfPresent(id, eventName, handler) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
    }

    async function loadReviewQueue() {
        setLocalStatus("正在加载待人工确认草稿", "info");
        try {
            const response = await fetchJson("/api/v1/admin/compile/review-queue?status=needs_human_review");
            state.items = Array.isArray(response && response.items) ? response.items : [];
            if (state.selectedId && !state.items.some(function (item) {
                return String(item.id) === String(state.selectedId);
            })) {
                state.selectedId = null;
                state.detail = null;
            }
            renderReviewQueueList(response && response.total != null ? response.total : state.items.length);
            if (!state.selectedId && state.items.length > 0) {
                await loadReviewQueueDetail(state.items[0].id);
                return;
            }
            if (state.selectedId) {
                await loadReviewQueueDetail(state.selectedId);
                return;
            }
            renderEmptyDetail();
            setLocalStatus(state.items.length === 0 ? "当前没有待人工确认草稿" : "待人工确认草稿已加载", "success");
        }
        catch (error) {
            renderReviewQueueList(0);
            renderEmptyDetail();
            showPageError("加载待人工确认草稿失败", error);
            setLocalStatus("加载待人工确认草稿失败：" + getErrorMessage(error), "danger");
        }
    }

    async function loadReviewQueueDetail(id) {
        if (id == null || id === "") {
            return;
        }
        state.selectedId = String(id);
        highlightSelectedItem();
        setLocalStatus("正在加载草稿详情", "info");
        try {
            const detail = await fetchJson("/api/v1/admin/compile/review-queue/" + encodeURIComponent(id));
            state.detail = detail || null;
            renderReviewQueueDetail(state.detail);
            highlightSelectedItem();
            setLocalStatus("草稿详情已加载", "success");
        }
        catch (error) {
            state.detail = null;
            renderEmptyDetail();
            showPageError("加载草稿详情失败", error);
            setLocalStatus("加载草稿详情失败：" + getErrorMessage(error), "danger");
        }
    }

    function handleListClick(event) {
        const button = event.target.closest("[data-review-queue-id]");
        if (!button) {
            return;
        }
        loadReviewQueueDetail(button.dataset.reviewQueueId);
    }

    function handleDetailClick(event) {
        const approveButton = event.target.closest("[data-review-queue-approve]");
        if (approveButton) {
            approveSelectedReviewQueueItem();
            return;
        }
        const rejectButton = event.target.closest("[data-review-queue-reject]");
        if (rejectButton) {
            rejectSelectedReviewQueueItem();
        }
    }

    async function approveSelectedReviewQueueItem() {
        const detail = state.detail;
        if (!detail) {
            setLocalStatus("请先选择一条待人工确认草稿", "warning");
            return;
        }
        const confirmed = window.confirm("确认后文章将进入正式知识库并参与检索。");
        if (!confirmed) {
            return;
        }
        const reviewedBy = window.prompt("请输入确认人", "admin");
        if (!reviewedBy) {
            return;
        }
        const comment = window.prompt("请输入确认说明（可选）", "") || "";
        await submitReviewQueueAction(detail, "approve", reviewedBy, comment, "确认入库完成");
    }

    async function rejectSelectedReviewQueueItem() {
        const detail = state.detail;
        if (!detail) {
            setLocalStatus("请先选择一条待人工确认草稿", "warning");
            return;
        }
        const confirmed = window.confirm("驳回后该草稿不会进入正式知识库。");
        if (!confirmed) {
            return;
        }
        const reviewedBy = window.prompt("请输入驳回人", "admin");
        if (!reviewedBy) {
            return;
        }
        const comment = window.prompt("请输入驳回原因（可选）", "") || "";
        await submitReviewQueueAction(detail, "reject", reviewedBy, comment, "草稿已驳回");
    }

    async function submitReviewQueueAction(detail, action, reviewedBy, comment, successMessage) {
        setActionButtonsDisabled(true);
        setLocalStatus("正在提交人工确认操作", "info");
        try {
            const result = await fetchJson(
                    "/api/v1/admin/compile/review-queue/"
                    + encodeURIComponent(detail.id)
                    + "/"
                    + encodeURIComponent(action),
                    {
                        method: "POST",
                        body: JSON.stringify({
                            reviewedBy: reviewedBy,
                            comment: comment,
                            expectedReviewStatus: detail.reviewStatus || "needs_human_review"
                        })
                    }
            );
            setLocalStatus(successMessage, "success");
            renderActionResult(result, successMessage);
            await loadReviewQueue();
            refreshCurrentProcessingTasks();
        }
        catch (error) {
            showPageError("提交人工确认操作失败", error);
            setLocalStatus("提交人工确认操作失败：" + getErrorMessage(error), "danger");
        }
        finally {
            setActionButtonsDisabled(false);
        }
    }

    function refreshCurrentProcessingTasks() {
        const refreshButton = document.getElementById("refresh-jobs");
        if (refreshButton) {
            refreshButton.click();
        }
    }

    function renderReviewQueueList(total) {
        const list = document.getElementById("review-queue-list");
        const count = document.getElementById("review-queue-count");
        if (count) {
            count.textContent = String(total || 0);
        }
        if (!list) {
            return;
        }
        if (state.items.length === 0) {
            list.innerHTML = "<div class='review-queue-empty'>"
                    + "<p class='item-summary'>当前没有待人工确认草稿。</p>"
                    + "</div>";
            return;
        }
        list.innerHTML = state.items.map(function (item) {
            return "<button class='review-queue-item' data-review-queue-id='"
                    + escapeHtml(item.id)
                    + "' type='button'>"
                    + "<div class='meta-row'>"
                    + "<span class='badge warning'>待人工确认</span>"
                    + "<span class='pill'>" + escapeHtml(formatDateTime(item.updatedAt || item.createdAt)) + "</span>"
                    + "</div>"
                    + "<h4>" + escapeHtml(item.title || "未命名草稿") + "</h4>"
                    + "<p class='item-summary'>质量检查需要人工确认</p>"
                    + "<div class='tag-list'>"
                    + renderSourcePathPills(item.sourcePaths, 2)
                    + "</div>"
                    + "<p class='item-caption'>修复轮次 "
                    + escapeHtml(String(item.fixAttemptCount || 0))
                    + " / "
                    + escapeHtml(String(item.maxFixRounds || 0))
                    + "</p>"
                    + "</button>";
        }).join("");
        highlightSelectedItem();
    }

    function renderReviewQueueDetail(detail) {
        const title = document.getElementById("review-queue-detail-title");
        const meta = document.getElementById("review-queue-detail-meta");
        const container = document.getElementById("review-queue-detail");
        if (!container) {
            return;
        }
        if (!detail) {
            renderEmptyDetail();
            return;
        }
        if (title) {
            title.textContent = detail.title || "未命名草稿";
        }
        if (meta) {
            meta.textContent = buildDetailMeta(detail);
        }
        container.innerHTML = ""
                + "<section class='detail-section'>"
                + "<div class='detail-section-header'>"
                + "<div>"
                + "<h4>草稿正文</h4>"
                + "<p class='detail-section-copy'>确认前请核对正文是否可以进入正式知识库。</p>"
                + "</div>"
                + "</div>"
                + "<pre class='code-view review-queue-content'>" + escapeHtml(detail.content || "暂无正文") + "</pre>"
                + "</section>"
                + "<section class='detail-section'>"
                + "<h4>来源文件</h4>"
                + "<div class='tag-list'>" + renderSourcePathPills(detail.sourcePaths, 20) + "</div>"
                + "</section>"
                + "<section class='detail-section'>"
                + "<h4>待人工确认说明</h4>"
                + renderReviewIssues(detail.reviewIssuesJson)
                + "</section>"
                + "<section class='detail-section'>"
                + "<h4>处理轮次</h4>"
                + "<div class='description-list'>"
                + renderDescriptionItem("已修复轮数", String(detail.fixAttemptCount || 0))
                + renderDescriptionItem("最大修复轮数", String(detail.maxFixRounds || 0))
                + "</div>"
                + "</section>"
                + "<section class='detail-section review-queue-actions'>"
                + "<div class='button-row wrap'>"
                + "<button class='primary-btn' type='button' data-review-queue-approve='true'>确认入库</button>"
                + "<button class='ghost-btn danger-action' type='button' data-review-queue-reject='true'>驳回</button>"
                + "</div>"
                + "</section>"
                + "<details class='advanced-toggle review-queue-technical'>"
                + "<summary>技术详情</summary>"
                + "<div class='advanced-toggle-body'>"
                + renderTechnicalDetails(detail)
                + "</div>"
                + "</details>";
    }

    function renderEmptyDetail() {
        const title = document.getElementById("review-queue-detail-title");
        const meta = document.getElementById("review-queue-detail-meta");
        const container = document.getElementById("review-queue-detail");
        if (title) {
            title.textContent = "请选择一条草稿";
        }
        if (meta) {
            meta.textContent = "";
        }
        if (container) {
            container.innerHTML = "<div class='detail-focus-card'>"
                    + "<p class='item-summary'>从左侧选择一条待人工确认草稿后，可查看正文、来源和待人工确认说明。</p>"
                    + "</div>";
        }
    }

    function renderReviewIssues(reviewIssuesJson) {
        const issues = parseJsonValue(reviewIssuesJson);
        if (!Array.isArray(issues) || issues.length === 0) {
            return "<div class='review-queue-empty'>"
                    + "<p class='item-summary'>质量检查需要人工确认，但未返回结构化问题详情。</p>"
                    + "</div>";
        }
        return "<div class='review-issue-list'>"
                + issues.map(renderReviewIssue)
                + "</div>";
    }

    function renderReviewIssue(issue) {
        if (!issue || typeof issue !== "object") {
            return "<article class='review-issue-card'><p class='item-summary'>"
                    + escapeHtml(String(issue || "未说明的问题"))
                    + "</p></article>";
        }
        const summary = issue.description || issue.message || issue.reason || issue.issue || "未说明的问题";
        const suggestion = issue.suggestion || issue.fixSuggestion || issue.recommendation || "";
        const tags = [
            issue.severity ? "严重度：" + issue.severity : "",
            issue.category ? "类型：" + issue.category : ""
        ].filter(Boolean);
        return "<article class='review-issue-card'>"
                + (tags.length > 0 ? "<div class='meta-row'>" + tags.map(renderPill).join("") + "</div>" : "")
                + "<p class='item-summary'>" + escapeHtml(summary) + "</p>"
                + (suggestion ? "<p class='item-caption'>建议：" + escapeHtml(suggestion) + "</p>" : "")
                + "</article>";
    }

    function renderTechnicalDetails(detail) {
        const metadata = prettyJson(detail.metadataJson);
        const issueJson = prettyJson(detail.reviewIssuesJson);
        return "<div class='description-list'>"
                + renderDescriptionItem("队列编号", detail.id)
                + renderDescriptionItem("编译任务", detail.jobId)
                + renderDescriptionItem("资料源", detail.sourceCode || detail.sourceId)
                + renderDescriptionItem("概念", detail.conceptId)
                + renderDescriptionItem("文章键", detail.articleKey)
                + renderDescriptionItem("审查路线", detail.reviewRoute)
                + renderDescriptionItem("审查模型", detail.reviewerModel)
                + renderDescriptionItem("创建时间", formatDateTime(detail.createdAt))
                + renderDescriptionItem("更新时间", formatDateTime(detail.updatedAt))
                + "</div>"
                + "<h5 class='review-queue-tech-title'>元数据</h5>"
                + "<pre class='code-view compact'>" + escapeHtml(metadata || "{}") + "</pre>"
                + "<h5 class='review-queue-tech-title'>结构化问题</h5>"
                + "<pre class='code-view compact'>" + escapeHtml(issueJson || "[]") + "</pre>";
    }

    function renderDescriptionItem(label, value) {
        const displayValue = value == null || value === "" ? "暂无" : String(value);
        return "<div class='description-item'>"
                + "<span class='description-label'>" + escapeHtml(label) + "</span>"
                + "<span class='description-value'>" + escapeHtml(displayValue) + "</span>"
                + "</div>";
    }

    function renderSourcePathPills(sourcePaths, limit) {
        const paths = Array.isArray(sourcePaths) ? sourcePaths.filter(Boolean) : [];
        if (paths.length === 0) {
            return "<span class='pill'>暂未记录来源文件</span>";
        }
        const visiblePaths = paths.slice(0, limit);
        const moreCount = paths.length - visiblePaths.length;
        return visiblePaths.map(renderPill).join("")
                + (moreCount > 0 ? renderPill("还有 " + moreCount + " 个来源") : "");
    }

    function renderPill(value) {
        return "<span class='pill'>" + escapeHtml(value) + "</span>";
    }

    function buildDetailMeta(detail) {
        const parts = [];
        parts.push("质量检查需要人工确认");
        if (detail.updatedAt || detail.createdAt) {
            parts.push("更新时间：" + formatDateTime(detail.updatedAt || detail.createdAt));
        }
        return parts.join(" | ");
    }

    function renderActionResult(result, successMessage) {
        const detail = document.getElementById("review-queue-detail");
        if (!detail) {
            return;
        }
        const auditText = result && result.auditId ? "审计记录：" + result.auditId : "已记录操作";
        detail.innerHTML = "<div class='detail-focus-card'>"
                + "<p class='item-summary'>" + escapeHtml(successMessage) + "</p>"
                + "<p class='item-caption'>" + escapeHtml(auditText) + "</p>"
                + "</div>";
    }

    function setActionButtonsDisabled(disabled) {
        document.querySelectorAll("[data-review-queue-approve], [data-review-queue-reject]").forEach(function (button) {
            button.disabled = !!disabled;
        });
    }

    function highlightSelectedItem() {
        document.querySelectorAll("[data-review-queue-id]").forEach(function (button) {
            button.classList.toggle("active", String(button.dataset.reviewQueueId) === String(state.selectedId || ""));
        });
    }

    function parseJsonValue(value) {
        if (!value) {
            return null;
        }
        if (typeof value !== "string") {
            return value;
        }
        try {
            return JSON.parse(value);
        }
        catch (error) {
            return null;
        }
    }

    function prettyJson(value) {
        const parsed = parseJsonValue(value);
        if (parsed == null) {
            return typeof value === "string" ? value : "";
        }
        return JSON.stringify(parsed, null, 2);
    }

    function setLocalStatus(message, tone) {
        const status = document.getElementById("review-queue-status");
        if (!status) {
            return;
        }
        status.textContent = message || "";
        status.dataset.tone = tone || "info";
    }

    function showPageError(prefix, error) {
        const message = getErrorMessage(error);
        const notice = document.getElementById("page-notice");
        if (!notice) {
            return;
        }
        notice.hidden = false;
        notice.className = "page-notice danger";
        notice.textContent = prefix + "：" + message;
    }

    function getErrorMessage(error) {
        return error && error.message ? error.message : String(error || "未知错误");
    }

    if (typeof globalThis !== "undefined" && globalThis.__LATTICE_ADMIN_TEST__) {
        globalThis.__LATTICE_ADMIN_TEST__.compileReviewQueue = {
            state: state,
            renderEmptyDetail: renderEmptyDetail,
            renderReviewQueueDetail: renderReviewQueueDetail,
            renderReviewQueueList: renderReviewQueueList,
            renderReviewIssues: renderReviewIssues,
            buildDetailMeta: buildDetailMeta
        };
    }
})();
