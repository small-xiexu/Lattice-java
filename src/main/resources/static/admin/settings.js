(function () {
    const AdminCommon = window.AdminCommon;
    const statusApi = AdminCommon.createPageNoticeApi("settings-page-notice");
    const fetchJson = AdminCommon.fetchJson;
    const setStatus = statusApi.setStatus;
    const showError = statusApi.showError;

    document.addEventListener("DOMContentLoaded", function () {
        if (!isSettingsEntryActive()) {
            return;
        }
        bindEvents();
    });

    function isSettingsEntryActive() {
        if (!window.AdminSections || typeof window.AdminSections.getActiveEntry !== "function") {
            return true;
        }
        return window.AdminSections.getActiveEntry() === "settings";
    }

    function bindEvents() {
        bindIfPresent("create-server-source", "click", createServerSourceAndSync);
        bindIfPresent("rebuild-chunks", "click", rebuildChunks);
    }

    function bindIfPresent(id, eventName, handler) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
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
            await fetchJson("/api/v1/admin/sources/" + encodeURIComponent(source.id) + "/sync", {
                method: "POST"
            });
            clearServerSourceForm();
            redirectToKnowledgeManagement(source.id);
        }
        catch (error) {
            showError("创建服务器目录资料源失败", error);
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
            setStatus("知识切片重建已完成", "success");
        }
        catch (error) {
            showError("重建知识切片失败", error);
        }
    }

    function clearServerSourceForm() {
        document.getElementById("server-source-name").value = "";
        document.getElementById("server-source-code").value = "";
        document.getElementById("server-source-dir").value = "";
    }

    function redirectToKnowledgeManagement(sourceId) {
        if (!sourceId) {
            window.location.assign("/admin");
            return;
        }
        const params = new URLSearchParams();
        params.set("tab", "knowledge-runs");
        params.set("sourceId", String(sourceId));
        params.set("notice", "server-dir-created");
        window.location.assign("/admin?" + params.toString());
    }
})();
