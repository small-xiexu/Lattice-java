(function (window) {
    const ADMIN_ENTRY_GROUP = "admin-console";
    const SETTINGS_ENTRY = "settings";
    const DEVELOPER_ENTRY = "developer-access";
    const DEVELOPER_ENTRY_PANEL = "developer-access-entry";
    const SETTINGS_TABS = [
        "settings-overview",
        "settings-llm",
        "settings-parse",
        "settings-sources"
    ];
    const ENTRY_META = {
        settings: {
            documentTitle: "邪修智库｜管理员设置",
            sidebarTitle: "管理员设置",
            sidebarCopy: "普通用户只看知识库管理和知识问答。这里集中放管理员才会用到的模型、OCR、高级资料源接入和后台维护动作；开发者接入已拆成单独入口。",
            brandKicker: "邪修智库 · 管理员设置",
            refreshLabel: "刷新配置"
        },
        "developer-access": {
            documentTitle: "邪修智库｜开发者接入",
            sidebarTitle: "开发者接入",
            sidebarCopy: "这里集中放 CLI、HTTP API、MCP 的接入模板、首次验证步骤和 FAQ，避免把技术接入埋在管理员设置里面。",
            brandKicker: "邪修智库 · 开发者接入",
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
            element.hidden = element.dataset.entryOnly !== normalizedEntry;
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
