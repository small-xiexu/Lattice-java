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
        bindIfPresent("rebuild-chunks", "click", rebuildChunks);
    }

    function bindIfPresent(id, eventName, handler) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
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

})();
