(function () {
    const state = {
        selectedConceptId: null,
        overview: null,
        health: null
    };

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        refreshPage();
    });

    function bindEvents() {
        document.getElementById("refresh-page").addEventListener("click", refreshPage);
        document.getElementById("refresh-summary").addEventListener("click", refreshSummary);
        document.getElementById("refresh-health").addEventListener("click", refreshHealth);
        document.getElementById("search-articles").addEventListener("click", loadArticles);
        document.getElementById("refresh-jobs").addEventListener("click", loadJobs);
        document.getElementById("submit-compile-job").addEventListener("click", submitCompileJob);
        document.getElementById("submit-upload-job").addEventListener("click", uploadAndCompile);
        document.getElementById("rebuild-chunks").addEventListener("click", rebuildChunks);
    }

    async function refreshPage() {
        setStatus("正在刷新知识库页面...");
        await Promise.all([
            refreshSummary(),
            loadArticles(),
            loadJobs()
        ]);
        setStatus("知识库页面已刷新");
    }

    async function refreshSummary() {
        try {
            const results = await Promise.all([
                fetchJson("/api/v1/admin/overview"),
                fetchHealthStatus()
            ]);
            state.overview = results[0];
            state.health = results[1];
            renderSummary(state.overview, state.health);
            renderHealthIndicator(state.health);
        }
        catch (error) {
            showError("刷新知识库状态失败", error);
        }
    }

    async function refreshHealth() {
        const health = await fetchHealthStatus();
        state.health = health;
        renderHealthIndicator(health);
        if (state.overview) {
            renderSummary(state.overview, health);
        }
        setStatus("服务健康状态：" + health.label);
    }

    async function loadArticles() {
        try {
            const query = encodeURIComponent(document.getElementById("article-query").value.trim());
            const lifecycle = encodeURIComponent(document.getElementById("article-lifecycle").value.trim());
            const response = await fetchJson("/api/v1/admin/articles?query=" + query + "&lifecycle=" + lifecycle);
            renderArticleList(response);
            if (!state.selectedConceptId && response.items && response.items.length > 0) {
                await loadArticleDetail(response.items[0].conceptId);
            }
            if (state.selectedConceptId && (!response.items || response.items.every(function (item) {
                return item.conceptId !== state.selectedConceptId;
            }))) {
                state.selectedConceptId = null;
                clearArticleDetail();
            }
        }
        catch (error) {
            showError("加载入库内容失败", error);
        }
    }

    async function loadArticleDetail(conceptId) {
        try {
            state.selectedConceptId = conceptId;
            const detail = await fetchJson("/api/v1/admin/articles/" + encodeURIComponent(conceptId));
            renderArticleDetail(detail);
            highlightArticle(conceptId);
        }
        catch (error) {
            showError("加载内容详情失败", error);
        }
    }

    async function submitCompileJob() {
        const sourceDir = document.getElementById("compile-source-dir").value.trim();
        if (!sourceDir) {
            setStatus("请输入源目录");
            return;
        }
        try {
            const response = await fetchJson("/api/v1/admin/compile/jobs", {
                method: "POST",
                body: JSON.stringify({
                    sourceDir: sourceDir,
                    incremental: document.getElementById("compile-incremental").checked,
                    async: document.getElementById("compile-async").checked,
                    orchestrationMode: "state_graph"
                })
            });
            renderGlobalResult(response);
            setStatus("目录同步任务已提交");
            await loadJobs();
        }
        catch (error) {
            renderGlobalResultError("提交目录同步失败", error);
            showError("提交目录同步失败", error);
        }
    }

    async function uploadAndCompile() {
        const filesInput = document.getElementById("compile-files");
        if (!filesInput.files || filesInput.files.length === 0) {
            setStatus("请先选择文件");
            return;
        }
        const formData = new FormData();
        Array.from(filesInput.files).forEach(function (file) {
            formData.append("files", file);
        });
        formData.append("incremental", String(document.getElementById("upload-incremental").checked));
        formData.append("async", String(document.getElementById("upload-async").checked));
        formData.append("orchestrationMode", "state_graph");

        try {
            const response = await fetchJson("/api/v1/admin/compile/upload", {
                method: "POST",
                body: formData,
                isFormData: true
            });
            renderGlobalResult(response);
            setStatus("上传处理任务已提交");
            filesInput.value = "";
            await loadJobs();
        }
        catch (error) {
            renderGlobalResultError("上传资料失败", error);
            showError("上传资料失败", error);
        }
    }

    async function rebuildChunks() {
        const confirmed = window.confirm("将基于当前文章和资料重建全部知识切片，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/compile/rebuild-chunks", {
                method: "POST"
            });
            document.getElementById("rebuild-result").textContent = JSON.stringify(result, null, 2);
            renderGlobalResult(result);
            setStatus("知识切片重建已完成");
            await Promise.all([refreshSummary(), loadJobs()]);
        }
        catch (error) {
            renderGlobalResultError("重建知识切片失败", error);
            showError("重建知识切片失败", error);
        }
    }

    async function loadJobs() {
        try {
            const response = await fetchJson("/api/v1/admin/jobs");
            renderJobs(response.items || []);
        }
        catch (error) {
            showError("加载处理状态失败", error);
        }
    }

    async function retryJob(jobId) {
        const confirmed = window.confirm("将重试失败任务 " + jobId + "，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/jobs/" + encodeURIComponent(jobId) + "/retry", {
                method: "POST"
            });
            renderGlobalResult(result);
            setStatus("任务已重新提交");
            await loadJobs();
        }
        catch (error) {
            renderGlobalResultError("重试任务失败", error);
            showError("重试任务失败", error);
        }
    }

    function renderSummary(overview, health) {
        const status = overview.status || {};
        const cards = [
            {
                label: "知识条目",
                value: status.articleCount || 0,
                note: "已沉淀进知识库的内容",
                tone: status.articleCount > 0 ? "success" : ""
            },
            {
                label: "资料文件",
                value: status.sourceFileCount || 0,
                note: "当前纳入处理的源文件数",
                tone: status.sourceFileCount > 0 ? "success" : ""
            },
            {
                label: "反馈沉淀",
                value: status.contributionCount || 0,
                note: "已确认并回写的问答反馈",
                tone: status.contributionCount > 0 ? "success" : ""
            },
            {
                label: "待处理反馈",
                value: status.pendingQueryCount || 0,
                note: (status.pendingQueryCount || 0) > 0 ? "后续可由内部继续处理" : "当前没有积压",
                tone: (status.pendingQueryCount || 0) > 0 ? "warning" : "success"
            },
            {
                label: "待人工复核",
                value: status.reviewPendingArticleCount || 0,
                note: (status.reviewPendingArticleCount || 0) > 0 ? "说明仍有内容需要人工确认" : "当前没有人工复核积压",
                tone: (status.reviewPendingArticleCount || 0) > 0 ? "warning" : "success"
            },
            {
                label: "服务健康",
                value: health ? health.label : "未检查",
                note: health ? health.note : "点击页头“刷新健康”后会在页面显示状态",
                tone: health ? health.tone : "warning"
            }
        ];
        document.getElementById("summary-cards").innerHTML = cards.map(renderMetricCard).join("");
    }

    function renderHealthIndicator(health) {
        const indicator = document.getElementById("health-indicator");
        const note = document.getElementById("health-indicator-note");
        if (!indicator || !note) {
            return;
        }
        indicator.className = "badge" + (health && health.tone ? " " + health.tone : "");
        indicator.textContent = health ? health.label : "未检查";
        note.textContent = health ? health.note : "点击按钮后会在页面显示服务状态";
    }

    function renderArticleList(response) {
        const items = response.items || [];
        document.getElementById("article-count").textContent = String(response.count || 0);
        const list = document.getElementById("article-list");
        if (items.length === 0) {
            list.innerHTML = "<div class='list-item'><p class='item-summary'>当前还没有入库内容，请先上传资料并等待处理完成。</p></div>";
            clearArticleDetail();
            return;
        }
        list.innerHTML = items.map(function (item) {
            return "<button class='list-item' data-concept-id='" + escapeHtml(item.conceptId) + "' type='button'>"
                    + "<div class='meta-row'>"
                    + renderBadge(item.lifecycle)
                    + renderBadge(item.reviewStatus)
                    + "</div>"
                    + "<h4>" + escapeHtml(item.title) + "</h4>"
                    + "<p class='item-summary'>" + escapeHtml(item.summary || "暂无摘要") + "</p>"
                    + "<div class='meta-row'><span class='pill'>" + escapeHtml(item.conceptId)
                    + "</span><span class='pill'>来源 " + escapeHtml(String(item.sourceCount)) + "</span></div>"
                    + "</button>";
        }).join("");
        list.querySelectorAll("[data-concept-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadArticleDetail(button.dataset.conceptId);
            });
        });
        highlightArticle(state.selectedConceptId);
    }

    function renderArticleDetail(detail) {
        document.getElementById("article-detail-title").textContent = detail.title || "未命名内容";
        document.getElementById("article-detail-meta").textContent = [
            detail.conceptId,
            detail.lifecycle ? "状态：" + getBadgeLabel(detail.lifecycle) : "",
            detail.reviewStatus ? "审查：" + getBadgeLabel(detail.reviewStatus) : "",
            detail.compiledAt ? "入库时间：" + formatDateTime(detail.compiledAt) : ""
        ].filter(Boolean).join(" | ");
        document.getElementById("article-detail-summary").textContent = detail.summary || "暂无摘要";
        document.getElementById("article-content").textContent = detail.content || "";
        document.getElementById("article-metadata").textContent = detail.metadataJson || "{}";
        document.getElementById("article-sources").innerHTML = renderTagGroup(detail.sourcePaths || []);
        const relations = []
                .concat((detail.referentialKeywords || []).map(function (item) { return "关键词: " + item; }))
                .concat((detail.dependsOn || []).map(function (item) { return "依赖: " + item; }))
                .concat((detail.related || []).map(function (item) { return "相关: " + item; }));
        document.getElementById("article-relations").innerHTML = renderTagGroup(relations);
    }

    function clearArticleDetail() {
        document.getElementById("article-detail-title").textContent = "请选择一条内容";
        document.getElementById("article-detail-meta").textContent = "";
        document.getElementById("article-detail-summary").textContent = "暂无摘要";
        document.getElementById("article-content").textContent = "暂无内容";
        document.getElementById("article-metadata").textContent = "暂无元数据";
        document.getElementById("article-sources").innerHTML = "";
        document.getElementById("article-relations").innerHTML = "";
    }

    function highlightArticle(conceptId) {
        document.querySelectorAll("#article-list .list-item").forEach(function (item) {
            item.classList.toggle("active", item.dataset.conceptId === conceptId);
        });
    }

    function renderJobs(items) {
        const container = document.getElementById("job-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>当前没有处理中的任务。上传新资料或发起目录同步后，这里会显示最新状态。</p></div>";
            return;
        }
        container.innerHTML = items.map(function (item) {
            const retryButton = item.status === "FAILED"
                    ? "<button class='ghost-btn' data-retry-job='" + escapeHtml(item.jobId) + "' type='button'>重试</button>"
                    : "";
            return "<div class='job-card'>"
                    + "<div class='meta-row'>"
                    + "<span class='pill'>" + escapeHtml(item.jobId) + "</span>"
                    + renderBadge(item.status)
                    + "<span class='pill'>" + escapeHtml(formatOrchestrationMode(item.orchestrationMode)) + "</span>"
                    + "</div>"
                    + "<h4>" + escapeHtml(item.sourceDir || "-") + "</h4>"
                    + "<p class='item-summary'>已持久化 " + escapeHtml(String(item.persistedCount || 0))
                    + " 条，尝试 " + escapeHtml(String(item.attemptCount || 0))
                    + " 次，增量处理：" + escapeHtml(item.incremental ? "是" : "否") + "</p>"
                    + "<p class='item-summary'>" + escapeHtml(item.errorMessage || "当前没有错误信息") + "</p>"
                    + (retryButton
                    ? "<div class='card-actions'>" + retryButton + "</div>"
                    : "")
                    + "</div>";
        }).join("");
        container.querySelectorAll("[data-retry-job]").forEach(function (button) {
            button.addEventListener("click", function () {
                retryJob(button.dataset.retryJob);
            });
        });
    }

    function renderMetricCard(item) {
        const toneClass = item.tone ? " " + item.tone : "";
        const note = item.note
                ? "<span class='note'>" + escapeHtml(item.note) + "</span>"
                : "";
        return "<div class='metric-card" + toneClass + "'><span class='label'>" + escapeHtml(item.label)
                + "</span><span class='value'>" + escapeHtml(String(item.value)) + "</span>" + note + "</div>";
    }

    function renderTagGroup(items) {
        if (!items || items.length === 0) {
            return "<span class='pill'>暂无</span>";
        }
        return items.map(function (item) {
            return "<span class='pill'>" + escapeHtml(item) + "</span>";
        }).join("");
    }

    function renderGlobalResult(result) {
        document.getElementById("global-result").textContent = JSON.stringify(result, null, 2);
    }

    function renderGlobalResultError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        document.getElementById("global-result").textContent = prefix + "：\n" + message;
    }

    function renderBadge(value) {
        const normalized = (value || "").toUpperCase();
        let className = "badge";
        if (normalized === "SUCCEEDED" || normalized === "ACTIVE" || normalized === "PASSED" || normalized === "CONFIRMED") {
            className += " success";
        }
        else if (normalized === "FAILED" || normalized === "DISCARDED" || normalized === "ARCHIVED") {
            className += " danger";
        }
        else if (normalized) {
            className += " warning";
        }
        return "<span class='" + className + "'>" + escapeHtml(getBadgeLabel(value)) + "</span>";
    }

    async function fetchJson(url, options) {
        const requestOptions = options || {};
        const headers = requestOptions.isFormData ? {} : {"Content-Type": "application/json"};
        const response = await fetch(url, {
            method: requestOptions.method || "GET",
            headers: headers,
            body: requestOptions.body
        });
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || ("HTTP " + response.status));
        }
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            return response.json();
        }
        return response.text();
    }

    async function fetchHealthStatus() {
        const checkedAt = new Date().toISOString();
        try {
            const response = await fetch("/actuator/health", {
                method: "GET",
                headers: {"Accept": "application/json"}
            });
            const payload = await parseResponseBody(response);
            return normalizeHealthStatus(response.status, payload, checkedAt);
        }
        catch (error) {
            return {
                rawStatus: "UNREACHABLE",
                label: "不可达",
                tone: "danger",
                note: compactHealthMessage(error && error.message ? error.message : "无法连接服务")
                        + " · "
                        + formatDateTime(checkedAt)
            };
        }
    }

    async function parseResponseBody(response) {
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            return response.json();
        }
        return response.text();
    }

    function normalizeHealthStatus(httpStatus, payload, checkedAt) {
        const rawStatus = payload && typeof payload === "object" && payload.status
                ? String(payload.status).toUpperCase()
                : httpStatus >= 200 && httpStatus < 300 ? "UP" : "DOWN";
        const unhealthyComponents = extractUnhealthyComponents(payload);
        const detailMessage = typeof payload === "string"
                ? compactHealthMessage(payload)
                : unhealthyComponents.length > 0
                ? "异常组件：" + unhealthyComponents.join("、")
                : "HTTP " + httpStatus;
        return {
            rawStatus: rawStatus,
            label: formatHealthLabel(rawStatus),
            tone: formatHealthTone(rawStatus),
            note: "Actuator: " + rawStatus + " · " + detailMessage + " · " + formatDateTime(checkedAt)
        };
    }

    function extractUnhealthyComponents(payload) {
        const components = payload && typeof payload === "object" && payload.components && typeof payload.components === "object"
                ? payload.components
                : {};
        return Object.keys(components).filter(function (name) {
            const component = components[name];
            const status = component && component.status ? String(component.status).toUpperCase() : "";
            return status && status !== "UP";
        });
    }

    function formatHealthLabel(status) {
        if (status === "UP") {
            return "正常";
        }
        if (status === "UNKNOWN") {
            return "未知";
        }
        if (status === "OUT_OF_SERVICE") {
            return "不可用";
        }
        if (status === "UNREACHABLE") {
            return "不可达";
        }
        return "异常";
    }

    function formatHealthTone(status) {
        if (status === "UP") {
            return "success";
        }
        if (status === "UNKNOWN") {
            return "warning";
        }
        return "danger";
    }

    function compactHealthMessage(message) {
        const normalized = String(message || "")
                .replace(/\s+/g, " ")
                .trim();
        if (!normalized) {
            return "未返回更多信息";
        }
        return normalized.length > 60 ? normalized.slice(0, 57) + "..." : normalized;
    }

    function setStatus(message) {
        document.getElementById("global-status").textContent = message;
    }

    function showError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        setStatus(prefix + "：" + message);
    }

    function formatOrchestrationMode(value) {
        if (!value) {
            return "-";
        }
        if (value === "state_graph") {
            return "图式流程";
        }
        return value;
    }

    function getBadgeLabel(value) {
        const normalized = (value || "").toUpperCase();
        const labels = {
            ACTIVE: "生效中",
            DEPRECATED: "已废弃",
            ARCHIVED: "已归档",
            PASSED: "已通过",
            PENDING: "待处理",
            NEEDS_HUMAN_REVIEW: "需人工复核",
            SUCCEEDED: "成功",
            FAILED: "失败",
            RUNNING: "进行中",
            QUEUED: "排队中",
            CONFIRMED: "已确认"
        };
        return labels[normalized] || value || "-";
    }

    function formatDateTime(value) {
        if (!value) {
            return "暂无";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }
        return date.toLocaleString("zh-CN", {
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    function escapeHtml(value) {
        return String(value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#39;");
    }
})();
