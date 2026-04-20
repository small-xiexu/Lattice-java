(function () {
    // 负责管理员设置页的数据加载与交互，不承载开发者接入页逻辑。
    const LLM_BINDING_ROLE_OPTIONS = {
        compile: [
            {
                value: "writer",
                label: "内容生成",
                stage: "第 1 步",
                summary: "按资料生成知识初稿",
                description: "负责首版入库内容，先把资料整理成可入库的知识条目。"
            },
            {
                value: "reviewer",
                label: "内容复核",
                stage: "第 2 步",
                summary: "检查事实和覆盖范围",
                description: "核对内容是否忠于资料，是否漏掉关键信息。"
            },
            {
                value: "fixer",
                label: "自动修正",
                stage: "第 3 步",
                summary: "按复核意见回写",
                description: "根据问题清单修正文稿，让内容回到可入库状态。"
            }
        ],
        query: [
            {
                value: "answer",
                label: "直接回答",
                stage: "第 1 步",
                summary: "基于检索结果生成回答",
                description: "负责产出首版回答，是问答链路里的主输出模型。"
            },
            {
                value: "reviewer",
                label: "答案复核",
                stage: "第 2 步",
                summary: "检查回答与证据是否一致",
                description: "发现答偏、答漏或引用不稳的地方。"
            },
            {
                value: "rewrite",
                label: "答案润色",
                stage: "第 3 步",
                summary: "按复核意见重写回答",
                description: "在需要时生成更稳妥的最终答复。"
            }
        ]
    };

    const LLM_SCENE_LABELS = {
        compile: "知识入库",
        query: "知识问答"
    };

    const state = {
        llmConnections: [],
        llmModels: [],
        llmBindings: [],
        llmBindingFlowExpanded: false,
        documentParseConnections: [],
        documentParseSettings: null,
        helpState: {
            lastSaveType: "",
            lastSaveSucceeded: false,
            lastTestType: "",
            lastTestFailed: false,
            bindingRecentlyChanged: false,
            documentParseTestFailed: false
        }
    };

    document.addEventListener("DOMContentLoaded", function () {
        if (!isSettingsEntryActive()) {
            return;
        }
        bindEvents();
        loadAiConfig(false);
    });

    function isSettingsEntryActive() {
        if (!window.AdminSections || typeof window.AdminSections.getActiveEntry !== "function") {
            return true;
        }
        return window.AdminSections.getActiveEntry() === "settings";
    }

    function bindEvents() {
        document.getElementById("refresh-ai").addEventListener("click", function () {
            loadAiConfig(true);
        });
        document.getElementById("test-llm-connection").addEventListener("click", testLlmConnection);
        document.getElementById("save-llm-connection").addEventListener("click", saveLlmConnection);
        document.getElementById("reset-llm-connection").addEventListener("click", resetLlmConnectionForm);
        document.getElementById("test-llm-model").addEventListener("click", testLlmModel);
        document.getElementById("save-llm-model").addEventListener("click", saveLlmModel);
        document.getElementById("reset-llm-model").addEventListener("click", resetLlmModelForm);
        document.getElementById("llm-model-kind").addEventListener("change", syncLlmModelKindForm);
        document.getElementById("save-llm-binding").addEventListener("click", saveLlmBinding);
        document.getElementById("reset-llm-binding").addEventListener("click", resetLlmBindingForm);
        document.getElementById("llm-binding-scene").addEventListener("change", function () {
            renderLlmBindingRoleOptions();
            renderLlmModelOptions();
            renderLlmBindingRoleGuide();
        });
        document.getElementById("llm-binding-agent-role").addEventListener("change", function () {
            renderLlmBindingRoleGuide();
        });
        document.getElementById("llm-binding-role-guide").addEventListener("click", function (event) {
            const toggle = event.target.closest("[data-role-guide-toggle]");
            if (!toggle) {
                return;
            }
            state.llmBindingFlowExpanded = !state.llmBindingFlowExpanded;
            renderLlmBindingRoleGuide();
        });
        document.getElementById("test-document-parse-connection").addEventListener("click", testDocumentParseConnection);
        document.getElementById("save-document-parse-connection").addEventListener("click", saveDocumentParseConnection);
        document.getElementById("reset-document-parse-connection").addEventListener("click", resetDocumentParseConnectionForm);
        document.getElementById("save-document-parse-settings").addEventListener("click", saveDocumentParseSettings);
        document.getElementById("document-parse-provider-type").addEventListener("change", syncDocumentParseEndpointSuggestion);
        document.addEventListener("click", handleSettingsHelpActionClick);
    }

    async function loadAiConfig(showSuccessFeedback) {
        if (showSuccessFeedback) {
            setStatus("正在刷新管理员设置...", "info");
        }
        try {
            const responses = await Promise.all([
                fetchJson("/api/v1/admin/llm/connections"),
                fetchJson("/api/v1/admin/llm/models"),
                fetchJson("/api/v1/admin/llm/bindings"),
                fetchJson("/api/v1/admin/document-parse/connections"),
                fetchJson("/api/v1/admin/document-parse/settings")
            ]);
            state.llmConnections = responses[0].items || [];
            state.llmModels = responses[1].items || [];
            state.llmBindings = responses[2].items || [];
            state.documentParseConnections = responses[3].items || [];
            state.documentParseSettings = responses[4] || null;
            renderLlmConnectionOptions();
            renderLlmBindingRoleOptions();
            renderLlmModelOptions();
            renderLlmBindingRoleGuide();
            renderLlmConnectionList(state.llmConnections);
            renderLlmModelList(state.llmModels);
            renderLlmBindingList(state.llmBindings);
            renderDocumentParseConnectionOptions();
            renderDocumentParseCleanupModelOptions();
            renderDocumentParseSettingsForm();
            renderDocumentParseConnectionList(state.documentParseConnections);
            renderDocumentParseSettingsSummary(state.documentParseSettings);
            syncLlmModelKindForm();
            syncDocumentParseEndpointSuggestion();
            renderSettingsHelpCard();
            syncSettingsFaqOpenState();
            if (showSuccessFeedback) {
                setStatus("管理员设置已刷新", "success");
            }
        }
        catch (error) {
            showError("加载管理员设置失败", error);
        }
    }

    async function testLlmConnection() {
        const button = document.getElementById("test-llm-connection");
        const connectionId = parseOptionalInteger(document.getElementById("llm-connection-id").value);
        const providerType = document.getElementById("llm-provider-type").value;
        const baseUrl = document.getElementById("llm-base-url").value.trim();
        const apiKey = document.getElementById("llm-api-key").value.trim();
        if (!baseUrl && !connectionId) {
            setStatus("请先填写接口地址，再测试连接", "warning");
            return;
        }
        button.disabled = true;
        setStatus("正在测试连接...", "info");
        try {
            const result = await fetchJson("/api/v1/admin/llm/connections/test", {
                method: "POST",
                body: JSON.stringify({
                    connectionId: connectionId,
                    providerType: providerType,
                    baseUrl: baseUrl,
                    apiKey: apiKey
                })
            });
            updateSettingsHelpState({
                lastTestType: "llm-connection",
                lastTestFailed: !result.success,
                documentParseTestFailed: false
            });
            setStatus(result.message || "连接测试已完成", result.success ? "success" : "danger");
        }
        catch (error) {
            updateSettingsHelpState({
                lastTestType: "llm-connection",
                lastTestFailed: true,
                documentParseTestFailed: false
            });
            showError("测试连接失败", error);
        }
        finally {
            button.disabled = false;
        }
    }

    async function testLlmModel() {
        const button = document.getElementById("test-llm-model");
        const payload = {
            modelId: parseOptionalInteger(document.getElementById("llm-model-id").value),
            connectionId: parseOptionalInteger(document.getElementById("llm-model-connection-id").value),
            modelName: document.getElementById("llm-model-name").value.trim(),
            modelKind: document.getElementById("llm-model-kind").value,
            expectedDimensions: parseOptionalInteger(document.getElementById("llm-model-expected-dimensions").value)
        };
        if (!payload.connectionId) {
            setStatus("请先选择所属连接，再测试模型", "warning");
            return;
        }
        if (!payload.modelName) {
            setStatus("请先填写模型名称，再测试模型", "warning");
            return;
        }
        if (payload.modelKind === "EMBEDDING" && (!payload.expectedDimensions || payload.expectedDimensions <= 0)) {
            setStatus("向量模型测试前请先填写正整数维度", "warning");
            return;
        }
        if (payload.modelKind !== "EMBEDDING") {
            payload.expectedDimensions = null;
        }
        button.disabled = true;
        setStatus("正在测试模型...", "info");
        try {
            const result = await fetchJson("/api/v1/admin/llm/models/test", {
                method: "POST",
                body: JSON.stringify(payload)
            });
            updateSettingsHelpState({
                lastTestType: "llm-model",
                lastTestFailed: !result.success,
                documentParseTestFailed: false
            });
            setStatus(result.message || "模型测试已完成", result.success ? "success" : "danger");
        }
        catch (error) {
            updateSettingsHelpState({
                lastTestType: "llm-model",
                lastTestFailed: true,
                documentParseTestFailed: false
            });
            showError("测试模型失败", error);
        }
        finally {
            button.disabled = false;
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
            setStatus("请填写连接名称和接口地址", "warning");
            return;
        }
        if (!id && !payload.apiKey) {
            setStatus("新增连接时必须填写 API Key", "warning");
            return;
        }
        try {
            await fetchJson(id
                    ? "/api/v1/admin/llm/connections/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/connections", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            updateSettingsHelpState({
                lastSaveType: "llm-connection",
                lastSaveSucceeded: true,
                lastTestFailed: false,
                documentParseTestFailed: false
            });
            setStatus(id ? "连接已更新" : "连接已创建", "success");
            resetLlmConnectionForm();
            await loadAiConfig(false);
        }
        catch (error) {
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
            setStatus("请先选择连接并填写模型名称", "warning");
            return;
        }
        if (payload.modelKind === "EMBEDDING" && (!payload.expectedDimensions || payload.expectedDimensions <= 0)) {
            setStatus("向量模型必须填写正整数维度", "warning");
            return;
        }
        if (payload.modelKind !== "EMBEDDING") {
            payload.expectedDimensions = null;
        }
        try {
            await fetchJson(id
                    ? "/api/v1/admin/llm/models/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/models", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            updateSettingsHelpState({
                lastSaveType: "llm-model",
                lastSaveSucceeded: true,
                lastTestFailed: false,
                documentParseTestFailed: false
            });
            setStatus(id ? "模型已更新" : "模型已创建", "success");
            resetLlmModelForm();
            await loadAiConfig(false);
        }
        catch (error) {
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
            setStatus("请先选择绑定模型", "warning");
            return;
        }
        try {
            await fetchJson(id
                    ? "/api/v1/admin/llm/bindings/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/bindings", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            updateSettingsHelpState({
                lastSaveType: "llm-binding",
                lastSaveSucceeded: true,
                lastTestFailed: false,
                documentParseTestFailed: false,
                bindingRecentlyChanged: true
            });
            setStatus(id ? "角色绑定已更新" : "角色绑定已创建", "success");
            resetLlmBindingForm();
            await loadAiConfig(false);
        }
        catch (error) {
            showError("保存角色绑定失败", error);
        }
    }

    async function testDocumentParseConnection() {
        const button = document.getElementById("test-document-parse-connection");
        const connectionId = parseOptionalInteger(document.getElementById("document-parse-connection-id").value);
        const providerType = document.getElementById("document-parse-provider-type").value;
        const baseUrl = document.getElementById("document-parse-base-url").value.trim();
        const endpointPath = document.getElementById("document-parse-endpoint-path").value.trim();
        const credential = document.getElementById("document-parse-credential").value.trim();
        if (!baseUrl && !connectionId) {
            setStatus("请先填写识别服务接口地址，再测试连接", "warning");
            return;
        }
        button.disabled = true;
        setStatus("正在测试识别服务连接...", "info");
        try {
            const result = await fetchJson("/api/v1/admin/document-parse/connections/test", {
                method: "POST",
                body: JSON.stringify({
                    connectionId: connectionId,
                    providerType: providerType,
                    baseUrl: baseUrl,
                    endpointPath: endpointPath,
                    credential: credential
                })
            });
            updateSettingsHelpState({
                lastTestType: "document-parse",
                lastTestFailed: !result.success,
                documentParseTestFailed: !result.success
            });
            setStatus(result.message || "识别服务连接测试已完成", result.success ? "success" : "danger");
        }
        catch (error) {
            updateSettingsHelpState({
                lastTestType: "document-parse",
                lastTestFailed: true,
                documentParseTestFailed: true
            });
            showError("测试识别服务连接失败", error);
        }
        finally {
            button.disabled = false;
        }
    }

    async function saveDocumentParseConnection() {
        const id = document.getElementById("document-parse-connection-id").value.trim();
        const payload = {
            connectionCode: document.getElementById("document-parse-connection-code").value.trim(),
            providerType: document.getElementById("document-parse-provider-type").value,
            baseUrl: document.getElementById("document-parse-base-url").value.trim(),
            endpointPath: document.getElementById("document-parse-endpoint-path").value.trim(),
            credential: document.getElementById("document-parse-credential").value.trim(),
            enabled: document.getElementById("document-parse-connection-enabled").checked
        };
        if (!payload.connectionCode || !payload.baseUrl) {
            setStatus("请填写识别服务连接名称和接口地址", "warning");
            return;
        }
        if (!payload.endpointPath) {
            payload.endpointPath = getDocumentParseDefaultEndpoint(payload.providerType);
        }
        if (!id && !payload.credential) {
            setStatus("新增识别服务连接时必须填写访问凭证", "warning");
            return;
        }
        try {
            await fetchJson(id
                    ? "/api/v1/admin/document-parse/connections/" + encodeURIComponent(id)
                    : "/api/v1/admin/document-parse/connections", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            updateSettingsHelpState({
                lastSaveType: "document-parse-connection",
                lastSaveSucceeded: true,
                lastTestFailed: false,
                documentParseTestFailed: false
            });
            setStatus(id ? "识别服务连接已更新" : "识别服务连接已创建", "success");
            resetDocumentParseConnectionForm();
            await loadAiConfig(false);
        }
        catch (error) {
            showError("保存识别服务连接失败", error);
        }
    }

    async function saveDocumentParseSettings() {
        const payload = {
            defaultConnectionId: parseOptionalInteger(document.getElementById("document-parse-default-connection-id").value),
            imageOcrEnabled: document.getElementById("document-parse-image-ocr-enabled").checked,
            scannedPdfOcrEnabled: document.getElementById("document-parse-scanned-pdf-ocr-enabled").checked,
            cleanupEnabled: document.getElementById("document-parse-cleanup-enabled").checked,
            cleanupModelProfileId: parseOptionalInteger(
                    document.getElementById("document-parse-cleanup-model-profile-id").value
            )
        };
        try {
            await fetchJson("/api/v1/admin/document-parse/settings", {
                method: "PUT",
                body: JSON.stringify(payload)
            });
            updateSettingsHelpState({
                lastSaveType: "document-parse-settings",
                lastSaveSucceeded: true,
                lastTestFailed: false,
                documentParseTestFailed: false
            });
            setStatus("识别策略已更新", "success");
            await loadAiConfig(false);
        }
        catch (error) {
            showError("保存识别策略失败", error);
        }
    }

    async function deleteLlmConnection(id) {
        if (!window.confirm("将删除该连接，确认继续吗？")) {
            return;
        }
        try {
            await fetchJson("/api/v1/admin/llm/connections/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            setStatus("连接已删除", "success");
            resetLlmConnectionForm();
            await loadAiConfig(false);
        }
        catch (error) {
            showError("删除连接失败", error);
        }
    }

    async function deleteLlmModel(id) {
        if (!window.confirm("将删除该模型，确认继续吗？")) {
            return;
        }
        try {
            await fetchJson("/api/v1/admin/llm/models/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            setStatus("模型已删除", "success");
            resetLlmModelForm();
            await loadAiConfig(false);
        }
        catch (error) {
            showError("删除模型失败", error);
        }
    }

    async function deleteLlmBinding(id) {
        if (!window.confirm("将删除该角色绑定，确认继续吗？")) {
            return;
        }
        try {
            await fetchJson("/api/v1/admin/llm/bindings/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            setStatus("角色绑定已删除", "success");
            resetLlmBindingForm();
            await loadAiConfig(false);
        }
        catch (error) {
            showError("删除角色绑定失败", error);
        }
    }

    async function deleteDocumentParseConnection(id) {
        if (!window.confirm("将删除该识别服务连接，确认继续吗？")) {
            return;
        }
        try {
            await fetchJson("/api/v1/admin/document-parse/connections/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            setStatus("识别服务连接已删除", "success");
            resetDocumentParseConnectionForm();
            await loadAiConfig(false);
        }
        catch (error) {
            showError("删除识别服务连接失败", error);
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
        document.getElementById("llm-provider-type").value = normalizeProviderTypeForForm(item.providerType);
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
        renderLlmBindingRoleGuide(item.agentRole || "writer");
    }

    function editDocumentParseConnection(id) {
        const item = state.documentParseConnections.find(function (entry) {
            return String(entry.id) === String(id);
        });
        if (!item) {
            return;
        }
        document.getElementById("document-parse-connection-id").value = item.id;
        document.getElementById("document-parse-connection-code").value = item.connectionCode || "";
        document.getElementById("document-parse-provider-type").value = item.providerType || "tencent_ocr";
        document.getElementById("document-parse-base-url").value = item.baseUrl || "";
        document.getElementById("document-parse-endpoint-path").value = item.endpointPath || "";
        document.getElementById("document-parse-credential").value = "";
        document.getElementById("document-parse-connection-enabled").checked = !!item.enabled;
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
        renderLlmBindingRoleGuide();
    }

    function resetDocumentParseConnectionForm() {
        document.getElementById("document-parse-connection-id").value = "";
        document.getElementById("document-parse-connection-code").value = "";
        document.getElementById("document-parse-provider-type").value = "tencent_ocr";
        document.getElementById("document-parse-base-url").value = "";
        document.getElementById("document-parse-endpoint-path").value = getDocumentParseDefaultEndpoint("tencent_ocr");
        document.getElementById("document-parse-credential").value = "";
        document.getElementById("document-parse-connection-enabled").checked = true;
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
                    + escapeHtml(item.connectionCode + " (" + getProviderDisplayLabel(item.providerType) + ")")
                    + "</option>";
        }).join("");
        if (currentValue) {
            select.value = currentValue;
        }
    }

    function renderDocumentParseConnectionOptions() {
        const select = document.getElementById("document-parse-default-connection-id");
        const currentValue = select.value;
        let options = "<option value=''>未设置默认连接</option>";
        if (state.documentParseConnections && state.documentParseConnections.length > 0) {
            options += state.documentParseConnections.map(function (item) {
                return "<option value='" + escapeHtml(String(item.id)) + "'>"
                        + escapeHtml(item.connectionCode + " (" + getDocumentParseProviderLabel(item.providerType) + ")")
                        + "</option>";
            }).join("");
        }
        select.innerHTML = options;
        if (currentValue) {
            select.value = currentValue;
        }
    }

    function renderDocumentParseCleanupModelOptions() {
        const select = document.getElementById("document-parse-cleanup-model-profile-id");
        const currentValue = select.value;
        const models = getBindingModels();
        let options = "<option value=''>不启用后整理模型</option>";
        options += models.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.modelName + " / " + resolveConnectionLabel(item.connectionId))
                    + "</option>";
        }).join("");
        select.innerHTML = options;
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
            return "<option value='" + escapeHtml(role.value) + "'>" + escapeHtml(role.label) + "</option>";
        }).join("");
        const nextRole = preferredRole && roles.some(function (role) {
            return role.value === preferredRole;
        }) ? preferredRole : roles[0].value;
        roleSelect.value = nextRole;
    }

    function renderLlmModelOptions(selectedId) {
        const select = document.getElementById("llm-binding-primary-model-id");
        const currentValue = String(selectedId || select.value || "");
        const bindingModels = getBindingModels();
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

    function renderLlmBindingRoleGuide(preferredRole) {
        const container = document.getElementById("llm-binding-role-guide");
        if (!container) {
            return;
        }
        const scene = document.getElementById("llm-binding-scene").value || "compile";
        const selectedRole = preferredRole || document.getElementById("llm-binding-agent-role").value;
        const roles = LLM_BINDING_ROLE_OPTIONS[scene] || [];
        const currentRole = getBindingRoleDefinition(scene, selectedRole) || roles[0];
        if (!currentRole) {
            container.innerHTML = "";
            return;
        }
        container.innerHTML = "<div class='role-guide-header'>"
                + "<div>"
                + "<div class='role-guide-title'>当前角色说明</div>"
                + "<p class='role-guide-hint'>先完成当前角色绑定，需要时再展开完整流程。</p>"
                + "</div>"
                + "<button class='ghost-link subtle-link role-guide-toggle' type='button' data-role-guide-toggle='toggle' aria-expanded='"
                + (state.llmBindingFlowExpanded ? "true" : "false") + "'>"
                + escapeHtml(state.llmBindingFlowExpanded ? "收起完整流程" : "查看完整流程")
                + "</button>"
                + "</div>"
                + "<div class='role-guide-focus'>"
                + "<div class='role-guide-stage'>" + escapeHtml(currentRole.stage) + "</div>"
                + "<div class='role-guide-name'>" + escapeHtml(currentRole.label) + "</div>"
                + "<div class='role-guide-summary'>" + escapeHtml(currentRole.summary) + "</div>"
                + "<p class='role-guide-copy'>" + escapeHtml(currentRole.description) + "</p>"
                + "</div>"
                + renderLlmBindingRoleFlow(scene, roles, currentRole.value);
    }

    function renderDocumentParseSettingsForm() {
        const settings = state.documentParseSettings || {
            defaultConnectionId: null,
            imageOcrEnabled: false,
            scannedPdfOcrEnabled: false,
            cleanupEnabled: false,
            cleanupModelProfileId: null
        };
        document.getElementById("document-parse-default-connection-id").value = String(
                settings.defaultConnectionId || ""
        );
        document.getElementById("document-parse-image-ocr-enabled").checked = !!settings.imageOcrEnabled;
        document.getElementById("document-parse-scanned-pdf-ocr-enabled").checked = !!settings.scannedPdfOcrEnabled;
        document.getElementById("document-parse-cleanup-enabled").checked = !!settings.cleanupEnabled;
        document.getElementById("document-parse-cleanup-model-profile-id").value = String(
                settings.cleanupModelProfileId || ""
        );
    }

    function renderLlmBindingRoleFlow(scene, roles, selectedRole) {
        if (!state.llmBindingFlowExpanded) {
            return "";
        }
        return "<div class='role-flow-panel'>"
                + "<div class='role-flow-title'>" + escapeHtml(getBindingSceneLabel(scene)) + "完整流程</div>"
                + "<div class='role-flow-list'>"
                + roles.map(function (role) {
                    const activeClass = role.value === selectedRole ? " active" : "";
                    return "<div class='role-flow-item" + activeClass + "'>"
                            + "<div class='role-flow-order'>" + escapeHtml(role.stage) + "</div>"
                            + "<div class='role-flow-name'>" + escapeHtml(role.label) + "</div>"
                            + "<p class='role-flow-copy'>" + escapeHtml(role.summary) + "</p>"
                            + "</div>";
                }).join("")
                + "</div>"
                + "</div>";
    }

    function renderLlmConnectionList(items) {
        const container = document.getElementById("llm-connection-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有连接配置，可以先添加一个平台连接。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>连接名称</th><th>接入类型</th><th>Base URL</th><th>API Key</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.connectionCode) + "</td>"
                            + "<td>" + escapeHtml(getProviderDisplayLabel(item.providerType)) + "</td>"
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
                            + "<td>" + escapeHtml(getModelKindLabel(item.modelKind || "CHAT")) + "</td>"
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
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有角色绑定，可以先为不同流程角色选择模型。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>绑定模型</th><th>使用场景</th><th>执行角色</th><th>流程作用</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(resolveModelLabel(item.primaryModelProfileId)) + "</td>"
                            + "<td>" + escapeHtml(getBindingSceneLabel(item.scene)) + "</td>"
                            + "<td>" + escapeHtml(getBindingRoleLabel(item.scene, item.agentRole)) + "</td>"
                            + "<td>" + escapeHtml(getBindingRoleSummary(item.scene, item.agentRole)) + "</td>"
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

    function renderDocumentParseConnectionList(items) {
        const container = document.getElementById("document-parse-connection-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有识别服务连接，可以先添加一个 OCR / Document AI 供应商。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>连接名称</th><th>供应商</th><th>Base URL</th><th>接口路径</th><th>凭证</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.connectionCode) + "</td>"
                            + "<td>" + escapeHtml(getDocumentParseProviderLabel(item.providerType)) + "</td>"
                            + "<td>" + escapeHtml(item.baseUrl) + "</td>"
                            + "<td>" + escapeHtml(item.endpointPath || "-") + "</td>"
                            + "<td>" + escapeHtml(item.credentialMask || "未设置") + "</td>"
                            + "<td>" + renderBadge(item.enabled ? "ACTIVE" : "ARCHIVED") + "</td>"
                            + "<td class='card-actions'>"
                            + "<button class='ghost-btn' data-edit-document-parse-connection='" + escapeHtml(String(item.id)) + "' type='button'>编辑</button>"
                            + "<button class='warn-btn' data-delete-document-parse-connection='" + escapeHtml(String(item.id)) + "' type='button'>删除</button>"
                            + "</td>"
                            + "</tr>";
                }).join("")
                + "</tbody></table>";
        container.querySelectorAll("[data-edit-document-parse-connection]").forEach(function (button) {
            button.addEventListener("click", function () {
                editDocumentParseConnection(button.dataset.editDocumentParseConnection);
            });
        });
        container.querySelectorAll("[data-delete-document-parse-connection]").forEach(function (button) {
            button.addEventListener("click", function () {
                deleteDocumentParseConnection(button.dataset.deleteDocumentParseConnection);
            });
        });
    }

    function renderDocumentParseSettingsSummary(settings) {
        const container = document.getElementById("document-parse-settings-summary");
        const effectiveSettings = settings || {};
        container.innerHTML = "<div class='job-card'>"
                + "<p class='item-summary'>默认识别连接："
                + escapeHtml(resolveDocumentParseConnectionLabel(effectiveSettings.defaultConnectionId))
                + "</p>"
                + "<p class='job-meta-line'>图片识别（OCR）："
                + escapeHtml(effectiveSettings.imageOcrEnabled ? "启用" : "关闭")
                + "｜扫描 PDF 识别（OCR）："
                + escapeHtml(effectiveSettings.scannedPdfOcrEnabled ? "启用" : "关闭")
                + "｜识别后整理："
                + escapeHtml(effectiveSettings.cleanupEnabled ? "启用" : "关闭")
                + "</p>"
                + "<p class='job-meta-line'>识别后整理模型："
                + escapeHtml(resolveModelLabel(effectiveSettings.cleanupModelProfileId))
                + "</p>"
                + "</div>";
    }

    function getBindingModels() {
        return (state.llmModels || []).filter(function (item) {
            return item.enabled && item.modelKind !== "EMBEDDING";
        });
    }

    function resolveConnectionLabel(connectionId) {
        const item = state.llmConnections.find(function (entry) {
            return String(entry.id) === String(connectionId);
        });
        return item ? item.connectionCode : String(connectionId || "-");
    }

    function resolveDocumentParseConnectionLabel(connectionId) {
        const item = state.documentParseConnections.find(function (entry) {
            return String(entry.id) === String(connectionId);
        });
        return item ? item.connectionCode : "未设置";
    }

    function normalizeProviderTypeForForm(providerType) {
        const normalized = String(providerType || "").trim().toLowerCase();
        if (!normalized || normalized === "openai_compatible") {
            return "openai";
        }
        return normalized;
    }

    function getProviderDisplayLabel(providerType) {
        const normalized = String(providerType || "").trim().toLowerCase();
        if (normalized === "anthropic") {
            return "Claude";
        }
        if (normalized === "ollama") {
            return "Ollama";
        }
        if (normalized === "openai" || normalized === "openai_compatible" || !normalized) {
            return "OpenAI";
        }
        return providerType || "-";
    }

    function getDocumentParseProviderLabel(providerType) {
        const normalized = String(providerType || "").trim().toLowerCase();
        if (normalized === "aliyun_ocr") {
            return "阿里云 OCR";
        }
        if (normalized === "google_document_ai") {
            return "Google Document AI";
        }
        if (normalized === "tencent_ocr") {
            return "腾讯云 OCR";
        }
        return providerType || "-";
    }

    function getDocumentParseDefaultEndpoint(providerType) {
        const normalized = String(providerType || "").trim().toLowerCase();
        if (normalized === "aliyun_ocr") {
            return "/ocr/v1/general";
        }
        if (normalized === "google_document_ai") {
            return "/v1/documents:process";
        }
        return "/ocr/v1/general-basic";
    }

    function getModelKindLabel(modelKind) {
        const normalized = String(modelKind || "").trim().toUpperCase();
        if (normalized === "EMBEDDING") {
            return "向量模型";
        }
        return "对话模型";
    }

    function getBindingSceneLabel(scene) {
        return LLM_SCENE_LABELS[scene] || scene || "-";
    }

    function getBindingRoleLabel(scene, role) {
        const matchedRole = getBindingRoleDefinition(scene, role);
        return matchedRole ? matchedRole.label : (role || "-");
    }

    function getBindingRoleSummary(scene, role) {
        const matchedRole = getBindingRoleDefinition(scene, role);
        return matchedRole ? matchedRole.summary : "-";
    }

    function getBindingRoleDefinition(scene, role) {
        const roles = LLM_BINDING_ROLE_OPTIONS[scene] || [];
        return roles.find(function (item) {
            return item.value === role;
        }) || null;
    }

    function resolveModelLabel(modelId) {
        if (!modelId) {
            return "未设置";
        }
        const item = state.llmModels.find(function (entry) {
            return String(entry.id) === String(modelId);
        });
        if (!item) {
            return String(modelId || "-");
        }
        return item.modelName + " / " + resolveConnectionLabel(item.connectionId);
    }

    function syncDocumentParseEndpointSuggestion() {
        const providerType = document.getElementById("document-parse-provider-type").value || "tencent_ocr";
        const endpointInput = document.getElementById("document-parse-endpoint-path");
        if (!endpointInput.value.trim()) {
            endpointInput.value = getDocumentParseDefaultEndpoint(providerType);
        }
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
            throw new Error(buildErrorMessage(response.status, text));
        }
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            return response.json();
        }
        return response.text();
    }

    function buildErrorMessage(status, rawText) {
        const fallback = "HTTP " + status;
        const text = String(rawText || "").trim();
        if (!text) {
            return fallback;
        }
        try {
            const payload = JSON.parse(text);
            if (payload.message) {
                return payload.message;
            }
            if (payload.error && payload.path) {
                return fallback + " " + payload.error + "（" + payload.path + "）";
            }
            if (payload.error) {
                return fallback + " " + payload.error;
            }
            if (payload.path) {
                return fallback + "（" + payload.path + "）";
            }
        }
        catch (error) {
            // 原始响应不是 JSON 时，直接走文本兜底。
        }
        return text.length > 180 ? text.slice(0, 180) + "..." : text;
    }

    function handleSettingsHelpActionClick(event) {
        const trigger = event.target.closest("[data-settings-help-action]");
        if (!trigger) {
            return;
        }
        const action = trigger.dataset.settingsHelpAction;
        if (action === "go-management") {
            window.location.assign("/admin");
            return;
        }
        if (action === "go-ask") {
            window.location.assign("/admin/ask");
            return;
        }
        if (action === "open-settings-llm"
                || action === "open-settings-parse"
                || action === "open-settings-sources"
                || action === "open-settings-overview") {
            const tabName = action.replace("open-", "");
            if (window.AdminTabs && typeof window.AdminTabs.activate === "function") {
                window.AdminTabs.activate("admin-console", tabName);
            }
        }
    }

    function updateSettingsHelpState(patch) {
        state.helpState = Object.assign({}, state.helpState, patch || {});
        renderSettingsHelpCard();
        syncSettingsFaqOpenState();
    }

    function deriveSettingsHelpState() {
        const helpState = state.helpState || {};
        if (helpState.documentParseTestFailed) {
            return {
                tone: "danger",
                title: "识别服务连接测试失败，先查基础信息",
                description: "先检查 Base URL、接口路径、访问凭证和供应商类型；如果普通文本导入没问题，只是扫描件或图片失败，优先从 OCR / 文档识别排查。",
                actions: [
                    {label: "去 OCR / 文档识别", action: "open-settings-parse", className: "primary-btn"},
                    {label: "返回概览", action: "open-settings-overview", className: "ghost-btn"}
                ],
                faqKey: "parse-test-failed"
            };
        }
        if (helpState.lastTestFailed) {
            return {
                tone: "danger",
                title: "连接或模型测试失败，先查必填项",
                description: "先检查接口地址、凭证和供应商类型是否完整，再判断是不是模型本身不可用。不要一上来就把问题归因为整条链路都坏了。",
                actions: [
                    {label: "去模型与角色", action: "open-settings-llm", className: "primary-btn"},
                    {label: "返回概览", action: "open-settings-overview", className: "ghost-btn"}
                ],
                faqKey: "parse-test-failed"
            };
        }
        if (helpState.bindingRecentlyChanged) {
            return {
                tone: "warning",
                title: "角色绑定已更新，建议用新任务验证",
                description: "角色绑定改动只会影响后续新任务，不会立刻切到正在运行中的同步、编译或问答。改完后请用一个新任务验证，而不是只看保存成功提示。",
                actions: [
                    {label: "去知识库管理", action: "go-management", className: "primary-btn"},
                    {label: "去模型与角色", action: "open-settings-llm", className: "ghost-btn"}
                ],
                faqKey: "binding-change"
            };
        }
        if (helpState.lastSaveSucceeded) {
            return {
                tone: "warning",
                title: "配置已保存，但只会影响新任务",
                description: "如果你刚改完就期待当前任务马上变化，很容易误判成“配置没生效”。先回知识库管理或知识问答，用新任务验证改动是否生效。",
                actions: [
                    {label: "去知识库管理", action: "go-management", className: "primary-btn"},
                    {label: "去知识问答", action: "go-ask", className: "ghost-btn"}
                ],
                faqKey: "saved-not-applied"
            };
        }
        return {
            tone: "info",
            title: "看不懂字段时，先不要改",
            description: "大多数日常问题并不需要动这里。普通用户如果只是想导资料、看结果、直接提问，应该优先回知识库管理或知识问答，而不是停留在后台配置页。",
            actions: [
                {label: "返回知识库管理", action: "go-management", className: "primary-btn"},
                {label: "返回概览", action: "open-settings-overview", className: "ghost-btn"}
            ],
            faqKey: "default-warning"
        };
    }

    function renderSettingsHelpCard() {
        const container = document.getElementById("settings-help-card");
        if (!container) {
            return;
        }
        const helpState = deriveSettingsHelpState();
        container.setAttribute("data-help-tone", helpState.tone || "info");
        container.innerHTML = "<p class='help-card-eyebrow'>配置影响范围</p>"
                + "<h2 class='help-card-title'>" + escapeHtml(helpState.title || "先看这里") + "</h2>"
                + "<p class='help-card-description'>" + escapeHtml(helpState.description || "") + "</p>"
                + renderSettingsHelpActions(helpState.actions || []);
    }

    function renderSettingsHelpActions(actions) {
        if (!actions || actions.length === 0) {
            return "";
        }
        return "<div class='help-action-row'>"
                + actions.map(function (item) {
                    return "<button class='" + escapeHtml(item.className || "ghost-btn")
                            + "' type='button' data-settings-help-action='" + escapeHtml(item.action || "") + "'>"
                            + escapeHtml(item.label || "继续查看")
                            + "</button>";
                }).join("")
                + "</div>";
    }

    function syncSettingsFaqOpenState() {
        const container = document.getElementById("settings-faq-list");
        if (!container) {
            return;
        }
        const helpState = deriveSettingsHelpState();
        const panels = Array.from(container.querySelectorAll("[data-help-faq-key]"));
        if (panels.length === 0) {
            return;
        }
        const target = panels.find(function (panel) {
            return panel.dataset.helpFaqKey === helpState.faqKey;
        }) || panels[0];
        panels.forEach(function (panel) {
            panel.open = panel === target;
        });
    }

    function setStatus(message, tone) {
        const target = document.getElementById("settings-page-notice")
                || document.getElementById("ai-page-feedback");
        if (!target) {
            return;
        }
        target.hidden = !message;
        target.textContent = message || "";
        const baseClass = target.id === "settings-page-notice"
                ? "page-notice"
                : "panel-feedback";
        target.className = baseClass + (tone ? " " + tone : "");
    }

    function showError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        setStatus(prefix + "：" + message, "danger");
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
