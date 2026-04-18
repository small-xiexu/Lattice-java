(function () {
    const LLM_BINDING_ROLE_OPTIONS = {
        compile: ["writer", "reviewer", "fixer"],
        query: ["answer", "reviewer", "rewrite"]
    };

    const state = {
        llmConnections: [],
        llmModels: [],
        llmBindings: []
    };

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        loadAiConfig();
    });

    function bindEvents() {
        document.getElementById("refresh-ai").addEventListener("click", loadAiConfig);
        document.getElementById("save-llm-connection").addEventListener("click", saveLlmConnection);
        document.getElementById("reset-llm-connection").addEventListener("click", resetLlmConnectionForm);
        document.getElementById("save-llm-model").addEventListener("click", saveLlmModel);
        document.getElementById("reset-llm-model").addEventListener("click", resetLlmModelForm);
        document.getElementById("llm-model-kind").addEventListener("change", syncLlmModelKindForm);
        document.getElementById("save-llm-binding").addEventListener("click", saveLlmBinding);
        document.getElementById("reset-llm-binding").addEventListener("click", resetLlmBindingForm);
        document.getElementById("llm-binding-scene").addEventListener("change", function () {
            renderLlmBindingRoleOptions();
            renderLlmModelOptions();
        });
    }

    async function loadAiConfig() {
        setStatus("正在刷新 AI 接入配置...");
        try {
            const responses = await Promise.all([
                fetchJson("/api/v1/admin/llm/connections"),
                fetchJson("/api/v1/admin/llm/models"),
                fetchJson("/api/v1/admin/llm/bindings")
            ]);
            state.llmConnections = responses[0].items || [];
            state.llmModels = responses[1].items || [];
            state.llmBindings = responses[2].items || [];
            renderLlmConnectionOptions();
            renderLlmBindingRoleOptions();
            renderLlmModelOptions();
            renderLlmConnectionList(state.llmConnections);
            renderLlmModelList(state.llmModels);
            renderLlmBindingList(state.llmBindings);
            syncLlmModelKindForm();
            setStatus("AI 接入配置已刷新");
        }
        catch (error) {
            renderGlobalResultError("加载 AI 接入配置失败", error);
            showError("加载 AI 接入配置失败", error);
        }
    }

    async function saveLlmConnection() {
        const id = document.getElementById("llm-connection-id").value.trim();
        const payload = {
            connectionCode: document.getElementById("llm-connection-code").value.trim(),
            providerType: document.getElementById("llm-provider-type").value,
            baseUrl: document.getElementById("llm-base-url").value.trim(),
            apiKey: document.getElementById("llm-api-key").value.trim(),
            enabled: document.getElementById("llm-connection-enabled").checked
        };
        if (!payload.connectionCode || !payload.baseUrl) {
            setStatus("请填写连接名称和 Base URL");
            return;
        }
        if (!id && !payload.apiKey) {
            setStatus("新增连接时必须填写 API Key");
            return;
        }
        try {
            const result = await fetchJson(id
                    ? "/api/v1/admin/llm/connections/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/connections", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            renderGlobalResult(result);
            setStatus(id ? "连接已更新" : "连接已创建");
            resetLlmConnectionForm();
            await loadAiConfig();
        }
        catch (error) {
            renderGlobalResultError("保存连接失败", error);
            showError("保存连接失败", error);
        }
    }

    async function saveLlmModel() {
        const id = document.getElementById("llm-model-id").value.trim();
        const payload = {
            modelCode: "",
            connectionId: parseOptionalInteger(document.getElementById("llm-model-connection-id").value),
            modelName: document.getElementById("llm-model-name").value.trim(),
            modelKind: document.getElementById("llm-model-kind").value,
            expectedDimensions: parseOptionalInteger(document.getElementById("llm-model-expected-dimensions").value),
            enabled: document.getElementById("llm-model-enabled").checked
        };
        if (!payload.modelName || !payload.connectionId) {
            setStatus("请先选择连接并填写模型名称");
            return;
        }
        if (payload.modelKind === "EMBEDDING" && (!payload.expectedDimensions || payload.expectedDimensions <= 0)) {
            setStatus("Embedding 模型必须填写正整数维度");
            return;
        }
        if (payload.modelKind !== "EMBEDDING") {
            payload.expectedDimensions = null;
        }
        try {
            const result = await fetchJson(id
                    ? "/api/v1/admin/llm/models/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/models", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            renderGlobalResult(result);
            setStatus(id ? "模型已更新" : "模型已创建");
            resetLlmModelForm();
            await loadAiConfig();
        }
        catch (error) {
            renderGlobalResultError("保存模型失败", error);
            showError("保存模型失败", error);
        }
    }

    async function saveLlmBinding() {
        const id = document.getElementById("llm-binding-id").value.trim();
        const payload = {
            scene: document.getElementById("llm-binding-scene").value,
            agentRole: document.getElementById("llm-binding-agent-role").value,
            primaryModelProfileId: parseOptionalInteger(document.getElementById("llm-binding-primary-model-id").value),
            enabled: document.getElementById("llm-binding-enabled").checked
        };
        if (!payload.primaryModelProfileId) {
            setStatus("请先为该 Agent 选择模型");
            return;
        }
        try {
            const result = await fetchJson(id
                    ? "/api/v1/admin/llm/bindings/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/bindings", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            renderGlobalResult(result);
            setStatus(id ? "Agent 绑定已更新" : "Agent 绑定已创建");
            resetLlmBindingForm();
            await loadAiConfig();
        }
        catch (error) {
            renderGlobalResultError("保存 Agent 绑定失败", error);
            showError("保存 Agent 绑定失败", error);
        }
    }

    async function deleteLlmConnection(id) {
        if (!window.confirm("将删除该连接，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/llm/connections/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            renderGlobalResult(result);
            setStatus("连接已删除");
            resetLlmConnectionForm();
            await loadAiConfig();
        }
        catch (error) {
            renderGlobalResultError("删除连接失败", error);
            showError("删除连接失败", error);
        }
    }

    async function deleteLlmModel(id) {
        if (!window.confirm("将删除该模型，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/llm/models/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            renderGlobalResult(result);
            setStatus("模型已删除");
            resetLlmModelForm();
            await loadAiConfig();
        }
        catch (error) {
            renderGlobalResultError("删除模型失败", error);
            showError("删除模型失败", error);
        }
    }

    async function deleteLlmBinding(id) {
        if (!window.confirm("将删除该 Agent 绑定，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/llm/bindings/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            renderGlobalResult(result);
            setStatus("Agent 绑定已删除");
            resetLlmBindingForm();
            await loadAiConfig();
        }
        catch (error) {
            renderGlobalResultError("删除 Agent 绑定失败", error);
            showError("删除 Agent 绑定失败", error);
        }
    }

    function editLlmConnection(id) {
        const item = state.llmConnections.find(function (entry) {
            return String(entry.id) === String(id);
        });
        if (!item) {
            return;
        }
        document.getElementById("llm-connection-id").value = item.id;
        document.getElementById("llm-connection-code").value = item.connectionCode || "";
        document.getElementById("llm-provider-type").value = item.providerType || "openai";
        document.getElementById("llm-base-url").value = item.baseUrl || "";
        document.getElementById("llm-api-key").value = "";
        document.getElementById("llm-connection-enabled").checked = !!item.enabled;
    }

    function editLlmModel(id) {
        const item = state.llmModels.find(function (entry) {
            return String(entry.id) === String(id);
        });
        if (!item) {
            return;
        }
        document.getElementById("llm-model-id").value = item.id;
        document.getElementById("llm-model-connection-id").value = String(item.connectionId || "");
        document.getElementById("llm-model-name").value = item.modelName || "";
        document.getElementById("llm-model-kind").value = item.modelKind || "CHAT";
        document.getElementById("llm-model-expected-dimensions").value = item.expectedDimensions || "";
        document.getElementById("llm-model-enabled").checked = !!item.enabled;
        syncLlmModelKindForm();
    }

    function editLlmBinding(id) {
        const item = state.llmBindings.find(function (entry) {
            return String(entry.id) === String(id);
        });
        if (!item) {
            return;
        }
        document.getElementById("llm-binding-id").value = item.id;
        document.getElementById("llm-binding-scene").value = item.scene || "compile";
        renderLlmBindingRoleOptions(item.agentRole || "writer");
        renderLlmModelOptions(item.primaryModelProfileId);
        document.getElementById("llm-binding-primary-model-id").value = String(item.primaryModelProfileId || "");
        document.getElementById("llm-binding-enabled").checked = !!item.enabled;
    }

    function resetLlmConnectionForm() {
        document.getElementById("llm-connection-id").value = "";
        document.getElementById("llm-connection-code").value = "";
        document.getElementById("llm-provider-type").value = "openai";
        document.getElementById("llm-base-url").value = "";
        document.getElementById("llm-api-key").value = "";
        document.getElementById("llm-connection-enabled").checked = true;
    }

    function resetLlmModelForm() {
        document.getElementById("llm-model-id").value = "";
        document.getElementById("llm-model-name").value = "";
        document.getElementById("llm-model-kind").value = "CHAT";
        document.getElementById("llm-model-expected-dimensions").value = "";
        document.getElementById("llm-model-enabled").checked = true;
        renderLlmConnectionOptions();
        syncLlmModelKindForm();
    }

    function resetLlmBindingForm() {
        document.getElementById("llm-binding-id").value = "";
        document.getElementById("llm-binding-scene").value = "compile";
        document.getElementById("llm-binding-enabled").checked = true;
        renderLlmBindingRoleOptions();
        renderLlmModelOptions();
    }

    function syncLlmModelKindForm() {
        const modelKind = document.getElementById("llm-model-kind").value || "CHAT";
        const dimensionsInput = document.getElementById("llm-model-expected-dimensions");
        dimensionsInput.disabled = modelKind !== "EMBEDDING";
        if (modelKind !== "EMBEDDING") {
            dimensionsInput.value = "";
        }
    }

    function renderLlmConnectionOptions() {
        const select = document.getElementById("llm-model-connection-id");
        const currentValue = select.value;
        if (!state.llmConnections || state.llmConnections.length === 0) {
            select.innerHTML = "<option value=''>请先创建连接</option>";
            return;
        }
        select.innerHTML = state.llmConnections.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.connectionCode + " (" + item.providerType + ")")
                    + "</option>";
        }).join("");
        if (currentValue) {
            select.value = currentValue;
        }
    }

    function renderLlmBindingRoleOptions(preferredRole) {
        const sceneSelect = document.getElementById("llm-binding-scene");
        const roleSelect = document.getElementById("llm-binding-agent-role");
        const scene = sceneSelect.value || "compile";
        const roles = LLM_BINDING_ROLE_OPTIONS[scene] || LLM_BINDING_ROLE_OPTIONS.compile;
        roleSelect.innerHTML = roles.map(function (role) {
            return "<option value='" + escapeHtml(role) + "'>" + escapeHtml(role) + "</option>";
        }).join("");
        const nextRole = preferredRole && roles.indexOf(preferredRole) >= 0 ? preferredRole : roles[0];
        roleSelect.value = nextRole;
    }

    function renderLlmModelOptions(selectedId) {
        const select = document.getElementById("llm-binding-primary-model-id");
        const currentValue = String(selectedId || select.value || "");
        const scene = document.getElementById("llm-binding-scene").value || "compile";
        const bindingModels = getBindingModels(scene);
        if (!bindingModels || bindingModels.length === 0) {
            select.innerHTML = "<option value=''>请先创建可绑定的对话模型</option>";
            return;
        }
        select.innerHTML = bindingModels.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.modelName + " / " + resolveConnectionLabel(item.connectionId))
                    + "</option>";
        }).join("");
        if (currentValue) {
            select.value = currentValue;
        }
    }

    function renderLlmConnectionList(items) {
        const container = document.getElementById("llm-connection-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有连接配置，可以先添加一个平台连接。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>连接名称</th><th>Provider</th><th>Base URL</th><th>API Key</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.connectionCode) + "</td>"
                            + "<td>" + escapeHtml(item.providerType) + "</td>"
                            + "<td>" + escapeHtml(item.baseUrl) + "</td>"
                            + "<td>" + escapeHtml(item.apiKeyMask || "未设置") + "</td>"
                            + "<td>" + renderBadge(item.enabled ? "ACTIVE" : "ARCHIVED") + "</td>"
                            + "<td class='card-actions'>"
                            + "<button class='ghost-btn' data-edit-llm-connection='" + escapeHtml(String(item.id)) + "' type='button'>编辑</button>"
                            + "<button class='warn-btn' data-delete-llm-connection='" + escapeHtml(String(item.id)) + "' type='button'>删除</button>"
                            + "</td>"
                            + "</tr>";
                }).join("")
                + "</tbody></table>";
        container.querySelectorAll("[data-edit-llm-connection]").forEach(function (button) {
            button.addEventListener("click", function () {
                editLlmConnection(button.dataset.editLlmConnection);
            });
        });
        container.querySelectorAll("[data-delete-llm-connection]").forEach(function (button) {
            button.addEventListener("click", function () {
                deleteLlmConnection(button.dataset.deleteLlmConnection);
            });
        });
    }

    function renderLlmModelList(items) {
        const container = document.getElementById("llm-model-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有模型，可以先为某个连接添加模型。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>模型名称</th><th>类型</th><th>连接</th><th>维度</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.modelName) + "</td>"
                            + "<td>" + escapeHtml(item.modelKind || "CHAT") + "</td>"
                            + "<td>" + escapeHtml(resolveConnectionLabel(item.connectionId)) + "</td>"
                            + "<td>" + escapeHtml(String(item.expectedDimensions || "-")) + "</td>"
                            + "<td>" + renderBadge(item.enabled ? "ACTIVE" : "ARCHIVED") + "</td>"
                            + "<td class='card-actions'>"
                            + "<button class='ghost-btn' data-edit-llm-model='" + escapeHtml(String(item.id)) + "' type='button'>编辑</button>"
                            + "<button class='warn-btn' data-delete-llm-model='" + escapeHtml(String(item.id)) + "' type='button'>删除</button>"
                            + "</td>"
                            + "</tr>";
                }).join("")
                + "</tbody></table>";
        container.querySelectorAll("[data-edit-llm-model]").forEach(function (button) {
            button.addEventListener("click", function () {
                editLlmModel(button.dataset.editLlmModel);
            });
        });
        container.querySelectorAll("[data-delete-llm-model]").forEach(function (button) {
            button.addEventListener("click", function () {
                deleteLlmModel(button.dataset.deleteLlmModel);
            });
        });
    }

    function renderLlmBindingList(items) {
        const container = document.getElementById("llm-binding-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有 Agent 绑定，可以开始为不同角色选择模型。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>场景</th><th>Agent 角色</th><th>绑定模型</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.scene) + "</td>"
                            + "<td>" + escapeHtml(item.agentRole) + "</td>"
                            + "<td>" + escapeHtml(resolveModelLabel(item.primaryModelProfileId)) + "</td>"
                            + "<td>" + renderBadge(item.enabled ? "ACTIVE" : "ARCHIVED") + "</td>"
                            + "<td class='card-actions'>"
                            + "<button class='ghost-btn' data-edit-llm-binding='" + escapeHtml(String(item.id)) + "' type='button'>编辑</button>"
                            + "<button class='warn-btn' data-delete-llm-binding='" + escapeHtml(String(item.id)) + "' type='button'>删除</button>"
                            + "</td>"
                            + "</tr>";
                }).join("")
                + "</tbody></table>";
        container.querySelectorAll("[data-edit-llm-binding]").forEach(function (button) {
            button.addEventListener("click", function () {
                editLlmBinding(button.dataset.editLlmBinding);
            });
        });
        container.querySelectorAll("[data-delete-llm-binding]").forEach(function (button) {
            button.addEventListener("click", function () {
                deleteLlmBinding(button.dataset.deleteLlmBinding);
            });
        });
    }

    function getBindingModels(scene) {
        const roleOptions = LLM_BINDING_ROLE_OPTIONS[scene] || LLM_BINDING_ROLE_OPTIONS.compile;
        return (state.llmModels || []).filter(function (item) {
            return item.enabled && item.modelKind !== "EMBEDDING" && roleOptions.length > 0;
        });
    }

    function resolveConnectionLabel(connectionId) {
        const item = state.llmConnections.find(function (entry) {
            return String(entry.id) === String(connectionId);
        });
        return item ? item.connectionCode : String(connectionId || "-");
    }

    function resolveModelLabel(modelId) {
        const item = state.llmModels.find(function (entry) {
            return String(entry.id) === String(modelId);
        });
        if (!item) {
            return String(modelId || "-");
        }
        return item.modelName + " / " + resolveConnectionLabel(item.connectionId);
    }

    function renderBadge(value) {
        const normalized = (value || "").toUpperCase();
        let className = "badge";
        if (normalized === "ACTIVE") {
            className += " success";
        }
        else if (normalized === "ARCHIVED") {
            className += " danger";
        }
        else if (normalized) {
            className += " warning";
        }
        return "<span class='" + className + "'>" + escapeHtml(getBadgeLabel(value)) + "</span>";
    }

    function getBadgeLabel(value) {
        const normalized = (value || "").toUpperCase();
        if (normalized === "ACTIVE") {
            return "启用中";
        }
        if (normalized === "ARCHIVED") {
            return "已停用";
        }
        return value || "-";
    }

    function parseOptionalInteger(value) {
        const text = String(value || "").trim();
        if (!text) {
            return null;
        }
        const parsed = Number(text);
        return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
    }

    async function fetchJson(url, options) {
        const requestOptions = options || {};
        const headers = {"Content-Type": "application/json"};
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

    function renderGlobalResult(result) {
        document.getElementById("global-result").textContent = JSON.stringify(result, null, 2);
    }

    function renderGlobalResultError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        document.getElementById("global-result").textContent = prefix + "：\n" + message;
    }

    function setStatus(message) {
        document.getElementById("global-status").textContent = message;
    }

    function showError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        setStatus(prefix + "：" + message);
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
