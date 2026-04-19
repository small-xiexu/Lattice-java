(function () {
    const RUN_POLLING_STATUSES = ["MATCHING", "MATERIALIZING", "COMPILE_QUEUED", "QUEUED", "RUNNING"];

    const state = {
        selectedArticleId: null,
        selectedArticleSourceId: null,
        selectedSourceId: null,
        overview: null,
        health: null,
        sources: [],
        activeRunId: null
    };

    let pageNoticeTimer = null;
    let runPollingTimer = null;

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        refreshPage();
    });

    function bindEvents() {
        document.getElementById("refresh-page").addEventListener("click", refreshPage);
        document.getElementById("refresh-summary").addEventListener("click", refreshSummary);
        document.getElementById("refresh-health").addEventListener("click", refreshHealth);
        document.getElementById("refresh-jobs").addEventListener("click", loadRecentRuns);
        document.getElementById("refresh-sources").addEventListener("click", loadSources);
        document.getElementById("search-articles").addEventListener("click", loadArticles);
        document.getElementById("article-source-filter").addEventListener("change", loadArticles);
        document.getElementById("submit-upload-job").addEventListener("click", uploadAndCompile);
        document.getElementById("create-server-source").addEventListener("click", createServerSourceAndSync);
        document.getElementById("sync-selected-source").addEventListener("click", syncSelectedSource);
        document.getElementById("rebuild-chunks").addEventListener("click", rebuildChunks);
    }

    async function refreshPage() {
        await refreshSummary();
        await loadSources();
        await Promise.all([
            loadRecentRuns(),
            loadArticles()
        ]);
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
        setStatus("服务健康状态：" + health.label, health.tone === "danger" ? "warning" : "success");
    }

    async function loadSources() {
        try {
            const response = await fetchJson("/api/v1/admin/sources?page=1&size=50");
            const items = response.items || [];
            state.sources = items;
            if (state.overview) {
                renderSummary(state.overview, state.health);
            }
            renderSourceSelects(items);
            renderSourceList(items);
            document.getElementById("source-count").textContent = String(response.total || items.length || 0);
            if (items.length === 0) {
                state.selectedSourceId = null;
                clearSourceDetail();
                return;
            }
            if (!containsSource(items, state.selectedSourceId)) {
                state.selectedSourceId = items[0].id;
            }
            highlightSource(state.selectedSourceId);
            await loadSourceDetail(state.selectedSourceId);
        }
        catch (error) {
            showError("加载资料源失败", error);
        }
    }

    async function loadSourceDetail(sourceId) {
        if (!sourceId) {
            clearSourceDetail();
            return;
        }
        try {
            state.selectedSourceId = sourceId;
            const results = await Promise.all([
                fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId)),
                fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId) + "/runs"),
                fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId) + "/files")
            ]);
            renderSourceDetail(results[0], results[1] || [], results[2] || []);
            highlightSource(sourceId);
        }
        catch (error) {
            showError("加载资料源详情失败", error);
        }
    }

    async function loadRecentRuns() {
        try {
            const response = await fetchJson("/api/v1/admin/source-runs?limit=10");
            renderRunCollection("job-list", response || [], "暂时没有同步记录");
        }
        catch (error) {
            showError("加载同步记录失败", error);
        }
    }

    async function loadArticles() {
        try {
            const query = encodeURIComponent(document.getElementById("article-query").value.trim());
            const lifecycle = encodeURIComponent(document.getElementById("article-lifecycle").value.trim());
            const sourceId = document.getElementById("article-source-filter").value.trim();
            const sourceIdQuery = sourceId ? "&sourceId=" + encodeURIComponent(sourceId) : "";
            const response = await fetchJson(
                    "/api/v1/admin/articles?query=" + query + "&lifecycle=" + lifecycle + sourceIdQuery
            );
            renderArticleList(response);
            const items = response.items || [];
            if (items.length === 0) {
                clearArticleDetail();
                state.selectedArticleId = null;
                state.selectedArticleSourceId = null;
                return;
            }
            if (!containsArticle(items, state.selectedArticleId, state.selectedArticleSourceId)) {
                await loadArticleDetail(items[0].articleKey || items[0].conceptId, items[0].sourceId);
            }
            else {
                highlightArticle(state.selectedArticleId, state.selectedArticleSourceId);
            }
        }
        catch (error) {
            showError("加载入库内容失败", error);
        }
    }

    async function loadArticleDetail(articleId, sourceId) {
        try {
            state.selectedArticleId = articleId;
            state.selectedArticleSourceId = sourceId || null;
            const query = sourceId ? "?sourceId=" + encodeURIComponent(String(sourceId)) : "";
            const detail = await fetchJson("/api/v1/admin/articles/" + encodeURIComponent(articleId) + query);
            renderArticleDetail(detail);
            highlightArticle(articleId, sourceId);
        }
        catch (error) {
            showError("加载内容详情失败", error);
        }
    }

    async function uploadAndCompile() {
        const filesInput = document.getElementById("compile-files");
        if (!filesInput.files || filesInput.files.length === 0) {
            setStatus("请先选择文件", "warning");
            return;
        }
        const formData = new FormData();
        Array.from(filesInput.files).forEach(function (file) {
            formData.append("files", file);
        });
        const sourceId = document.getElementById("upload-target-source").value.trim();
        if (sourceId) {
            formData.append("sourceId", sourceId);
        }

        try {
            const response = await fetchJson("/api/v1/admin/uploads", {
                method: "POST",
                body: formData,
                isFormData: true
            });
            filesInput.value = "";
            await handleSubmittedRun(response, "资料已接收，正在进入识别与编译流程");
        }
        catch (error) {
            showError("上传资料失败", error);
        }
    }

    async function createServerSourceAndSync() {
        const name = document.getElementById("server-source-name").value.trim();
        const sourceCode = document.getElementById("server-source-code").value.trim();
        const serverDir = document.getElementById("server-source-dir").value.trim();
        if (!name || !serverDir) {
            setStatus("请先填写资料源名称和服务器目录", "warning");
            return;
        }
        try {
            const source = await fetchJson("/api/v1/admin/sources/server-dir", {
                method: "POST",
                body: JSON.stringify({
                    sourceCode: sourceCode || null,
                    name: name,
                    contentProfile: "DOCUMENT",
                    serverDir: serverDir
                })
            });
            clearServerSourceForm();
            state.selectedSourceId = source.id;
            await loadSources();
            const run = await requestSourceSync(source.id);
            await handleSubmittedRun(run, "服务器目录资料源已创建，正在开始同步");
        }
        catch (error) {
            showError("创建服务器目录资料源失败", error);
        }
    }

    async function syncSelectedSource() {
        if (!state.selectedSourceId) {
            setStatus("请先选择一个资料源", "warning");
            return;
        }
        try {
            const run = await requestSourceSync(state.selectedSourceId);
            await handleSubmittedRun(run, "资料源同步已提交");
        }
        catch (error) {
            showError("发起资料源同步失败", error);
        }
    }

    async function requestSourceSync(sourceId) {
        return fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId) + "/sync", {
            method: "POST"
        });
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
            setStatus("知识切片重建已完成", "success");
            await refreshPage();
        }
        catch (error) {
            showError("重建知识切片失败", error);
        }
    }

    async function confirmRun(runId, decision, sourceId) {
        try {
            const response = await fetchJson("/api/v1/admin/source-runs/" + encodeURIComponent(runId) + "/confirm", {
                method: "POST",
                body: JSON.stringify({
                    decision: decision,
                    sourceId: sourceId || null
                })
            });
            await handleSubmittedRun(response, "人工确认已提交，系统继续处理这批资料");
        }
        catch (error) {
            showError("提交人工确认失败", error);
        }
    }

    async function handleSubmittedRun(run, submittedMessage) {
        if (!run || !run.runId) {
            setStatus(submittedMessage, "success");
            await refreshPage();
            return;
        }
        state.activeRunId = run.runId;
        await refreshPage();
        if (isPollingStatus(run.status)) {
            setStatus(submittedMessage + "，页面会自动刷新结果。", "info", false);
            startRunPolling(run.runId);
            return;
        }
        if (normalizeStatus(run.status) === "WAIT_CONFIRM") {
            setStatus("资料包需要人工确认归并方式，请在“最近同步运行”卡片中处理。", "warning", true);
            return;
        }
        setStatus(buildRunCompletionMessage(run), resolveRunNoticeTone(run), run.status === "FAILED");
    }

    function startRunPolling(runId) {
        stopRunPolling();
        state.activeRunId = runId;
        runPollingTimer = window.setTimeout(function () {
            pollRunStatus(runId);
        }, 1800);
    }

    function stopRunPolling() {
        if (runPollingTimer) {
            window.clearTimeout(runPollingTimer);
            runPollingTimer = null;
        }
    }

    async function pollRunStatus(runId) {
        try {
            const run = await fetchJson("/api/v1/admin/source-runs/" + encodeURIComponent(runId));
            await refreshPage();
            if (isPollingStatus(run.status)) {
                startRunPolling(runId);
                return;
            }
            stopRunPolling();
            if (normalizeStatus(run.status) === "WAIT_CONFIRM") {
                setStatus("资料包需要人工确认归并方式，请在“最近同步运行”卡片中处理。", "warning", true);
                return;
            }
            setStatus(buildRunCompletionMessage(run), resolveRunNoticeTone(run), normalizeStatus(run.status) === "FAILED");
        }
        catch (error) {
            stopRunPolling();
            showError("刷新同步状态失败", error);
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
                label: "资料源",
                value: state.sources.length,
                note: state.sources.length > 0 ? "支持上传、目录与 Git 多资料源" : "还没有建立资料源",
                tone: state.sources.length > 0 ? "success" : "warning"
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

    function renderSourceSelects(items) {
        const uploadSelect = document.getElementById("upload-target-source");
        const articleSelect = document.getElementById("article-source-filter");
        const previousUploadValue = uploadSelect.value;
        const previousArticleValue = articleSelect.value;
        const options = items.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.name + "（" + item.sourceCode + "）")
                    + "</option>";
        }).join("");
        uploadSelect.innerHTML = "<option value=''>自动识别 / 自动归并</option>" + options;
        articleSelect.innerHTML = "<option value=''>全部资料源</option>" + options;
        if (containsSource(items, parseOptionalInteger(previousUploadValue))) {
            uploadSelect.value = previousUploadValue;
        }
        if (containsSource(items, parseOptionalInteger(previousArticleValue))) {
            articleSelect.value = previousArticleValue;
        }
    }

    function renderSourceList(items) {
        const container = document.getElementById("source-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='list-item'><p class='item-summary'>还没有资料源。你可以直接上传资料自动归并，或先创建一个服务器目录资料源。</p></div>";
            return;
        }
        container.innerHTML = items.map(function (item) {
            return "<button class='list-item' data-source-id='" + escapeHtml(String(item.id)) + "' type='button'>"
                    + "<div class='meta-row'>"
                    + renderBadge(item.status)
                    + renderBadge(item.sourceType)
                    + "</div>"
                    + "<h4>" + escapeHtml(item.name || item.sourceCode || "未命名资料源") + "</h4>"
                    + "<p class='item-meta'>"
                    + escapeHtml(item.sourceCode || "-")
                    + " · "
                    + escapeHtml(item.contentProfile || "-")
                    + "</p>"
                    + "<p class='item-summary'>" + escapeHtml(getSourceSummary(item)) + "</p>"
                    + "<div class='meta-row'>"
                    + "<span class='pill'>默认：" + escapeHtml(getBadgeLabel(item.defaultSyncMode)) + "</span>"
                    + "<span class='pill'>最近：" + escapeHtml(item.lastSyncStatus ? getBadgeLabel(item.lastSyncStatus) : "未同步") + "</span>"
                    + "</div>"
                    + "</button>";
        }).join("");
        container.querySelectorAll("[data-source-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadSourceDetail(parseOptionalInteger(button.dataset.sourceId));
            });
        });
        highlightSource(state.selectedSourceId);
    }

    function renderSourceDetail(source, runs, files) {
        document.getElementById("source-detail-title").textContent = source.name || source.sourceCode || "未命名资料源";
        document.getElementById("source-detail-meta").textContent = [
            source.sourceCode,
            "类型：" + getBadgeLabel(source.sourceType),
            "画像：" + getBadgeLabel(source.contentProfile),
            "状态：" + getBadgeLabel(source.status),
            source.lastSyncAt ? "最近同步：" + formatDateTime(source.lastSyncAt) : "最近同步：未执行"
        ].join(" | ");
        document.getElementById("source-detail-config").textContent = prettyJson(source.configJson);
        renderRunCollection("source-run-list", runs, "当前资料源还没有同步历史");
        renderSourceFileList(files);
    }

    function renderSourceFileList(files) {
        const container = document.getElementById("source-file-list");
        if (!files || files.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>当前资料源还没有已物化文件。完成一次同步后，这里会展示解析方式与文件格式。</p></div>";
            return;
        }
        const sortedFiles = files.slice().sort(function (left, right) {
            return String(left.relativePath || "").localeCompare(String(right.relativePath || ""));
        });
        container.innerHTML = sortedFiles.map(function (item) {
            return "<div class='job-card'>"
                    + "<div class='meta-row'>"
                    + renderBadge(item.format)
                    + renderBadge(item.parseMode || "UNKNOWN")
                    + "</div>"
                    + "<h4>" + escapeHtml(item.relativePath || "-") + "</h4>"
                    + "<p class='item-summary'>"
                    + escapeHtml(buildSourceFileSummary(item))
                    + "</p>"
                    + "<p class='job-meta-line'>"
                    + escapeHtml(buildSourceFileMeta(item))
                    + "</p>"
                    + "</div>";
        }).join("");
    }

    function renderArticleList(response) {
        const items = response.items || [];
        document.getElementById("article-count").textContent = String(response.count || 0);
        const list = document.getElementById("article-list");
        if (items.length === 0) {
            list.innerHTML = "<div class='list-item'><p class='item-summary'>当前还没有入库内容。上传资料或同步资料源后，页面会在处理完成时自动刷新到这里。</p></div>";
            return;
        }
        list.innerHTML = items.map(function (item) {
            const articleId = item.articleKey || item.conceptId;
            return "<button class='list-item' data-article-id='" + escapeHtml(articleId) + "' data-source-id='"
                    + escapeHtml(item.sourceId == null ? "" : String(item.sourceId))
                    + "' type='button'>"
                    + "<div class='meta-row'>"
                    + renderBadge(item.lifecycle)
                    + renderBadge(item.reviewStatus)
                    + "</div>"
                    + "<h4>" + escapeHtml(resolveArticleDisplayTitle(item)) + "</h4>"
                    + "<p class='item-meta'>" + escapeHtml(buildArticleSourceLine(item)) + "</p>"
                    + "<p class='item-summary'>" + escapeHtml(resolveArticleSummary(item)) + "</p>"
                    + "<div class='meta-row'>"
                    + "<span class='pill'>" + escapeHtml(articleId) + "</span>"
                    + "<span class='pill'>来源 " + escapeHtml(String(item.sourceCount || 0)) + "</span>"
                    + "</div>"
                    + "</button>";
        }).join("");
        list.querySelectorAll("[data-article-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadArticleDetail(
                        button.dataset.articleId,
                        parseOptionalInteger(button.dataset.sourceId)
                );
            });
        });
        highlightArticle(state.selectedArticleId, state.selectedArticleSourceId);
    }

    function renderArticleDetail(detail) {
        document.getElementById("article-detail-title").textContent = resolveArticleDisplayTitle(detail);
        document.getElementById("article-detail-meta").textContent = [
            detail.articleKey || detail.conceptId,
            buildArticleSourceMeta(detail),
            detail.lifecycle ? "状态：" + getBadgeLabel(detail.lifecycle) : "",
            detail.reviewStatus ? "审查：" + getBadgeLabel(detail.reviewStatus) : "",
            detail.compiledAt ? "入库时间：" + formatDateTime(detail.compiledAt) : ""
        ].filter(Boolean).join(" | ");
        document.getElementById("article-detail-summary").textContent = detail.summary || "暂无摘要";
        document.getElementById("article-content").textContent = detail.content || "";
        document.getElementById("article-metadata").textContent = prettyJson(detail.metadataJson);
        document.getElementById("article-sources").innerHTML = renderTagGroup(detail.sourcePaths || []);
        const relations = []
                .concat((detail.referentialKeywords || []).map(function (item) { return "关键词: " + item; }))
                .concat((detail.dependsOn || []).map(function (item) { return "依赖: " + item; }))
                .concat((detail.related || []).map(function (item) { return "相关: " + item; }));
        document.getElementById("article-relations").innerHTML = renderTagGroup(relations);
    }

    function clearSourceDetail() {
        document.getElementById("source-detail-title").textContent = "请选择一个资料源";
        document.getElementById("source-detail-meta").textContent = "";
        document.getElementById("source-run-list").innerHTML = "<div class='job-card'><p class='item-summary'>暂无同步历史</p></div>";
        document.getElementById("source-file-list").innerHTML = "<div class='job-card'><p class='item-summary'>暂无文件</p></div>";
        document.getElementById("source-detail-config").textContent = "暂无配置";
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

    function renderRunCollection(containerId, items, emptyMessage) {
        const container = document.getElementById(containerId);
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>" + escapeHtml(emptyMessage) + "</p></div>";
            return;
        }
        const visibleItems = items.slice()
                .sort(compareRunsByRequestedAtDesc)
                .slice(0, 6);
        container.innerHTML = visibleItems.map(function (item) {
            return "<div class='job-card'>"
                    + "<div class='meta-row'>"
                    + renderBadge(item.status)
                    + renderBadge(item.syncAction || item.resolverDecision || "AUTO")
                    + renderBadge(item.sourceType || "UPLOAD")
                    + "</div>"
                    + "<h4>" + escapeHtml(getRunTitle(item)) + "</h4>"
                    + "<p class='item-summary'>" + escapeHtml(getRunSummary(item)) + "</p>"
                    + "<p class='job-meta-line'>" + escapeHtml(getRunMetaLine(item)) + "</p>"
                    + (item.errorMessage
                    ? "<div class='job-error'><strong>错误信息</strong><p>" + escapeHtml(item.errorMessage) + "</p></div>"
                    : "")
                    + buildRunActions(item)
                    + "</div>";
        }).join("");
        bindRunActions(container);
    }

    function bindRunActions(container) {
        container.querySelectorAll("[data-confirm-run]").forEach(function (button) {
            button.addEventListener("click", function () {
                confirmRun(
                        parseOptionalInteger(button.dataset.confirmRun),
                        button.dataset.confirmDecision,
                        parseOptionalInteger(button.dataset.confirmSourceId)
                );
            });
        });
    }

    function buildRunActions(item) {
        if (normalizeStatus(item.status) !== "WAIT_CONFIRM") {
            return "";
        }
        const buttons = [
            "<button class='ghost-btn' data-confirm-run='" + escapeHtml(String(item.runId))
                    + "' data-confirm-decision='NEW_SOURCE' type='button'>确认为新资料源</button>"
        ];
        if (item.matchedSourceId) {
            buttons.push(
                    "<button class='secondary-btn' data-confirm-run='" + escapeHtml(String(item.runId))
                    + "' data-confirm-decision='EXISTING_SOURCE_APPEND' data-confirm-source-id='"
                    + escapeHtml(String(item.matchedSourceId))
                    + "' type='button'>追加到候选资料源</button>"
            );
            buttons.push(
                    "<button class='ghost-btn' data-confirm-run='" + escapeHtml(String(item.runId))
                    + "' data-confirm-decision='EXISTING_SOURCE_UPDATE' data-confirm-source-id='"
                    + escapeHtml(String(item.matchedSourceId))
                    + "' type='button'>按更新覆盖候选资料源</button>"
            );
        }
        return "<div class='card-actions'>" + buttons.join("") + "</div>";
    }

    function highlightSource(sourceId) {
        document.querySelectorAll("#source-list .list-item").forEach(function (item) {
            item.classList.toggle("active", item.dataset.sourceId === String(sourceId || ""));
        });
    }

    function highlightArticle(articleId, sourceId) {
        document.querySelectorAll("#article-list .list-item").forEach(function (item) {
            const sameArticle = item.dataset.articleId === String(articleId || "");
            const sameSource = item.dataset.sourceId === String(sourceId == null ? "" : sourceId);
            item.classList.toggle("active", sameArticle && sameSource);
        });
    }

    function buildSourceFileSummary(item) {
        const mode = item.parseMode ? getBadgeLabel(item.parseMode) : "未记录";
        const provider = item.parseProvider ? item.parseProvider : "默认解析链";
        return "解析方式：" + mode + "；解析提供方：" + provider + "。";
    }

    function buildSourceFileMeta(item) {
        return [
            "格式：" + (item.format || "-"),
            "大小：" + formatBytes(item.fileSize)
        ].join(" · ");
    }

    function getSourceSummary(item) {
        if (item.lastSyncStatus) {
            return "最近同步状态：" + getBadgeLabel(item.lastSyncStatus)
                    + (item.lastSyncAt ? "，完成于 " + formatDateTime(item.lastSyncAt) : "");
        }
        return "还没有执行过同步，可以先选中后手动发起一次。";
    }

    function getRunTitle(item) {
        if (item.sourceName) {
            return item.sourceName;
        }
        if (item.sourceNames && item.sourceNames.length > 0) {
            if (item.sourceNames.length === 1) {
                return item.sourceNames[0];
            }
            return item.sourceNames[0] + " 等 " + String(item.sourceNames.length) + " 个文件";
        }
        return "资料同步运行 #" + String(item.runId);
    }

    function getRunSummary(item) {
        const normalized = normalizeStatus(item.status);
        if (normalized === "WAIT_CONFIRM") {
            return item.message || "系统无法自动判断该资料包应新建还是合并，需要人工确认。";
        }
        if (normalized === "SKIPPED_NO_CHANGE") {
            return "资料内容与最近一次成功快照一致，本次跳过编译。";
        }
        if (normalized === "FAILED") {
            return item.errorMessage || "同步运行失败，请根据错误信息检查资料源配置。";
        }
        if (normalized === "SUCCEEDED") {
            return item.message || "同步成功，资料已经完成解析并写入知识库。";
        }
        if (normalized === "COMPILE_QUEUED") {
            return "识别与物化已完成，正在等待编译任务执行。";
        }
        if (normalized === "RUNNING") {
            return "编译任务执行中，入库完成后页面会自动刷新。";
        }
        if (normalized === "MATERIALIZING") {
            return "正在拉取 Git / 复制服务器目录并准备标准化源文件。";
        }
        if (normalized === "MATCHING") {
            return "正在做资料包特征提取、规则召回和自动归并判断。";
        }
        return item.message || "同步状态已更新。";
    }

    function getRunMetaLine(item) {
        return [
            item.sourceType ? "类型：" + getBadgeLabel(item.sourceType) : "",
            item.resolverDecision ? "决策：" + getBadgeLabel(item.resolverDecision) : "",
            item.syncAction ? "动作：" + getBadgeLabel(item.syncAction) : "",
            item.requestedAt ? "提交于 " + formatDateTime(item.requestedAt) : ""
        ].filter(Boolean).join(" · ");
    }

    function buildRunCompletionMessage(run) {
        const normalized = normalizeStatus(run.status);
        if (normalized === "SKIPPED_NO_CHANGE") {
            return getRunTitle(run) + " 已检查完成，本次没有发现变化。";
        }
        if (normalized === "WAIT_CONFIRM") {
            return getRunTitle(run) + " 需要人工确认归并方式。";
        }
        if (normalized === "FAILED") {
            return getRunTitle(run) + " 处理失败，请检查资料源或上传内容。";
        }
        return getRunTitle(run) + " 已处理完成，并已更新页面状态。";
    }

    function resolveRunNoticeTone(run) {
        const normalized = normalizeStatus(run.status);
        if (normalized === "FAILED" || normalized === "WAIT_CONFIRM") {
            return "warning";
        }
        if (normalized === "SKIPPED_NO_CHANGE") {
            return "info";
        }
        return "success";
    }

    function isPollingStatus(status) {
        return RUN_POLLING_STATUSES.indexOf(normalizeStatus(status)) >= 0;
    }

    function compareRunsByRequestedAtDesc(left, right) {
        return toTimestamp(right && right.requestedAt) - toTimestamp(left && left.requestedAt);
    }

    function resolveArticleDisplayTitle(item) {
        if (!item) {
            return "未命名内容";
        }
        const primarySourceName = getPrimaryArticleSourceName(item);
        if (isGenericArticleTitle(item.title, item.conceptId) && primarySourceName) {
            return formatArticleSourceTitle(primarySourceName);
        }
        return item.title || formatArticleSourceTitle(primarySourceName) || item.articleKey || item.conceptId || "未命名内容";
    }

    function resolveArticleSummary(item) {
        const summary = String(item && item.summary ? item.summary : "").trim();
        if (summary && !isGenericArticleTitle(summary, item && item.conceptId ? item.conceptId : "")) {
            return summary;
        }
        const primarySourceName = getPrimaryArticleSourceName(item);
        if (primarySourceName) {
            return "这条内容来自 " + primarySourceName + "。";
        }
        return summary || "暂无摘要";
    }

    function buildArticleSourceLine(item) {
        const primarySourceName = getPrimaryArticleSourceName(item);
        const articleId = item.articleKey || item.conceptId || "-";
        if (!primarySourceName) {
            return articleId;
        }
        return primarySourceName + " · " + articleId;
    }

    function buildArticleSourceMeta(item) {
        const parts = [];
        if (item.sourceId != null) {
            parts.push("资料源：" + String(item.sourceId));
        }
        if (item.articleKey) {
            parts.push("文章键：" + item.articleKey);
        }
        if (item.sourcePaths && item.sourcePaths.length > 0) {
            parts.push("来源：" + item.sourcePaths[0]);
        }
        return parts.join(" | ");
    }

    function getPrimaryArticleSourceName(item) {
        return item && item.primarySourceName
                ? String(item.primarySourceName)
                : Array.isArray(item && item.sourcePaths) && item.sourcePaths.length > 0
                ? String(item.sourcePaths[0])
                : "";
    }

    function containsSource(items, sourceId) {
        return items.some(function (item) {
            return item.id === sourceId;
        });
    }

    function containsArticle(items, articleId, sourceId) {
        return items.some(function (item) {
            const currentArticleId = item.articleKey || item.conceptId;
            return currentArticleId === articleId && (item.sourceId || null) === (sourceId || null);
        });
    }

    function clearServerSourceForm() {
        document.getElementById("server-source-name").value = "";
        document.getElementById("server-source-code").value = "";
        document.getElementById("server-source-dir").value = "";
    }

    function formatBytes(value) {
        const size = Number(value || 0);
        if (!Number.isFinite(size) || size <= 0) {
            return "0 B";
        }
        if (size < 1024) {
            return String(size) + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024).toFixed(1) + " KB";
        }
        return (size / (1024 * 1024)).toFixed(1) + " MB";
    }

    function prettyJson(value) {
        if (!value) {
            return "暂无配置";
        }
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        }
        catch (error) {
            return String(value);
        }
    }

    function isGenericArticleTitle(title, conceptId) {
        const normalizedTitle = normalizeComparableLabel(title);
        if (!normalizedTitle) {
            return true;
        }
        const normalizedConceptId = normalizeComparableLabel(conceptId);
        return normalizedTitle === "defaultgroup"
                || normalizedTitle === "default"
                || (normalizedConceptId && normalizedTitle === normalizedConceptId);
    }

    function normalizeComparableLabel(value) {
        return String(value || "")
                .toLowerCase()
                .replace(/[^\p{L}\p{N}]+/gu, "");
    }

    function formatArticleSourceTitle(sourceName) {
        const normalized = String(sourceName || "").trim().replaceAll("\\", "/");
        if (!normalized) {
            return "";
        }
        const segments = normalized.split("/").filter(function (segment) {
            return segment;
        });
        const fileName = segments.length === 0 ? normalized : segments[segments.length - 1];
        const extensionIndex = fileName.lastIndexOf(".");
        if (extensionIndex > 0) {
            return fileName.slice(0, extensionIndex);
        }
        return fileName;
    }

    function toTimestamp(value) {
        const date = new Date(value || "");
        return Number.isNaN(date.getTime()) ? 0 : date.getTime();
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

    function renderBadge(value) {
        const normalized = normalizeStatus(value);
        let className = "badge";
        if (normalized === "SUCCEEDED"
                || normalized === "ACTIVE"
                || normalized === "PASSED"
                || normalized === "CONFIRMED"
                || normalized === "DOCUMENT"
                || normalized === "UPLOAD"
                || normalized === "NORMAL") {
            className += " success";
        }
        else if (normalized === "FAILED" || normalized === "ARCHIVED") {
            className += " danger";
        }
        else {
            className += " warning";
        }
        return "<span class='" + className + "'>" + escapeHtml(getBadgeLabel(value)) + "</span>";
    }

    function normalizeStatus(value) {
        return String(value || "").trim().toUpperCase();
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
            throw await buildHttpError(response);
        }
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            return response.json();
        }
        return response.text();
    }

    async function buildHttpError(response) {
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            const payload = await response.json();
            const message = payload && payload.message
                    ? payload.message
                    : JSON.stringify(payload);
            const error = new Error(message || ("HTTP " + response.status));
            error.status = response.status;
            error.payload = payload;
            return error;
        }
        const text = await response.text();
        const error = new Error(text || ("HTTP " + response.status));
        error.status = response.status;
        return error;
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

    function setStatus(message, tone, persist) {
        const resolvedTone = tone || "info";
        const resolvedPersist = typeof persist === "boolean"
                ? persist
                : (resolvedTone === "danger" || resolvedTone === "warning");
        renderPageNotice(message, resolvedTone, resolvedPersist);
    }

    function showError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        setStatus(prefix + "：" + message, "danger");
    }

    function renderPageNotice(message, tone, persist) {
        const notice = document.getElementById("page-notice");
        if (!notice) {
            return;
        }
        if (pageNoticeTimer) {
            window.clearTimeout(pageNoticeTimer);
            pageNoticeTimer = null;
        }
        notice.hidden = false;
        notice.className = "page-notice" + (tone ? " " + tone : "");
        notice.textContent = message;
        if (!persist) {
            pageNoticeTimer = window.setTimeout(function () {
                notice.hidden = true;
                notice.className = "page-notice";
                notice.textContent = "";
                pageNoticeTimer = null;
            }, 3200);
        }
    }

    function getBadgeLabel(value) {
        const normalized = normalizeStatus(value);
        const labels = {
            ACTIVE: "生效中",
            DISABLED: "已停用",
            ARCHIVED: "已归档",
            PASSED: "已通过",
            PENDING: "待处理",
            NEEDS_HUMAN_REVIEW: "需人工复核",
            SUCCEEDED: "成功",
            FAILED: "失败",
            RUNNING: "编译中",
            QUEUED: "排队中",
            MATCHING: "识别中",
            MATERIALIZING: "物化中",
            COMPILE_QUEUED: "待编译",
            WAIT_CONFIRM: "待确认",
            SKIPPED_NO_CHANGE: "无变化跳过",
            CONFIRMED: "已确认",
            NEW_SOURCE: "新建资料源",
            EXISTING_SOURCE_UPDATE: "更新已有资料源",
            EXISTING_SOURCE_APPEND: "追加到已有资料源",
            AMBIGUOUS: "需人工判断",
            CREATE: "新建",
            UPDATE: "更新",
            APPEND: "追加",
            REBUILD: "重建",
            ACTIVE_ONLY: "仅活跃",
            UPLOAD: "上传型",
            GIT: "Git",
            SERVER_DIR: "服务器目录",
            DOCUMENT: "文档",
            CODE: "代码",
            REPORT: "报告",
            ASSET_HEAVY: "附件型",
            FULL: "全量",
            INCREMENTAL: "增量",
            AUTO: "自动",
            NORMAL: "普通",
            ADMIN_ONLY: "仅管理员",
            DIRECT_TEXT: "直读",
            OCR_IMAGE: "图片 OCR",
            OCR_SCANNED_PDF: "扫描 PDF OCR",
            HYBRID_PDF: "PDF 混合解析",
            UNKNOWN: "未知"
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

    function parseOptionalInteger(value) {
        if (value == null || String(value).trim() === "") {
            return null;
        }
        const parsed = Number.parseInt(String(value), 10);
        return Number.isNaN(parsed) ? null : parsed;
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
