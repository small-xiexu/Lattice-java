(function (window) {
    const ADMIN_ENTRY_GROUP = "admin-console";
    const SETTINGS_ENTRY = "settings";
    const DEVELOPER_ENTRY = "developer-access";
    const DEVELOPER_ENTRY_PANEL = "developer-access-entry";
    const SETTINGS_TABS = [
        "settings-llm",
        "settings-parse",
        "settings-sources"
    ];
    const ENTRY_META = {
        settings: {
            documentTitle: "邪修智库｜系统配置",
            sidebarTitle: "系统配置",
            sidebarCopy: "先判断哪一项未就绪，再进入对应模块。这里集中处理模型与向量、OCR / 文档识别和高级维护；开发接入已拆成独立入口。",
            brandKicker: "邪修智库 · 系统配置",
            refreshLabel: "刷新配置"
        },
        "developer-access": {
            documentTitle: "邪修智库｜开发接入",
            sidebarTitle: "开发接入",
            sidebarCopy: "这里集中放 CLI、HTTP API、MCP 的接入模板、首次验证步骤和 FAQ，避免把技术接入混进日常系统配置。",
            brandKicker: "邪修智库 · 开发接入",
            refreshLabel: "刷新接入信息"
        }
    };

    let activeEntry = SETTINGS_ENTRY;

    document.addEventListener("DOMContentLoaded", function () {
        if (!document.querySelector("[data-tab-group='admin-console']")) {
            return;
        }
        activeEntry = resolveEntry();
        applyEntry(activeEntry);
        activateSettingsConsole(activeEntry);
    });

    function resolveEntry() {
        if (typeof window === "undefined" || !window.location) {
            return SETTINGS_ENTRY;
        }
        const path = String(window.location.pathname || "");
        if (path === "/admin/developer-access" || path === "/admin/developer-access/") {
            return DEVELOPER_ENTRY;
        }
        return SETTINGS_ENTRY;
    }

    function applyEntry(entry) {
        const normalizedEntry = entry === DEVELOPER_ENTRY ? DEVELOPER_ENTRY : SETTINGS_ENTRY;
        const meta = ENTRY_META[normalizedEntry];
        document.body.setAttribute("data-admin-entry", normalizedEntry);
        document.querySelectorAll("[data-entry-only]").forEach(function (element) {
            const matchesEntry = element.dataset.entryOnly === normalizedEntry;
            if (!matchesEntry) {
                element.hidden = true;
                element.dataset.entryHiddenBySections = "true";
                return;
            }
            if (element.dataset.entryHiddenBySections === "true") {
                element.hidden = false;
                delete element.dataset.entryHiddenBySections;
            }
        });
        document.querySelectorAll("[data-admin-nav]").forEach(function (element) {
            element.classList.toggle("active", element.dataset.adminNav === normalizedEntry);
        });
        setText("admin-sidebar-title", meta.sidebarTitle);
        setText("admin-sidebar-copy", meta.sidebarCopy);
        setText("admin-page-kicker", meta.brandKicker);
        setButtonText("refresh-ai", meta.refreshLabel);
        if (document.title !== meta.documentTitle) {
            document.title = meta.documentTitle;
        }
        activeEntry = normalizedEntry;
    }

    function activateSettingsConsole(entry) {
        if (!window.AdminTabs || typeof window.AdminTabs.activate !== "function") {
            return;
        }
        if (entry === DEVELOPER_ENTRY) {
            window.AdminTabs.activate(ADMIN_ENTRY_GROUP, DEVELOPER_ENTRY_PANEL);
            return;
        }
        const params = new URLSearchParams(window.location.search || "");
        const requestedTab = normalizeSettingsTab(params.get("tab"));
        window.AdminTabs.activate(ADMIN_ENTRY_GROUP, requestedTab || SETTINGS_TABS[0]);
    }

    function normalizeSettingsTab(tabName) {
        const normalized = String(tabName || "").trim();
        return SETTINGS_TABS.indexOf(normalized) >= 0 ? normalized : null;
    }

    function setText(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    function setButtonText(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    window.AdminSections = {
        getActiveEntry: function () {
            return activeEntry;
        },
        activate: function (entry) {
            applyEntry(entry);
            activateSettingsConsole(activeEntry);
        }
    };
})(window);
