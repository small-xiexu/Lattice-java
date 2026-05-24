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
        detail: null,
        modalAction: null,
        filters: {
            sourceFile: "",
            riskLevel: "",
            issueType: "",
            groupBySource: false
        }
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
            list.addEventListener("keydown", handleListKeydown);
        }
        const detail = document.getElementById("review-queue-detail");
        if (detail) {
            detail.addEventListener("click", handleDetailClick);
        }
        bindFilterEvents();
    }

    function bindFilterEvents() {
        bindIfPresent("review-queue-filter-source", "change", function (event) {
            state.filters.sourceFile = event.target.value || "";
            renderReviewQueueList(state.items.length);
        });
        bindIfPresent("review-queue-filter-risk", "change", function (event) {
            state.filters.riskLevel = event.target.value || "";
            renderReviewQueueList(state.items.length);
        });
        bindIfPresent("review-queue-filter-issue-type", "change", function (event) {
            state.filters.issueType = event.target.value || "";
            renderReviewQueueList(state.items.length);
        });
        bindIfPresent("review-queue-group-source", "change", function (event) {
            state.filters.groupBySource = event.target.checked;
            renderReviewQueueList(state.items.length);
        });
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
            renderFilterBar();
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

    function handleListKeydown(event) {
        if (event.key !== "ArrowUp" && event.key !== "ArrowDown") {
            return;
        }
        event.preventDefault();
        const filtered = getFilteredItems();
        if (filtered.length === 0) {
            return;
        }
        const currentIndex = filtered.findIndex(function (item) {
            return String(item.id) === String(state.selectedId);
        });
        if (event.key === "ArrowDown") {
            const nextIndex = currentIndex < filtered.length - 1 ? currentIndex + 1 : 0;
            loadReviewQueueDetail(filtered[nextIndex].id);
        } else {
            const prevIndex = currentIndex > 0 ? currentIndex - 1 : filtered.length - 1;
            loadReviewQueueDetail(filtered[prevIndex].id);
        }
        const selectedButton = document.querySelector("[data-review-queue-id].active");
        if (selectedButton) {
            selectedButton.scrollIntoView({ block: "nearest", behavior: "smooth" });
        }
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

    function openReviewActionModal(action) {
        const detail = state.detail;
        if (!detail) {
            setLocalStatus("请先选择一条待人工确认草稿", "warning");
            return;
        }
        state.modalAction = action;
        if (state._modalKeydownHandler) {
            document.removeEventListener("keydown", state._modalKeydownHandler);
            state._modalKeydownHandler = null;
        }
        const overlay = document.getElementById("review-action-modal-overlay");
        if (overlay) {
            overlay.remove();
        }
        const overlayHtml = buildReviewActionModalHtml(detail, action);
        const template = document.createElement("template");
        template.innerHTML = overlayHtml.trim();
        const modalOverlay = template.content.firstElementChild;
        document.body.appendChild(modalOverlay);
        bindReviewActionModalEvents(modalOverlay, detail, action);
        if (action === "reject") {
            const reasonInput = modalOverlay.querySelector("#modal-reject-reason");
            if (reasonInput) {
                window.setTimeout(function () { reasonInput.focus(); }, 100);
            }
        } else {
            const operatorInput = modalOverlay.querySelector("#modal-operator");
            if (operatorInput) {
                window.setTimeout(function () { operatorInput.focus(); }, 100);
            }
        }
    }

    function closeReviewActionModal() {
        state.modalAction = null;
        if (state._modalKeydownHandler) {
            document.removeEventListener("keydown", state._modalKeydownHandler);
            state._modalKeydownHandler = null;
        }
        const overlay = document.getElementById("review-action-modal-overlay");
        if (overlay) {
            overlay.remove();
        }
    }

    function buildReviewActionModalHtml(detail, action) {
        var title = escapeHtml(detail.title || "未命名草稿");
        var isApprove = action === "approve";
        var modalTitle = isApprove ? "确认入库" : "驳回草稿";
        var riskLevel = resolveItemRiskLevel(detail);
        var riskLabel = riskLevel ? mapRiskLevelLabel(riskLevel) : "未评级";
        var severityClass = mapSeverityClass(riskLevel);
        var issueTypes = resolveItemIssueTypes(detail);
        var issueCount = resolveReviewIssueCount(detail.reviewIssuesJson);
        var sourceFile = (detail.sourcePaths && detail.sourcePaths.length > 0) ? detail.sourcePaths[0] : "暂无";
        var updatedAt = formatDateTime(detail.updatedAt || detail.createdAt);

        var html = "<div class='modal-overlay' id='review-action-modal-overlay' role='dialog' aria-modal='true' aria-label='" + escapeHtml(modalTitle) + "'>";
        html += "<div class='modal-card review-action-modal-card'>";
        html += "<div class='modal-header'>";
        html += "<h3>" + escapeHtml(modalTitle) + "</h3>";
        html += "<button class='modal-close-btn' type='button' aria-label='关闭'>&times;</button>";
        html += "</div>";
        html += "<div class='modal-body'>";

        // === 审核摘要（顶部主视觉） ===
        html += "<div class='review-decision-summary'>";
        html += "<p class='modal-subtitle'>" + title + "</p>";
        html += "<div class='decision-summary-badges'>";
        html += "<span class='review-modal-badge severity-" + severityClass + "'>" + escapeHtml(riskLabel) + "</span>";
        html += "<span class='decision-issue-count'>" + issueCount + " 个待确认问题</span>";
        html += "</div>";
        if (issueTypes.length > 0) {
            html += "<div class='decision-issue-tags'>";
            html += issueTypes.map(function (t) {
                return "<span class='decision-issue-tag'>" + escapeHtml(mapCategory(t)) + "</span>";
            }).join("");
            html += "</div>";
        }
        html += "<div class='decision-source-row'>";
        html += "<span class='decision-source-label'>来源</span>";
        html += "<span class='decision-source-file'>" + escapeHtml(sourceFile) + "</span>";
        html += "<span class='decision-source-time'>" + escapeHtml(updatedAt) + "</span>";
        html += "</div>";
        html += "<p class='decision-context-hint'>请核对以下内容后决定是否" + (isApprove ? "入库" : "驳回") + "。</p>";
        html += "</div>";

        // === 核对清单（决策辅助） ===
        html += "<div class='review-decision-checklist'>";
        html += "<h4 class='decision-checklist-title'>核对清单</h4>";
        html += "<ul class='review-modal-checklist'>";
        html += "<li data-risk='high'>是否超出源文范围</li>";
        html += "<li data-risk='high'>是否新增源文未提供的主题或结论</li>";
        html += "<li data-risk='medium'>是否存在概念偏差</li>";
        html += "<li data-risk='medium'>来源是否足以支撑正文</li>";
        if (!isApprove) {
            html += "<li data-risk='high'>问题是否无法通过简单修正解决</li>";
        }
        html += "</ul>";
        html += "</div>";

        // === 操作记录（底部次要区） ===
        html += "<div class='review-decision-record'>";
        html += "<div class='decision-record-field'>";
        html += "<label for='modal-operator'>操作人</label>";
        html += "<input id='modal-operator' type='text' value='admin' autocomplete='off'>";
        html += "</div>";
        if (!isApprove) {
            html += "<div class='decision-record-field'>";
            html += "<label for='modal-reject-reason'>驳回原因 <span class='required-mark'>*</span></label>";
            html += "<p class='modal-field-hint'>建议填写：超出源文范围 / 概念偏差 / 新增未提供内容 / 依据不足 / 数值矛盾</p>";
            html += "<textarea id='modal-reject-reason' rows='3' placeholder='请填写驳回原因'></textarea>";
            html += "</div>";
        } else {
            html += "<div class='decision-record-field'>";
            html += "<label for='modal-approve-note'>备注（选填）</label>";
            html += "<input id='modal-approve-note' type='text' placeholder='可选备注，如修正建议' autocomplete='off'>";
            html += "</div>";
        }
        html += "</div>";

        html += "</div>";
        html += "<div class='modal-footer review-modal-footer'>";
        html += "<button class='ghost-btn modal-cancel-btn' type='button'>取消</button>";
        if (isApprove) {
            html += "<button class='ghost-btn danger-action modal-reject-inline-btn' type='button' data-review-queue-reject='true'>驳回</button>";
        }
        html += "<button class='" + (isApprove ? "primary-btn" : "ghost-btn danger-action") + " modal-submit-btn' type='button'>" + escapeHtml(isApprove ? "确认入库" : "确认驳回") + "</button>";
        html += "</div>";
        html += "</div>";
        html += "</div>";
        return html;
    }

    function bindReviewActionModalEvents(overlay, detail, action) {
        var isApprove = action === "approve";
        var submitting = false;

        overlay.addEventListener("click", function (event) {
            if (event.target === overlay) {
                closeReviewActionModal();
            }
        });

        var closeBtn = overlay.querySelector(".modal-close-btn");
        if (closeBtn) {
            closeBtn.addEventListener("click", closeReviewActionModal);
        }

        var cancelBtn = overlay.querySelector(".modal-cancel-btn");
        if (cancelBtn) {
            cancelBtn.addEventListener("click", closeReviewActionModal);
        }

        // Inline reject button inside approve modal
        var rejectInlineBtn = overlay.querySelector(".modal-reject-inline-btn");
        if (rejectInlineBtn) {
            rejectInlineBtn.addEventListener("click", function () {
                closeReviewActionModal();
                openReviewActionModal("reject");
            });
        }

        var modalKeydownHandler = function (event) {
            if (event.key === "Escape") {
                closeReviewActionModal();
            }
        };
        state._modalKeydownHandler = modalKeydownHandler;
        document.addEventListener("keydown", modalKeydownHandler);

        var submitBtn = overlay.querySelector(".modal-submit-btn");
        if (submitBtn) {
            submitBtn.addEventListener("click", async function () {
                if (submitting) {
                    return;
                }
                var operatorInput = overlay.querySelector("#modal-operator");
                var reviewedBy = operatorInput ? operatorInput.value.trim() : "admin";
                if (!reviewedBy) {
                    setLocalStatus("请填写操作人", "warning");
                    return;
                }
                var comment = "";
                if (!isApprove) {
                    var reasonInput = overlay.querySelector("#modal-reject-reason");
                    var reason = reasonInput ? reasonInput.value.trim() : "";
                    if (!reason) {
                        setLocalStatus("请填写驳回原因", "warning");
                        if (reasonInput) {
                            reasonInput.focus();
                        }
                        return;
                    }
                    comment = reason;
                } else {
                    var noteInput = overlay.querySelector("#modal-approve-note");
                    if (noteInput && noteInput.value.trim()) {
                        comment = noteInput.value.trim();
                    }
                }
                submitting = true;
                submitBtn.disabled = true;
                submitBtn.textContent = isApprove ? "正在确认..." : "正在驳回...";

                try {
                    await submitReviewQueueAction(detail, action, reviewedBy, comment,
                        isApprove ? "确认入库完成" : "草稿已驳回");
                    closeReviewActionModal();
                } catch (e) {
                    submitting = false;
                    submitBtn.disabled = false;
                    submitBtn.textContent = isApprove ? "确认入库" : "确认驳回";
                }
            });
        }
    }

    async function approveSelectedReviewQueueItem() {
        openReviewActionModal("approve");
    }

    async function rejectSelectedReviewQueueItem() {
        openReviewActionModal("reject");
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
            throw error;
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

    // ---- Filter helpers ----

    function resolveItemRiskLevel(item) {
        var detail = item.reviewIssuesJson !== undefined ? item : null;
        // item can be a list item (has reviewIssuesJson) or detail object
        var issues = parseJsonValue(item.reviewIssuesJson);
        if (!Array.isArray(issues) || issues.length === 0) {
            return null;
        }
        var hasCritical = issues.some(function (i) { return (i.severity || "").toUpperCase() === "CRITICAL"; });
        if (hasCritical) { return "CRITICAL"; }
        var hasHigh = issues.some(function (i) { return (i.severity || "").toUpperCase() === "HIGH"; });
        if (hasHigh) { return "HIGH"; }
        var hasMedium = issues.some(function (i) { return (i.severity || "").toUpperCase() === "MEDIUM"; });
        if (hasMedium) { return "MEDIUM"; }
        var hasLow = issues.some(function (i) { return (i.severity || "").toUpperCase() === "LOW"; });
        if (hasLow) { return "LOW"; }
        return null;
    }

    function resolveItemIssueTypes(item) {
        var issues = parseJsonValue(item.reviewIssuesJson);
        if (!Array.isArray(issues)) {
            return [];
        }
        var types = [];
        var seen = {};
        issues.forEach(function (i) {
            var cat = i.category;
            if (cat && !seen[cat]) {
                seen[cat] = true;
                types.push(cat);
            }
        });
        return types;
    }

    function mapRiskLevelLabel(level) {
        if (!level) { return "未评级"; }
        switch (level) {
            case "CRITICAL": return "严重";
            case "HIGH": return "高风险";
            case "MEDIUM": return "中风险";
            case "LOW": return "低风险";
            default: return "未评级";
        }
    }

    function mapRiskLevelSortOrder(level) {
        switch (level) {
            case "CRITICAL": return 0;
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            case "LOW": return 3;
            default: return 4;
        }
    }

    function getFilteredItems() {
        var items = state.items;
        if (state.filters.sourceFile) {
            items = items.filter(function (item) {
                return (item.sourcePaths || []).indexOf(state.filters.sourceFile) >= 0;
            });
        }
        if (state.filters.riskLevel) {
            items = items.filter(function (item) {
                return resolveItemRiskLevel(item) === state.filters.riskLevel;
            });
        }
        if (state.filters.issueType) {
            items = items.filter(function (item) {
                return resolveItemIssueTypes(item).indexOf(state.filters.issueType) >= 0;
            });
        }
        return items;
    }

    function getFilterOptions() {
        var sources = {};
        var riskLevels = {};
        var issueTypes = {};
        state.items.forEach(function (item) {
            (item.sourcePaths || []).forEach(function (s) { if (s) { sources[s] = true; } });
            var rl = resolveItemRiskLevel(item);
            if (rl) { riskLevels[rl] = true; }
            resolveItemIssueTypes(item).forEach(function (t) { issueTypes[t] = true; });
        });
        var sourceList = Object.keys(sources).sort();
        var riskList = Object.keys(riskLevels).sort(function (a, b) {
            return mapRiskLevelSortOrder(a) - mapRiskLevelSortOrder(b);
        });
        var issueTypeList = Object.keys(issueTypes).sort();
        return { sources: sourceList, riskLevels: riskList, issueTypes: issueTypeList };
    }

    // ---- Render functions ----

    function renderFilterBar() {
        var container = document.getElementById("review-queue-filter-bar");
        if (!container) {
            return;
        }
        if (state.items.length === 0) {
            container.innerHTML = "";
            container.style.display = "none";
            return;
        }
        container.style.display = "";
        var options = getFilterOptions();
        var html = "<div class='review-queue-filter-row'>";
        html += "<select id='review-queue-filter-source' class='filter-select'>";
        html += "<option value=''>全部来源文件</option>";
        options.sources.forEach(function (s) {
            var selected = state.filters.sourceFile === s ? " selected" : "";
            html += "<option value='" + escapeHtml(s) + "'" + selected + ">" + escapeHtml(s) + "</option>";
        });
        html += "</select>";
        html += "<select id='review-queue-filter-risk' class='filter-select'>";
        html += "<option value=''>全部风险等级</option>";
        options.riskLevels.forEach(function (r) {
            var selected = state.filters.riskLevel === r ? " selected" : "";
            html += "<option value='" + escapeHtml(r) + "'" + selected + ">" + escapeHtml(mapRiskLevelLabel(r)) + "</option>";
        });
        html += "</select>";
        html += "<select id='review-queue-filter-issue-type' class='filter-select'>";
        html += "<option value=''>全部问题类型</option>";
        options.issueTypes.forEach(function (t) {
            var selected = state.filters.issueType === t ? " selected" : "";
            html += "<option value='" + escapeHtml(t) + "'" + selected + ">" + escapeHtml(mapCategory(t)) + "</option>";
        });
        html += "</select>";
        html += "<label class='filter-checkbox-label'>";
        html += "<input type='checkbox' id='review-queue-group-source'" + (state.filters.groupBySource ? " checked" : "") + ">";
        html += "按来源分组";
        html += "</label>";
        html += "</div>";
        container.innerHTML = html;
        // Re-bind filter events since innerHTML was replaced
        bindFilterEvents();
    }

    function renderReviewQueueList(total) {
        var list = document.getElementById("review-queue-list");
        var count = document.getElementById("review-queue-count");
        var filtered = getFilteredItems();
        if (count) {
            count.textContent = String(filtered.length) + " / " + String(total || 0);
        }
        if (!list) {
            return;
        }
        if (filtered.length === 0) {
            list.innerHTML = "<div class='review-queue-empty'>"
                    + "<p class='item-summary'>" + (state.items.length === 0 ? "当前没有待人工确认草稿。" : "当前筛选条件下无匹配草稿。") + "</p>"
                    + "</div>";
            return;
        }
        if (state.filters.groupBySource) {
            list.innerHTML = renderGroupedList(filtered);
        } else {
            list.innerHTML = filtered.map(renderListItem).join("");
        }
        highlightSelectedItem();
    }

    function renderGroupedList(items) {
        var groups = {};
        items.forEach(function (item) {
            var source = (item.sourcePaths && item.sourcePaths.length > 0) ? item.sourcePaths[0] : "未记录来源";
            if (!groups[source]) {
                groups[source] = [];
            }
            groups[source].push(item);
        });
        var sourceKeys = Object.keys(groups).sort();
        var html = "";
        sourceKeys.forEach(function (source) {
            var groupItems = groups[source];
            html += "<div class='review-queue-source-group'>";
            html += "<div class='review-queue-source-group-header'>";
            html += "<span class='source-group-icon'>&#128193;</span>";
            html += "<span class='source-group-name'>" + escapeHtml(source) + "</span>";
            html += "<span class='source-group-count pill'>" + groupItems.length + " 篇</span>";
            html += "</div>";
            html += groupItems.map(renderListItem).join("");
            html += "</div>";
        });
        return html;
    }

    function renderListItem(item) {
        var riskLevel = resolveItemRiskLevel(item);
        var riskLabel = riskLevel ? mapRiskLevelLabel(riskLevel) : "";
        var riskClass = riskLevel ? "severity-" + mapSeverityClass(riskLevel).replace("severity-", "") : "";
        var issueTypes = resolveItemIssueTypes(item);
        var issueCount = resolveReviewIssueCount(item.reviewIssuesJson);
        var primarySource = (item.sourcePaths && item.sourcePaths.length > 0) ? item.sourcePaths[0] : "未记录来源";
        var sourceFileName = extractFileName(primarySource);
        var fixRoundText = String(item.fixAttemptCount || 0) + " / " + String(item.maxFixRounds || 0);

        var html = "<button class='review-queue-item' data-review-queue-id='"
                + escapeHtml(item.id)
                + "' type='button'>";
        html += "<div class='review-queue-item-top'>";
        html += "<h4>" + escapeHtml(item.title || "未命名草稿") + "</h4>";
        if (riskLabel) {
            html += "<span class='review-queue-risk-badge " + riskClass + "'>" + escapeHtml(riskLabel) + "</span>";
        }
        html += "</div>";
        html += "<div class='review-queue-item-meta'>";
        html += "<span class='review-queue-source-label' title='" + escapeHtml(primarySource) + "'>" + escapeHtml(sourceFileName) + "</span>";
        html += "<span class='review-queue-issue-count'>" + issueCount + " 个问题</span>";
        html += "<span class='review-queue-fix-rounds'>修复 " + escapeHtml(fixRoundText) + "</span>";
        html += "</div>";
        if (issueTypes.length > 0) {
            html += "<div class='review-queue-item-tags'>";
            html += issueTypes.slice(0, 3).map(function (t) {
                return "<span class='review-queue-issue-tag'>" + escapeHtml(mapCategory(t)) + "</span>";
            }).join("");
            if (issueTypes.length > 3) {
                html += "<span class='review-queue-issue-tag'>+" + (issueTypes.length - 3) + "</span>";
            }
            html += "</div>";
        }
        html += "</button>";
        return html;
    }

    function extractFileName(path) {
        if (!path) { return "未记录来源"; }
        var lastSlash = path.lastIndexOf("/");
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    function renderReviewQueueDetail(detail) {
        var title = document.getElementById("review-queue-detail-title");
        var meta = document.getElementById("review-queue-detail-meta");
        var container = document.getElementById("review-queue-detail");
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

        var riskLevel = resolveItemRiskLevel(detail);
        var riskLabel = riskLevel ? mapRiskLevelLabel(riskLevel) : "未评级";
        var riskClass = riskLevel ? mapSeverityClass(riskLevel) : "severity-unknown";
        var issueTypes = resolveItemIssueTypes(detail);
        var issueCount = resolveReviewIssueCount(detail.reviewIssuesJson);
        var primarySource = (detail.sourcePaths && detail.sourcePaths.length > 0) ? detail.sourcePaths[0] : "暂无";
        var updatedAt = formatDateTime(detail.updatedAt || detail.createdAt);

        container.innerHTML = ""
                // Risk + issue summary (top priority for review)
                + "<section class='detail-section review-detail-risk-summary'>"
                + "<h4>审核概览</h4>"
                + "<div class='review-risk-summary-row'>"
                + "<div class='review-risk-item'>"
                + "<span class='description-label'>风险等级</span>"
                + "<span class='issue-severity " + riskClass + "'>" + escapeHtml(riskLabel) + "</span>"
                + "</div>"
                + "<div class='review-risk-item'>"
                + "<span class='description-label'>问题数量</span>"
                + "<span class='description-value'>" + issueCount + " 个</span>"
                + "</div>"
                + "<div class='review-risk-item'>"
                + "<span class='description-label'>问题类型</span>"
                + "<span class='description-value'>" + (issueTypes.length > 0 ? issueTypes.map(function (t) { return escapeHtml(mapCategory(t)); }).join("、") : "无") + "</span>"
                + "</div>"
                + "</div>"
                + "<div class='review-risk-summary-row'>"
                + "<div class='review-risk-item'>"
                + "<span class='description-label'>来源文件</span>"
                + "<span class='description-value'>" + escapeHtml(primarySource) + "</span>"
                + "</div>"
                + "<div class='review-risk-item'>"
                + "<span class='description-label'>更新时间</span>"
                + "<span class='description-value'>" + escapeHtml(updatedAt) + "</span>"
                + "</div>"
                + "</div>"
                + "</section>"
                // Review issues (moved to second position)
                + "<section class='detail-section'>"
                + "<h4>待人工确认说明（共 " + issueCount + " 个问题）</h4>"
                + renderReviewIssues(detail.reviewIssuesJson)
                + "</section>"
                // Source files
                + "<section class='detail-section'>"
                + "<h4>来源文件</h4>"
                + "<div class='tag-list'>" + renderSourcePathPills(detail.sourcePaths, 20) + "</div>"
                + "</section>"
                // Article content
                + "<section class='detail-section'>"
                + "<div class='detail-section-header'>"
                + "<div>"
                + "<h4>草稿正文</h4>"
                + "<p class='detail-section-copy'>确认前请核对正文是否可以进入正式知识库。</p>"
                + "</div>"
                + "</div>"
                + "<pre class='code-view review-queue-content'>" + escapeHtml(detail.content || "暂无正文") + "</pre>"
                + "</section>"
                // Fix rounds
                + "<section class='detail-section'>"
                + "<h4>处理轮次</h4>"
                + "<div class='description-list'>"
                + renderDescriptionItem("已修复轮数", String(detail.fixAttemptCount || 0))
                + renderDescriptionItem("最大修复轮数", String(detail.maxFixRounds || 0))
                + "</div>"
                + "</section>"
                // Action buttons
                + "<section class='detail-section review-queue-actions'>"
                + "<div class='button-row wrap'>"
                + "<button class='primary-btn' type='button' data-review-queue-approve='true'>确认入库</button>"
                + "<button class='ghost-btn danger-action' type='button' data-review-queue-reject='true'>驳回</button>"
                + "</div>"
                + "</section>"
                // Technical details (collapsed)
                + "<details class='advanced-toggle review-queue-technical'>"
                + "<summary>技术详情</summary>"
                + "<div class='advanced-toggle-body'>"
                + renderTechnicalDetails(detail)
                + "</div>"
                + "</details>";
    }

    function renderEmptyDetail() {
        var title = document.getElementById("review-queue-detail-title");
        var meta = document.getElementById("review-queue-detail-meta");
        var container = document.getElementById("review-queue-detail");
        if (title) {
            title.textContent = "请选择一条草稿";
        }
        if (meta) {
            meta.textContent = "";
        }
        if (container) {
            container.innerHTML = "<div class='detail-focus-card'>"
                    + "<p class='item-summary'>从左侧选择一条待人工确认草稿后，可查看正文、来源和待人工确认说明。</p>"
                    + "<p class='item-caption'>提示：可使用 ↑↓ 方向键快速切换草稿。</p>"
                    + "</div>";
        }
    }

    function resolveReviewIssueCount(reviewIssuesJson) {
        var issues = parseJsonValue(reviewIssuesJson);
        return Array.isArray(issues) ? issues.length : 0;
    }

    function renderReviewIssues(reviewIssuesJson) {
        var issues = parseJsonValue(reviewIssuesJson);
        if (!Array.isArray(issues) || issues.length === 0) {
            return "<div class='review-queue-empty'>"
                    + "<p class='item-summary'>质量检查需要人工确认，但未返回结构化问题详情。</p>"
                    + "</div>";
        }
        return "<div class='review-issue-list review-issue-list-scroll'>"
                + issues.map(renderReviewIssue).join("")
                + "</div>";
    }

    function mapSeverity(severity) {
        if (!severity) {
            return "未评级";
        }
        switch (severity) {
            case "CRITICAL": return "严重";
            case "HIGH": return "高风险";
            case "MEDIUM": return "中风险";
            case "LOW": return "低风险";
            default: return "未评级";
        }
    }

    function mapSeverityClass(severity) {
        if (!severity) {
            return "severity-unknown";
        }
        switch (severity) {
            case "CRITICAL": return "severity-critical";
            case "HIGH": return "severity-high";
            case "MEDIUM": return "severity-medium";
            case "LOW": return "severity-low";
            default: return "severity-unknown";
        }
    }

    function mapCategory(category) {
        if (!category) {
            return "其他质量问题";
        }
        switch (category) {
            case "false_provenance": return "来源不一致";
            case "value_mismatch": return "数值不匹配";
            case "missing_referential": return "缺少引用依据";
            case "conceptual_distortion": return "概念偏差";
            case "hallucination": return "事实编造";
            case "unsupported_claim": return "无依据论断";
            case "unsupported_exact_values": return "无依据精确值";
            case "missing_required_content": return "缺少必要信息";
            case "PARSE_RESCUED": return "解析救援";
            case "REWRITE_REQUIRED": return "需重写";
            case "REVIEW_REJECTED": return "审查不通过";
            case "GENERAL": return "其他质量问题";
            default: return "其他质量问题";
        }
    }

    function mapSuggestion(category, severity) {
        if (category) {
            switch (category) {
                case "false_provenance": return "建议核对源文件引用路径，确认后重新审查";
                case "value_mismatch": return "建议以源文件数值为准，修正后重新审查";
                case "missing_referential": return "建议补充引用依据或标注为推断性内容";
                case "conceptual_distortion": return "建议对照源文件修正概念表述";
                case "hallucination": return "建议驳回，核实源文件中是否存在对应信息";
                case "unsupported_claim": return "建议补充证据或降低论断确定性";
                case "unsupported_exact_values": return "建议核实精确值是否来自源文件";
                case "missing_required_content": return "建议补充缺失内容后重新审查";
            }
        }
        if (severity === "CRITICAL" || severity === "HIGH") {
            return "建议驳回或修改后重新审查";
        }
        if (severity === "MEDIUM") {
            return "建议修改后再确认";
        }
        if (severity === "LOW") {
            return "可确认入库，建议后续优化";
        }
        return "请人工判断是否可确认入库";
    }

    function summarizeDescription(description) {
        if (!description) {
            return "";
        }
        if (description.length <= 80) {
            return description;
        }
        return description.substring(0, 80) + "...";
    }

    function renderReviewIssue(issue) {
        if (!issue || typeof issue !== "object") {
            return "<article class='review-issue-card'><p class='item-summary'>"
                    + escapeHtml(String(issue || "未说明的问题"))
                    + "</p></article>";
        }
        var severity = issue.severity || "";
        var category = issue.category || "";
        var description = issue.description || issue.message || issue.reason || issue.issue || "";
        var severityLabel = mapSeverity(severity);
        var severityClass = mapSeverityClass(severity);
        var categoryLabel = mapCategory(category);
        var rawSuggestion = issue.suggestion || issue.fixSuggestion || issue.recommendation || "";
        var suggestion = rawSuggestion || mapSuggestion(category, severity);
        var summary = description ? summarizeDescription(description) : "审查未提供详细说明";

        return "<article class='review-issue-card'>"
                + "<div class='issue-header'>"
                + "<span class='issue-severity " + severityClass + "'>" + escapeHtml(severityLabel) + "</span>"
                + "<span class='issue-category'>" + escapeHtml(categoryLabel) + "</span>"
                + "</div>"
                + "<p class='issue-summary'>" + escapeHtml(summary) + "</p>"
                + "<p class='issue-suggestion'>" + escapeHtml(suggestion) + "</p>"
                + "<details class='issue-detail'>"
                + "<summary>展开详情</summary>"
                + (description ? "<p class='issue-description'>" + escapeHtml(description) + "</p>" : "")
                + "<div class='issue-technical'>"
                + "<span>原始严重度：" + escapeHtml(severity || "无") + "</span>"
                + "<span>原始类型：" + escapeHtml(category || "无") + "</span>"
                + "</div>"
                + "</details>"
                + "</article>";
    }

    function renderTechnicalDetails(detail) {
        var metadata = prettyJson(detail.metadataJson);
        var issueJson = prettyJson(detail.reviewIssuesJson);
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
        var displayValue = value == null || value === "" ? "暂无" : String(value);
        return "<div class='description-item'>"
                + "<span class='description-label'>" + escapeHtml(label) + "</span>"
                + "<span class='description-value'>" + escapeHtml(displayValue) + "</span>"
                + "</div>";
    }

    function renderSourcePathPills(sourcePaths, limit) {
        var paths = Array.isArray(sourcePaths) ? sourcePaths.filter(Boolean) : [];
        if (paths.length === 0) {
            return "<span class='pill'>暂未记录来源文件</span>";
        }
        var visiblePaths = paths.slice(0, limit);
        var moreCount = paths.length - visiblePaths.length;
        return visiblePaths.map(renderPill).join("")
                + (moreCount > 0 ? renderPill("还有 " + moreCount + " 个来源") : "");
    }

    function renderPill(value) {
        return "<span class='pill'>" + escapeHtml(value) + "</span>";
    }

    function buildDetailMeta(detail) {
        var parts = [];
        var riskLevel = resolveItemRiskLevel(detail);
        if (riskLevel) {
            parts.push("风险等级：" + mapRiskLevelLabel(riskLevel));
        }
        if (detail.updatedAt || detail.createdAt) {
            parts.push("更新时间：" + formatDateTime(detail.updatedAt || detail.createdAt));
        }
        return parts.join(" | ");
    }

    function renderActionResult(result, successMessage) {
        var detail = document.getElementById("review-queue-detail");
        if (!detail) {
            return;
        }
        var auditText = result && result.auditId ? "审计记录：" + result.auditId : "已记录操作";
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
        var parsed = parseJsonValue(value);
        if (parsed == null) {
            return typeof value === "string" ? value : "";
        }
        return JSON.stringify(parsed, null, 2);
    }

    function setLocalStatus(message, tone) {
        var status = document.getElementById("review-queue-status");
        if (!status) {
            return;
        }
        status.textContent = message || "";
        status.dataset.tone = tone || "info";
    }

    function showPageError(prefix, error) {
        var message = getErrorMessage(error);
        var notice = document.getElementById("page-notice");
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
            renderFilterBar: renderFilterBar,
            renderReviewIssues: renderReviewIssues,
            resolveReviewIssueCount: resolveReviewIssueCount,
            resolveItemRiskLevel: resolveItemRiskLevel,
            resolveItemIssueTypes: resolveItemIssueTypes,
            getFilteredItems: getFilteredItems,
            getFilterOptions: getFilterOptions,
            buildDetailMeta: buildDetailMeta,
            buildReviewActionModalHtml: buildReviewActionModalHtml,
            openReviewActionModal: openReviewActionModal,
            closeReviewActionModal: closeReviewActionModal,
            approveSelectedReviewQueueItem: approveSelectedReviewQueueItem,
            rejectSelectedReviewQueueItem: rejectSelectedReviewQueueItem,
            submitReviewQueueAction: submitReviewQueueAction,
            renderListItem: renderListItem,
            renderGroupedList: renderGroupedList
        };
    }
})();
