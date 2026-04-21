(function () {
    const RUN_POLLING_STATUSES = ["MATCHING", "MATERIALIZING", "COMPILE_QUEUED", "QUEUED", "RUNNING"];
    const UPLOAD_SUPPORTED_EXTENSIONS = Object.freeze(new Set([
        "doc", "docx", "pptx", "pdf", "xlsx", "xls", "csv",
        "md", "markdown", "txt", "json", "html", "xml",
        "yml", "yaml", "properties", "js", "css", "java", "sh", "py"
    ]));

    const state = {
        selectedArticleId: null,
        selectedArticleSourceId: null,
        selectedSourceId: null,
        overview: null,
        health: null,
        recentRuns: [],
        articleCount: 0,
        pendingRouteTab: null,
        pendingRouteNotice: null,
        uploadFiles: [],
        sourceCredentials: [],
        sources: [],
        activeRunId: null
    };

    let pageNoticeTimer = null;
    let runPollingTimer = null;

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
        bindIfPresent("refresh-jobs", "click", loadRecentRuns);
        bindIfPresent("refresh-sources", "click", loadSources);
        bindIfPresent("search-articles", "click", loadArticles);
        bindIfPresent("article-source-filter", "change", loadArticles);
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
        document.addEventListener("click", handleKnowledgeHelpActionClick);
    }

    function bindIfPresent(id, eventName, handler) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
    }

    function bindUploadFilePicker() {
        const picker = document.getElementById("compile-file-picker");
        const filesInput = document.getElementById("compile-files");
        const folderInput = document.getElementById("compile-folder-files");
        const trigger = document.getElementById("compile-file-trigger");
        const folderTrigger = document.getElementById("compile-folder-trigger");
        const clearButton = document.getElementById("compile-file-clear");
        const list = document.getElementById("compile-file-list");
        if (!picker || !filesInput) {
            return;
        }

        filesInput.addEventListener("change", function () {
            handleUploadInputChange(filesInput.files);
            filesInput.value = "";
        });
        if (folderInput) {
            folderInput.addEventListener("change", function () {
                handleUploadInputChange(folderInput.files);
                folderInput.value = "";
            });
        }
        if (trigger) {
            trigger.addEventListener("click", openUploadFileDialog);
        }
        if (folderTrigger) {
            folderTrigger.addEventListener("click", openUploadFolderDialog);
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
        if (event.target.closest("button") || event.target.closest(".import-file-picker-list")) {
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

    function openUploadFolderDialog(event) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        const folderInput = document.getElementById("compile-folder-files");
        if (!folderInput) {
            return;
        }
        folderInput.click();
    }

    function clearUploadFileSelection(event) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        const filesInput = document.getElementById("compile-files");
        const folderInput = document.getElementById("compile-folder-files");
        if (filesInput) {
            filesInput.value = "";
        }
        if (folderInput) {
            folderInput.value = "";
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
        const folderTrigger = document.getElementById("compile-folder-trigger");
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
            trigger.textContent = files.length > 0 ? "重新选择文件" : "选择文件";
        }
        if (folderTrigger) {
            folderTrigger.textContent = files.length > 0 ? "重新选择文件夹" : "选择文件夹";
        }
        if (clearButton) {
            clearButton.hidden = files.length === 0;
        }
        if (files.length === 0) {
            summary.textContent = "未选择任何文件";
            helper.textContent = "支持拖拽多个文件，也支持一次选择多个文件或整个文件夹。";
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

    function activateKnowledgeTab(tabName) {
        if (!window.AdminTabs || typeof window.AdminTabs.activate !== "function") {
            return;
        }
        window.AdminTabs.activate("knowledge-console", tabName);
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
        const allowedTabs = ["knowledge-upload", "knowledge-runs", "knowledge-articles"];
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
            loadRecentRuns(),
            loadArticles()
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
            const items = response || [];
            state.recentRuns = items;
            renderRecentRunOverview(items);
            renderRecentRunBoard(items);
            renderKnowledgeHelpSystem();
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
            activateKnowledgeTab("knowledge-runs");
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
                activateKnowledgeTab("knowledge-runs");
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
        if (action === "knowledge-upload" || action === "knowledge-runs" || action === "knowledge-articles") {
            activateKnowledgeTab(action);
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
        const status = state.overview && state.overview.status ? state.overview.status : null;
        if (!status) {
            return {
                tone: "info",
                title: "正在整理知识库状态",
                description: "页面会先拉取总览、同步运行和已入库内容，再告诉你现在应该去哪个区块继续操作。",
                actions: [
                    {label: "回到首屏状态", action: "workbench-top", className: "secondary-btn"}
                ],
                faqKey: "first-steps"
            };
        }

        const runs = Array.isArray(state.recentRuns) ? state.recentRuns.slice().sort(compareRunsByRequestedAtDesc) : [];
        const failedRun = runs.find(function (item) {
            return normalizeStatus(item.status) === "FAILED";
        });
        if (failedRun) {
            return {
                tone: "danger",
                title: "最近一次入库失败了",
                description: buildKnowledgeHelpDescription(
                        "先看最近同步运行里的错误信息，再判断是资料格式、解析方式还是连接问题。",
                        failedRun.errorMessage || ""
                ),
                actions: [
                    {label: "查看最近同步运行", action: "knowledge-runs", className: "primary-btn"},
                    {label: "回资料导入", action: "knowledge-upload", className: "ghost-btn"}
                ],
                faqKey: "upload-delay"
            };
        }

        const waitConfirmRun = runs.find(function (item) {
            return normalizeStatus(item.status) === "WAIT_CONFIRM";
        });
        if (waitConfirmRun) {
            return {
                tone: "warning",
                title: "有一批资料还在等待人工确认",
                description: "系统已经接收到资料，但还不能自动判断是新建还是合并。先去最近同步运行处理确认，再决定是否继续提问。",
                actions: [
                    {label: "去最近同步运行", action: "knowledge-runs", className: "primary-btn"},
                    {label: "看已入库内容", action: "knowledge-articles", className: "ghost-btn"}
                ],
                faqKey: "upload-delay"
            };
        }

        const activeRun = runs.find(function (item) {
            return isPollingStatus(item.status);
        });
        if (activeRun) {
            return {
                tone: "warning",
                title: "资料正在处理中",
                description: "上传成功不等于已经进入可问答状态。先看最近同步运行确认当前阶段，等处理完成后再判断问答结果是否正常。",
                actions: [
                    {label: "查看最近同步运行", action: "knowledge-runs", className: "primary-btn"},
                    {label: "去已入库内容", action: "knowledge-articles", className: "ghost-btn"}
                ],
                faqKey: "upload-delay"
            };
        }

        const articleCount = Number(status.articleCount || 0);
        const sourceFileCount = Number(status.sourceFileCount || 0);
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
                description: "先去已入库内容搜索标题、摘要或概念词，确认内容是否真的沉淀。如果这里仍为空，说明还需要继续排查同步或解析问题。",
                actions: [
                    {label: "去已入库内容", action: "knowledge-articles", className: "primary-btn"},
                    {label: "回资料导入", action: "knowledge-upload", className: "ghost-btn"}
                ],
                faqKey: "cannot-answer"
            };
        }

        return {
            tone: "success",
            title: "知识库已经可以使用",
            description: "资料已经进入知识库，现在可以去知识问答直接提问；如果结果不准，再回到这里核对已入库内容和最近同步运行。",
            actions: [
                {label: "去知识问答", action: "go-ask", className: "primary-btn"},
                {label: "去已入库内容", action: "knowledge-articles", className: "ghost-btn"}
            ],
            faqKey: "cannot-answer"
        };
    }

    function buildKnowledgeHelpDescription(primary, appendix) {
        const compactAppendix = String(appendix || "").trim();
        if (!compactAppendix) {
            return primary;
        }
        return primary + " 最近错误：" + compactHealthMessage(compactAppendix);
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
            container.innerHTML = "<div class='inline-credential-card'><h4>还没有可用凭据</h4><p>如果你要导入私有 Git 仓库，可以直接在上面新增一个。公开仓库则不需要这里。</p></div>";
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
                + "</div>"
                + "<p class='item-kicker'>知识条目</p>"
                + "<h4>" + escapeHtml(resolveArticleDisplayTitle(item)) + "</h4>"
                + "<p class='item-summary article-purpose'>" + escapeHtml(resolveArticleSummary(item)) + "</p>"
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

    function renderArticleDetail(detail) {
        document.getElementById("article-detail-title").textContent = resolveArticleDisplayTitle(detail);
        document.getElementById("article-detail-meta").textContent = [
            buildArticleAvailabilitySummary(detail),
            buildArticleSourceMeta(detail),
            detail.compiledAt ? "入库时间：" + formatDateTime(detail.compiledAt) : ""
        ].filter(Boolean).join(" | ");
        document.getElementById("article-detail-summary").textContent = resolveArticleSummary(detail);
        document.getElementById("article-primary-source").textContent = buildPrimarySourceSummary(detail);
        document.getElementById("article-source-overview").textContent = buildArticleSourceOverview(detail);
        document.getElementById("article-source-note").textContent = buildArticleTraceabilityNote(detail);
        document.getElementById("article-content").textContent = detail.content || "";
        document.getElementById("article-metadata").textContent = prettyJson(detail.metadataJson, "暂无技术元数据");
        document.getElementById("article-sources").innerHTML = renderArticleSourceReferences(detail.sourcePaths || []);
        const relations = []
                .concat((detail.referentialKeywords || []).map(function (item) { return "关键词: " + item; }))
                .concat((detail.dependsOn || []).map(function (item) { return "依赖: " + item; }))
                .concat((detail.related || []).map(function (item) { return "相关: " + item; }));
        document.getElementById("article-relations").innerHTML = renderTagGroup(relations);
        document.getElementById("article-technical-info").innerHTML = renderDescriptionList(buildArticleTechnicalInfo(detail));
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
        document.getElementById("article-primary-source").textContent = "暂无主要参考文件";
        document.getElementById("article-source-overview").textContent = "暂无来源概况";
        document.getElementById("article-source-note").textContent = "当前还没有可追溯的来源文件。";
        document.getElementById("article-content").textContent = "暂无内容";
        document.getElementById("article-metadata").textContent = "暂无技术元数据";
        document.getElementById("article-sources").innerHTML = "";
        document.getElementById("article-relations").innerHTML = "";
        document.getElementById("article-technical-info").innerHTML = "";
    }

    function renderRecentRunOverview(items) {
        const container = document.getElementById("recent-run-overview");
        if (!container) {
            return;
        }
        const effectiveItems = items || [];
        const runningCount = effectiveItems.filter(function (item) {
            return isPollingStatus(item.status);
        }).length;
        const waitingCount = effectiveItems.filter(function (item) {
            return normalizeStatus(item.status) === "WAIT_CONFIRM";
        }).length;
        const successCount = effectiveItems.filter(function (item) {
            const normalized = normalizeStatus(item.status);
            return normalized === "SUCCEEDED" || normalized === "SKIPPED_NO_CHANGE";
        }).length;
        const failedCount = effectiveItems.filter(function (item) {
            return normalizeStatus(item.status) === "FAILED";
        }).length;
        const cards = [
            {
                label: "进行中",
                value: runningCount,
                note: runningCount > 0 ? "系统仍在继续处理这些资料" : "当前没有正在执行的同步",
                tone: runningCount > 0 ? "warning" : ""
            },
            {
                label: "待确认",
                value: waitingCount,
                note: waitingCount > 0 ? "需要人工判断新建还是合并" : "当前没有需要人工确认的任务",
                tone: waitingCount > 0 ? "warning" : "success"
            },
            {
                label: "已完成",
                value: successCount,
                note: successCount > 0 ? "最近已经成功处理并收口" : "最近还没有成功完成的任务",
                tone: successCount > 0 ? "success" : ""
            },
            {
                label: "失败",
                value: failedCount,
                note: failedCount > 0 ? "建议打开下方卡片查看错误信息" : "最近没有失败任务",
                tone: failedCount > 0 ? "danger" : "success"
            }
        ];
        container.innerHTML = cards.map(renderMetricCard).join("");
    }

    function renderRecentRunBoard(items) {
        const container = document.getElementById("job-list");
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>暂时没有同步记录</p></div>";
            return;
        }
        const visibleItems = items.slice()
                .sort(compareRunsByRequestedAtDesc)
                .slice(0, 6);
        container.innerHTML = visibleItems.map(function (item) {
            return renderRecentRunCard(item);
        }).join("");
        bindRunActions(container);
    }

    function renderRecentRunCard(item) {
        const stageInfo = getRunStageInfo(item);
        const toneClass = "run-tone-" + stageInfo.tone;
        return "<article class='run-spotlight-card " + toneClass + "'>"
                + "<div class='run-spotlight-header'>"
                + "<div class='meta-row'>"
                + renderBadge(item.sourceType || "UPLOAD")
                + renderBadge(item.status)
                + renderBadge(item.syncAction || item.resolverDecision || "AUTO")
                + "</div>"
                + "<div class='run-spotlight-time'>提交 " + escapeHtml(formatDateTime(item.requestedAt)) + "</div>"
                + "</div>"
                + "<h3 class='run-spotlight-title'>" + escapeHtml(getRunTitle(item)) + "</h3>"
                + "<p class='run-spotlight-summary'>" + escapeHtml(getRunSummary(item)) + "</p>"
                + "<div class='run-spotlight-highlights'>"
                + "<span class='surface-chip'>当前阶段：" + escapeHtml(stageInfo.label) + "</span>"
                + "<span class='surface-chip'>下一步：" + escapeHtml(stageInfo.nextStep) + "</span>"
                + "</div>"
                + buildRunProgressStrip(item, stageInfo)
                + "<div class='run-spotlight-footnotes'>"
                + "<p class='run-spotlight-note'><strong>来源概况：</strong>" + escapeHtml(buildRunSourcePreview(item)) + "</p>"
                + "<p class='run-spotlight-note'><strong>任务线索：</strong>" + escapeHtml(buildRunOperationalNote(item)) + "</p>"
                + "</div>"
                + (item.errorMessage
                ? "<div class='job-error'><strong>错误信息</strong><p>" + escapeHtml(item.errorMessage) + "</p></div>"
                : "")
                + buildRunActions(item)
                + "</article>";
    }

    function buildRunProgressStrip(item, stageInfo) {
        const steps = ["资料接收", "自动识别", "物化编译", "完成收口"];
        const normalized = normalizeStatus(item.status);
        return "<div class='run-progress-strip'>"
                + steps.map(function (stepLabel, index) {
                    let stepClass = "pending";
                    if (normalized === "SUCCEEDED" || normalized === "SKIPPED_NO_CHANGE") {
                        stepClass = "completed";
                    }
                    else if (index < stageInfo.stepIndex) {
                        stepClass = "completed";
                    }
                    else if (index === stageInfo.stepIndex) {
                        stepClass = stageInfo.tone === "danger"
                                ? "failed"
                                : stageInfo.tone === "warning"
                                ? "warning"
                                : "active";
                    }
                    return "<div class='run-progress-step " + stepClass + "'>"
                            + "<span class='run-progress-order'>" + escapeHtml(String(index + 1)) + "</span>"
                            + "<span class='run-progress-label'>" + escapeHtml(stepLabel) + "</span>"
                            + "</div>";
                }).join("")
                + "</div>";
    }

    function getRunStageInfo(item) {
        const normalized = normalizeStatus(item.status);
        if (normalized === "MATCHING") {
            return {label: "自动识别中", nextStep: "判断新建、更新还是追加", stepIndex: 1, tone: "warning"};
        }
        if (normalized === "WAIT_CONFIRM") {
            return {label: "等待人工确认", nextStep: "选择新建或合并方式", stepIndex: 1, tone: "warning"};
        }
        if (normalized === "MATERIALIZING") {
            return {label: "资料物化中", nextStep: "复制目录或拉取仓库内容", stepIndex: 2, tone: "warning"};
        }
        if (normalized === "COMPILE_QUEUED") {
            return {label: "等待编译", nextStep: "排队进入知识编译任务", stepIndex: 2, tone: "warning"};
        }
        if (normalized === "RUNNING") {
            return {label: "编译入库中", nextStep: "写入知识库并生成结果", stepIndex: 2, tone: "warning"};
        }
        if (normalized === "SUCCEEDED") {
            return {label: "处理完成", nextStep: "可以查看已入库内容或继续问答", stepIndex: 3, tone: "success"};
        }
        if (normalized === "SKIPPED_NO_CHANGE") {
            return {label: "无变化跳过", nextStep: "本次无需重新编译", stepIndex: 3, tone: "success"};
        }
        if (normalized === "FAILED") {
            return {
                label: "处理失败",
                nextStep: "查看错误信息后重试",
                stepIndex: item.compileJobId ? 2 : 1,
                tone: "danger"
            };
        }
        if (normalized === "QUEUED") {
            return {label: "等待开始", nextStep: "准备进入自动识别", stepIndex: 0, tone: "warning"};
        }
        return {label: getBadgeLabel(item.status), nextStep: "等待系统继续处理", stepIndex: 0, tone: "warning"};
    }

    function buildRunSourcePreview(item) {
        if (item.sourceName) {
            return item.sourceName;
        }
        if (!item.sourceNames || item.sourceNames.length === 0) {
            return "系统正在整理来源文件";
        }
        if (item.sourceNames.length === 1) {
            return item.sourceNames[0];
        }
        const preview = item.sourceNames.slice(0, 2).join("、");
        return preview + " 等 " + String(item.sourceNames.length) + " 个文件";
    }

    function buildRunOperationalNote(item) {
        const parts = [];
        if (item.resolverDecision) {
            parts.push("决策：" + getBadgeLabel(item.resolverDecision));
        }
        if (item.syncAction) {
            parts.push("动作：" + getBadgeLabel(item.syncAction));
        }
        if (item.compileJobStatus) {
            parts.push("编译：" + getBadgeLabel(item.compileJobStatus));
        }
        if (item.updatedAt) {
            parts.push("最近更新 " + formatDateTime(item.updatedAt));
        }
        return parts.length > 0 ? parts.join(" · ") : "系统正在继续处理这条同步任务";
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
                label: "技术置信度",
                value: item && item.confidence ? String(item.confidence) : ""
            }
        ];
        return technicalItems.filter(function (technicalItem) {
            return technicalItem.value;
        });
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

    function renderArticleSourceReferences(sourcePaths) {
        if (!sourcePaths || sourcePaths.length === 0) {
            return "<div class='source-reference-card'><p class='item-summary'>暂未记录可追溯的来源文件。</p></div>";
        }
        return sourcePaths.map(function (sourcePath, index) {
            const sourceType = resolveSourceTypeLabel(sourcePath);
            const sourceRole = index === 0 ? "主要参考文件" : "关联来源";
            return "<article class='source-reference-card'>"
                    + "<div class='source-reference-header'>"
                    + "<div>"
                    + "<p class='item-kicker'>" + escapeHtml(sourceRole) + "</p>"
                    + "<p class='source-reference-path'>" + escapeHtml(sourcePath) + "</p>"
                    + "</div>"
                    + "<span class='pill'>" + escapeHtml(sourceType) + "</span>"
                    + "</div>"
                    + "<p class='item-summary'>" + escapeHtml(buildSourceGranularityNote(sourcePath)) + "</p>"
                    + "</article>";
        }).join("");
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
                || normalized === "GIT_TOKEN"
                || normalized === "GIT_HTTP_BASIC"
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
            DEPRECATED: "已废弃",
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

    if (typeof globalThis !== "undefined" && globalThis.__LATTICE_ADMIN_TEST__) {
        globalThis.__LATTICE_ADMIN_TEST__.article = {
            resolveArticleDisplayTitle: resolveArticleDisplayTitle,
            resolveArticleSummary: resolveArticleSummary,
            buildArticleAvailabilitySummary: buildArticleAvailabilitySummary,
            buildArticleSourceCountText: buildArticleSourceCountText,
            buildArticleSourceOverview: buildArticleSourceOverview,
            buildArticleTraceabilityNote: buildArticleTraceabilityNote,
            collectArticleSourceTypes: collectArticleSourceTypes,
            getPrimaryArticleSourcePath: getPrimaryArticleSourcePath,
            resolveSourceTypeLabel: resolveSourceTypeLabel,
            buildSourceGranularityNote: buildSourceGranularityNote,
            buildFileLevelTraceExplanation: buildFileLevelTraceExplanation
        };
    }
})();
