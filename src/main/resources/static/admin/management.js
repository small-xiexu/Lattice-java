(function () {
    const PROCESSING_TASK_LIST_LIMIT = 50;
    const PROCESSING_TASK_POLL_INTERVAL_MS = 3000;
    const PROCESSING_TASK_RETRY_INTERVAL_MS = 6000;
    const UPLOAD_SUPPORTED_EXTENSIONS = Object.freeze(new Set([
        "doc", "docx", "pptx", "pdf", "xlsx", "xls", "csv",
        "md", "markdown", "txt", "json", "html", "xml",
        "yml", "yaml", "properties", "js", "css", "java", "sh", "py"
    ]));
    const RUN_REASON_SUMMARY_DISPLAY_STATUSES = Object.freeze(new Set([
        "FAILED",
        "STALLED",
        "WAIT_CONFIRM",
        "SKIPPED_NO_CHANGE",
        "QUEUED",
        "PENDING"
    ]));
    const RUN_BOARD_FOCUS_STATUSES = Object.freeze(new Set([
        "RUNNING",
        "QUEUED",
        "MATCHING",
        "MATERIALIZING",
        "COMPILE_QUEUED",
        "PENDING",
        "FAILED",
        "STALLED",
        "WAIT_CONFIRM"
    ]));

    const state = {
        selectedArticleId: null,
        selectedArticleSourceId: null,
        selectedSourceId: null,
        selectedSourceRunKey: null,
        selectedSourceRunMode: "auto",
        latestSourceRunKey: null,
        selectedSourceFilePath: null,
        overview: null,
        health: null,
        recentRunSummary: null,
        recentRuns: [],
        articleCount: 0,
        pendingRouteTab: null,
        pendingRouteNotice: null,
        uploadFiles: [],
        sourceCredentials: [],
        sources: [],
        sourceRuns: [],
        sourceFiles: [],
        queryFeedbackItems: [],
        selectedQueryFeedbackId: null,
        activeRunId: null
    };

    let pageNoticeTimer = null;
    let runPollingTimer = null;
    let processingTaskPollingTimer = null;
    let processingTaskPollingInFlight = false;
    let processingTaskAutoRefreshActive = false;
    let processingTaskLastRefreshedAt = null;

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        consumeInitialRoute();
        refreshPage();
    });

    function bindEvents() {
        bindIfPresent("refresh-page", "click", refreshPage);
        bindIfPresent("refresh-summary", "click", refreshSummary);
        bindIfPresent("refresh-health", "click", refreshHealth);
        bindIfPresent("scroll-workbench-top", "click", scrollToWorkbenchTop);
        bindIfPresent("refresh-jobs", "click", function () {
            loadProcessingTasks({background: false});
        });
        bindIfPresent("refresh-sources", "click", loadSources);
        bindIfPresent("search-articles", "click", loadArticles);
        bindIfPresent("article-source-filter", "change", loadArticles);
        bindIfPresent("article-review-status", "change", loadArticles);
        bindIfPresent("article-risk-filter", "change", loadArticles);
        bindIfPresent("refresh-hotspots", "click", refreshHotspots);
        bindIfPresent("approve-article-review", "click", approveSelectedArticleReview);
        bindIfPresent("request-article-changes", "click", requestSelectedArticleChanges);
        bindIfPresent("refresh-query-feedback", "click", loadQueryFeedback);
        bindIfPresent("query-feedback-status-filter", "change", loadQueryFeedback);
        bindIfPresent("resolve-query-feedback", "click", resolveSelectedQueryFeedback);
        bindIfPresent("dismiss-query-feedback", "click", dismissSelectedQueryFeedback);
        bindIfPresent("submit-upload-job", "click", uploadAndCompile);
        bindIfPresent("create-git-source", "click", createGitSourceAndSync);
        bindIfPresent("toggle-inline-git-credential", "click", toggleInlineGitCredentialPanel);
        bindIfPresent("save-inline-source-credential", "click", saveInlineSourceCredential);
        bindIfPresent("reset-inline-source-credential", "click", resetInlineSourceCredentialForm);
        bindGitAccessModeEvents();
        bindUploadFilePicker();
        bindIfPresent("create-server-source", "click", createServerSourceAndSync);
        bindIfPresent("sync-selected-source", "click", syncSelectedSource);
        bindIfPresent("rebuild-chunks", "click", rebuildChunks);
        bindProcessingTaskAutoRefreshEvents();
        document.addEventListener("click", handleKnowledgeHelpActionClick);
    }

    function bindIfPresent(id, eventName, handler) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
    }

    function bindProcessingTaskAutoRefreshEvents() {
        document.addEventListener("visibilitychange", handleProcessingTaskVisibilityChange);
        if (window && typeof window.addEventListener === "function") {
            window.addEventListener("focus", handleProcessingTaskWindowFocus);
        }
    }

    function handleProcessingTaskVisibilityChange() {
        if (isDocumentHidden()) {
            clearProcessingTaskPollingTimer();
            if (processingTaskAutoRefreshActive) {
                renderProcessingTaskRefreshStatus("paused");
            }
            return;
        }
        if (processingTaskAutoRefreshActive || hasActiveProcessingTasks(state.recentRuns)) {
            loadProcessingTasks({background: true});
        }
    }

    function handleProcessingTaskWindowFocus() {
        if (!isDocumentHidden() && (processingTaskAutoRefreshActive || hasActiveProcessingTasks(state.recentRuns))) {
            loadProcessingTasks({background: true});
        }
    }

    function bindUploadFilePicker() {
        const picker = document.getElementById("compile-file-picker");
        const filesInput = document.getElementById("compile-files");
        const fileTrigger = document.getElementById("compile-file-trigger");
        const clearButton = document.getElementById("compile-file-clear");
        const list = document.getElementById("compile-file-list");
        if (!picker || !filesInput) {
            return;
        }

        filesInput.addEventListener("change", function () {
            handleUploadInputChange(filesInput.files);
            filesInput.value = "";
        });
        if (fileTrigger) {
            fileTrigger.addEventListener("click", openUploadFileDialog);
        }
        if (clearButton) {
            clearButton.addEventListener("click", clearUploadFileSelection);
        }
        if (list) {
            list.addEventListener("click", handleUploadFileListClick);
        }
        picker.addEventListener("click", handleUploadPickerClick);
        picker.addEventListener("dragenter", handleUploadPickerDragEnter);
        picker.addEventListener("dragover", handleUploadPickerDragEnter);
        picker.addEventListener("dragleave", handleUploadPickerDragLeave);
        picker.addEventListener("drop", handleUploadPickerDrop);
        renderUploadFileSelection(state.uploadFiles);
        renderUploadPickerFeedback([], 0);
    }

    function handleUploadInputChange(fileList) {
        const validation = validateUploadFiles(Array.from(fileList || []));
        setUploadFiles(validation.acceptedFiles);
        renderUploadPickerFeedback(validation.rejectedFiles, 0);
    }

    function handleUploadPickerClick(event) {
        if (!event) {
            return;
        }
        if (event.target.closest("button")
                || event.target.closest(".import-file-picker-list")) {
            return;
        }
        openUploadFileDialog();
    }

    function openUploadFileDialog(event) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        const filesInput = document.getElementById("compile-files");
        if (!filesInput) {
            return;
        }
        filesInput.click();
    }

    function clearUploadFileSelection(event) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        const filesInput = document.getElementById("compile-files");
        if (filesInput) {
            filesInput.value = "";
        }
        setUploadFiles([]);
        renderUploadPickerFeedback([], 0);
    }

    function handleUploadPickerDragEnter(event) {
        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = "copy";
        }
        const picker = document.getElementById("compile-file-picker");
        if (picker) {
            picker.classList.add("dragover");
        }
    }

    function handleUploadPickerDragLeave(event) {
        const picker = document.getElementById("compile-file-picker");
        if (!picker) {
            return;
        }
        if (event.relatedTarget && picker.contains(event.relatedTarget)) {
            return;
        }
        picker.classList.remove("dragover");
    }

    function handleUploadPickerDrop(event) {
        event.preventDefault();
        const picker = document.getElementById("compile-file-picker");
        if (picker) {
            picker.classList.remove("dragover");
        }
        if (!event.dataTransfer || !event.dataTransfer.files || event.dataTransfer.files.length === 0) {
            return;
        }
        const validation = validateUploadFiles(Array.from(event.dataTransfer.files));
        const mergeResult = mergeUploadFiles(state.uploadFiles, validation.acceptedFiles);
        setUploadFiles(mergeResult.files);
        renderUploadPickerFeedback(validation.rejectedFiles, mergeResult.duplicateCount);
    }

    function handleUploadFileListClick(event) {
        const removeButton = event.target.closest("[data-file-remove-index]");
        if (!removeButton) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        const index = Number(removeButton.getAttribute("data-file-remove-index"));
        removeUploadFileAt(index);
    }

    function removeUploadFileAt(index) {
        if (Number.isNaN(index)) {
            return;
        }
        const nextFiles = state.uploadFiles.filter(function (file, fileIndex) {
            return fileIndex !== index;
        });
        setUploadFiles(nextFiles);
    }

    function setUploadFiles(files) {
        state.uploadFiles = Array.from(files || []);
        renderUploadFileSelection(state.uploadFiles);
    }

    function mergeUploadFiles(existingFiles, incomingFiles) {
        const merged = [];
        const seen = new Set();
        let duplicateCount = 0;
        existingFiles.concat(incomingFiles).forEach(function (file) {
            const key = buildUploadFileKey(file);
            if (seen.has(key)) {
                duplicateCount++;
                return;
            }
            seen.add(key);
            merged.push(file);
        });
        return {
            files: merged,
            duplicateCount: duplicateCount
        };
    }

    function buildUploadFileKey(file) {
        return [getUploadRelativePath(file), file.size || 0, file.lastModified || 0].join("::");
    }

    function getUploadRelativePath(file) {
        const rawPath = file && typeof file.webkitRelativePath === "string" && file.webkitRelativePath.trim()
                ? file.webkitRelativePath
                : file && file.name
                ? file.name
                : "";
        return String(rawPath || "")
                .replace(/\\/g, "/")
                .replace(/^\/+/g, "")
                .replace(/\/+$/g, "");
    }

    function getUploadBaseName(file) {
        const normalizedPath = getUploadRelativePath(file);
        if (!normalizedPath) {
            return "";
        }
        const segments = normalizedPath.split("/");
        return segments[segments.length - 1] || normalizedPath;
    }

    function countUploadFolders(files) {
        const topLevelFolders = new Set();
        Array.from(files || []).forEach(function (file) {
            const relativePath = getUploadRelativePath(file);
            const slashIndex = relativePath.indexOf("/");
            if (slashIndex > 0) {
                topLevelFolders.add(relativePath.substring(0, slashIndex));
            }
        });
        return topLevelFolders.size;
    }

    function validateUploadFiles(files) {
        const acceptedFiles = [];
        const rejectedFiles = [];
        files.forEach(function (file) {
            if (isSupportedUploadFile(file)) {
                acceptedFiles.push(file);
                return;
            }
            rejectedFiles.push(file);
        });
        return {
            acceptedFiles: acceptedFiles,
            rejectedFiles: rejectedFiles
        };
    }

    function isSupportedUploadFile(file) {
        const extension = extractUploadFileExtension(getUploadBaseName(file));
        return extension ? UPLOAD_SUPPORTED_EXTENSIONS.has(extension) : false;
    }

    function extractUploadFileExtension(fileName) {
        const normalized = String(fileName || "").trim().toLowerCase();
        const dotIndex = normalized.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex === normalized.length - 1) {
            return "";
        }
        return normalized.substring(dotIndex + 1);
    }

    function renderUploadFileSelection(fileList) {
        const picker = document.getElementById("compile-file-picker");
        const trigger = document.getElementById("compile-file-trigger");
        const clearButton = document.getElementById("compile-file-clear");
        const summary = document.getElementById("compile-file-summary");
        const helper = document.getElementById("compile-file-helper");
        const list = document.getElementById("compile-file-list");
        if (!picker || !summary || !helper || !list) {
            return;
        }
        const files = Array.from(fileList || []);
        picker.classList.remove("dragover");
        picker.classList.toggle("has-files", files.length > 0);
        if (trigger) {
            trigger.textContent = files.length > 0 ? "继续添加文件" : "上传资料";
        }
        if (clearButton) {
            clearButton.hidden = files.length === 0;
        }
        if (files.length === 0) {
            summary.textContent = "未选择任何文件";
            helper.textContent = "支持拖拽多个文件，也支持直接把整个文件夹拖进来上传。";
            list.hidden = true;
            list.replaceChildren();
            return;
        }

        const totalBytes = files.reduce(function (sum, file) {
            return sum + (file.size || 0);
        }, 0);
        const folderCount = countUploadFolders(files);
        const summaryParts = [files.length === 1 ? "已选择 1 个文件" : "已选择 " + files.length + " 个文件"];
        if (folderCount > 0) {
            summaryParts.push("来自 " + folderCount + " 个文件夹");
        }
        summaryParts.push(formatFileSize(totalBytes));
        summary.textContent = summaryParts.join(" · ");
        helper.textContent = folderCount > 0
                ? "会按原始相对路径上传文件夹内容；可移除单个文件或直接清空整个列表。"
                : "可拖拽继续追加，也可以移除单个文件或直接清空整个列表。";

        const fragment = document.createDocumentFragment();
        files.forEach(function (file, index) {
            const displayName = getUploadRelativePath(file);
            const baseName = getUploadBaseName(file) || file.name || "未命名文件";
            const tone = resolveUploadFileTone(baseName);
            const item = document.createElement("div");
            item.className = "import-file-item";
            item.setAttribute("data-file-tone", tone);

            const main = document.createElement("div");
            main.className = "import-file-item-main";

            const chip = document.createElement("span");
            chip.className = "import-file-chip";
            chip.setAttribute("data-file-tone", tone);
            chip.textContent = resolveUploadFileLabel(baseName);

            const copy = document.createElement("div");
            copy.className = "import-file-item-copy";

            const title = document.createElement("strong");
            title.textContent = baseName;
            title.title = displayName;

            const meta = document.createElement("span");
            meta.textContent = [
                displayName !== baseName ? displayName : null,
                formatFileSize(file.size || 0),
                resolveUploadFileTypeText(baseName)
            ].filter(Boolean).join(" · ");

            copy.appendChild(title);
            copy.appendChild(meta);
            main.appendChild(chip);
            main.appendChild(copy);

            const removeButton = document.createElement("button");
            removeButton.type = "button";
            removeButton.className = "import-file-item-remove";
            removeButton.setAttribute("data-file-remove-index", String(index));
            removeButton.setAttribute("aria-label", "移除 " + displayName);
            removeButton.textContent = "移除";

            item.appendChild(main);
            item.appendChild(removeButton);
            fragment.appendChild(item);
        });
        list.replaceChildren(fragment);
        list.hidden = false;
    }

    function resolveUploadFileLabel(fileName) {
        const extension = extractUploadFileExtension(fileName);
        const labelMap = {
            markdown: "md",
            properties: "prop"
        };
        const label = labelMap[extension] || extension || "file";
        return label.toUpperCase();
    }

    function resolveUploadFileTone(fileName) {
        const extension = extractUploadFileExtension(fileName);
        if (["doc", "docx", "pdf", "pptx"].includes(extension)) {
            return "document";
        }
        if (["xlsx", "xls", "csv"].includes(extension)) {
            return "data";
        }
        if (["md", "markdown", "txt", "json", "xml", "yml", "yaml", "properties"].includes(extension)) {
            return "text";
        }
        return "code";
    }

    function resolveUploadFileTypeText(fileName) {
        const tone = resolveUploadFileTone(fileName);
        if (tone === "document") {
            return "文档资料";
        }
        if (tone === "data") {
            return "表格数据";
        }
        if (tone === "text") {
            return "文本资料";
        }
        return "代码/配置";
    }

    function renderUploadPickerFeedback(rejectedFiles, duplicateCount) {
        const feedback = document.getElementById("compile-file-feedback");
        if (!feedback) {
            return;
        }
        const rejected = Array.from(rejectedFiles || []);
        if (rejected.length === 0 && !duplicateCount) {
            feedback.hidden = true;
            feedback.replaceChildren();
            return;
        }

        const fragment = document.createDocumentFragment();
        const title = document.createElement("strong");
        title.textContent = "文件列表已更新";
        fragment.appendChild(title);

        if (rejected.length > 0) {
            const message = document.createElement("p");
            message.textContent = "以下文件未加入当前列表，因为格式暂不支持：";
            fragment.appendChild(message);

            const list = document.createElement("div");
            list.className = "import-file-picker-feedback-list";
            rejected.forEach(function (file) {
                const item = document.createElement("span");
                item.className = "import-file-feedback-chip";
                item.textContent = getUploadRelativePath(file) || "未命名文件";
                list.appendChild(item);
            });
            fragment.appendChild(list);
        }

        if (duplicateCount > 0) {
            const duplicateMessage = document.createElement("p");
            duplicateMessage.textContent = "另有 " + duplicateCount + " 个重复文件已自动忽略。";
            fragment.appendChild(duplicateMessage);
        }

        feedback.replaceChildren(fragment);
        feedback.hidden = false;
    }

    function formatFileSize(bytes) {
        if (!bytes || bytes <= 0) {
            return "0 B";
        }
        const units = ["B", "KB", "MB", "GB"];
        let value = bytes;
        let index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value = value / 1024;
            index++;
        }
        const digits = value >= 10 || index === 0 ? 0 : 1;
        return value.toFixed(digits) + " " + units[index];
    }

    function activateKnowledgeTab(tabName, options) {
        if (!window.AdminTabs || typeof window.AdminTabs.activate !== "function") {
            return;
        }
        window.AdminTabs.activate("knowledge-console", tabName, options);
    }

    function scrollToWorkbenchTop() {
        const target = document.querySelector(".workbench-status-panel");
        if (target && typeof target.scrollIntoView === "function") {
            target.scrollIntoView({behavior: "smooth", block: "start"});
        }
    }

    function consumeInitialRoute() {
        if (typeof window === "undefined" || !window.location || typeof URLSearchParams !== "function") {
            return;
        }
        const params = new URLSearchParams(window.location.search || "");
        const sourceId = parseOptionalInteger(params.get("sourceId"));
        const tabName = normalizeKnowledgeTab(params.get("tab"));
        const noticeCode = String(params.get("notice") || "").trim();

        if (sourceId) {
            state.selectedSourceId = sourceId;
        }
        if (tabName) {
            state.pendingRouteTab = tabName;
        }
        if (noticeCode === "server-dir-created") {
            state.pendingRouteNotice = "已切换到刚创建的服务器目录资料源，可直接查看同步状态、文件明细或继续手动同步。";
            params.delete("notice");
            const nextQuery = params.toString();
            const nextUrl = window.location.pathname + (nextQuery ? "?" + nextQuery : "") + (window.location.hash || "");
            if (window.history && typeof window.history.replaceState === "function") {
                window.history.replaceState(null, "", nextUrl);
            }
        }
    }

    function normalizeKnowledgeTab(tabName) {
        const normalized = String(tabName || "").trim();
        if (normalized === "knowledge-status") {
            return "knowledge-upload";
        }
        const allowedTabs = ["knowledge-upload", "knowledge-runs", "knowledge-articles", "knowledge-feedback"];
        return allowedTabs.includes(normalized) ? normalized : null;
    }

    function applyInitialRouteState() {
        if (state.pendingRouteTab) {
            activateKnowledgeTab(state.pendingRouteTab);
            state.pendingRouteTab = null;
        }
        if (state.pendingRouteNotice) {
            setStatus(state.pendingRouteNotice, "success");
            state.pendingRouteNotice = null;
        }
    }

    async function refreshPage() {
        await refreshSummary();
        await loadSources();
        await Promise.all([
            loadSourceCredentials(),
            loadProcessingTasks({background: false}),
            loadArticles(),
            loadQueryFeedback()
        ]);
        applyGitAccessMode(resolveGitAccessMode());
        applyInitialRouteState();
    }

    async function loadSourceCredentials() {
        try {
            const items = await fetchJson("/api/v1/admin/source-credentials");
            state.sourceCredentials = items || [];
            renderGitCredentialOptions(state.sourceCredentials);
            renderInlineSourceCredentialList(state.sourceCredentials);
        }
        catch (error) {
            state.sourceCredentials = [];
            renderGitCredentialOptions([]);
            renderInlineSourceCredentialList([]);
        }
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
            renderKnowledgeHelpSystem();
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
        if (health.tone === "success") {
            clearStatus();
            return;
        }
        setStatus("服务健康状态：" + health.label, health.tone === "danger" ? "warning" : health.tone);
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
                renderKnowledgeHelpSystem();
                return;
            }
            if (!containsSource(items, state.selectedSourceId)) {
                state.selectedSourceId = items[0].id;
            }
            highlightSource(state.selectedSourceId);
            renderKnowledgeHelpSystem();
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
            if (state.selectedSourceId !== sourceId) {
                state.selectedSourceRunMode = "auto";
                state.selectedSourceRunKey = null;
                state.latestSourceRunKey = null;
            }
            state.selectedSourceId = sourceId;
            const results = await Promise.all([
                fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId)),
                fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId) + "/processing-tasks?limit=20"),
                fetchJson("/api/v1/admin/sources/" + encodeURIComponent(sourceId) + "/files")
            ]);
            renderSourceDetail(results[0], resolveSourceProcessingHistoryItems(results[1]), results[2] || []);
            highlightSource(sourceId);
        }
        catch (error) {
            showError("加载资料源详情失败", error);
        }
    }

    async function loadProcessingTasks(options) {
        const backgroundRefresh = Boolean(options && options.background);
        clearProcessingTaskPollingTimer();
        if (processingTaskPollingInFlight) {
            if (!backgroundRefresh || !processingTaskLastRefreshedAt) {
                renderProcessingTaskRefreshStatus("loading");
            }
            return;
        }
        processingTaskPollingInFlight = true;
        if (!backgroundRefresh || !processingTaskLastRefreshedAt) {
            renderProcessingTaskRefreshStatus("loading");
        }
        try {
            const response = await fetchJson("/api/v1/admin/processing-tasks?limit=" + PROCESSING_TASK_LIST_LIMIT);
            const items = response && response.items ? response.items : [];
            state.recentRunSummary = response && response.summary ? response.summary : null;
            state.recentRuns = items;
            processingTaskLastRefreshedAt = new Date();
            renderRecentRunOverview(state.recentRunSummary);
            renderRecentRunBoard(items);
            renderKnowledgeHelpSystem();
            updateProcessingTaskAutoRefresh(items);
        }
        catch (error) {
            state.recentRunSummary = null;
            renderProcessingTaskRefreshStatus("error");
            if (processingTaskAutoRefreshActive && !isDocumentHidden()) {
                scheduleProcessingTaskPolling(PROCESSING_TASK_RETRY_INTERVAL_MS);
            }
            showError("加载当前处理任务失败", error);
        }
        finally {
            processingTaskPollingInFlight = false;
        }
    }

    function updateProcessingTaskAutoRefresh(items) {
        const hasActiveTasks = hasActiveProcessingTasks(items);
        processingTaskAutoRefreshActive = hasActiveTasks;
        if (!hasActiveTasks) {
            stopProcessingTaskPolling();
            renderProcessingTaskRefreshStatus("idle");
            return;
        }
        if (isDocumentHidden()) {
            clearProcessingTaskPollingTimer();
            renderProcessingTaskRefreshStatus("paused");
            return;
        }
        renderProcessingTaskRefreshStatus("active");
        scheduleProcessingTaskPolling(PROCESSING_TASK_POLL_INTERVAL_MS);
    }

    function hasActiveProcessingTasks(items) {
        return Array.isArray(items) && items.some(isActiveProcessingTask);
    }

    function isActiveProcessingTask(item) {
        return Boolean(item && item.processingActive);
    }

    function scheduleProcessingTaskPolling(delayMs) {
        clearProcessingTaskPollingTimer();
        processingTaskPollingTimer = window.setTimeout(function () {
            processingTaskPollingTimer = null;
            loadProcessingTasks({background: true});
        }, delayMs);
    }

    function stopProcessingTaskPolling() {
        clearProcessingTaskPollingTimer();
        processingTaskAutoRefreshActive = false;
    }

    function clearProcessingTaskPollingTimer() {
        if (processingTaskPollingTimer) {
            window.clearTimeout(processingTaskPollingTimer);
            processingTaskPollingTimer = null;
        }
    }

    function isDocumentHidden() {
        return typeof document.hidden === "boolean" && document.hidden;
    }

    function renderProcessingTaskRefreshStatus(status) {
        const element = document.getElementById("processing-task-refresh-status");
        if (!element) {
            return;
        }
        const refreshedAt = processingTaskLastRefreshedAt
                ? " · 上次 " + formatRefreshTime(processingTaskLastRefreshedAt)
                : "";
        const labels = {
            loading: "正在刷新",
            active: "自动刷新中" + refreshedAt,
            paused: "后台已暂停" + refreshedAt,
            idle: processingTaskLastRefreshedAt ? "上次刷新 " + formatRefreshTime(processingTaskLastRefreshedAt) : "",
            error: "刷新失败" + refreshedAt
        };
        element.textContent = labels[status] || "";
        element.dataset.status = status || "";
    }

    async function loadArticles() {
        try {
            const query = document.getElementById("article-query").value.trim();
            const lifecycle = document.getElementById("article-lifecycle").value.trim();
            const sourceId = document.getElementById("article-source-filter").value.trim();
            const reviewStatus = document.getElementById("article-review-status").value.trim();
            const riskFilter = document.getElementById("article-risk-filter").value.trim();
            const response = await fetchJson(buildArticleListRequestUrl(query, lifecycle, sourceId, reviewStatus, riskFilter));
            state.articleCount = Number(response.count || 0);
            renderArticleList(response);
            const items = response.items || [];
            if (items.length === 0) {
                clearArticleDetail();
                state.selectedArticleId = null;
                state.selectedArticleSourceId = null;
                renderKnowledgeHelpSystem();
                return;
            }
            if (!containsArticle(items, state.selectedArticleId, state.selectedArticleSourceId)) {
                await loadArticleDetail(items[0].articleKey || items[0].conceptId, items[0].sourceId);
            }
            else {
                highlightArticle(state.selectedArticleId, state.selectedArticleSourceId);
            }
            renderKnowledgeHelpSystem();
        }
        catch (error) {
            showError("加载入库内容失败", error);
        }
    }

    async function refreshHotspots() {
        renderHotspotRefreshStatus({loading: true});
        try {
            const response = await fetchJson("/api/v1/admin/articles/hotspots/refresh", {
                method: "POST",
                body: JSON.stringify({})
            });
            renderHotspotRefreshStatus(response);
            const riskFilterElement = document.getElementById("article-risk-filter");
            if (riskFilterElement) {
                riskFilterElement.value = "requiresResultVerification:true";
            }
            await refreshSummary();
            await loadArticles();
            setStatus(buildHotspotRefreshStatusText(response), "success");
        }
        catch (error) {
            renderHotspotRefreshStatus(null);
            showError("刷新热点统计失败", error);
        }
    }

    async function loadQueryFeedback() {
        const statusFilterElement = document.getElementById("query-feedback-status-filter");
        if (!statusFilterElement) {
            return;
        }
        try {
            const status = statusFilterElement.value.trim();
            const response = await fetchJson(buildQueryFeedbackListRequestUrl(status, 50));
            const items = response.items || [];
            state.queryFeedbackItems = items;
            document.getElementById("query-feedback-count").textContent = String(response.count || items.length || 0);
            renderQueryFeedbackList(items);
            if (items.length === 0) {
                clearQueryFeedbackDetail();
                renderKnowledgeHelpSystem();
                return;
            }
            if (!containsQueryFeedback(items, state.selectedQueryFeedbackId)) {
                await loadQueryFeedbackDetail(items[0].id);
            }
            else {
                highlightQueryFeedback(state.selectedQueryFeedbackId);
            }
            renderKnowledgeHelpSystem();
        }
        catch (error) {
            showError("加载结果反馈失败", error);
        }
    }

    function buildQueryFeedbackListRequestUrl(status, limit) {
        const statusParam = encodeURIComponent(String(status || "").trim());
        const limitParam = encodeURIComponent(String(limit || 50));
        return "/api/v1/admin/query-feedback?status=" + statusParam + "&limit=" + limitParam;
    }

    function renderQueryFeedbackList(items) {
        const list = document.getElementById("query-feedback-list");
        if (!list) {
            return;
        }
        if (!items || items.length === 0) {
            list.innerHTML = "<div class='list-item'><p class='item-summary'>当前没有结果反馈。</p></div>";
            return;
        }
        list.innerHTML = items.map(renderQueryFeedbackListItem).join("");
        list.querySelectorAll("[data-query-feedback-id]").forEach(function (button) {
            button.addEventListener("click", function () {
                loadQueryFeedbackDetail(parseOptionalInteger(button.dataset.queryFeedbackId));
            });
        });
        highlightQueryFeedback(state.selectedQueryFeedbackId);
    }

    function renderQueryFeedbackListItem(item) {
        return "<button class='list-item query-feedback-list-item' data-query-feedback-id='"
                + escapeHtml(String(item.id || ""))
                + "' type='button'>"
                + "<div class='meta-row'>"
                + renderBadge(item.status)
                + renderBadge(item.feedbackType)
                + "</div>"
                + "<p class='item-kicker'>结果反馈</p>"
                + "<h4>" + escapeHtml(compactText(item.question || "未记录问题", 80)) + "</h4>"
                + "<p class='item-summary'>" + escapeHtml(compactText(item.answerSummary || "暂无答案摘要", 120)) + "</p>"
                + "<p class='item-caption'>" + escapeHtml(buildQueryFeedbackCaption(item)) + "</p>"
                + "</button>";
    }

    async function loadQueryFeedbackDetail(feedbackId) {
        if (!feedbackId) {
            clearQueryFeedbackDetail();
            return;
        }
        try {
            state.selectedQueryFeedbackId = feedbackId;
            const response = await fetchJson("/api/v1/admin/query-feedback/" + encodeURIComponent(feedbackId));
            renderQueryFeedbackDetail(response);
            highlightQueryFeedback(feedbackId);
        }
        catch (error) {
            showError("加载结果反馈详情失败", error);
        }
    }

    function renderQueryFeedbackDetail(response) {
        const feedback = response && response.feedback ? response.feedback : null;
        if (!feedback) {
            clearQueryFeedbackDetail();
            return;
        }
        document.getElementById("query-feedback-detail-title").textContent = "结果反馈 #" + String(feedback.id || "");
        document.getElementById("query-feedback-detail-meta").textContent = [
            getBadgeLabel(feedback.status),
            getBadgeLabel(feedback.feedbackType),
            feedback.createdAt ? "提交时间：" + formatDateTime(feedback.createdAt) : "",
            feedback.reportedBy ? "反馈人：" + feedback.reportedBy : ""
        ].filter(Boolean).join(" | ");
        document.getElementById("query-feedback-question").textContent = feedback.question || "暂无问题";
        document.getElementById("query-feedback-answer").textContent = feedback.answerSummary || "暂无答案摘要";
        document.getElementById("query-feedback-comment").textContent = feedback.comment || "暂无反馈说明";
        document.getElementById("query-feedback-articles").innerHTML = renderTagGroup(feedback.articleKeys || []);
        document.getElementById("query-feedback-sources").innerHTML = renderTagGroup(feedback.sourcePaths || []);
        renderQueryFeedbackActionPanel(feedback);
        renderQueryFeedbackHistory(response.audits || []);
    }

    function clearQueryFeedbackDetail() {
        state.selectedQueryFeedbackId = null;
        const title = document.getElementById("query-feedback-detail-title");
        if (!title) {
            return;
        }
        title.textContent = "请选择一条反馈";
        document.getElementById("query-feedback-detail-meta").textContent = "";
        document.getElementById("query-feedback-question").textContent = "暂无问题";
        document.getElementById("query-feedback-answer").textContent = "暂无答案摘要";
        document.getElementById("query-feedback-comment").textContent = "暂无反馈说明";
        document.getElementById("query-feedback-articles").innerHTML = renderTagGroup([]);
        document.getElementById("query-feedback-sources").innerHTML = renderTagGroup([]);
        renderQueryFeedbackActionPanel(null);
        renderQueryFeedbackHistory([]);
    }

    function renderQueryFeedbackActionPanel(feedback) {
        const panel = document.getElementById("query-feedback-action-panel");
        if (!panel) {
            return;
        }
        panel.hidden = normalizeStatus(feedback && feedback.status) !== "PENDING";
    }

    function renderQueryFeedbackHistory(items) {
        const container = document.getElementById("query-feedback-history");
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='review-history-empty'><p class='item-summary'>暂无处理历史。</p></div>";
            return;
        }
        container.innerHTML = items.map(renderQueryFeedbackHistoryItem).join("");
    }

    function renderQueryFeedbackHistoryItem(item) {
        const statusText = [
            getBadgeLabel(item && item.previousStatus),
            getBadgeLabel(item && item.nextStatus)
        ].filter(Boolean).join(" → ");
        return "<article class='review-history-item'>"
                + "<div class='review-history-head'>"
                + "<div class='meta-row'>"
                + renderBadge(item && item.action)
                + "<span class='pill'>" + escapeHtml(statusText || "状态未记录") + "</span>"
                + "</div>"
                + "<span class='detail-compact-time'>" + escapeHtml(formatDateTime(item && item.operatedAt)) + "</span>"
                + "</div>"
                + "<p class='item-summary'>" + escapeHtml(item && item.comment ? item.comment : "暂无处理说明") + "</p>"
                + "<p class='item-caption'>" + escapeHtml(item && item.operatedBy ? "操作人：" + item.operatedBy : "未记录操作人") + "</p>"
                + "</article>";
    }

    async function resolveSelectedQueryFeedback() {
        await handleSelectedQueryFeedback("resolve");
    }

    async function dismissSelectedQueryFeedback() {
        await handleSelectedQueryFeedback("dismiss");
    }

    async function handleSelectedQueryFeedback(action) {
        if (!state.selectedQueryFeedbackId) {
            setStatus("请先选择一条结果反馈", "warning");
            return;
        }
        try {
            const request = buildQueryFeedbackHandleRequest();
            await fetchJson(
                    "/api/v1/admin/query-feedback/" + encodeURIComponent(state.selectedQueryFeedbackId) + "/" + action,
                    {
                        method: "POST",
                        body: JSON.stringify(request)
                    }
            );
            setStatus(action === "resolve" ? "结果反馈已标记处理" : "结果反馈已标记忽略", "success");
            await refreshSummary();
            await loadQueryFeedback();
        }
        catch (error) {
            showError("处理结果反馈失败", error);
        }
    }

    function buildQueryFeedbackHandleRequest() {
        const handledByElement = document.getElementById("query-feedback-handler");
        const commentElement = document.getElementById("query-feedback-resolution-comment");
        return {
            handledBy: handledByElement ? handledByElement.value.trim() : "",
            comment: commentElement ? commentElement.value.trim() : ""
        };
    }

    function containsQueryFeedback(items, feedbackId) {
        return (items || []).some(function (item) {
            return String(item.id || "") === String(feedbackId || "");
        });
    }

    function highlightQueryFeedback(feedbackId) {
        document.querySelectorAll("[data-query-feedback-id]").forEach(function (button) {
            button.classList.toggle("active", String(button.dataset.queryFeedbackId || "") === String(feedbackId || ""));
        });
    }

    function buildQueryFeedbackCaption(item) {
        return [
            item && item.queryId ? "queryId: " + item.queryId : "",
            item && item.createdAt ? "提交：" + formatDateTime(item.createdAt) : "",
            item && item.reportedBy ? "反馈人：" + item.reportedBy : ""
        ].filter(Boolean).join(" | ");
    }

    function compactText(value, limit) {
        const normalized = String(value || "").replace(/\s+/g, " ").trim();
        const safeLimit = Number(limit || 80);
        if (normalized.length <= safeLimit) {
            return normalized;
        }
        return normalized.slice(0, Math.max(0, safeLimit - 3)) + "...";
    }

    function buildArticleListRequestUrl(query, lifecycle, sourceId, reviewStatus, riskFilter) {
        const queryParam = encodeURIComponent(String(query || "").trim());
        const lifecycleParam = encodeURIComponent(String(lifecycle || "").trim());
        const sourceIdParam = String(sourceId || "").trim();
        const reviewStatusParam = String(reviewStatus || "").trim();
        const sourceIdQuery = sourceIdParam ? "&sourceId=" + encodeURIComponent(sourceIdParam) : "";
        const reviewStatusQuery = reviewStatusParam ? "&reviewStatus=" + encodeURIComponent(reviewStatusParam) : "";
        const riskFilterQuery = buildArticleRiskFilterQuery(riskFilter);
        return "/api/v1/admin/articles?query=" + queryParam + "&lifecycle=" + lifecycleParam
                + sourceIdQuery + reviewStatusQuery + riskFilterQuery;
    }

    function buildArticleRiskFilterQuery(riskFilter) {
        const normalized = String(riskFilter || "").trim();
        if (!normalized) {
            return "";
        }
        const separatorIndex = normalized.indexOf(":");
        if (separatorIndex <= 0 || separatorIndex >= normalized.length - 1) {
            return "";
        }
        const key = normalized.slice(0, separatorIndex);
        const value = normalized.slice(separatorIndex + 1);
        const allowedKeys = ["riskLevel", "riskReason", "isHotspot", "requiresResultVerification"];
        if (!allowedKeys.includes(key)) {
            return "";
        }
        return "&" + encodeURIComponent(key) + "=" + encodeURIComponent(value);
    }

    async function loadArticleDetail(articleId, sourceId) {
        try {
            state.selectedArticleId = articleId;
            state.selectedArticleSourceId = sourceId || null;
            const query = sourceId ? "?sourceId=" + encodeURIComponent(String(sourceId)) : "";
            const results = await Promise.all([
                fetchJson("/api/v1/admin/articles/" + encodeURIComponent(articleId) + query),
                fetchJson("/api/v1/admin/articles/" + encodeURIComponent(articleId) + "/review/audits" + query)
            ]);
            renderArticleDetail(results[0], results[1]);
            highlightArticle(articleId, sourceId);
        }
        catch (error) {
            showError("加载内容详情失败", error);
        }
    }

    async function approveSelectedArticleReview() {
        if (!state.selectedArticleId) {
            setStatus("请先选择一条内容", "warning");
            return;
        }
        const request = buildArticleReviewRequest(false);
        if (!window.confirm("将把当前内容确认通过，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson(
                    "/api/v1/admin/articles/" + encodeURIComponent(state.selectedArticleId) + "/review/approve",
                    {
                        method: "POST",
                        body: JSON.stringify(request)
                    }
            );
            renderArticleReviewActionResult(result);
            setStatus("内容已确认通过", "success");
            await refreshArticleReviewAfterAction();
        }
        catch (error) {
            showError("确认通过失败", error);
        }
    }

    async function requestSelectedArticleChanges() {
        if (!state.selectedArticleId) {
            setStatus("请先选择一条内容", "warning");
            return;
        }
        const request = buildArticleReviewRequest(true);
        if (!request.correctionSummary) {
            setStatus("请先填写修正意见", "warning");
            return;
        }
        try {
            const result = await fetchJson(
                    "/api/v1/admin/articles/" + encodeURIComponent(state.selectedArticleId) + "/review/request-changes",
                    {
                        method: "POST",
                        body: JSON.stringify(request)
                    }
            );
            renderArticleReviewActionResult(result);
            setStatus("修正已提交，内容保持待复核", "success");
            await refreshArticleReviewAfterAction();
        }
        catch (error) {
            showError("提交修正失败", error);
        }
    }

    function buildArticleReviewRequest(includeCorrectionSummary) {
        const reviewerInput = document.getElementById("article-reviewer");
        const commentInput = document.getElementById("article-review-comment");
        const correctionInput = document.getElementById("article-correction-summary");
        const selectedReviewStatus = state.selectedArticleReviewStatus || null;
        return {
            sourceId: state.selectedArticleSourceId || null,
            reviewedBy: reviewerInput ? reviewerInput.value.trim() || "admin" : "admin",
            comment: commentInput ? commentInput.value.trim() : "",
            expectedReviewStatus: selectedReviewStatus,
            correctionSummary: includeCorrectionSummary && correctionInput ? correctionInput.value.trim() : ""
        };
    }

    async function refreshArticleReviewAfterAction() {
        const articleId = state.selectedArticleId;
        const sourceId = state.selectedArticleSourceId;
        await refreshSummary();
        await loadArticles();
        if (articleId) {
            await loadArticleDetail(articleId, sourceId);
        }
    }

    async function uploadAndCompile() {
        if (!state.uploadFiles || state.uploadFiles.length === 0) {
            setStatus("请先选择文件或文件夹", "warning");
            return;
        }
        const formData = new FormData();
        state.uploadFiles.forEach(function (file) {
            formData.append("files", file, getUploadRelativePath(file));
        });
        const uploadTargetSelect = document.getElementById("upload-target-source");
        const sourceId = uploadTargetSelect ? uploadTargetSelect.value.trim() : "";
        if (sourceId) {
            formData.append("sourceId", sourceId);
        }

        try {
            const response = await fetchJson("/api/v1/admin/uploads", {
                method: "POST",
                body: formData,
                isFormData: true
            });
            clearUploadFileSelection();
            renderUploadPickerFeedback([], 0);
            activateKnowledgeTab("knowledge-runs");
            await handleSubmittedRun(response, "资料已接收，正在进入识别与编译流程");
        }
        catch (error) {
            showError("上传资料失败", error);
        }
    }

    async function createGitSourceAndSync() {
        const remoteUrl = document.getElementById("git-source-remote-url").value.trim();
        const nameInput = document.getElementById("git-source-name").value.trim();
        const sourceCode = document.getElementById("git-source-code").value.trim();
        const branch = document.getElementById("git-source-branch").value.trim();
        const accessMode = resolveGitAccessMode();
        const credentialRef = accessMode === "PRIVATE"
                ? document.getElementById("git-source-credential-ref").value.trim()
                : "";
        const resolvedName = nameInput || deriveGitSourceName(remoteUrl);
        if (!remoteUrl) {
            setStatus("请先填写 Git 仓库地址", "warning");
            return;
        }
        if (!resolvedName) {
            setStatus("请先填写资料源名称，或提供一个可识别仓库名的 Git 地址", "warning");
            return;
        }
        if (accessMode === "PRIVATE" && !credentialRef) {
            const panel = document.getElementById("git-inline-credential-panel");
            if (panel && state.sourceCredentials.length === 0) {
                panel.hidden = false;
                panel.open = true;
            }
            setStatus("私有仓库需要先选一个访问凭据；如果还没有，可以直接在下方新增。", "warning");
            return;
        }
        try {
            const source = await fetchJson("/api/v1/admin/sources/git", {
                method: "POST",
                body: JSON.stringify({
                    sourceCode: sourceCode || null,
                    name: resolvedName,
                    contentProfile: "DOCUMENT",
                    remoteUrl: remoteUrl,
                    branch: branch || null,
                    credentialRef: credentialRef || null
                })
            });
            clearGitSourceForm();
            state.selectedSourceId = source.id;
            await loadSources();
            const run = await requestSourceSync(source.id);
            activateKnowledgeTab("knowledge-runs");
            await handleSubmittedRun(
                    run,
                    credentialRef
                            ? "私有仓库已提交导入，正在使用所选凭据同步"
                            : "Git 仓库已提交导入，正在开始同步"
            );
        }
        catch (error) {
            showError("导入 Git 仓库失败", error);
        }
    }

    function toggleInlineGitCredentialPanel() {
        const panel = document.getElementById("git-inline-credential-panel");
        if (!panel) {
            return;
        }
        if (panel.hidden) {
            panel.hidden = false;
        }
        panel.open = !panel.open;
        if (panel.open) {
            const codeInput = document.getElementById("inline-source-credential-code");
            if (codeInput) {
                window.setTimeout(function () {
                    codeInput.focus();
                }, 30);
            }
        }
    }

    async function saveInlineSourceCredential() {
        const credentialCode = document.getElementById("inline-source-credential-code").value.trim();
        const credentialType = document.getElementById("inline-source-credential-type").value.trim();
        const secret = document.getElementById("inline-source-credential-secret").value.trim();
        const updatedBy = document.getElementById("inline-source-credential-updated-by").value.trim();
        if (!credentialCode || !secret) {
            setStatus("请先填写凭据编码和凭据明文", "warning");
            return;
        }
        try {
            const response = await fetchJson("/api/v1/admin/source-credentials", {
                method: "POST",
                body: JSON.stringify({
                    credentialCode: credentialCode,
                    credentialType: credentialType || "GIT_TOKEN",
                    secret: secret,
                    updatedBy: updatedBy || null
                })
            });
            await loadSourceCredentials();
            applyGitAccessMode("PRIVATE");
            selectGitCredential(response.credentialCode);
            resetInlineSourceCredentialForm();
            const panel = document.getElementById("git-inline-credential-panel");
            if (panel) {
                panel.open = false;
            }
            setStatus("访问凭据已保存，并已自动选中到当前导入表单。", "success");
        }
        catch (error) {
            showError("保存访问凭据失败", error);
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
            activateKnowledgeTab("knowledge-runs");
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
        const selectedSource = findSourceById(state.sources, state.selectedSourceId);
        if (isUploadSource(selectedSource)) {
            setStatus("上传型资料源不支持再次同步，请重新上传或使用重建知识切片入口", "warning");
            return;
        }
        try {
            const run = await requestSourceSync(state.selectedSourceId);
            activateKnowledgeTab("knowledge-runs");
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

    async function requestSourceRunRetry(runId) {
        return fetchJson("/api/v1/admin/source-runs/" + encodeURIComponent(runId) + "/retry", {
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
        if (Boolean(run.processingActive)) {
            setStatus(submittedMessage + "，页面会自动刷新结果。", "info", false);
            startRunPolling(run.runId);
            return;
        }
        if (Boolean(run.requiresManualAction)) {
            activateKnowledgeTab("knowledge-runs");
            setStatus(String(run.completionNotice || "资料包需要人工确认归并方式，请在“当前处理任务”卡片中处理。"), "warning", true);
            return;
        }
        setStatus(buildRunCompletionMessage(run), resolveRunNoticeTone(run), resolveRunDisplayStatus(run) === "FAILED");
    }

    function startRunPolling(runId) {
        stopRunPolling();
        clearProcessingTaskPollingTimer();
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
            if (Boolean(run.processingActive)) {
                startRunPolling(runId);
                return;
            }
            stopRunPolling();
            if (Boolean(run.requiresManualAction)) {
                activateKnowledgeTab("knowledge-runs");
                setStatus(String(run.completionNotice || "资料包需要人工确认归并方式，请在“当前处理任务”卡片中处理。"), "warning", true);
                return;
            }
            setStatus(buildRunCompletionMessage(run), resolveRunNoticeTone(run), resolveRunDisplayStatus(run) === "FAILED");
        }
        catch (error) {
            stopRunPolling();
            showError("刷新同步状态失败", error);
        }
    }

    function renderSummary(overview, health) {
        const status = overview.status || {};
        const manualReviewCount = Number(status.reviewPendingArticleCount || 0);
        const highRiskCount = Number(status.highRiskArticleCount || 0);
        const hotspotPendingCount = Number(status.hotspotPendingVerificationCount || 0);
        const userReportedCount = Number(status.userReportedAnswerCount || 0);
        const answerFeedbackPendingCount = Number(status.answerFeedbackPendingCount || 0);
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
                label: "需复核内容",
                value: manualReviewCount,
                note: manualReviewCount > 0 ? "到“已入库内容”按复核状态筛选" : "当前没有复核积压",
                tone: manualReviewCount > 0 ? "warning" : "success"
            },
            {
                label: "高风险内容",
                value: highRiskCount,
                note: highRiskCount > 0 ? "按风险筛选定位来源与原因" : "当前没有高风险内容",
                tone: highRiskCount > 0 ? "warning" : "success"
            },
            {
                label: "热点待抽检",
                value: hotspotPendingCount,
                note: hotspotPendingCount > 0 ? "优先核对高频问题结果" : "当前没有热点抽检积压",
                tone: hotspotPendingCount > 0 ? "warning" : "success"
            },
            {
                label: "用户反馈风险",
                value: userReportedCount,
                note: userReportedCount > 0 ? "按用户反馈筛选处理" : "当前没有用户反馈风险",
                tone: userReportedCount > 0 ? "warning" : "success"
            },
            {
                label: "结果反馈待处理",
                value: answerFeedbackPendingCount,
                note: answerFeedbackPendingCount > 0 ? "到“结果反馈”处理问答结果" : "当前没有结果反馈积压",
                tone: answerFeedbackPendingCount > 0 ? "warning" : "success"
            }
        ];
        document.getElementById("summary-cards").innerHTML = cards.map(renderMetricCard).join("");
    }

    function handleKnowledgeHelpActionClick(event) {
        const trigger = event.target.closest("[data-knowledge-help-action]");
        if (!trigger) {
            return;
        }
        const action = trigger.dataset.knowledgeHelpAction;
        if (action === "knowledge-upload"
                || action === "knowledge-runs"
                || action === "knowledge-articles"
                || action === "knowledge-feedback") {
            activateKnowledgeTab(action, {scroll: true});
            return;
        }
        if (action === "knowledge-status" || action === "workbench-top") {
            scrollToWorkbenchTop();
            return;
        }
        if (action === "go-ask") {
            window.location.assign("/admin/ask");
            return;
        }
        if (action === "go-settings") {
            window.location.assign("/admin/settings");
        }
    }

    function renderKnowledgeHelpSystem() {
        const helpState = deriveKnowledgeHelpState();
        renderKnowledgeHelpCard(helpState);
        syncKnowledgeFaqOpenState(helpState.faqKey);
    }

    function deriveKnowledgeHelpState() {
        const summary = state.recentRunSummary || {};
        const backendHelpState = summary && typeof summary.helpState === "object" ? summary.helpState : null;
        if (backendHelpState) {
            return backendHelpState;
        }
        const status = state.overview && state.overview.status ? state.overview.status : null;
        if (!status) {
            return {
                tone: "info",
                title: "正在整理知识库状态",
                description: "页面会先拉取总览、当前处理任务和已入库内容，再告诉你现在应该去哪个区块继续操作。",
                actions: [
                    {label: "回到首屏状态", action: "workbench-top", className: "secondary-btn"}
                ],
                faqKey: "first-steps"
            };
        }

        const articleCount = Number(status.articleCount || 0);
        const sourceFileCount = Number(status.sourceFileCount || 0);
        const manualReviewCount = Number(status.reviewPendingArticleCount || 0);
        const highRiskCount = Number(status.highRiskArticleCount || 0);
        const hotspotPendingCount = Number(status.hotspotPendingVerificationCount || 0);
        const answerFeedbackPendingCount = Number(status.answerFeedbackPendingCount || 0);
        if (articleCount <= 0 && sourceFileCount <= 0) {
            return {
                tone: "info",
                title: "当前还没有可用资料",
                description: "先上传一批文件或接入 Git 仓库。只有资料真正入库后，知识问答页才会稳定返回结果。",
                actions: [
                    {label: "上传资料", action: "knowledge-upload", className: "primary-btn"},
                    {label: "回到首屏状态", action: "workbench-top", className: "ghost-btn"}
                ],
                faqKey: "first-steps"
            };
        }

        if (sourceFileCount > 0 && articleCount <= 0) {
            return {
                tone: "warning",
                title: "资料已经处理过，但还没有进入可问答状态",
                description: "请先查看已入库内容；如果这里仍为空，再回到当前处理任务查看后端返回的状态与原因摘要。",
                actions: [
                    {label: "去已入库内容", action: "knowledge-articles", className: "primary-btn"},
                    {label: "回资料导入", action: "knowledge-upload", className: "ghost-btn"}
                ],
                faqKey: "cannot-answer"
            };
        }

        if (manualReviewCount > 0) {
            return {
                tone: "warning",
                title: "有内容需要复核收口",
                description: "进入“已入库内容”，用复核状态筛选只看需处理条目；确认通过或提交修正后，再回到知识问答验证结果。",
                actions: [
                    {label: "去已入库内容", action: "knowledge-articles", className: "primary-btn"},
                    {label: "去知识问答", action: "go-ask", className: "ghost-btn"}
                ],
                faqKey: "cannot-answer"
            };
        }

        if (answerFeedbackPendingCount > 0) {
            return {
                tone: "warning",
                title: "有问答结果反馈待处理",
                description: "进入“结果反馈”只处理用户标记过的回答结果；先看问题、答案摘要和引用来源，再决定已处理或忽略。",
                actions: [
                    {label: "去结果反馈", action: "knowledge-feedback", className: "primary-btn"},
                    {label: "去知识问答", action: "go-ask", className: "ghost-btn"}
                ],
                faqKey: "cannot-answer"
            };
        }

        if (hotspotPendingCount > 0) {
            return {
                tone: "warning",
                title: "有高频热点内容待抽检",
                description: "进入“已入库内容”，用待结果抽检或高频热点筛选只看高热度条目；优先核对结果是否稳定、来源是否足够清楚。",
                actions: [
                    {label: "去已入库内容", action: "knowledge-articles", className: "primary-btn"},
                    {label: "去知识问答", action: "go-ask", className: "ghost-btn"}
                ],
                faqKey: "cannot-answer"
            };
        }

        if (highRiskCount > 0) {
            return {
                tone: "warning",
                title: "还有高风险内容需要治理",
                description: "进入“已入库内容”，用风险筛选先看风险原因和来源依据；只对高风险、高频或用户反馈结果做抽检。",
                actions: [
                    {label: "去已入库内容", action: "knowledge-articles", className: "primary-btn"},
                    {label: "去知识问答", action: "go-ask", className: "ghost-btn"}
                ],
                faqKey: "cannot-answer"
            };
        }

        return {
            tone: "success",
            title: "知识库已经可以使用",
            description: "资料已经进入知识库。现在可以直接提问；如果结果不准，再回到这里核对已入库内容和当前处理任务。",
            actions: [
                {label: "去知识问答", action: "go-ask", className: "primary-btn"},
                {label: "去已入库内容", action: "knowledge-articles", className: "ghost-btn"}
            ],
            faqKey: "cannot-answer"
        };
    }

    function renderKnowledgeHelpCard(helpState) {
        const container = document.getElementById("knowledge-help-card");
        if (!container || !helpState) {
            return;
        }
        container.setAttribute("data-help-tone", helpState.tone || "info");
        container.innerHTML = "<p class='help-card-eyebrow'>现在该怎么做</p>"
                + "<h3 class='help-card-title'>" + escapeHtml(helpState.title || "先看这里") + "</h3>"
                + "<p class='help-card-description'>" + escapeHtml(helpState.description || "") + "</p>"
                + renderKnowledgeHelpActions(helpState.actions || []);
    }

    function renderKnowledgeHelpActions(actions) {
        if (!actions || actions.length === 0) {
            return "";
        }
        return "<div class='help-action-row'>"
                + actions.map(function (item) {
                    return "<button class='" + escapeHtml(item.className || "ghost-btn")
                            + "' type='button' data-knowledge-help-action='" + escapeHtml(item.action || "") + "'>"
                            + escapeHtml(item.label || "继续查看")
                            + "</button>";
                }).join("")
                + "</div>";
    }

    function syncKnowledgeFaqOpenState(faqKey) {
        const container = document.getElementById("knowledge-faq-list");
        if (!container) {
            return;
        }
        const panels = Array.from(container.querySelectorAll("[data-help-faq-key]"));
        if (panels.length === 0) {
            return;
        }
        const target = panels.find(function (panel) {
            return panel.dataset.helpFaqKey === faqKey;
        }) || panels[0];
        panels.forEach(function (panel) {
            panel.open = panel === target;
        });
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
        const articleSelect = document.getElementById("article-source-filter");
        const uploadSelect = document.getElementById("upload-target-source");
        const previousUploadValue = uploadSelect ? uploadSelect.value : "";
        const previousArticleValue = articleSelect ? articleSelect.value : "";
        const options = items.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.name + "（" + item.sourceCode + "）")
                    + "</option>";
        }).join("");
        if (uploadSelect) {
            uploadSelect.innerHTML = "<option value=''>自动识别 / 自动归并</option>" + options;
        }
        if (articleSelect) {
            articleSelect.innerHTML = "<option value=''>全部资料源</option>" + options;
        }
        if (uploadSelect && containsSource(items, parseOptionalInteger(previousUploadValue))) {
            uploadSelect.value = previousUploadValue;
        }
        if (articleSelect && containsSource(items, parseOptionalInteger(previousArticleValue))) {
            articleSelect.value = previousArticleValue;
        }
    }

    function renderGitCredentialOptions(items) {
        const select = document.getElementById("git-source-credential-ref");
        if (!select) {
            return;
        }
        const previousValue = select.value;
        let options = "<option value=''>请选择访问凭据</option>";
        if (items && items.length > 0) {
            options += items.map(function (item) {
                return "<option value='" + escapeHtml(item.credentialCode) + "'>"
                        + escapeHtml(item.credentialCode + "（" + item.credentialType + "）")
                        + "</option>";
            }).join("");
        }
        select.innerHTML = options;
        if (items && items.some(function (item) {
            return item.credentialCode === previousValue;
        })) {
            select.value = previousValue;
        }
        applyGitAccessMode(resolveGitAccessMode());
    }

    function renderInlineSourceCredentialList(items) {
        const container = document.getElementById("inline-source-credential-list");
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='inline-credential-card inline-credential-card-empty'><h4>还没有可用凭据</h4><p>如果你要导入私有 Git 仓库，可以直接在上面新增一个。公开仓库则不需要这里。</p></div>";
            return;
        }
        container.innerHTML = items.map(function (item) {
            return "<div class='inline-credential-card'>"
                    + "<div class='meta-row'>"
                    + renderBadge(item.credentialType || "GIT_TOKEN")
                    + "<span class='pill'>" + escapeHtml(item.secretMask || "已加密保存") + "</span>"
                    + "</div>"
                    + "<h4>" + escapeHtml(item.credentialCode || "-") + "</h4>"
                    + "<p>" + escapeHtml(item.updatedAt ? "最近更新 " + formatDateTime(item.updatedAt) : "已保存，可直接用于私有仓库导入") + "</p>"
                    + "<div class='card-actions'>"
                    + "<button class='ghost-btn' data-use-git-credential='" + escapeHtml(item.credentialCode || "") + "' type='button'>用于当前导入</button>"
                    + "</div>"
                    + "</div>";
        }).join("");
        container.querySelectorAll("[data-use-git-credential]").forEach(function (button) {
            button.addEventListener("click", function () {
                selectGitCredential(button.dataset.useGitCredential);
                const panel = document.getElementById("git-inline-credential-panel");
                if (panel) {
                    panel.open = false;
                }
                setStatus("已为当前 Git 导入选中访问凭据。", "success");
            });
        });
    }

    function renderSourceList(items) {
        const container = document.getElementById("source-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='list-item'><p class='item-summary'>还没有资料源。你可以直接上传资料自动归并，或先创建一个服务器目录资料源。</p></div>";
            return;
        }
        container.innerHTML = items.map(function (item) {
            const displayName = resolveSourceDisplayName(item);
            const documentTitle = resolveSourceDocumentTitle(item);
            const sourceMeta = [item.sourceCode, getBadgeLabel(item.contentProfile)]
                    .filter(Boolean)
                    .join(" · ");
            return "<button class='list-item' data-source-id='" + escapeHtml(String(item.id)) + "' type='button'>"
                    + "<div class='source-list-row-top'>"
                    + "<div>"
                    + "<h4>" + escapeHtml(displayName || item.sourceCode || "未命名资料源") + "</h4>"
                    + (documentTitle && documentTitle !== displayName
                    ? "<p class='item-summary source-document-title'>" + escapeHtml(documentTitle) + "</p>"
                    : "")
                    + "</div>"
                    + "<span class='detail-compact-time'>" + escapeHtml(formatSourceLastSyncTime(item)) + "</span>"
                    + "</div>"
                    + "<p class='item-meta'>" + escapeHtml(sourceMeta || "-") + "</p>"
                    + "<div class='source-list-status-row'>"
                    + renderBadge(item.status)
                    + renderContextBadge(item.sourceType)
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
        state.sourceRuns = resolveSourceProcessingHistoryItems(runs);
        state.sourceFiles = files || [];
        const displayName = resolveSourceDisplayName(source);
        const documentTitle = resolveSourceDocumentTitle(source);
        const configElement = document.getElementById("source-detail-config");
        document.getElementById("source-detail-title").textContent = displayName || source.sourceCode || "未命名资料源";
        document.getElementById("source-detail-meta").textContent = [
            documentTitle && documentTitle !== displayName ? "文档标题：" + documentTitle : "",
            source.sourceCode,
            getBadgeLabel(source.sourceType),
            getBadgeLabel(source.status),
            source.lastSyncAt ? "最近同步 " + formatDateTime(source.lastSyncAt) : "最近未同步"
        ].filter(Boolean).join(" | ");
        if (configElement) {
            configElement.textContent = prettyJson(source.configJson, "暂无特殊配置");
            const configSection = configElement.closest(".detail-section");
            if (configSection) {
                configSection.hidden = isEmptyJson(source.configJson);
            }
        }
        updateSourceSyncButton(source);
        renderSourceRunList(state.sourceRuns);
        renderSourceFileList(state.sourceFiles);
    }

    function resolveSourceProcessingHistoryItems(response) {
        if (!response) {
            return [];
        }
        if (Array.isArray(response)) {
            return response;
        }
        if (Array.isArray(response.items)) {
            return response.items;
        }
        return [];
    }

    function resolveSourceDisplayName(source) {
        const displayName = String(source && source.displayName || "").trim();
        if (displayName) {
            return displayName;
        }
        const fallbackName = String(source && source.name || "").trim();
        const documentTitle = String(source && source.primaryDocumentTitle || "").trim();
        if (fallbackName && fallbackName !== documentTitle) {
            return fallbackName;
        }
        const metadataDisplayName = readSourceBundleField(source, "displayName");
        if (metadataDisplayName) {
            return metadataDisplayName;
        }
        const firstPath = readSourceBundleFirstPath(source);
        if (firstPath) {
            return formatArticleSourceTitle(firstPath);
        }
        return fallbackName;
    }

    function resolveSourceDocumentTitle(source) {
        const primaryDocumentTitle = String(source && source.primaryDocumentTitle || "").trim();
        if (primaryDocumentTitle) {
            return primaryDocumentTitle;
        }
        const titleHints = readSourceBundleArray(source, "titleHints");
        return titleHints.length > 0 ? titleHints[0] : "";
    }

    function readSourceBundleField(source, fieldName) {
        const bundleSummary = readSourceBundleSummary(source);
        const value = bundleSummary && bundleSummary[fieldName];
        return typeof value === "string" ? value.trim() : "";
    }

    function readSourceBundleFirstPath(source) {
        const paths = readSourceBundleArray(source, "relativePathsSample");
        return paths.length > 0 ? paths[0] : "";
    }

    function readSourceBundleArray(source, fieldName) {
        const bundleSummary = readSourceBundleSummary(source);
        const values = bundleSummary && Array.isArray(bundleSummary[fieldName])
                ? bundleSummary[fieldName]
                : [];
        return uniqueNonEmptyStrings(values);
    }

    function readSourceBundleSummary(source) {
        const metadataJson = source && source.metadataJson;
        if (!metadataJson || typeof metadataJson !== "string") {
            return null;
        }
        try {
            const metadata = JSON.parse(metadataJson);
            return metadata && typeof metadata === "object" ? metadata.bundleSummary : null;
        }
        catch (error) {
            return null;
        }
    }

    function formatSourceLastSyncTime(item) {
        if (item && item.lastSyncAt) {
            return formatDateTime(item.lastSyncAt);
        }
        return "未同步";
    }

    function isEmptyJson(value) {
        if (!value) {
            return true;
        }
        try {
            const parsed = JSON.parse(value);
            if (parsed == null) {
                return true;
            }
            if (Array.isArray(parsed)) {
                return parsed.length === 0;
            }
            if (typeof parsed === "object") {
                return Object.keys(parsed).length === 0;
            }
            return false;
        }
        catch (error) {
            return false;
        }
    }

    function updateSourceSyncButton(source) {
        const syncButton = document.getElementById("sync-selected-source");
        if (!syncButton) {
            return;
        }
        const uploadSource = isUploadSource(source);
        syncButton.hidden = uploadSource;
        syncButton.disabled = uploadSource;
        syncButton.title = uploadSource
                ? "上传型资料源不支持再次同步，请重新上传或使用重建知识切片入口"
                : "";
    }

    function renderSourceRunList(runs) {
        const container = document.getElementById("source-run-list");
        const detail = document.getElementById("source-run-detail");
        if (!runs || runs.length === 0) {
            state.selectedSourceRunKey = null;
            state.selectedSourceRunMode = "auto";
            state.latestSourceRunKey = null;
            if (container) {
                container.innerHTML = "<div class='detail-compact-empty'><p class='item-summary'>当前资料源还没有处理历史。</p></div>";
            }
            if (detail) {
                detail.hidden = true;
                detail.innerHTML = "";
            }
            return;
        }
        const visibleItems = runs.slice()
                .sort(compareRunsByRequestedAtDesc)
                .slice(0, 8);
        const latestRunKey = resolveSourceRunKey(visibleItems[0]);
        if (shouldFollowLatestSourceRun(visibleItems, latestRunKey)) {
            state.selectedSourceRunKey = latestRunKey;
            state.selectedSourceRunMode = "auto";
        }
        else if (!containsSourceRun(visibleItems, state.selectedSourceRunKey)) {
            state.selectedSourceRunKey = resolveSourceRunKey(visibleItems[0]);
            state.selectedSourceRunMode = "auto";
        }
        state.latestSourceRunKey = latestRunKey;
        container.innerHTML = visibleItems.map(function (item) {
            return renderSourceRunListItem(item, state.selectedSourceRunKey === resolveSourceRunKey(item));
        }).join("");
        container.querySelectorAll("[data-source-run-key]").forEach(function (button) {
            button.addEventListener("click", function () {
                state.selectedSourceRunKey = button.dataset.sourceRunKey;
                state.selectedSourceRunMode = "manual";
                renderSourceRunList(state.sourceRuns);
                focusSourceRunDetail();
            });
        });
        const selectedRun = findSourceRunByKey(visibleItems, state.selectedSourceRunKey);
        renderSourceRunDetail(selectedRun);
    }

    function shouldFollowLatestSourceRun(visibleItems, latestRunKey) {
        if (!visibleItems || visibleItems.length === 0) {
            return true;
        }
        if (!state.selectedSourceRunKey) {
            return true;
        }
        if (state.selectedSourceRunMode !== "manual") {
            return true;
        }
        if (!state.latestSourceRunKey) {
            return false;
        }
        return latestRunKey !== state.latestSourceRunKey;
    }

    function renderSourceRunListItem(item, active) {
        const runKey = resolveSourceRunKey(item);
        const stageInfo = getRunStageInfo(item);
        const lastProgressAt = resolveRunLastProgressAt(item);
        const summary = resolveRunSpotlightSummaryText(item);
        return "<button class='detail-compact-item" + (active ? " active" : "") + "' data-source-run-key='"
                + escapeHtml(runKey) + "' type='button'>"
                + "<div class='detail-compact-title-row'>"
                + "<div class='meta-row'>"
                + renderBadge(resolveRunDisplayStatus(item) || item.status)
                + renderDerivedStatusBadge(item)
                + "<span class='pill'>" + escapeHtml(stageInfo.label) + "</span>"
                + "</div>"
                + "<span class='detail-compact-time'>" + escapeHtml(formatDateTime(resolveRunTimelineAt(item))) + "</span>"
                + "</div>"
                + "<h5 class='detail-compact-title'>" + escapeHtml(getRunTitle(item)) + "</h5>"
                + (summary ? "<p class='detail-compact-summary'>" + escapeHtml(summary) + "</p>" : "")
                + "<p class='detail-compact-meta'>"
                + escapeHtml(lastProgressAt
                        ? "最近推进 " + formatDateTime(lastProgressAt)
                        : getRunMetaLine(item))
                + "</p>"
                + "</button>";
    }

    function renderSourceRunDetail(item) {
        const container = document.getElementById("source-run-detail");
        if (!container) {
            return;
        }
        if (!item) {
            container.hidden = true;
            container.innerHTML = "";
            return;
        }
        const stageInfo = getRunStageInfo(item);
        container.hidden = false;
        container.innerHTML = buildSourceRunDetailCard(item, stageInfo);
        bindRunActions(container);
    }

    function focusSourceRunDetail() {
        const detail = document.getElementById("source-run-detail");
        if (!detail || detail.hidden || typeof detail.scrollIntoView !== "function") {
            return;
        }
        detail.scrollIntoView({behavior: "smooth", block: "nearest"});
    }

    function buildSourceRunDetailCard(item, stageInfo) {
        const detailedProgress = shouldRenderDetailedHistoryProgress(item, stageInfo)
                ? buildRunProgressStrip(item, stageInfo)
                : "";
        const fileSummary = buildRunFileSummary(item);
        const spotlightSummary = resolveRunSpotlightSummaryText(item);
        const stageHighlights = buildRunStageHighlights(item, stageInfo);
        return "<div class='detail-focus-header'>"
                + "<div>"
                + "<span class='detail-focus-kicker'>选中运行详情</span>"
                + (spotlightSummary
                ? "<p class='detail-focus-copy'>" + escapeHtml(spotlightSummary) + "</p>"
                : "")
                + (fileSummary
                ? "<p class='detail-focus-files'><strong>本次文件：</strong>" + escapeHtml(fileSummary) + "</p>"
                : "")
                + "</div>"
                + "<span class='detail-focus-time'>最近更新时间 "
                + escapeHtml(formatDateTime(resolveRunUpdatedAt(item)))
                + "</span>"
                + "</div>"
                + buildRunDetailFacts(item)
                + detailedProgress
                + buildRunRuntimeSnapshot(item)
                + stageHighlights
                + buildRunFailurePanel(item)
                + buildRunActions(item);
    }

    function renderSourceFileList(files) {
        const container = document.getElementById("source-file-list");
        const detail = document.getElementById("source-file-detail");
        if (!files || files.length === 0) {
            state.selectedSourceFilePath = null;
            container.innerHTML = "<div class='detail-compact-empty'><p class='item-summary'>当前资料源还没有已物化文件。完成一次同步后，这里会展示解析方式与文件格式。</p></div>";
            if (detail) {
                detail.hidden = true;
                detail.innerHTML = "";
            }
            return;
        }
        const sortedFiles = files.slice().sort(function (left, right) {
            return String(left.relativePath || "").localeCompare(String(right.relativePath || ""));
        });
        if (!containsSourceFile(sortedFiles, state.selectedSourceFilePath)) {
            state.selectedSourceFilePath = resolveSourceFileKey(sortedFiles[0]);
        }
        container.innerHTML = sortedFiles.map(function (item) {
            return renderSourceFileListItem(item, state.selectedSourceFilePath === resolveSourceFileKey(item));
        }).join("");
        container.querySelectorAll("[data-source-file-path]").forEach(function (button) {
            button.addEventListener("click", function () {
                state.selectedSourceFilePath = button.dataset.sourceFilePath;
                renderSourceFileList(state.sourceFiles);
            });
        });
        renderSourceFileDetail(findSourceFileByKey(sortedFiles, state.selectedSourceFilePath));
    }

    function renderSourceFileListItem(item, active) {
        const relativePath = item.relativePath || "-";
        const fileName = extractSourceFileName(relativePath);
        return "<button class='detail-compact-item" + (active ? " active" : "") + "' data-source-file-path='"
                + escapeHtml(resolveSourceFileKey(item)) + "' type='button'>"
                + "<div class='detail-compact-title-row'>"
                + "<div class='meta-row'>"
                + renderContextBadge(item.format)
                + renderContextBadge(item.parseMode || "UNKNOWN")
                + "</div>"
                + "<span class='detail-compact-time'>" + escapeHtml(formatBytes(item.fileSize)) + "</span>"
                + "</div>"
                + "<h5 class='detail-compact-title'>" + escapeHtml(fileName || relativePath) + "</h5>"
                + (relativePath !== fileName ? "<p class='detail-compact-path'>" + escapeHtml(relativePath) + "</p>" : "")
                + "<p class='detail-compact-meta'>" + escapeHtml(buildSourceFileMeta(item)) + "</p>"
                + "</button>";
    }

    function renderSourceFileDetail(item) {
        const container = document.getElementById("source-file-detail");
        if (!container) {
            return;
        }
        if (!item) {
            container.hidden = true;
            container.innerHTML = "";
            return;
        }
        container.hidden = false;
        container.innerHTML = buildSourceFileDetailCard(item);
    }

    function buildSourceFileDetailCard(item) {
        return "<div class='detail-focus-header'>"
                + "<div>"
                + "<span class='detail-focus-kicker'>选中文件详情</span>"
                + "<h5>" + escapeHtml(extractSourceFileName(item.relativePath || "") || item.relativePath || "-") + "</h5>"
                + "<p class='detail-focus-copy'>" + escapeHtml(buildSourceFileSummary(item)) + "</p>"
                + "</div>"
                + "</div>"
                + "<div class='run-runtime-grid detail-focus-grid'>"
                + buildRunRuntimeItem("完整路径", item.relativePath || "-", true)
                + buildRunRuntimeItem("文件大小", formatBytes(item.fileSize))
                + buildRunRuntimeItem("解析提供方", formatParseProvider(item.parseProvider))
                + buildRunRuntimeItem("解析方式", formatParseMode(item.parseMode))
                + "</div>";
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
            return renderArticleListItem(item);
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

    function renderArticleListItem(item) {
        const articleId = item.articleKey || item.conceptId;
        const primarySourcePath = getPrimaryArticleSourcePath(item);
        const primarySourceTitle = primarySourcePath
                ? extractSourceFileName(primarySourcePath)
                : "暂未记录主要参考文件";
        return "<button class='list-item article-list-item' data-article-id='" + escapeHtml(articleId) + "' data-source-id='"
                + escapeHtml(item.sourceId == null ? "" : String(item.sourceId))
                + "' type='button'>"
                + "<div class='meta-row'>"
                + renderBadge(item.lifecycle)
                + renderBadge(item.reviewStatus)
                + renderArticleRiskBadge(item)
                + "</div>"
                + "<p class='item-kicker'>知识条目</p>"
                + "<h4>" + escapeHtml(resolveArticleDisplayTitle(item)) + "</h4>"
                + "<p class='item-summary article-purpose'>" + escapeHtml(resolveArticleSummary(item)) + "</p>"
                + "<p class='item-summary article-risk-summary'>" + escapeHtml(buildArticleRiskSummary(item)) + "</p>"
                + "<div class='article-reference-box'>"
                + "<span class='article-reference-label'>主要参考文件</span>"
                + "<strong class='article-reference-title'>" + escapeHtml(primarySourceTitle) + "</strong>"
                + "<span class='article-reference-path'>" + escapeHtml(primarySourcePath || "暂未记录来源路径") + "</span>"
                + "</div>"
                + "<div class='meta-row article-pill-row'>"
                + "<span class='pill'>" + escapeHtml(buildArticleSourceCountText(item)) + "</span>"
                + renderArticleTypePills(item)
                + "</div>"
                + "<p class='item-caption'>" + escapeHtml(buildArticleAvailabilitySummary(item)) + "</p>"
                + "</button>";
    }

    function renderArticleRiskBadge(item) {
        const riskLevel = normalizeStatus(item && item.riskLevel);
        if (!riskLevel || riskLevel === "LOW") {
            return "";
        }
        return renderBadge(riskLevel);
    }

    function renderArticleDetail(detail, auditResponse) {
        state.selectedArticleReviewStatus = detail && detail.reviewStatus ? detail.reviewStatus : null;
        document.getElementById("article-detail-title").textContent = resolveArticleDisplayTitle(detail);
        document.getElementById("article-detail-meta").textContent = [
            buildArticleAvailabilitySummary(detail),
            buildArticleSourceMeta(detail),
            detail.updatedAt ? "入库时间：" + formatDateTime(detail.updatedAt) : ""
        ].filter(Boolean).join(" | ");
        document.getElementById("article-detail-summary").textContent = resolveArticleSummary(detail);
        document.getElementById("article-risk-summary").innerHTML = buildArticleRiskNotice(detail);
        document.getElementById("article-primary-source").textContent = buildPrimarySourceSummary(detail);
        document.getElementById("article-source-overview").textContent = buildArticleSourceOverview(detail);
        document.getElementById("article-source-note").textContent = buildArticleTraceabilityNote(detail);
        document.getElementById("article-content").textContent = detail.content || "";
        document.getElementById("article-metadata").textContent = prettyJson(detail.metadataJson, "暂无技术元数据");
        document.getElementById("article-sources").innerHTML = renderArticleSourceReferences(detail);
        renderArticleSourcePreview(null);
        bindArticleSourcePreviewActions(detail);
        renderArticleReviewPanel(detail);
        renderArticleReviewHistory(auditResponse);
        const relations = []
                .concat((detail.referentialKeywords || []).map(function (item) { return "关键词: " + item; }))
                .concat((detail.dependsOn || []).map(function (item) { return "依赖: " + item; }))
                .concat((detail.related || []).map(function (item) { return "相关: " + item; }));
        document.getElementById("article-relations").innerHTML = renderTagGroup(relations);
        document.getElementById("article-technical-info").innerHTML = renderDescriptionList(buildArticleTechnicalInfo(detail));
    }

    function clearSourceDetail() {
        state.sourceRuns = [];
        state.sourceFiles = [];
        state.selectedSourceRunKey = null;
        state.selectedSourceRunMode = "auto";
        state.latestSourceRunKey = null;
        state.selectedSourceFilePath = null;
        document.getElementById("source-detail-title").textContent = "请选择一个资料源";
        document.getElementById("source-detail-meta").textContent = "";
        document.getElementById("source-run-list").innerHTML = "<div class='detail-compact-empty'><p class='item-summary'>暂无处理历史</p></div>";
        document.getElementById("source-file-list").innerHTML = "<div class='detail-compact-empty'><p class='item-summary'>暂无文件</p></div>";
        document.getElementById("source-run-detail").hidden = true;
        document.getElementById("source-run-detail").innerHTML = "";
        document.getElementById("source-file-detail").hidden = true;
        document.getElementById("source-file-detail").innerHTML = "";
        document.getElementById("source-detail-config").textContent = "暂无配置";
    }

    function clearArticleDetail() {
        document.getElementById("article-detail-title").textContent = "请选择一条内容";
        document.getElementById("article-detail-meta").textContent = "";
        document.getElementById("article-detail-summary").textContent = "暂无摘要";
        document.getElementById("article-risk-summary").innerHTML = "暂无风险提示";
        document.getElementById("article-primary-source").textContent = "暂无主要参考文件";
        document.getElementById("article-source-overview").textContent = "暂无来源概况";
        document.getElementById("article-source-note").textContent = "当前还没有可追溯的来源文件。";
        document.getElementById("article-content").textContent = "暂无内容";
        document.getElementById("article-metadata").textContent = "暂无技术元数据";
        document.getElementById("article-sources").innerHTML = "";
        renderArticleSourcePreview(null);
        renderArticleReviewPanel(null);
        renderArticleReviewHistory(null);
        document.getElementById("article-relations").innerHTML = "";
        document.getElementById("article-technical-info").innerHTML = "";
        state.selectedArticleReviewStatus = null;
    }

    function renderRecentRunOverview(summary) {
        const container = document.getElementById("recent-run-overview");
        if (!container) {
            return;
        }
        const cardsFromBackend = Array.isArray(summary && summary.cards) ? summary.cards : [];
        updateContainerMarkup(container, cardsFromBackend.map(renderMetricCard).join(""), "soft-refresh-panel");
    }

    function renderRecentRunBoard(items) {
        const container = document.getElementById("job-list");
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            updateContainerMarkup(
                    container,
                    "<div class='job-card'><p class='item-summary'>当前没有需要关注的处理任务。资料源的完整同步记录可以在下方“资料源与文件”里查看。</p></div>",
                    "soft-refresh-panel"
            );
            bindRunActions(container);
            return;
        }
        const visibleItems = items.slice()
                .sort(compareRunsByRequestedAtDesc);
        let boardItems = visibleItems.filter(shouldRenderRunAsBoardFocus);
        if (boardItems.length === 0) {
            const latestCompletionItem = visibleItems.find(shouldRenderRunAsCompletionNotice);
            if (latestCompletionItem) {
                boardItems = [latestCompletionItem];
            }
        }
        const recentCompletionItems = visibleItems.filter(function (item) {
            return !shouldRenderRunAsBoardFocus(item) && shouldRenderRunAsCompletionNotice(item);
        }).filter(function (item) {
            return !boardItems.some(function (boardItem) {
                return resolveProcessingTaskKey(boardItem) === resolveProcessingTaskKey(item);
            });
        }).slice(0, 3);
        const orderedItems = boardItems.concat(recentCompletionItems);
        if (orderedItems.length === 0) {
            updateContainerMarkup(
                    container,
                    "<div class='job-card'><p class='item-summary'>当前没有需要关注的处理任务。已完成记录归档在下方“资料源与文件”的处理历史中。</p></div>",
                    "soft-refresh-panel"
            );
            bindRunActions(container);
            return;
        }
        reconcileRecentRunCollection(container, orderedItems, renderRecentRunBoardItem);
        bindRunActions(container);
    }

    function renderRecentRunBoardItem(item) {
        if (shouldRenderRunAsBoardFocus(item) || shouldPromoteCompletionRunAsBoardFocus(item)) {
            return renderRecentRunCard(item);
        }
        return renderRecentRunCompletionNotice(item);
    }

    function shouldPromoteCompletionRunAsBoardFocus(item) {
        if (!item) {
            return false;
        }
        if (!shouldRenderRunAsCompletionNotice(item)) {
            return false;
        }
        if (!Array.isArray(state.recentRuns)) {
            return false;
        }
        for (let index = 0; index < state.recentRuns.length; index++) {
            if (shouldRenderRunAsBoardFocus(state.recentRuns[index])) {
                return false;
            }
        }
        const latestCompletionItem = state.recentRuns.slice()
                .sort(compareRunsByRequestedAtDesc)
                .find(shouldRenderRunAsCompletionNotice);
        return Boolean(latestCompletionItem)
                && resolveProcessingTaskKey(latestCompletionItem) === resolveProcessingTaskKey(item);
    }

    function shouldRenderRunAsBoardFocus(item) {
        if (!item) {
            return false;
        }
        if (item.processingActive || item.requiresManualAction) {
            return true;
        }
        const displayStatus = resolveRunDisplayStatus(item);
        const baseStatus = normalizeStatus(item.status);
        return RUN_BOARD_FOCUS_STATUSES.has(displayStatus)
                || RUN_BOARD_FOCUS_STATUSES.has(baseStatus);
    }

    function shouldRenderRunAsCompletionNotice(item) {
        const displayStatus = resolveRunDisplayStatus(item);
        const baseStatus = normalizeStatus(item && item.status);
        return displayStatus === "SUCCEEDED"
                || displayStatus === "SKIPPED_NO_CHANGE"
                || baseStatus === "SUCCEEDED"
                || baseStatus === "SKIPPED_NO_CHANGE";
    }

    function renderRecentRunCard(item) {
        const stageInfo = getRunStageInfo(item);
        const toneClass = "run-tone-" + stageInfo.tone;
        const operationalNote = resolveRunOperationalNoteText(item);
        const spotlightSummary = resolveRunSpotlightSummaryText(item);
        const stageHighlights = buildRunStageHighlights(item, stageInfo);
        return "<article class='run-spotlight-card " + toneClass + "' data-processing-task-key='"
                + escapeHtml(resolveProcessingTaskKey(item)) + "'>"
                + "<div class='run-spotlight-header'>"
                + "<div class='meta-row run-spotlight-badges'>"
                + renderBadge(item.sourceType || "UPLOAD")
                + renderBadge(item.status)
                + renderDerivedStatusBadge(item)
                + renderTaskModeBadge(item)
                + "</div>"
                + "<div class='run-spotlight-time'>提交 " + escapeHtml(formatDateTime(item.requestedAt)) + "</div>"
                + "</div>"
                + "<h3 class='run-spotlight-title'>" + escapeHtml(getRunTitle(item)) + "</h3>"
                + (spotlightSummary
                ? "<p class='run-spotlight-summary'>" + escapeHtml(spotlightSummary) + "</p>"
                : "")
                + stageHighlights
                + buildRunProgressStrip(item, stageInfo)
                + buildRunRuntimeSnapshot(item)
                + (operationalNote
                ? "<div class='run-spotlight-footnotes'>"
                + "<p class='run-spotlight-note'><strong>任务线索：</strong>" + escapeHtml(operationalNote) + "</p>"
                + "</div>"
                : "")
                + buildRunFailurePanel(item)
                + buildRunActions(item)
                + "</article>";
    }

    function renderRecentRunCompletionNotice(item) {
        const stageInfo = getRunStageInfo(item);
        return "<article class='run-completion-notice' data-processing-task-key='"
                + escapeHtml(resolveProcessingTaskKey(item)) + "'>"
                + "<div class='run-completion-main'>"
                + "<div class='meta-row run-spotlight-badges'>"
                + renderBadge(item.sourceType || "UPLOAD")
                + renderBadge(resolveRunDisplayStatus(item) || item.status)
                + renderTaskModeBadge(item)
                + "</div>"
                + "<h3 class='run-completion-title'>" + escapeHtml(getRunTitle(item)) + "</h3>"
                + "<p class='run-completion-copy'>" + escapeHtml(getRunSummary(item)) + "</p>"
                + "</div>"
                + "<div class='run-completion-side'>"
                + "<span class='run-spotlight-time'>完成 "
                + escapeHtml(formatDateTime(resolveRunUpdatedAt(item)))
                + "</span>"
                + "<span class='surface-chip'>完整记录在资料源处理历史</span>"
                + "</div>"
                + "</article>";
    }

    function reconcileRecentRunCollection(container, items, renderCard) {
        const existingCards = new Map();
        Array.from(container.children).forEach(function (child) {
            if (!(child instanceof HTMLElement)) {
                return;
            }
            const taskKey = child.dataset.processingTaskKey;
            if (taskKey) {
                existingCards.set(taskKey, child);
            }
        });
        const hadExistingCards = existingCards.size > 0;
        const nextTaskKeys = new Set();
        items.forEach(function (item) {
            const taskKey = resolveProcessingTaskKey(item);
            const markup = renderCard(item);
            const signature = buildRenderSignature(markup);
            let card = existingCards.get(taskKey);
            const shouldReplaceCard = !card || card.dataset.renderSignature !== signature;
            if (shouldReplaceCard) {
                const nextCard = createElementFromMarkup(markup);
                if (!nextCard) {
                    return;
                }
                nextCard.dataset.renderSignature = signature;
                if (card && card.parentNode === container) {
                    card.replaceWith(nextCard);
                }
                card = nextCard;
                existingCards.set(taskKey, card);
                if (hadExistingCards) {
                    triggerSoftRefreshAnimation(card, "soft-refresh-card");
                }
            }
            nextTaskKeys.add(taskKey);
            container.appendChild(card);
        });
        Array.from(container.children).forEach(function (child) {
            if (!(child instanceof HTMLElement)) {
                return;
            }
            const taskKey = child.dataset.processingTaskKey;
            if (!taskKey || !nextTaskKeys.has(taskKey)) {
                child.remove();
            }
        });
        container.dataset.renderSignature = items.map(function (item) {
            return resolveProcessingTaskKey(item);
        }).join("|");
    }

    function updateContainerMarkup(container, markup, animationClass) {
        const resolvedMarkup = markup || "";
        const nextSignature = buildRenderSignature(resolvedMarkup);
        if (container.dataset.renderSignature === nextSignature) {
            return false;
        }
        const hadContent = container.childElementCount > 0 || Boolean(container.textContent && container.textContent.trim());
        container.innerHTML = resolvedMarkup;
        container.dataset.renderSignature = nextSignature;
        if (animationClass && hadContent && resolvedMarkup) {
            triggerSoftRefreshAnimation(container, animationClass);
        }
        return true;
    }

    function createElementFromMarkup(markup) {
        const template = document.createElement("template");
        template.innerHTML = String(markup || "").trim();
        return template.content.firstElementChild;
    }

    function buildRenderSignature(value) {
        const markup = String(value || "");
        let hash = 0;
        for (let index = 0; index < markup.length; index++) {
            hash = ((hash << 5) - hash) + markup.charCodeAt(index);
            hash |= 0;
        }
        return String(markup.length) + ":" + String(hash);
    }

    function triggerSoftRefreshAnimation(element, className) {
        if (!element || !className) {
            return;
        }
        element.classList.remove(className);
        void element.offsetWidth;
        element.classList.add(className);
        element.addEventListener("animationend", function handleAnimationEnd() {
            element.classList.remove(className);
            element.removeEventListener("animationend", handleAnimationEnd);
        });
    }

    function buildRunProgressStrip(item, stageInfo) {
        const progressSteps = Array.isArray(item && item.progressSteps) ? item.progressSteps : [];
        if (progressSteps.length === 0) {
            return "";
        }
        const displayStatus = resolveRunDisplayStatus(item);
        const runStalled = displayStatus === "STALLED";
        const runFailed = displayStatus === "FAILED";
        return "<div class='run-progress-strip run-progress-strip-detailed'>"
                + progressSteps.map(function (step, index) {
                    const stepStatus = normalizeStatus(step && step.status);
                    const stepDetail = cleanRunProgressDetail(step && step.detail);
                    const stepClass = resolveRunProgressStepClass(stepStatus, runStalled, runFailed);
                    const stepStatusMark = buildRunProgressStatusMark(stepClass, displayStatus);
                    return "<div class='run-progress-step " + stepClass + "'>"
                            + "<span class='run-progress-order'>" + escapeHtml(String(index + 1)) + "</span>"
                            + "<div class='run-progress-copy'>"
                            + "<div class='run-progress-label-row'>"
                            + "<span class='run-progress-label'>" + escapeHtml(step.label || step.key || ("步骤 " + String(index + 1))) + "</span>"
                            + stepStatusMark
                            + "</div>"
                            + (stepDetail ? "<span class='run-progress-detail'>" + escapeHtml(stepDetail) + "</span>" : "")
                            + "</div>"
                            + "</div>";
                }).join("")
                + "</div>";
    }

    function resolveRunProgressStepClass(stepStatus, runStalled, runFailed) {
        if (stepStatus === "COMPLETED") {
            return "completed";
        }
        if (stepStatus === "FAILED") {
            return runStalled ? "warning" : "failed";
        }
        if (stepStatus === "ACTIVE") {
            if (runStalled) {
                return "warning";
            }
            if (runFailed) {
                return "failed";
            }
            return "active";
        }
        return "pending";
    }

    function buildRunProgressStatusMark(stepClass, displayStatus) {
        if (stepClass === "warning" && displayStatus === "STALLED") {
            return "<span class='run-progress-status-mark warning'>卡住</span>";
        }
        if (stepClass === "failed") {
            return "<span class='run-progress-status-mark failed'>失败</span>";
        }
        return "";
    }

    function cleanRunProgressDetail(detail) {
        return String(detail || "").replace(/^细分状态[：:]\s*/, "");
    }

    function getRunStageInfo(item) {
        const displayStatusLabel = String(item && item.displayStatusLabel || "").trim();
        const nextStepHint = String(item && item.nextStepHint || "").trim();
        return {
            label: displayStatusLabel || getBadgeLabel(resolveRunDisplayStatus(item) || (item && item.status)),
            nextStep: nextStepHint || "等待系统继续处理",
            tone: String(item && item.displayTone || "").trim() || "warning"
        };
    }

    function buildRunOperationalNote(item) {
        if (item && item.operationalNote) {
            return String(item.operationalNote);
        }
        return "系统正在继续处理当前任务";
    }

    function resolveRunOperationalNoteText(item) {
        const operationalNote = sanitizeDisplayMessage(buildRunOperationalNote(item));
        if (!operationalNote) {
            return "";
        }
        return isRunOperationalNoteDuplicated(item, operationalNote) ? "" : operationalNote;
    }

    function resolveRunSpotlightSummaryText(item) {
        const summary = sanitizeDisplayMessage(getRunSummary(item));
        if (!summary) {
            return "";
        }
        return isRunSpotlightSummaryDuplicated(item, summary) ? "" : summary;
    }

    function buildRunStageHighlights(item, stageInfo) {
        const nextStep = resolveRunNextStepText(item, stageInfo);
        if (!nextStep) {
            return "";
        }
        return "<div class='run-spotlight-highlights'>"
                + "<span class='surface-chip'>下一步：" + escapeHtml(nextStep) + "</span>"
                + "</div>";
    }

    function resolveRunNextStepText(item, stageInfo) {
        const nextStep = sanitizeDisplayMessage(stageInfo && stageInfo.nextStep ? stageInfo.nextStep : "");
        if (!nextStep) {
            return "";
        }
        return shouldHideRunNextStep(item, nextStep) ? "" : nextStep;
    }

    function bindRunActions(container) {
        if (!container || container.dataset.actionDelegated === "true") {
            return;
        }
        container.dataset.actionDelegated = "true";
        container.addEventListener("click", async function (event) {
            const target = event.target;
            if (!(target instanceof Element)) {
                return;
            }
            const confirmButton = target.closest("[data-confirm-run]");
            if (confirmButton && container.contains(confirmButton)) {
                confirmRun(
                        parseOptionalInteger(confirmButton.dataset.runId || confirmButton.dataset.confirmRun),
                        confirmButton.dataset.confirmDecision,
                        parseOptionalInteger(confirmButton.dataset.confirmSourceId)
                );
                return;
            }
            const resyncButton = target.closest("[data-resync-source]");
            if (!resyncButton || !container.contains(resyncButton)) {
                return;
            }
            const runId = parseOptionalInteger(resyncButton.dataset.runId || resyncButton.dataset.resyncRun);
            const sourceId = parseOptionalInteger(resyncButton.dataset.resyncSource);
            const uploadRetry = resyncButton.dataset.uploadRetry === "true";
            if (uploadRetry && runId == null) {
                return;
            }
            if (!uploadRetry && sourceId == null) {
                return;
            }
            const confirmed = window.confirm(
                    uploadRetry
                            ? "将重试当前上传任务，确认继续吗？"
                            : "将重新同步资料源 " + sourceId + "，确认继续吗？"
            );
            if (!confirmed) {
                return;
            }
            try {
                const run = uploadRetry
                        ? await requestSourceRunRetry(runId)
                        : await requestSourceSync(sourceId);
                activateKnowledgeTab("knowledge-runs");
                await handleSubmittedRun(run, uploadRetry ? "已重新提交当前上传任务" : "已重新提交资料源同步");
            }
            catch (error) {
                showError(uploadRetry ? "重试当前上传任务失败" : "重新同步资料源失败", error);
            }
        });
    }

    function resolveProcessingTaskKey(item) {
        if (item && item.taskId) {
            return String(item.taskId);
        }
        if (item && item.compileJobId) {
            return "compile-job:" + String(item.compileJobId);
        }
        return resolveSourceRunKey(item);
    }

    function buildRunActions(item) {
        const actionItems = Array.isArray(item && item.actions) ? item.actions : [];
        const buttons = actionItems.map(function (action) {
            return renderStructuredRunAction(action);
        }).filter(Boolean);
        if (buttons.length === 0) {
            return "";
        }
        return "<div class='card-actions'>" + buttons.join("") + "</div>";
    }

    function renderStructuredRunAction(action) {
        if (!action || typeof action !== "object") {
            return "";
        }
        const actionKey = String(action.actionKey || "").trim().toUpperCase();
        const label = String(action.label || "").trim();
        const buttonClass = String(action.buttonClass || "ghost-btn").trim() || "ghost-btn";
        if (!actionKey || !label) {
            return "";
        }
        if (actionKey.indexOf("CONFIRM_") === 0) {
            return "<button class='" + escapeHtml(buttonClass)
                    + "' data-confirm-run='" + escapeHtml(String(action.runId || ""))
                    + "' data-run-id='" + escapeHtml(String(action.runId || ""))
                    + "' data-confirm-decision='" + escapeHtml(String(action.decision || ""))
                    + "' data-confirm-source-id='" + escapeHtml(String(action.decisionSourceId || ""))
                    + "' type='button'>" + escapeHtml(label) + "</button>";
        }
        if (actionKey === "RESYNC_SOURCE" || actionKey === "RETRY_UPLOAD") {
            return "<button class='" + escapeHtml(buttonClass)
                    + "' data-resync-source='" + escapeHtml(String(action.sourceId || ""))
                    + "' data-run-id='" + escapeHtml(String(action.runId || ""))
                    + "' data-resync-run='" + escapeHtml(String(action.runId || ""))
                    + "' data-upload-retry='" + escapeHtml(String(Boolean(action.uploadRetry)))
                    + "' type='button'>" + escapeHtml(label) + "</button>";
        }
        return "";
    }

    function highlightSource(sourceId) {
        document.querySelectorAll("#source-list .list-item").forEach(function (item) {
            item.classList.toggle("active", item.dataset.sourceId === String(sourceId || ""));
        });
    }

    function resolveSourceRunKey(item) {
        if (item && item.runId != null) {
            return "run-" + String(item.runId);
        }
        return [
            item && item.sourceId != null ? String(item.sourceId) : "",
            item && item.requestedAt ? String(item.requestedAt) : "",
            item && item.title ? String(item.title) : "",
            item && item.status ? String(item.status) : ""
        ].join("::");
    }

    function containsSourceRun(items, selectedKey) {
        if (!selectedKey) {
            return false;
        }
        return items.some(function (item) {
            return resolveSourceRunKey(item) === selectedKey;
        });
    }

    function findSourceRunByKey(items, selectedKey) {
        if (!selectedKey) {
            return null;
        }
        for (let index = 0; index < items.length; index++) {
            if (resolveSourceRunKey(items[index]) === selectedKey) {
                return items[index];
            }
        }
        return null;
    }

    function resolveSourceFileKey(item) {
        return item && item.relativePath ? String(item.relativePath) : "";
    }

    function containsSourceFile(items, selectedKey) {
        if (!selectedKey) {
            return false;
        }
        return items.some(function (item) {
            return resolveSourceFileKey(item) === selectedKey;
        });
    }

    function findSourceFileByKey(items, selectedKey) {
        if (!selectedKey) {
            return null;
        }
        for (let index = 0; index < items.length; index++) {
            if (resolveSourceFileKey(items[index]) === selectedKey) {
                return items[index];
            }
        }
        return null;
    }

    function highlightArticle(articleId, sourceId) {
        document.querySelectorAll("#article-list .list-item").forEach(function (item) {
            const sameArticle = item.dataset.articleId === String(articleId || "");
            const sameSource = item.dataset.sourceId === String(sourceId == null ? "" : sourceId);
            item.classList.toggle("active", sameArticle && sameSource);
        });
    }

    function buildSourceFileSummary(item) {
        const mode = formatParseMode(item.parseMode);
        const provider = formatParseProvider(item.parseProvider);
        return "解析方式：" + mode + "；解析提供方：" + provider + "。";
    }

    function buildSourceFileMeta(item) {
        return [
            "格式：" + getBadgeLabel(item.format),
            "解析：" + formatParseMode(item.parseMode),
            "大小：" + formatBytes(item.fileSize)
        ].join(" · ");
    }

    function formatParseMode(value) {
        return getBadgeLabel(value || "UNKNOWN");
    }

    function formatParseProvider(value) {
        const normalized = String(value || "").trim();
        if (!normalized) {
            return "默认解析链";
        }
        const labels = {
            filesystem: "本地文件系统",
            "default-parser": "默认解析器"
        };
        return labels[normalized.toLowerCase()] || normalized;
    }

    function deriveGitSourceName(remoteUrl) {
        const text = String(remoteUrl || "").trim();
        if (!text) {
            return "";
        }
        const normalized = text.replace(/\/+$/g, "");
        const tail = normalized.split("/").pop() || "";
        const plainTail = tail.replace(/\.git$/i, "");
        return plainTail ? plainTail.replace(/[-_]+/g, " ") : "";
    }

    function getSourceSummary(item) {
        if (item.lastSyncStatus) {
            return "最近同步状态：" + getBadgeLabel(item.lastSyncStatus)
                    + (item.lastSyncAt ? "，完成于 " + formatDateTime(item.lastSyncAt) : "");
        }
        return "还没有执行过同步，可以先选中后手动发起一次。";
    }

    function getRunTitle(item) {
        const fileSummary = buildRunFileSummary(item);
        if (fileSummary) {
            return fileSummary;
        }
        if (item && item.title) {
            return item.title;
        }
        if (item && item.sourceName) {
            return item.sourceName;
        }
        return item && item.runId ? "资料处理任务 #" + String(item.runId) : "资料处理任务";
    }

    function buildRunFileSummary(item) {
        const fileNames = resolveRunFileNames(item);
        if (fileNames.length === 0) {
            return "";
        }
        if (fileNames.length === 1) {
            return fileNames[0];
        }
        if (fileNames.length === 2) {
            return fileNames[0] + "、" + fileNames[1];
        }
        return fileNames[0] + "、" + fileNames[1] + " 等 " + String(fileNames.length) + " 个文件";
    }

    function resolveRunFileNames(item) {
        const fromEvidence = readRunFileNamesFromEvidence(item && item.evidenceJson);
        if (fromEvidence.length > 0) {
            return fromEvidence;
        }
        const fromSourceNames = Array.isArray(item && item.sourceNames) ? item.sourceNames : [];
        return uniqueNonEmptyStrings(fromSourceNames);
    }

    function readRunFileNamesFromEvidence(evidenceJson) {
        if (!evidenceJson || typeof evidenceJson !== "string") {
            return [];
        }
        try {
            const evidence = JSON.parse(evidenceJson);
            const bundleSummary = evidence && typeof evidence === "object" ? evidence.bundleSummary : null;
            const relativePathsSample = bundleSummary && Array.isArray(bundleSummary.relativePathsSample)
                    ? bundleSummary.relativePathsSample
                    : [];
            return uniqueNonEmptyStrings(relativePathsSample);
        }
        catch (error) {
            return [];
        }
    }

    function uniqueNonEmptyStrings(items) {
        const result = [];
        const seen = new Set();
        (items || []).forEach(function (item) {
            const normalized = String(item || "").trim();
            if (!normalized || seen.has(normalized)) {
                return;
            }
            seen.add(normalized);
            result.push(normalized);
        });
        return result;
    }

    function buildRunDetailFacts(item) {
        const facts = [];
        if (item && item.sourceType) {
            facts.push({label: "类型", value: getBadgeLabel(item.sourceType)});
        }
        if (item && item.resolverDecision) {
            facts.push({label: "决策", value: getBadgeLabel(item.resolverDecision)});
        }
        if (item && item.syncAction) {
            facts.push({label: "动作", value: getBadgeLabel(item.syncAction)});
        }
        if (facts.length === 0) {
            return "";
        }
        return "<div class='run-runtime-inline-list detail-fact-list'>"
                + facts.map(function (fact) {
                    return buildRunRuntimeBadge(fact.label, fact.value);
                }).join("")
                + "</div>";
    }

    function resolveRunUpdatedAt(item) {
        return item && (item.finishedAt || item.updatedAt || item.compileLastHeartbeatAt || item.requestedAt)
                ? (item.finishedAt || item.updatedAt || item.compileLastHeartbeatAt || item.requestedAt)
                : "";
    }

    function resolveRunTimelineAt(item) {
        return resolveRunLastProgressAt(item) || resolveRunUpdatedAt(item) || "";
    }

    function renderDerivedStatusBadge(item) {
        const derivedStatus = resolveRunDisplayStatus(item);
        if (!derivedStatus || derivedStatus === normalizeStatus(item && item.status)) {
            return "";
        }
        return renderBadge(derivedStatus);
    }

    function renderTaskModeBadge(item) {
        if (item && item.syncAction) {
            return renderContextBadge(item.syncAction);
        }
        if (item && item.resolverDecision) {
            return renderContextBadge(item.resolverDecision);
        }
        if (item && item.taskType === "SOURCE_SYNC") {
            return renderContextBadge("AUTO");
        }
        return "";
    }

    function resolveRunDisplayStatus(item) {
        const displayStatus = normalizeStatus(item && item.displayStatus);
        if (displayStatus) {
            return displayStatus;
        }
        const compileDerivedStatus = normalizeStatus(item && item.compileDerivedStatus);
        if (compileDerivedStatus) {
            return compileDerivedStatus;
        }
        const compileJobStatus = normalizeStatus(item && item.compileJobStatus);
        if (compileJobStatus) {
            return compileJobStatus;
        }
        return normalizeStatus(item && item.status);
    }

    function resolveRunErrorCode(item) {
        return normalizeStatus(item && item.compileErrorCode);
    }

    function resolveRunStepLabel(item) {
        return item && item.currentStepLabel ? String(item.currentStepLabel) : "暂无";
    }

    function resolveRunProgressText(item) {
        return item && item.progressText ? String(item.progressText) : "等待下一步刷新";
    }

    function resolveRunLastProgressAt(item) {
        return item && (item.compileLastHeartbeatAt || item.updatedAt || item.requestedAt)
                ? (item.compileLastHeartbeatAt || item.updatedAt || item.requestedAt)
                : "";
    }

    function buildRunReasonSummary(item) {
        return item && item.reasonSummary ? String(item.reasonSummary) : "暂无";
    }

    function resolveRunReasonSummaryText(item) {
        return item && item.reasonSummary ? String(item.reasonSummary).trim() : "";
    }

    function shouldRenderRunReasonSummary(item) {
        const reasonSummary = resolveRunReasonSummaryText(item);
        if (!reasonSummary || isPlaceholderRunReasonSummary(reasonSummary)) {
            return false;
        }
        if (shouldHideRunReasonSummaryBecauseFailurePanelCoversIt(item, reasonSummary)) {
            return false;
        }
        if (!shouldExposeRunReasonByStatus(item)) {
            return false;
        }
        return !isRunReasonSummaryDuplicated(item, reasonSummary);
    }

    function shouldExposeRunReasonByStatus(item) {
        const displayStatus = resolveRunDisplayStatus(item);
        const baseStatus = normalizeStatus(item && item.status);
        return RUN_REASON_SUMMARY_DISPLAY_STATUSES.has(displayStatus)
                || RUN_REASON_SUMMARY_DISPLAY_STATUSES.has(baseStatus);
    }

    function isPlaceholderRunReasonSummary(reasonSummary) {
        const normalizedReason = normalizeRunMessageForComparison(reasonSummary);
        return normalizedReason === ""
                || normalizedReason === "暂无"
                || normalizedReason === "无"
                || normalizedReason === "暂无原因"
                || normalizedReason === "无原因";
    }

    function isRunReasonSummaryDuplicated(item, reasonSummary) {
        const normalizedReason = normalizeRunMessageForComparison(reasonSummary);
        if (!normalizedReason) {
            return true;
        }
        const comparableMessages = [
            resolveRunProgressText(item),
            resolveRunStepLabel(item),
            item && item.message,
            item && item.compileProgressMessage,
            item && item.currentStepLabel
        ];
        return comparableMessages.some(function (message) {
            const normalizedMessage = normalizeRunMessageForComparison(message);
            if (!normalizedMessage) {
                return false;
            }
            return normalizedMessage === normalizedReason
                    || normalizedMessage.includes(normalizedReason)
                    || normalizedReason.includes(normalizedMessage);
        });
    }

    function shouldHideRunReasonSummaryBecauseFailurePanelCoversIt(item, reasonSummary) {
        const displayStatus = resolveRunDisplayStatus(item);
        if (displayStatus !== "FAILED" && displayStatus !== "STALLED") {
            return false;
        }
        const normalizedReason = normalizeRunMessageForComparison(reasonSummary);
        const normalizedFailureSummary = normalizeRunMessageForComparison(buildRunReasonSummary(item));
        if (!normalizedReason || !normalizedFailureSummary) {
            return false;
        }
        return normalizedReason === normalizedFailureSummary
                || normalizedReason.includes(normalizedFailureSummary)
                || normalizedFailureSummary.includes(normalizedReason);
    }

    function isRunOperationalNoteDuplicated(item, operationalNote) {
        const normalizedNote = normalizeRunMessageForComparison(operationalNote);
        if (!normalizedNote) {
            return true;
        }
        const comparableMessages = [
            resolveRunProgressText(item),
            resolveRunStepLabel(item),
            item && item.message,
            item && item.compileProgressMessage,
            item && item.currentStepLabel,
            item && item.reasonSummary
        ];
        return comparableMessages.some(function (message) {
            const normalizedMessage = normalizeRunMessageForComparison(message);
            if (!normalizedMessage) {
                return false;
            }
            return normalizedMessage === normalizedNote
                    || normalizedMessage.includes(normalizedNote)
                    || normalizedNote.includes(normalizedMessage);
        });
    }

    function isRunSpotlightSummaryDuplicated(item, summary) {
        const normalizedSummary = normalizeRunMessageForComparison(summary);
        if (!normalizedSummary) {
            return true;
        }
        const comparableMessages = [
            resolveRunProgressText(item),
            resolveRunStepLabel(item),
            item && item.compileProgressMessage,
            item && item.currentStepLabel
        ];
        return comparableMessages.some(function (message) {
            const normalizedMessage = normalizeRunMessageForComparison(message);
            if (!normalizedMessage) {
                return false;
            }
            return normalizedMessage === normalizedSummary
                    || normalizedMessage.includes(normalizedSummary)
                    || normalizedSummary.includes(normalizedMessage);
        });
    }

    function shouldHideRunNextStep(item, nextStep) {
        const normalizedNextStep = normalizeRunMessageForComparison(nextStep);
        if (!normalizedNextStep) {
            return true;
        }
        const placeholderNextSteps = [
            "等待系统继续处理",
            "继续等待当前真实步骤推进",
            "等待当前真实步骤推进",
            "继续等待系统处理",
            "等待下一步刷新"
        ];
        if (placeholderNextSteps.some(function (placeholder) {
            return normalizeRunMessageForComparison(placeholder) === normalizedNextStep;
        })) {
            return true;
        }
        const comparableMessages = [
            resolveRunProgressText(item),
            resolveRunStepLabel(item),
            item && item.currentStepLabel,
            item && item.operationalNote
        ];
        return comparableMessages.some(function (message) {
            const normalizedMessage = normalizeRunMessageForComparison(message);
            if (!normalizedMessage) {
                return false;
            }
            return normalizedMessage === normalizedNextStep
                    || normalizedMessage.includes(normalizedNextStep)
                    || normalizedNextStep.includes(normalizedMessage);
        });
    }

    function normalizeRunMessageForComparison(message) {
        return String(message || "")
                .trim()
                .replace(/^\d+\s*\/\s*\d+\s*[·:：\-]\s*/, "")
                .replace(/[，,。.\s:：·\-_/\\]+/g, "")
                .toLowerCase();
    }

    function compactDisplayMessage(message) {
        const normalized = sanitizeDisplayMessage(message);
        if (!normalized) {
            return "";
        }
        return normalized.length > 88 ? normalized.slice(0, 85) + "..." : normalized;
    }

    function buildRunRuntimeSnapshot(item) {
        const reasonSummary = shouldRenderRunReasonSummary(item)
                ? resolveRunReasonSummaryText(item)
                : "";
        const progressSteps = Array.isArray(item && item.progressSteps) ? item.progressSteps : [];
        const shouldShowCurrentStep = progressSteps.length === 0;
        return "<div class='run-runtime-summary'>"
                + "<div class='run-runtime-inline-list'>"
                + (shouldShowCurrentStep
                ? buildRunRuntimeBadge("当前步骤", resolveRunStepLabel(item))
                : "")
                + buildRunRuntimeBadge("当前进度", resolveRunProgressText(item))
                + "</div>"
                + (reasonSummary
                ? "<div class='run-runtime-reason'>"
                + "<span class='run-runtime-label'>原因摘要</span>"
                + "<strong class='run-runtime-value'>" + escapeHtml(reasonSummary) + "</strong>"
                + "</div>"
                : "")
                + "</div>";
    }

    function buildRunRuntimeItem(label, value, wide) {
        const className = wide ? "run-runtime-item run-runtime-item-wide" : "run-runtime-item";
        return "<div class='" + className + "'>"
                + "<span class='run-runtime-label'>" + escapeHtml(label) + "</span>"
                + "<strong class='run-runtime-value'>" + escapeHtml(value || "暂无") + "</strong>"
                + "</div>";
    }

    function buildRunRuntimeBadge(label, value) {
        const wideClass = label === "当前进度" ? " run-runtime-badge-wide" : "";
        return "<div class='run-runtime-badge" + wideClass + "'>"
                + "<span class='run-runtime-label'>" + escapeHtml(label) + "</span>"
                + "<strong class='run-runtime-value'>" + escapeHtml(value || "暂无") + "</strong>"
                + "</div>";
    }

    function buildRunFailurePanel(item) {
        const displayStatus = resolveRunDisplayStatus(item);
        if (displayStatus !== "FAILED" && displayStatus !== "STALLED") {
            return "";
        }
        const errorCode = resolveRunErrorCode(item);
        return "<div class='job-error'><strong>处理失败</strong><p>"
                + escapeHtml(buildRunReasonSummary(item))
                + "</p>"
                + (errorCode
                ? "<p class='job-error-meta'>错误码：" + escapeHtml(errorCode) + "</p>"
                : "")
                + "</div>";
    }

    function shouldRenderDetailedHistoryProgress(item, stageInfo) {
        const displayStatus = resolveRunDisplayStatus(item);
        if (displayStatus === "WAIT_CONFIRM" || displayStatus === "FAILED" || displayStatus === "STALLED") {
            return true;
        }
        if (item && item.processingActive) {
            return true;
        }
        return stageInfo && stageInfo.tone === "warning";
    }

    function findPrimaryActiveProcessingTask() {
        if (!Array.isArray(state.recentRuns)) {
            return null;
        }
        for (let index = 0; index < state.recentRuns.length; index++) {
            const item = state.recentRuns[index];
            if (item && item.processingActive) {
                return item;
            }
        }
        return null;
    }

    function getRunSummary(item) {
        const reasonSummary = String(item && item.reasonSummary || "").trim();
        if (reasonSummary) {
            return reasonSummary;
        }
        const message = String(item && item.message || "").trim();
        return message || "同步状态已更新。";
    }

    function getRunMetaLine(item) {
        return [
            item.sourceType ? "类型：" + getBadgeLabel(item.sourceType) : "",
            resolveRunDisplayStatus(item) ? "运行态：" + getBadgeLabel(resolveRunDisplayStatus(item)) : "",
            item.requestedAt ? "提交于 " + formatDateTime(item.requestedAt) : ""
        ].filter(Boolean).join(" · ");
    }

    function buildRunCompletionMessage(run) {
        const completionNotice = String(run && run.completionNotice || "").trim();
        if (completionNotice) {
            return getRunTitle(run) + "：" + completionNotice;
        }
        const reasonSummary = String(run && run.reasonSummary || "").trim();
        if (reasonSummary) {
            return getRunTitle(run) + "：" + reasonSummary;
        }
        return getRunTitle(run) + " 已处理完成，并已更新页面状态。";
    }

    function resolveRunNoticeTone(run) {
        return String(run && run.noticeTone || "").trim() || "success";
    }

    function compareRunsByRequestedAtDesc(left, right) {
        return toTimestamp(resolveRunSortAt(right)) - toTimestamp(resolveRunSortAt(left));
    }

    function resolveRunSortAt(item) {
        if (!item) {
            return "";
        }
        return resolveRunLastProgressAt(item) || item.updatedAt || item.requestedAt || "";
    }

    function resolveArticleDisplayTitle(item) {
        if (!item) {
            return "未命名内容";
        }
        const primarySourcePath = getPrimaryArticleSourcePath(item);
        if (isGenericArticleTitle(item.title, item.conceptId) && primarySourcePath) {
            return formatArticleSourceTitle(primarySourcePath);
        }
        return item.title || formatArticleSourceTitle(primarySourcePath) || item.articleKey || item.conceptId || "未命名内容";
    }

    function resolveArticleSummary(item) {
        const summary = String(item && item.summary ? item.summary : "").trim();
        if (summary && !isGenericArticleTitle(summary, item && item.conceptId ? item.conceptId : "")) {
            return summary;
        }
        const displayTitle = resolveArticleDisplayTitle(item);
        if (displayTitle && displayTitle !== "未命名内容") {
            return "用于说明“" + displayTitle + "”相关的知识内容。";
        }
        const primarySourcePath = getPrimaryArticleSourcePath(item);
        if (primarySourcePath) {
            return "系统已整理来自 " + extractSourceFileName(primarySourcePath) + " 的知识内容。";
        }
        return "系统已整理出一条可用于问答的知识内容。";
    }

    function buildArticleAvailabilitySummary(item) {
        const normalizedLifecycle = normalizeStatus(item && item.lifecycle);
        const normalizedReviewStatus = normalizeStatus(item && item.reviewStatus);
        if (normalizedLifecycle === "ARCHIVED") {
            return "当前已归档，默认不作为系统主依据。";
        }
        if (normalizedLifecycle === "DEPRECATED") {
            return "当前已废弃，除排查历史问题外不建议继续使用。";
        }
        if (normalizedLifecycle === "DISABLED") {
            return "当前已停用，暂不用于系统问答。";
        }
        if (normalizedLifecycle === "ACTIVE" && normalizedReviewStatus === "NEEDS_HUMAN_REVIEW") {
            return "当前可见，但建议人工复核后再作为稳定依据。";
        }
        if (normalizedLifecycle === "ACTIVE" && normalizedReviewStatus === "PENDING") {
            return "当前已入库，仍在等待进一步审查。";
        }
        if (normalizedLifecycle === "ACTIVE") {
            return "当前可被系统直接使用。";
        }
        return "当前状态为 " + getBadgeLabel(item && item.lifecycle ? item.lifecycle : "UNKNOWN") + "。";
    }

    function buildArticleSourceCountText(item) {
        const sourcePaths = getArticleSourcePaths(item);
        const resolvedSourceCount = Number(item && item.sourceCount != null ? item.sourceCount : sourcePaths.length);
        if (!Number.isFinite(resolvedSourceCount) || resolvedSourceCount <= 0) {
            return "暂未记录关联来源文件";
        }
        return "关联来源 " + String(resolvedSourceCount) + " 个文件";
    }

    function buildPrimarySourceSummary(item) {
        const primarySourcePath = getPrimaryArticleSourcePath(item);
        if (!primarySourcePath) {
            return "暂未记录主要参考文件。";
        }
        return primarySourcePath + "。列表页会优先用它帮助用户判断这条知识大致来自哪里。";
    }

    function buildArticleSourceOverview(item) {
        const sourceTypeText = buildArticleTypeMeta(item);
        const parts = [buildArticleSourceCountText(item) + "。"];
        if (sourceTypeText) {
            parts.push("来源类型包括 " + sourceTypeText + "。");
        }
        parts.push(buildFileLevelTraceExplanation(collectArticleSourceTypes(item)));
        return parts.join("");
    }

    function shouldShowArticleReviewPanel(item) {
        const normalizedReviewStatus = normalizeStatus(item && item.reviewStatus);
        return normalizedReviewStatus === "NEEDS_HUMAN_REVIEW" || normalizedReviewStatus === "NEEDS_REVIEW";
    }

    function renderArticleReviewPanel(item) {
        const panel = document.getElementById("article-review-panel");
        if (!panel) {
            return;
        }
        if (!shouldShowArticleReviewPanel(item)) {
            panel.hidden = true;
            clearArticleReviewInputs();
            return;
        }
        panel.hidden = false;
        const reviewerInput = document.getElementById("article-reviewer");
        const note = document.getElementById("article-review-note");
        if (reviewerInput && !reviewerInput.value.trim()) {
            reviewerInput.value = "admin";
        }
        if (note) {
            note.textContent = buildArticleReviewNote(item);
        }
    }

    function clearArticleReviewInputs() {
        ["article-reviewer", "article-review-comment", "article-correction-summary"].forEach(function (id) {
            const element = document.getElementById(id);
            if (element) {
                element.value = "";
            }
        });
    }

    function buildArticleReviewNote(item) {
        const normalizedReviewStatus = normalizeStatus(item && item.reviewStatus);
        if (normalizedReviewStatus === "NEEDS_REVIEW") {
            return "这条内容已提交过修正，确认证据稳定后再通过。";
        }
        return "这条内容需要人工复核，可确认通过，也可提交修正意见后继续走纠错链路。";
    }

    function renderArticleReviewHistory(auditResponse) {
        const container = document.getElementById("article-review-history");
        if (!container) {
            return;
        }
        const items = Array.isArray(auditResponse && auditResponse.items) ? auditResponse.items : [];
        if (items.length === 0) {
            container.innerHTML = "<div class='review-history-empty'><p class='item-summary'>暂无复核历史。</p></div>";
            return;
        }
        container.innerHTML = items.map(renderArticleReviewHistoryItem).join("");
    }

    function renderArticleReviewHistoryItem(item) {
        const statusText = [
            getBadgeLabel(item && item.previousReviewStatus),
            getBadgeLabel(item && item.nextReviewStatus)
        ].filter(Boolean).join(" → ");
        return "<article class='review-history-item'>"
                + "<div class='review-history-head'>"
                + "<div class='meta-row'>"
                + renderBadge(item && item.action)
                + "<span class='pill'>" + escapeHtml(statusText || "状态未记录") + "</span>"
                + "</div>"
                + "<span class='detail-compact-time'>" + escapeHtml(formatDateTime(item && item.reviewedAt)) + "</span>"
                + "</div>"
                + "<p class='item-summary'>" + escapeHtml(item && item.comment ? item.comment : "暂无复核意见") + "</p>"
                + "<p class='item-caption'>" + escapeHtml(item && item.reviewedBy ? "复核人：" + item.reviewedBy : "未记录复核人") + "</p>"
                + "</article>";
    }

    function renderArticleReviewActionResult(result) {
        const container = document.getElementById("article-review-history");
        if (!container || !result) {
            return;
        }
        container.innerHTML = "<div class='review-history-empty'><p class='item-summary'>"
                + escapeHtml("刚刚完成：" + getBadgeLabel(result.previousReviewStatus) + " → " + getBadgeLabel(result.reviewStatus))
                + "</p></div>";
    }

    function buildArticleTraceabilityNote(item) {
        const sourcePaths = getArticleSourcePaths(item);
        if (sourcePaths.length === 0) {
            return "当前还没有可追溯的来源文件。";
        }
        return "以下列出这条知识条目的完整来源文件。首个文件作为主要参考文件展示；"
                + buildFileLevelTraceExplanation(collectArticleSourceTypes(item));
    }

    function renderArticleTypePills(item) {
        const sourceTypes = collectArticleSourceTypes(item);
        if (sourceTypes.length === 0) {
            return "<span class='pill'>来源类型待识别</span>";
        }
        return sourceTypes.map(function (sourceType) {
            return "<span class='pill'>" + escapeHtml(sourceType) + "</span>";
        }).join("");
    }

    function buildArticleSourceMeta(item) {
        const parts = [];
        const primarySourcePath = getPrimaryArticleSourcePath(item);
        const sourceTypeText = buildArticleTypeMeta(item);
        if (primarySourcePath) {
            parts.push("主要参考文件：" + primarySourcePath);
        }
        parts.push(buildArticleSourceCountText(item));
        if (sourceTypeText) {
            parts.push("来源类型：" + sourceTypeText);
        }
        return parts.join(" | ");
    }

    function buildArticleTypeMeta(item) {
        const sourceTypes = collectArticleSourceTypes(item);
        return sourceTypes.length === 0 ? "" : sourceTypes.join("、");
    }

    function buildArticleTechnicalInfo(item) {
        const technicalItems = [
            {
                label: "资料源 ID",
                value: item && item.sourceId != null ? String(item.sourceId) : ""
            },
            {
                label: "文章键",
                value: item && item.articleKey ? String(item.articleKey) : ""
            },
            {
                label: "概念 ID",
                value: item && item.conceptId ? String(item.conceptId) : ""
            },
            {
                label: "审查标记",
                value: item && item.reviewStatus ? getBadgeLabel(item.reviewStatus) : ""
            },
            {
                label: "风险等级",
                value: item && item.riskLevel ? getBadgeLabel(item.riskLevel) : ""
            },
            {
                label: "风险原因",
                value: item && item.riskReasons && item.riskReasons.length > 0
                        ? item.riskReasons.map(getBadgeLabel).join("、")
                        : ""
            },
            {
                label: "热点内容",
                value: item && Boolean(item.isHotspot) ? "是" : ""
            },
            {
                label: "结果抽检",
                value: item && Boolean(item.requiresResultVerification) ? "需要" : ""
            },
            {
                label: "技术置信度",
                value: item && item.confidence ? String(item.confidence) : ""
            }
        ];
        return technicalItems.filter(function (technicalItem) {
            return technicalItem.value;
        });
    }

    function buildArticleRiskNotice(item) {
        if (!item) {
            return "<p class='item-summary'>暂无风险提示</p>";
        }
        const riskSummary = buildArticleRiskSummary(item);
        const riskReasons = item.riskReasons || [];
        const reasonList = riskReasons.length > 0
                ? "<div class='risk-reason-list'>"
                + riskReasons.map(function (riskReason) {
                    return "<span class='pill'>" + escapeHtml(getBadgeLabel(riskReason)) + "</span>";
                }).join("")
                + "</div>"
                : "";
        return "<p class='item-summary'>" + escapeHtml(riskSummary) + "</p>" + reasonList;
    }

    function buildArticleRiskSummary(item) {
        if (!item) {
            return "暂无风险提示";
        }
        const riskLevel = normalizeStatus(item.riskLevel || "low");
        const riskReasons = item.riskReasons || [];
        const flags = [];
        if (Boolean(item.isHotspot)) {
            flags.push("高频热点");
        }
        if (Boolean(item.requiresResultVerification)) {
            flags.push("需要结果抽检");
        }
        const reasonLabels = riskReasons.map(getBadgeLabel);
        if (riskLevel === "LOW" && reasonLabels.length === 0 && flags.length === 0) {
            return "低风险，暂无额外抽检原因";
        }
        const parts = [];
        parts.push(getBadgeLabel(riskLevel || "low"));
        if (reasonLabels.length > 0) {
            parts.push("不稳定点：" + reasonLabels.join("、"));
        }
        if (flags.length > 0) {
            parts.push("治理标记：" + flags.join("、"));
        }
        return parts.join("；");
    }

    function buildHotspotRefreshStatusText(response) {
        if (!response) {
            return "热点未刷新";
        }
        if (response.loading) {
            return "热点刷新中";
        }
        const candidateCount = Number(response.hotspotCandidateCount || 0);
        const updatedCount = Number(response.updatedArticleCount || 0);
        const threshold = Number(response.heatScoreThreshold || 0);
        return "候选 " + candidateCount + " · 更新 " + updatedCount + " · 阈值 " + threshold;
    }

    function renderHotspotRefreshStatus(response) {
        const element = document.getElementById("hotspot-refresh-status");
        if (!element) {
            return;
        }
        element.textContent = buildHotspotRefreshStatusText(response);
        if (response && response.loading) {
            element.dataset.status = "loading";
            return;
        }
        element.dataset.status = response ? "refreshed" : "idle";
    }

    function renderDescriptionList(items) {
        if (!items || items.length === 0) {
            return "<p class='item-summary'>暂无技术信息</p>";
        }
        return items.map(function (item) {
            return "<div class='description-item'>"
                    + "<span class='description-label'>" + escapeHtml(item.label) + "</span>"
                    + "<span class='description-value'>" + escapeHtml(item.value) + "</span>"
                    + "</div>";
        }).join("");
    }

    function renderArticleSourceReferences(item) {
        const sourcePaths = getArticleSourcePaths(item);
        if (!sourcePaths || sourcePaths.length === 0) {
            return "<div class='source-reference-card'><p class='item-summary'>暂未记录可追溯的来源文件。</p></div>";
        }
        return sourcePaths.map(function (sourcePath, index) {
            const sourceType = resolveSourceTypeLabel(sourcePath);
            const sourceRole = index === 0 ? "主要参考文件" : "关联来源";
            const sourceFile = findArticleSourceFile(item, sourcePath);
            const previewButton = sourceFile
                    ? "<button class='ghost-btn source-preview-trigger' type='button' data-article-source-path='"
                    + escapeHtml(sourcePath)
                    + "'>预览</button>"
                    : "";
            return "<article class='source-reference-card'>"
                    + "<div class='source-reference-header'>"
                    + "<div>"
                    + "<p class='item-kicker'>" + escapeHtml(sourceRole) + "</p>"
                    + "<p class='source-reference-path'>" + escapeHtml(sourcePath) + "</p>"
                    + "</div>"
                    + "<div class='meta-row'>"
                    + "<span class='pill'>" + escapeHtml(sourceType) + "</span>"
                    + previewButton
                    + "</div>"
                    + "</div>"
                    + "<p class='item-summary'>" + escapeHtml(buildSourceGranularityNote(sourcePath)) + "</p>"
                    + "</article>";
        }).join("");
    }

    function bindArticleSourcePreviewActions(item) {
        const container = document.getElementById("article-sources");
        if (!container || typeof container.querySelectorAll !== "function") {
            return;
        }
        container.querySelectorAll("[data-article-source-path]").forEach(function (button) {
            button.addEventListener("click", function () {
                renderArticleSourcePreview(findArticleSourceFile(item, button.dataset.articleSourcePath));
            });
        });
    }

    function renderArticleSourcePreview(sourceFile) {
        const container = document.getElementById("article-source-preview");
        if (!container) {
            return;
        }
        if (!sourceFile) {
            container.hidden = true;
            container.innerHTML = "";
            return;
        }
        container.hidden = false;
        const preview = String(sourceFile.contentPreview || "").trim();
        container.innerHTML = "<div class='source-preview-header'>"
                + "<div>"
                + "<p class='item-kicker'>来源预览</p>"
                + "<h5>" + escapeHtml(extractSourceFileName(sourceFile.relativePath || "") || sourceFile.relativePath || "来源文件") + "</h5>"
                + "</div>"
                + renderContextBadge(sourceFile.format || "UNKNOWN")
                + "</div>"
                + "<p class='source-reference-path'>" + escapeHtml(sourceFile.relativePath || "-") + "</p>"
                + "<pre class='code-view compact'>" + escapeHtml(preview || "当前文件没有可展示预览。") + "</pre>";
    }

    function findArticleSourceFile(item, sourcePath) {
        if (!item || !sourcePath || !Array.isArray(state.sourceFiles)) {
            return null;
        }
        const normalizedSourceId = item.sourceId == null ? null : String(item.sourceId);
        const normalizedSourcePath = normalizeSourceFilePath(sourcePath);
        for (let index = 0; index < state.sourceFiles.length; index++) {
            const sourceFile = state.sourceFiles[index];
            const sameSource = normalizedSourceId == null || String(sourceFile.sourceId || "") === normalizedSourceId;
            const samePath = normalizeSourceFilePath(sourceFile.relativePath) === normalizedSourcePath
                    || normalizeSourceFilePath(sourceFile.filePath) === normalizedSourcePath;
            if (sameSource && samePath) {
                return sourceFile;
            }
        }
        return null;
    }

    function normalizeSourceFilePath(value) {
        return String(value || "").trim().replaceAll("\\", "/");
    }

    function collectArticleSourceTypes(item) {
        const sourceTypes = [];
        const seenTypes = new Set();
        getArticleSourcePaths(item).forEach(function (sourcePath) {
            const sourceType = resolveSourceTypeLabel(sourcePath);
            if (!seenTypes.has(sourceType)) {
                seenTypes.add(sourceType);
                sourceTypes.push(sourceType);
            }
        });
        return sourceTypes;
    }

    function getArticleSourcePaths(item) {
        if (Array.isArray(item && item.sourcePaths) && item.sourcePaths.length > 0) {
            return item.sourcePaths.map(function (sourcePath) {
                return String(sourcePath);
            });
        }
        const primarySourcePath = getPrimaryArticleSourcePath(item);
        return primarySourcePath ? [primarySourcePath] : [];
    }

    function getPrimaryArticleSourcePath(item) {
        if (item && item.primarySourcePath) {
            return String(item.primarySourcePath);
        }
        if (item && item.primarySourceName) {
            return String(item.primarySourceName);
        }
        return Array.isArray(item && item.sourcePaths) && item.sourcePaths.length > 0
                ? String(item.sourcePaths[0])
                : "";
    }

    function getPrimaryArticleSourceName(item) {
        return getPrimaryArticleSourcePath(item);
    }

    function extractSourceFileName(sourcePath) {
        const normalized = String(sourcePath || "").trim().replaceAll("\\", "/");
        if (!normalized) {
            return "";
        }
        const segments = normalized.split("/").filter(function (segment) {
            return segment;
        });
        return segments.length === 0 ? normalized : segments[segments.length - 1];
    }

    function resolveSourceTypeLabel(sourcePath) {
        const normalizedPath = String(sourcePath || "").trim().toLowerCase();
        if (!normalizedPath) {
            return "未识别";
        }
        if (normalizedPath.endsWith(".java")) {
            return "Java";
        }
        if (normalizedPath.endsWith(".pdf")) {
            return "PDF";
        }
        if (normalizedPath.endsWith(".xls") || normalizedPath.endsWith(".xlsx")) {
            return "Excel";
        }
        if (normalizedPath.endsWith(".csv") || normalizedPath.endsWith(".tsv")) {
            return "CSV";
        }
        if (normalizedPath.endsWith(".doc") || normalizedPath.endsWith(".docx")) {
            return "Word";
        }
        if (normalizedPath.endsWith(".md") || normalizedPath.endsWith(".markdown")) {
            return "Markdown";
        }
        if (normalizedPath.endsWith(".json")) {
            return "JSON";
        }
        if (normalizedPath.endsWith(".xml")) {
            return "XML";
        }
        if (normalizedPath.endsWith(".html") || normalizedPath.endsWith(".htm")) {
            return "HTML";
        }
        if (normalizedPath.endsWith(".yml") || normalizedPath.endsWith(".yaml") || normalizedPath.endsWith(".properties")) {
            return "配置";
        }
        if (normalizedPath.endsWith(".txt")) {
            return "文本";
        }
        if (normalizedPath.endsWith(".js")) {
            return "JavaScript";
        }
        if (normalizedPath.endsWith(".css")) {
            return "CSS";
        }
        if (normalizedPath.endsWith(".py")) {
            return "Python";
        }
        if (normalizedPath.endsWith(".sh")) {
            return "Shell";
        }
        return "其他文件";
    }

    function buildSourceGranularityNote(sourcePath) {
        const sourceType = resolveSourceTypeLabel(sourcePath);
        if (sourceType === "Excel") {
            return "当前展示的是文件级来源，还未细化到 Sheet、表格区域或单元格。";
        }
        if (sourceType === "PDF") {
            return "当前展示的是文件级来源，还未细化到页码范围或段落片段。";
        }
        return "当前展示的是文件级来源，用于帮助继续追溯原始资料。";
    }

    function buildFileLevelTraceExplanation(sourceTypes) {
        const hasExcelSource = sourceTypes.indexOf("Excel") >= 0;
        const hasPdfSource = sourceTypes.indexOf("PDF") >= 0;
        if (hasExcelSource && hasPdfSource) {
            return "当前追溯停留在文件级，尚未细化到 Excel 的 Sheet / 单元格，也未细化到 PDF 页码或段落。";
        }
        if (hasExcelSource) {
            return "当前追溯停留在文件级，尚未细化到 Excel 的 Sheet / 单元格。";
        }
        if (hasPdfSource) {
            return "当前追溯停留在文件级，尚未细化到 PDF 页码或段落。";
        }
        return "当前追溯停留在文件级来源。";
    }

    function containsSource(items, sourceId) {
        return items.some(function (item) {
            return item.id === sourceId;
        });
    }

    function findSourceById(items, sourceId) {
        if (!Array.isArray(items)) {
            return null;
        }
        for (let index = 0; index < items.length; index++) {
            if (items[index] && items[index].id === sourceId) {
                return items[index];
            }
        }
        return null;
    }

    function isUploadSource(source) {
        return Boolean(source && String(source.sourceType || "").trim().toUpperCase() === "UPLOAD");
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

    function clearGitSourceForm() {
        document.getElementById("git-source-name").value = "";
        document.getElementById("git-source-code").value = "";
        document.getElementById("git-source-remote-url").value = "";
        document.getElementById("git-source-branch").value = "";
        document.getElementById("git-source-credential-ref").value = "";
        applyGitAccessMode("PUBLIC");
    }

    function resetInlineSourceCredentialForm() {
        const codeInput = document.getElementById("inline-source-credential-code");
        const typeSelect = document.getElementById("inline-source-credential-type");
        const secretInput = document.getElementById("inline-source-credential-secret");
        const updatedByInput = document.getElementById("inline-source-credential-updated-by");
        if (codeInput) {
            codeInput.value = "";
        }
        if (typeSelect) {
            typeSelect.value = "GIT_TOKEN";
        }
        if (secretInput) {
            secretInput.value = "";
        }
        if (updatedByInput) {
            updatedByInput.value = "";
        }
    }

    function selectGitCredential(credentialCode) {
        const select = document.getElementById("git-source-credential-ref");
        if (!select || !credentialCode) {
            return;
        }
        const matchedOption = Array.from(select.options).some(function (option) {
            return option.value === credentialCode;
        });
        if (matchedOption) {
            select.value = credentialCode;
        }
    }

    function bindGitAccessModeEvents() {
        document.querySelectorAll("[data-git-access-mode]").forEach(function (button) {
            button.addEventListener("click", function () {
                applyGitAccessMode(button.dataset.gitAccessMode);
            });
        });
    }

    function resolveGitAccessMode() {
        const activeButton = document.querySelector("[data-git-access-mode].active");
        return activeButton && activeButton.dataset.gitAccessMode
                ? String(activeButton.dataset.gitAccessMode).toUpperCase()
                : "PUBLIC";
    }

    function applyGitAccessMode(mode) {
        const resolvedMode = String(mode || "PUBLIC").toUpperCase() === "PRIVATE" ? "PRIVATE" : "PUBLIC";
        document.querySelectorAll("[data-git-access-mode]").forEach(function (button) {
            const active = String(button.dataset.gitAccessMode || "").toUpperCase() === resolvedMode;
            button.classList.toggle("active", active);
            button.setAttribute("aria-selected", active ? "true" : "false");
        });

        const note = document.getElementById("git-access-mode-note");
        const credentialField = document.getElementById("git-private-credential-field");
        const credentialPanel = document.getElementById("git-inline-credential-panel");
        const toggleButton = document.getElementById("toggle-inline-git-credential");

        if (resolvedMode === "PRIVATE") {
            if (note) {
                note.textContent = state.sourceCredentials.length > 0
                        ? "私有仓库需要访问凭据，你可以直接选择一个可用凭据。"
                        : "私有仓库需要访问凭据，你可以直接在下方新增。";
            }
            if (credentialField) {
                credentialField.hidden = false;
            }
            if (toggleButton) {
                toggleButton.hidden = false;
                toggleButton.textContent = "添加私有仓库凭据";
            }
            if (credentialPanel) {
                credentialPanel.hidden = false;
                if (state.sourceCredentials.length === 0) {
                    credentialPanel.open = true;
                }
            }
            return;
        }

        if (note) {
            note.textContent = "公开仓库可直接导入，不需要访问凭据。";
        }
        if (credentialField) {
            credentialField.hidden = true;
        }
        if (toggleButton) {
            toggleButton.hidden = true;
        }
        if (credentialPanel) {
            credentialPanel.open = false;
            credentialPanel.hidden = true;
        }
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

    function prettyJson(value, emptyText) {
        if (!value) {
            return emptyText || "暂无配置";
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
                ? "<span class='note'>" + escapeHtml(compactMetricNote(item.note)) + "</span>"
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
                || normalized === "APPROVE"
                || normalized === "CONFIRMED"
                || normalized === "DOCUMENT"
                || normalized === "UPLOAD"
                || normalized === "GIT_TOKEN"
                || normalized === "GIT_HTTP_BASIC"
                || normalized === "NORMAL") {
            className += " success";
        }
        else if (normalized === "FAILED" || normalized === "ARCHIVED" || normalized === "STALLED") {
            className += " danger";
        }
        else {
            className += " warning";
        }
        return "<span class='" + className + "' title='" + escapeHtml(String(value || "")) + "'>"
                + escapeHtml(getBadgeLabel(value))
                + "</span>";
    }

    function renderContextBadge(value) {
        return "<span class='badge context' title='" + escapeHtml(String(value || "")) + "'>"
                + escapeHtml(getBadgeLabel(value))
                + "</span>";
    }

    function compactMetricNote(note) {
        const text = String(note || "").trim();
        if (!text) {
            return "";
        }
        const replacements = [{
            from: "系统仍在持续推进这些任务",
            to: "仍在持续推进"
        }, {
            from: "当前没有需要人工确认的任务",
            to: "当前无需人工确认"
        }, {
            from: "最近还没有成功完成的任务",
            to: "最近暂无成功任务"
        }, {
            from: "最近没有失败任务",
            to: "最近暂无失败任务"
        }];
        const matched = replacements.find(function (replacement) {
            return replacement.from === text;
        });
        return matched ? matched.to : text;
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

    function sanitizeDisplayMessage(message) {
        const normalized = String(message || "")
                .split(/\r?\n/)
                .filter(function (line) {
                    const compactLine = String(line || "").trim();
                    return compactLine && !compactLine.startsWith("at ");
                })
                .join(" ")
                .replace(/\s+/g, " ")
                .trim();
        if (!normalized) {
            return "";
        }
        return normalized
                .replace(/\b[a-zA-Z0-9_$.]+Exception:\s*/g, "")
                .replace(/\b[a-zA-Z0-9_$.]+Error:\s*/g, "")
                .replace(/\bjava\.[^:]+:\s*/g, "");
    }

    function resolveHttpErrorDisplayMessage(error) {
        const payload = error && error.payload ? error.payload : null;
        const code = normalizeStatus(payload && payload.code);
        if (code === "SOURCE_SYNC_CONFLICT") {
            return "当前资料源已经有运行中的同步任务，请等它结束后再试。";
        }
        const payloadMessage = payload && payload.message ? sanitizeDisplayMessage(payload.message) : "";
        if (payloadMessage) {
            return compactDisplayMessage(payloadMessage);
        }
        const fallbackMessage = sanitizeDisplayMessage(error && error.message ? error.message : String(error));
        return fallbackMessage ? compactDisplayMessage(fallbackMessage) : "系统未返回更多可展示的信息。";
    }

    function setStatus(message, tone, persist) {
        const resolvedTone = tone || "info";
        const resolvedPersist = typeof persist === "boolean"
                ? persist
                : (resolvedTone === "danger" || resolvedTone === "warning");
        renderPageNotice(message, resolvedTone, resolvedPersist);
    }

    function clearStatus() {
        const notice = document.getElementById("page-notice");
        if (!notice) {
            return;
        }
        if (pageNoticeTimer) {
            window.clearTimeout(pageNoticeTimer);
            pageNoticeTimer = null;
        }
        notice.hidden = true;
        notice.className = "page-notice";
        notice.textContent = "";
    }

    function showError(prefix, error) {
        const message = resolveHttpErrorDisplayMessage(error);
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
            DEPRECATED: "已废弃",
            DISABLED: "已停用",
            ARCHIVED: "已归档",
            PASSED: "已通过",
            PENDING: "待处理",
            NEEDS_REVIEW: "待复核",
            NEEDS_HUMAN_REVIEW: "需人工复核",
            LOW: "低风险",
            MEDIUM: "中风险",
            HIGH: "高风险",
            SOURCE_CONFLICT: "来源冲突",
            LOW_TRACEABILITY: "低可追溯",
            OCR_LOW_CONFIDENCE: "OCR 低置信",
            HOTSPOT_UNVERIFIED: "热点未验证",
            USER_REPORTED: "用户反馈",
            RELIABLE: "答案可靠",
            ANSWER_PROBLEM: "答案有问题",
            NEEDS_MANUAL_CONFIRMATION: "需要人工确认",
            RESOLVED: "已处理",
            DISMISSED: "已忽略",
            RESOLVE: "标记处理",
            DISMISS: "标记忽略",
            APPROVE: "确认通过",
            REQUEST_CHANGES: "提交修正",
            SUCCEEDED: "成功",
            FAILED: "失败",
            STALLED: "失败",
            RUNNING: "进行中",
            QUEUED: "排队中",
            MATCHING: "识别中",
            MATERIALIZING: "整理中",
            COMPILE_QUEUED: "待处理",
            WAIT_CONFIRM: "待确认",
            SKIPPED_NO_CHANGE: "已完成",
            DIRECT_COMPILE: "直接编译",
            CONFIRMED: "已确认",
            NEW_SOURCE: "新建资料源",
            EXISTING_SOURCE_UPDATE: "更新已有资料源",
            EXISTING_SOURCE_APPEND: "追加到已有资料源",
            AMBIGUOUS: "需人工判断",
            CREATE: "新建",
            UPDATE: "更新",
            APPEND: "追加",
            REBUILD: "重建",
            GIT_TOKEN: "Git Token",
            GIT_HTTP_BASIC: "Git HTTP Basic",
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
            TEXT_READ: "文本读取",
            NATIVE: "原生解析",
            MD: "Markdown",
            MARKDOWN: "Markdown",
            TXT: "文本",
            PDF: "PDF",
            DOC: "Word",
            DOCX: "Word",
            XLS: "Excel",
            XLSX: "Excel",
            CSV: "CSV",
            JSON: "JSON",
            HTML: "HTML",
            XML: "XML",
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

    function formatRefreshTime(value) {
        const date = value instanceof Date ? value : new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "刚刚";
        }
        return date.toLocaleTimeString("zh-CN", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
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

    if (typeof globalThis !== "undefined" && globalThis.__LATTICE_ADMIN_TEST__) {
        globalThis.__LATTICE_ADMIN_TEST_STATE__ = state;
        globalThis.__LATTICE_ADMIN_TEST__.runs = {
            resolveRunDisplayStatus: resolveRunDisplayStatus,
            resolveRunStepLabel: resolveRunStepLabel,
            resolveRunProgressText: resolveRunProgressText,
            resolveRunSpotlightSummaryText: resolveRunSpotlightSummaryText,
            resolveRunNextStepText: resolveRunNextStepText,
            buildRunProgressStrip: buildRunProgressStrip,
            buildRunReasonSummary: buildRunReasonSummary,
            shouldRenderRunReasonSummary: shouldRenderRunReasonSummary,
            buildRunRuntimeSnapshot: buildRunRuntimeSnapshot,
            shouldRenderRunAsBoardFocus: shouldRenderRunAsBoardFocus,
            shouldPromoteCompletionRunAsBoardFocus: shouldPromoteCompletionRunAsBoardFocus,
            shouldRenderRunAsCompletionNotice: shouldRenderRunAsCompletionNotice,
            renderRecentRunBoardItem: renderRecentRunBoardItem,
            renderSourceRunListItem: renderSourceRunListItem,
            buildSourceRunDetailCard: buildSourceRunDetailCard,
            sanitizeDisplayMessage: sanitizeDisplayMessage,
            resolveHttpErrorDisplayMessage: resolveHttpErrorDisplayMessage
        };
        globalThis.__LATTICE_ADMIN_TEST__.source = {
            renderSourceList: renderSourceList,
            renderSourceDetail: renderSourceDetail,
            resolveSourceDisplayName: resolveSourceDisplayName,
            resolveSourceDocumentTitle: resolveSourceDocumentTitle,
            renderSourceFileListItem: renderSourceFileListItem,
            buildSourceFileDetailCard: buildSourceFileDetailCard,
            resolveSourceProcessingHistoryItems: resolveSourceProcessingHistoryItems,
            resolveSourceRunKey: resolveSourceRunKey,
            resolveSourceFileKey: resolveSourceFileKey,
            isUploadSource: isUploadSource,
            focusSourceRunDetail: focusSourceRunDetail,
            shouldFollowLatestSourceRun: shouldFollowLatestSourceRun
        };
        globalThis.__LATTICE_ADMIN_TEST__.knowledge = {
            renderSummary: renderSummary,
            deriveKnowledgeHelpState: deriveKnowledgeHelpState
        };
        globalThis.__LATTICE_ADMIN_TEST__.feedback = {
            buildQueryFeedbackListRequestUrl: buildQueryFeedbackListRequestUrl,
            renderQueryFeedbackListItem: renderQueryFeedbackListItem,
            renderQueryFeedbackHistoryItem: renderQueryFeedbackHistoryItem,
            buildQueryFeedbackHandleRequest: buildQueryFeedbackHandleRequest,
            compactText: compactText
        };
        globalThis.__LATTICE_ADMIN_TEST__.article = {
            resolveArticleDisplayTitle: resolveArticleDisplayTitle,
            resolveArticleSummary: resolveArticleSummary,
            buildArticleAvailabilitySummary: buildArticleAvailabilitySummary,
            buildArticleSourceCountText: buildArticleSourceCountText,
            buildArticleSourceOverview: buildArticleSourceOverview,
            buildArticleTraceabilityNote: buildArticleTraceabilityNote,
            buildArticleListRequestUrl: buildArticleListRequestUrl,
            buildArticleRiskFilterQuery: buildArticleRiskFilterQuery,
            buildArticleRiskSummary: buildArticleRiskSummary,
            buildArticleRiskNotice: buildArticleRiskNotice,
            buildHotspotRefreshStatusText: buildHotspotRefreshStatusText,
            renderHotspotRefreshStatus: renderHotspotRefreshStatus,
            collectArticleSourceTypes: collectArticleSourceTypes,
            getPrimaryArticleSourcePath: getPrimaryArticleSourcePath,
            renderArticleDetail: renderArticleDetail,
            shouldShowArticleReviewPanel: shouldShowArticleReviewPanel,
            buildArticleReviewNote: buildArticleReviewNote,
            buildArticleReviewRequest: buildArticleReviewRequest,
            renderArticleReviewHistory: renderArticleReviewHistory,
            renderArticleSourceReferences: renderArticleSourceReferences,
            renderArticleSourcePreview: renderArticleSourcePreview,
            findArticleSourceFile: findArticleSourceFile,
            resolveSourceTypeLabel: resolveSourceTypeLabel,
            buildSourceGranularityNote: buildSourceGranularityNote,
            buildFileLevelTraceExplanation: buildFileLevelTraceExplanation
        };
    }
})();
