(function () {
    const state = {
        selectedConceptId: null,
        llmConnections: [],
        llmModels: [],
        llmBindings: []
    };

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        refreshAll();
    });

    function bindEvents() {
        document.getElementById("refresh-all").addEventListener("click", refreshAll);
        document.getElementById("refresh-overview").addEventListener("click", refreshOverview);
        document.getElementById("search-articles").addEventListener("click", loadArticles);
        document.getElementById("refresh-pending").addEventListener("click", loadPendingQueries);
        document.getElementById("refresh-jobs").addEventListener("click", loadJobs);
        document.getElementById("submit-compile-job").addEventListener("click", submitCompileJob);
        document.getElementById("submit-upload-job").addEventListener("click", uploadAndCompile);
        document.getElementById("rebuild-chunks").addEventListener("click", rebuildChunks);
        document.getElementById("refresh-llm").addEventListener("click", loadLlmConfig);
        document.getElementById("save-llm-connection").addEventListener("click", saveLlmConnection);
        document.getElementById("reset-llm-connection").addEventListener("click", resetLlmConnectionForm);
        document.getElementById("save-llm-model").addEventListener("click", saveLlmModel);
        document.getElementById("reset-llm-model").addEventListener("click", resetLlmModelForm);
        document.getElementById("save-llm-binding").addEventListener("click", saveLlmBinding);
        document.getElementById("reset-llm-binding").addEventListener("click", resetLlmBindingForm);
        document.getElementById("admin-vault-export").addEventListener("click", exportVaultFromAdmin);
        document.getElementById("admin-vault-sync").addEventListener("click", syncVaultFromAdmin);
        document.getElementById("admin-repo-diff").addEventListener("click", loadRepoDiffFromAdmin);
        document.getElementById("admin-repo-rollback").addEventListener("click", rollbackRepoFromAdmin);
        document.getElementById("refresh-governance").addEventListener("click", refreshGovernance);
        document.getElementById("run-lint").addEventListener("click", runLint);
        document.getElementById("run-lint-fix").addEventListener("click", runLintFix);
        document.getElementById("load-quality").addEventListener("click", loadQualityGovernance);
        document.getElementById("load-coverage").addEventListener("click", loadCoverageGovernance);
        document.getElementById("load-inspect").addEventListener("click", loadInspectGovernance);
        document.getElementById("import-inspection-answer").addEventListener("click", importInspectionAnswer);
        document.getElementById("load-article-snapshots").addEventListener("click", loadArticleSnapshots);
        document.getElementById("load-repo-snapshots").addEventListener("click", loadRepoSnapshots);
        document.getElementById("run-link-enhance").addEventListener("click", runLinkEnhance);
        document.getElementById("load-article-history").addEventListener("click", loadArticleHistory);
        document.getElementById("rollback-article").addEventListener("click", rollbackArticleFromGovernance);

        document.querySelectorAll(".tab-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                if (button.dataset.tab) {
                    activateTab(button.dataset.tab);
                    return;
                }
                if (button.dataset.governanceTab) {
                    activateGovernanceTab(button.dataset.governanceTab);
                }
            });
        });

        document.querySelectorAll(".lifecycle-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                transitionLifecycle(button.dataset.action);
            });
        });

        document.addEventListener("click", function (event) {
            const trigger = event.target.closest("[data-jump-tab]");
            if (!trigger) {
                return;
            }
            jumpToTab(trigger.dataset.jumpTab);
        });
    }

    async function refreshAll() {
        setStatus("正在刷新知识库工作台...");
        await Promise.all([
            refreshOverview(),
            loadArticles(),
            loadPendingQueries(),
            loadJobs(),
            loadLlmConfig(),
            refreshGovernance()
        ]);
        setStatus("知识库工作台已刷新");
    }

    async function refreshOverview() {
        try {
            const results = await Promise.all([
                fetchJson("/api/v1/admin/overview"),
                fetchJson("/api/v1/admin/quality?days=7"),
                fetchJson("/api/v1/admin/coverage"),
                fetchJson("/api/v1/admin/omissions")
            ]);
            renderOverview(results[0], results[1], results[2], results[3]);
        }
        catch (error) {
            showError("刷新总览失败", error);
        }
    }

    async function loadArticles() {
        try {
            const query = encodeURIComponent(document.getElementById("article-query").value.trim());
            const lifecycle = encodeURIComponent(document.getElementById("article-lifecycle").value.trim());
            const response = await fetchJson("/api/v1/admin/articles?query=" + query + "&lifecycle=" + lifecycle);
            renderArticleList(response);
            if (!state.selectedConceptId && response.items.length > 0) {
                await loadArticleDetail(response.items[0].conceptId);
            }
            if (state.selectedConceptId && response.items.every(function (item) {
                return item.conceptId !== state.selectedConceptId;
            })) {
                state.selectedConceptId = null;
                clearArticleDetail();
            }
        }
        catch (error) {
            showError("加载文章列表失败", error);
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
            showError("加载文章详情失败", error);
        }
    }

    async function transitionLifecycle(action) {
        if (!state.selectedConceptId) {
            setStatus("请先选择一篇文章");
            return;
        }
        const confirmed = window.confirm("将把文章生命周期切换为“" + formatLifecycleAction(action) + "”，确认继续吗？");
        if (!confirmed) {
            return;
        }
        const reason = window.prompt("请输入生命周期变更原因");
        if (!reason) {
            return;
        }
        const updatedBy = window.prompt("请输入操作人", "admin");
        if (!updatedBy) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/articles/" + encodeURIComponent(state.selectedConceptId)
                    + "/lifecycle/" + encodeURIComponent(action), {
                method: "POST",
                body: JSON.stringify({
                    reason: reason,
                    updatedBy: updatedBy
                })
            });
            renderGlobalResult(result || {
                action: "lifecycle",
                conceptId: state.selectedConceptId,
                target: action
            });
            setStatus("生命周期已更新");
            await Promise.all([loadArticleDetail(state.selectedConceptId), loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderGlobalResultError("更新生命周期失败", error);
            showError("更新生命周期失败", error);
        }
    }

    async function loadPendingQueries() {
        try {
            const response = await fetchJson("/api/v1/admin/pending");
            renderPendingQueries(response);
        }
        catch (error) {
            showError("加载待处理反馈失败", error);
        }
    }

    async function correctPending(queryId) {
        const textarea = document.querySelector("[data-correction-for='" + cssEscape(queryId) + "']");
        const correction = textarea ? textarea.value.trim() : "";
        if (!correction) {
            setStatus("请输入修正内容");
            return;
        }
        const confirmed = window.confirm("将提交该待处理反馈的修正内容，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/pending/" + encodeURIComponent(queryId) + "/correct", {
                method: "POST",
                body: JSON.stringify({correction: correction})
            });
            renderGlobalResult(result || {
                action: "correctPending",
                queryId: queryId,
                correction: correction
            });
            setStatus("修正已提交");
            await loadPendingQueries();
        }
        catch (error) {
            renderGlobalResultError("提交修正失败", error);
            showError("提交修正失败", error);
        }
    }

    async function confirmPending(queryId) {
        const confirmed = window.confirm("将确认该待处理反馈并沉淀为知识反馈记录，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/pending/" + encodeURIComponent(queryId) + "/confirm", {
                method: "POST"
            });
            renderGlobalResult(result || {
                action: "confirmPending",
                queryId: queryId,
                status: "confirmed"
            });
            setStatus("待处理反馈已确认");
            await Promise.all([loadPendingQueries(), refreshOverview()]);
        }
        catch (error) {
            renderGlobalResultError("确认失败", error);
            showError("确认失败", error);
        }
    }

    async function discardPending(queryId) {
        const confirmed = window.confirm("将丢弃该待处理反馈，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/pending/" + encodeURIComponent(queryId) + "/discard", {
                method: "POST"
            });
            renderGlobalResult(result || {
                action: "discardPending",
                queryId: queryId,
                status: "discarded"
            });
            setStatus("待处理反馈已丢弃");
            await Promise.all([loadPendingQueries(), refreshOverview()]);
        }
        catch (error) {
            renderGlobalResultError("丢弃失败", error);
            showError("丢弃失败", error);
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
                    orchestrationMode: document.getElementById("compile-mode").value
                })
            });
            setStatus("编译任务已提交: " + response.jobId);
            await loadJobs();
        }
        catch (error) {
            showError("提交编译任务失败", error);
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
        formData.append("orchestrationMode", document.getElementById("upload-compile-mode").value);

        try {
            const response = await fetchJson("/api/v1/admin/compile/upload", {
                method: "POST",
                body: formData,
                isFormData: true
            });
            setStatus("上传编译已提交: " + response.jobId);
            filesInput.value = "";
            await loadJobs();
        }
        catch (error) {
            showError("上传并编译失败", error);
        }
    }

    async function loadJobs() {
        try {
            const response = await fetchJson("/api/v1/admin/jobs");
            renderJobs(response.items || []);
        }
        catch (error) {
            showError("加载作业列表失败", error);
        }
    }

    async function loadLlmConfig() {
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
            renderLlmModelOptions();
            renderLlmConnectionList(state.llmConnections);
            renderLlmModelList(state.llmModels);
            renderLlmBindingList(state.llmBindings);
        }
        catch (error) {
            showError("加载 LLM 配置失败", error);
        }
    }

    async function saveLlmConnection() {
        const id = document.getElementById("llm-connection-id").value.trim();
        const payload = {
            connectionCode: document.getElementById("llm-connection-code").value.trim(),
            providerType: document.getElementById("llm-provider-type").value,
            baseUrl: document.getElementById("llm-base-url").value.trim(),
            apiKey: document.getElementById("llm-api-key").value.trim(),
            remarks: document.getElementById("llm-connection-remarks").value.trim(),
            operator: document.getElementById("llm-connection-operator").value.trim() || "admin",
            enabled: document.getElementById("llm-connection-enabled").checked
        };
        if (!payload.connectionCode || !payload.baseUrl) {
            setStatus("请填写连接编码和 Base URL");
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
            setStatus(id ? "连接配置已更新" : "连接配置已创建");
            resetLlmConnectionForm();
            await loadLlmConfig();
        }
        catch (error) {
            renderGlobalResultError("保存连接配置失败", error);
            showError("保存连接配置失败", error);
        }
    }

    async function saveLlmModel() {
        const id = document.getElementById("llm-model-id").value.trim();
        const payload = {
            modelCode: document.getElementById("llm-model-code").value.trim(),
            connectionId: parseOptionalInteger(document.getElementById("llm-model-connection-id").value),
            modelName: document.getElementById("llm-model-name").value.trim(),
            temperature: parseOptionalDecimal(document.getElementById("llm-model-temperature").value),
            maxTokens: parseOptionalInteger(document.getElementById("llm-model-max-tokens").value),
            timeoutSeconds: parseOptionalInteger(document.getElementById("llm-model-timeout-seconds").value),
            inputPricePer1kTokens: parseOptionalDecimal(document.getElementById("llm-model-input-price").value),
            outputPricePer1kTokens: parseOptionalDecimal(document.getElementById("llm-model-output-price").value),
            extraOptionsJson: document.getElementById("llm-model-extra-options").value.trim() || "{}",
            remarks: document.getElementById("llm-model-remarks").value.trim(),
            operator: document.getElementById("llm-model-operator").value.trim() || "admin",
            enabled: document.getElementById("llm-model-enabled").checked
        };
        if (!payload.modelCode || !payload.modelName || !payload.connectionId) {
            setStatus("请填写模型编码、模型名称并选择连接");
            return;
        }
        try {
            const result = await fetchJson(id
                    ? "/api/v1/admin/llm/models/" + encodeURIComponent(id)
                    : "/api/v1/admin/llm/models", {
                method: id ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });
            renderGlobalResult(result);
            setStatus(id ? "模型配置已更新" : "模型配置已创建");
            resetLlmModelForm();
            await loadLlmConfig();
        }
        catch (error) {
            renderGlobalResultError("保存模型配置失败", error);
            showError("保存模型配置失败", error);
        }
    }

    async function saveLlmBinding() {
        const id = document.getElementById("llm-binding-id").value.trim();
        const payload = {
            scene: document.getElementById("llm-binding-scene").value,
            agentRole: document.getElementById("llm-binding-agent-role").value,
            primaryModelProfileId: parseOptionalInteger(document.getElementById("llm-binding-primary-model-id").value),
            fallbackModelProfileId: parseOptionalInteger(document.getElementById("llm-binding-fallback-model-id").value),
            routeLabel: document.getElementById("llm-binding-route-label").value.trim(),
            remarks: document.getElementById("llm-binding-remarks").value.trim(),
            operator: document.getElementById("llm-binding-operator").value.trim() || "admin",
            enabled: document.getElementById("llm-binding-enabled").checked
        };
        if (!payload.primaryModelProfileId || !payload.routeLabel) {
            setStatus("请为绑定选择主模型并填写路由标签");
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
            await loadLlmConfig();
        }
        catch (error) {
            renderGlobalResultError("保存 Agent 绑定失败", error);
            showError("保存 Agent 绑定失败", error);
        }
    }

    async function deleteLlmConnection(id) {
        if (!window.confirm("将删除连接配置 " + id + "，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/llm/connections/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            renderGlobalResult(result);
            setStatus("连接配置已删除");
            resetLlmConnectionForm();
            await loadLlmConfig();
        }
        catch (error) {
            renderGlobalResultError("删除连接配置失败", error);
            showError("删除连接配置失败", error);
        }
    }

    async function deleteLlmModel(id) {
        if (!window.confirm("将删除模型配置 " + id + "，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/llm/models/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            renderGlobalResult(result);
            setStatus("模型配置已删除");
            resetLlmModelForm();
            await loadLlmConfig();
        }
        catch (error) {
            renderGlobalResultError("删除模型配置失败", error);
            showError("删除模型配置失败", error);
        }
    }

    async function deleteLlmBinding(id) {
        if (!window.confirm("将删除 Agent 绑定 " + id + "，确认继续吗？")) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/llm/bindings/" + encodeURIComponent(id), {
                method: "DELETE"
            });
            renderGlobalResult(result);
            setStatus("Agent 绑定已删除");
            resetLlmBindingForm();
            await loadLlmConfig();
        }
        catch (error) {
            renderGlobalResultError("删除 Agent 绑定失败", error);
            showError("删除 Agent 绑定失败", error);
        }
    }

    async function retryJob(jobId) {
        const confirmed = window.confirm("将重试失败作业 " + jobId + "，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/jobs/" + encodeURIComponent(jobId) + "/retry", {
                method: "POST"
            });
            renderGlobalResult(result || {
                action: "retryJob",
                jobId: jobId,
                status: "retried"
            });
            setStatus("作业已重新入队: " + jobId);
            await loadJobs();
        }
        catch (error) {
            renderGlobalResultError("重试作业失败", error);
            showError("重试作业失败", error);
        }
    }

    async function rebuildChunks() {
        const confirmed = window.confirm("将基于当前文章与源文件正文重建全部知识切片，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/compile/rebuild-chunks", {
                method: "POST"
            });
            document.getElementById("rebuild-result").textContent = JSON.stringify(result, null, 2);
            setStatus("知识切片重建已完成");
            await Promise.all([loadJobs(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("rebuild-result", "知识切片重建失败", error);
            showError("知识切片重建失败", error);
        }
    }

    async function exportVaultFromAdmin() {
        const vaultDir = readVaultDir();
        if (!vaultDir) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/vault/export", {
                method: "POST",
                body: JSON.stringify({vaultDir: vaultDir})
            });
            renderVaultAdminResult(result);
            setStatus("知识仓导出已完成");
        }
        catch (error) {
            renderResultError("vault-admin-result", "知识仓导出失败", error);
            showError("知识仓导出失败", error);
        }
    }

    async function syncVaultFromAdmin() {
        const vaultDir = readVaultDir();
        if (!vaultDir) {
            return;
        }
        const force = document.getElementById("vault-sync-force").checked;
        const confirmed = window.confirm(
                force
                        ? "将强制覆盖冲突并回写知识仓，确认继续吗？"
                        : "将执行知识仓内容回写，确认继续吗？"
        );
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/vault/sync", {
                method: "POST",
                body: JSON.stringify({
                    vaultDir: vaultDir,
                    force: force
                })
            });
            renderVaultAdminResult(result);
            setStatus("知识仓回写已完成");
            await Promise.all([loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("vault-admin-result", "知识仓回写失败", error);
            showError("知识仓回写失败", error);
        }
    }

    async function loadRepoDiffFromAdmin() {
        const vaultDir = readVaultDir();
        const snapshotId = readRepoSnapshotId();
        if (!vaultDir || !snapshotId) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/snapshot/repo/" + encodeURIComponent(snapshotId)
                    + "/diff?vaultDir=" + encodeURIComponent(vaultDir));
            renderVaultAdminResult(result);
            setStatus("版本差异已加载");
        }
        catch (error) {
            renderResultError("vault-admin-result", "加载版本差异失败", error);
            showError("加载版本差异失败", error);
        }
    }

    async function rollbackRepoFromAdmin() {
        const vaultDir = readVaultDir();
        const snapshotId = readRepoSnapshotId();
        if (!vaultDir || !snapshotId) {
            return;
        }
        const confirmed = window.confirm("将整库回滚到快照版本 " + snapshotId + "，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/rollback/repo", {
                method: "POST",
                body: JSON.stringify({
                    vaultDir: vaultDir,
                    snapshotId: Number(snapshotId)
                })
            });
            renderVaultAdminResult(result);
            setStatus("整库回滚已完成");
            await Promise.all([loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("vault-admin-result", "整库回滚失败", error);
            showError("整库回滚失败", error);
        }
    }

    async function refreshGovernance() {
        await Promise.all([
            runLint(true),
            loadQualityGovernance(true),
            loadCoverageGovernance(true),
            loadInspectGovernance(true),
            loadRepoSnapshots(true)
        ]);
    }

    async function runLint(silent) {
        try {
            const lint = await fetchJson("/api/v1/admin/lint");
            document.getElementById("governance-lint-result").textContent = JSON.stringify(lint, null, 2);
            if (!silent) {
                setStatus("规则巡检已完成");
            }
        }
        catch (error) {
            renderResultError("governance-lint-result", "执行规则巡检失败", error);
            showError("执行规则巡检失败", error);
        }
    }

    async function runLintFix() {
        const confirmed = window.confirm("将执行规则自动修复，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/lint/fix", {
                method: "POST",
                body: JSON.stringify({})
            });
            document.getElementById("governance-lint-result").textContent = JSON.stringify(result, null, 2);
            setStatus("规则自动修复已完成");
            await Promise.all([loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("governance-lint-result", "执行规则自动修复失败", error);
            showError("执行规则自动修复失败", error);
        }
    }

    async function loadQualityGovernance(silent) {
        try {
            const result = await fetchJson("/api/v1/admin/quality?days=7");
            document.getElementById("governance-quality-result").textContent = JSON.stringify(result, null, 2);
            if (!silent) {
                setStatus("质量趋势已加载");
            }
        }
        catch (error) {
            renderResultError("governance-quality-result", "加载质量趋势失败", error);
            showError("加载质量趋势失败", error);
        }
    }

    async function loadCoverageGovernance(silent) {
        try {
            const coverage = await fetchJson("/api/v1/admin/coverage");
            const omissions = await fetchJson("/api/v1/admin/omissions");
            document.getElementById("governance-coverage-result").textContent = JSON.stringify({
                coverage: coverage,
                omissions: omissions
            }, null, 2);
            if (!silent) {
                setStatus("覆盖率与遗漏已加载");
            }
        }
        catch (error) {
            renderResultError("governance-coverage-result", "加载覆盖率失败", error);
            showError("加载覆盖率失败", error);
        }
    }

    async function loadInspectGovernance(silent) {
        try {
            const result = await fetchJson("/api/v1/admin/inspect");
            document.getElementById("governance-inspect-result").textContent = JSON.stringify(result, null, 2);
            if (!silent) {
                setStatus("人工核查列表已加载");
            }
        }
        catch (error) {
            renderResultError("governance-inspect-result", "加载人工核查失败", error);
            showError("加载人工核查失败", error);
        }
    }

    async function importInspectionAnswer() {
        const inspectionId = document.getElementById("inspection-id").value.trim();
        const finalAnswer = document.getElementById("inspection-answer").value.trim();
        const confirmedBy = document.getElementById("inspection-confirmed-by").value.trim() || "admin";
        if (!inspectionId || !finalAnswer) {
            setStatus("请输入核查记录 ID 和最终答案");
            return;
        }
        const confirmed = window.confirm("将导入核查最终答案并写入知识反馈记录，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/inspect/import-answers", {
                method: "POST",
                body: JSON.stringify({
                    inspectionId: inspectionId,
                    finalAnswer: finalAnswer,
                    confirmedBy: confirmedBy
                })
            });
            document.getElementById("governance-inspect-result").textContent = JSON.stringify(result, null, 2);
            setStatus("人工核查答案已导入");
            await Promise.all([loadPendingQueries(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("governance-inspect-result", "导入人工核查答案失败", error);
            showError("导入人工核查答案失败", error);
        }
    }

    async function loadArticleSnapshots() {
        try {
            const result = await fetchJson("/api/v1/admin/snapshot/article?limit=10");
            document.getElementById("governance-snapshot-result").textContent = JSON.stringify(result, null, 2);
            setStatus("文章快照已加载");
        }
        catch (error) {
            renderResultError("governance-snapshot-result", "加载文章快照失败", error);
            showError("加载文章快照失败", error);
        }
    }

    async function loadRepoSnapshots(silent) {
        try {
            const result = await fetchJson("/api/v1/admin/snapshot/repo?limit=10");
            document.getElementById("governance-snapshot-result").textContent = JSON.stringify(result, null, 2);
            if (!silent) {
                setStatus("整库快照已加载");
            }
        }
        catch (error) {
            renderResultError("governance-snapshot-result", "加载整库快照失败", error);
            showError("加载整库快照失败", error);
        }
    }

    async function loadArticleHistory() {
        const conceptId = readArticleConceptId();
        if (!conceptId) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/snapshot/article?conceptId="
                    + encodeURIComponent(conceptId) + "&limit=10");
            document.getElementById("governance-snapshot-result").textContent = JSON.stringify(result, null, 2);
            setStatus("文章历史已加载");
        }
        catch (error) {
            renderResultError("governance-snapshot-result", "加载文章历史失败", error);
            showError("加载文章历史失败", error);
        }
    }

    async function rollbackArticleFromGovernance() {
        const conceptId = readArticleConceptId();
        const snapshotId = readArticleSnapshotId();
        if (!conceptId || !snapshotId) {
            return;
        }
        const confirmed = window.confirm("将文章 " + conceptId + " 回滚到快照版本 " + snapshotId + "，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/rollback/article", {
                method: "POST",
                body: JSON.stringify({
                    conceptId: conceptId,
                    snapshotId: Number(snapshotId)
                })
            });
            document.getElementById("governance-snapshot-result").textContent = JSON.stringify(result, null, 2);
            setStatus("文章回滚已完成");
            await Promise.all([loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("governance-snapshot-result", "文章回滚失败", error);
            showError("文章回滚失败", error);
        }
    }

    async function runLinkEnhance() {
        const persist = document.getElementById("link-enhance-persist").checked;
        if (persist) {
            const confirmed = window.confirm("将持久化链接增强结果并改写文章内容，确认继续吗？");
            if (!confirmed) {
                return;
            }
        }
        try {
            const result = await fetchJson("/api/v1/admin/link-enhance", {
                method: "POST",
                body: JSON.stringify({
                    persist: persist
                })
            });
            document.getElementById("governance-link-result").textContent = JSON.stringify(result, null, 2);
            setStatus("链接增强已完成");
            await loadArticles();
        }
        catch (error) {
            renderResultError("governance-link-result", "执行链接增强失败", error);
            showError("执行链接增强失败", error);
        }
    }

    function renderOverview(overview, qualityResponse, coverage, omissions) {
        const status = overview.status || {};
        const pending = overview.pending || {};
        const overviewQuality = overview.quality || {};
        const report = qualityResponse && qualityResponse.report ? qualityResponse.report : overviewQuality;
        const trend = qualityResponse && qualityResponse.trend ? qualityResponse.trend : {};
        const reviewPassRate = report.totalArticles > 0
                ? (report.passedArticles || 0) * 100 / report.totalArticles
                : 0;
        const coveragePercent = (coverage.coverageRatio || 0) * 100;
        const omittedSourceFileCount = omissions.omittedSourceFileCount || 0;
        const pendingCount = status.pendingQueryCount || 0;
        const manualReviewCount = report.needsHumanReviewArticles || status.reviewPendingArticleCount || 0;

        const cards = [
            {
                label: "知识文章",
                value: status.articleCount || 0,
                note: "当前沉淀的知识条目",
                tone: status.articleCount > 0 ? "success" : ""
            },
            {
                label: "源文件",
                value: status.sourceFileCount || 0,
                note: "已纳入管理的知识来源",
                tone: status.sourceFileCount > 0 ? "success" : ""
            },
            {
                label: "反馈沉淀",
                value: status.contributionCount || 0,
                note: "已确认并沉淀的反馈",
                tone: status.contributionCount > 0 ? "success" : ""
            },
            {
                label: "待处理反馈",
                value: pendingCount,
                note: pendingCount > 0 ? "建议优先处理" : "当前没有积压",
                tone: pendingCount > 0 ? "warning" : "success"
            },
            {
                label: "需人工复核",
                value: manualReviewCount,
                note: manualReviewCount > 0 ? "建议进入治理修复" : "当前没有人工复核积压",
                tone: manualReviewCount > 0 ? "danger" : "success"
            }
        ];
        document.getElementById("overview-cards").innerHTML = cards.map(renderMetricCard).join("");

        const story = buildOverviewStory(status, report, pending, coveragePercent, trend, omittedSourceFileCount);
        document.getElementById("overview-story-title").textContent = story.title;
        document.getElementById("overview-story-copy").textContent = story.copy;

        const healthCards = [
            {
                label: "审查通过率",
                value: formatPercent(reviewPassRate, 1),
                note: (report.passedArticles || 0) + " / " + (report.totalArticles || 0) + " 篇文章已通过",
                tone: reviewPassRate >= 80 ? "success" : reviewPassRate >= 50 ? "warning" : "danger"
            },
            {
                label: "来源覆盖率",
                value: formatPercent(coveragePercent, 1),
                note: (coverage.coveredSourceFileCount || 0) + " / " + (coverage.totalSourceFileCount || 0) + " 源文件已被文章引用",
                tone: coveragePercent >= 80 ? "success" : coveragePercent >= 50 ? "warning" : "danger"
            },
            {
                label: "未覆盖源文件",
                value: omittedSourceFileCount,
                note: omittedSourceFileCount > 0 ? "说明还有素材未沉淀进知识文章" : "当前没有覆盖遗漏",
                tone: omittedSourceFileCount > 0 ? "warning" : "success"
            },
            {
                label: "待审文章",
                value: report.pendingReviewArticles || 0,
                note: "等待系统或人工继续处理",
                tone: (report.pendingReviewArticles || 0) > 0 ? "warning" : "success"
            },
            {
                label: "7 日文章变化",
                value: formatSignedInteger(trend.totalArticlesDelta || 0),
                note: trend.latestMeasuredAt
                        ? "最近测量 " + formatDateTime(trend.latestMeasuredAt)
                        : "暂无趋势历史",
                tone: trend.totalArticlesDelta >= 0 ? "success" : "warning"
            },
            {
                label: "7 日质量变化",
                value: formatSignedPercent(trend.reviewPassRateDelta || 0),
                note: "覆盖 " + formatSignedPercent(trend.groundingRateDelta || 0)
                        + " / 明确性 " + formatSignedPercent(trend.referentialRateDelta || 0),
                tone: (trend.reviewPassRateDelta || 0) >= 0 ? "success" : "warning"
            }
        ];
        document.getElementById("health-cards").innerHTML = healthCards.map(renderMetricCard).join("");
        document.getElementById("overview-focus").innerHTML = renderOverviewFocus(pending, manualReviewCount, omittedSourceFileCount);
    }

    function renderArticleList(response) {
        const items = response.items || [];
        document.getElementById("article-count").textContent = String(response.count || 0);
        const list = document.getElementById("article-list");
        if (items.length === 0) {
            list.innerHTML = "<div class='list-item'><p class='item-summary'>暂无文章</p></div>";
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
        document.getElementById("article-detail-title").textContent = detail.title || "未命名文章";
        document.getElementById("article-detail-meta").textContent =
                [
                    detail.conceptId,
                    detail.lifecycle ? "生命周期：" + getBadgeLabel(detail.lifecycle) : "",
                    detail.reviewStatus ? "审查：" + getBadgeLabel(detail.reviewStatus) : "",
                    detail.compiledAt ? "编译时间：" + formatDateTime(detail.compiledAt) : ""
                ]
                        .filter(Boolean)
                        .join(" | ");
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
        document.getElementById("article-detail-title").textContent = "请选择一篇文章";
        document.getElementById("article-detail-meta").textContent = "";
        document.getElementById("article-detail-summary").textContent = "暂无摘要";
        document.getElementById("article-content").textContent = "暂无内容";
        document.getElementById("article-metadata").textContent = "暂无元数据";
        document.getElementById("article-sources").innerHTML = "";
        document.getElementById("article-relations").innerHTML = "";
    }

    function renderVaultAdminResult(result) {
        document.getElementById("vault-admin-result").textContent = JSON.stringify(result, null, 2);
        renderGlobalResult(result);
    }

    function renderResultError(targetId, prefix, error) {
        const message = error && error.message ? error.message : String(error);
        const target = document.getElementById(targetId);
        if (target) {
            target.textContent = prefix + "：\n" + message;
        }
        renderGlobalResultError(prefix, error);
    }

    function renderGlobalResult(result) {
        document.getElementById("global-result").textContent = JSON.stringify(result, null, 2);
    }

    function renderGlobalResultError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        document.getElementById("global-result").textContent = prefix + "：\n" + message;
    }

    function readVaultDir() {
        const vaultDir = document.getElementById("vault-dir").value.trim();
        if (!vaultDir) {
            setStatus("请输入知识仓目录");
            return "";
        }
        return vaultDir;
    }

    function readRepoSnapshotId() {
        const snapshotId = document.getElementById("repo-snapshot-id").value.trim();
        if (!snapshotId) {
            setStatus("请输入版本快照 ID");
            return "";
        }
        return snapshotId;
    }

    function readArticleConceptId() {
        const conceptId = document.getElementById("article-snapshot-concept-id").value.trim();
        if (!conceptId) {
            setStatus("请输入文章概念 ID");
            return "";
        }
        return conceptId;
    }

    function readArticleSnapshotId() {
        const snapshotId = document.getElementById("article-snapshot-id").value.trim();
        if (!snapshotId) {
            setStatus("请输入文章快照 ID");
            return "";
        }
        return snapshotId;
    }

    function highlightArticle(conceptId) {
        document.querySelectorAll("#article-list .list-item").forEach(function (item) {
            item.classList.toggle("active", item.dataset.conceptId === conceptId);
        });
    }

    function renderPendingQueries(response) {
        const items = response.items || [];
        const container = document.getElementById("pending-list");
        if (items.length === 0) {
            container.innerHTML = "<div class='pending-card'><p class='item-summary'>当前没有待处理反馈，可以回到知识文章页继续巡检内容。</p></div>";
            return;
        }
        container.innerHTML = items.map(function (item) {
            return "<div class='pending-card'>"
                    + "<div class='meta-row'>"
                    + "<span class='pill'>" + escapeHtml(item.queryId) + "</span>"
                    + renderBadge(item.reviewStatus)
                    + "</div>"
                    + "<h4>" + escapeHtml(item.question) + "</h4>"
                    + "<p class='item-summary'>" + escapeHtml(item.answer || "暂无答案") + "</p>"
                    + "<div class='tag-list'>" + renderTagGroup(item.selectedConceptIds || []) + "</div>"
                    + "<div class='tag-list'>" + renderTagGroup(item.sourceFilePaths || []) + "</div>"
                    + "<textarea data-correction-for='" + escapeHtml(item.queryId) + "' placeholder='输入修正内容'></textarea>"
                    + "<div class='card-actions'>"
                    + "<button class='primary-btn' data-correct='" + escapeHtml(item.queryId) + "' type='button'>提交修正</button>"
                    + "<button class='secondary-btn' data-confirm='" + escapeHtml(item.queryId) + "' type='button'>确认</button>"
                    + "<button class='ghost-btn' data-discard='" + escapeHtml(item.queryId) + "' type='button'>丢弃</button>"
                    + "</div>"
                    + "</div>";
        }).join("");

        container.querySelectorAll("[data-correct]").forEach(function (button) {
            button.addEventListener("click", function () {
                correctPending(button.dataset.correct);
            });
        });
        container.querySelectorAll("[data-confirm]").forEach(function (button) {
            button.addEventListener("click", function () {
                confirmPending(button.dataset.confirm);
            });
        });
        container.querySelectorAll("[data-discard]").forEach(function (button) {
            button.addEventListener("click", function () {
                discardPending(button.dataset.discard);
            });
        });
    }

    function renderJobs(items) {
        const container = document.getElementById("job-list");
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>暂无编译作业，知识库目前没有排队中的导入或重建任务。</p></div>";
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
                    + "<p class='item-summary'>持久化: " + escapeHtml(String(item.persistedCount || 0))
                    + " | 尝试次数: " + escapeHtml(String(item.attemptCount || 0))
                    + " | 增量: " + escapeHtml(item.incremental ? "是" : "否") + "</p>"
                    + "<p class='item-summary'>" + escapeHtml(item.errorMessage || "无错误信息") + "</p>"
                    + "<div class='card-actions'>"
                    + retryButton
                    + "</div>"
                    + "</div>";
        }).join("");
        container.querySelectorAll("[data-retry-job]").forEach(function (button) {
            button.addEventListener("click", function () {
                retryJob(button.dataset.retryJob);
            });
        });
    }

    function renderLlmConnectionOptions() {
        const select = document.getElementById("llm-model-connection-id");
        if (!select) {
            return;
        }
        if (!state.llmConnections || state.llmConnections.length === 0) {
            select.innerHTML = "<option value=''>暂无连接，请先创建</option>";
            return;
        }
        select.innerHTML = state.llmConnections.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.connectionCode + " (" + item.providerType + ")")
                    + "</option>";
        }).join("");
    }

    function renderLlmModelOptions() {
        const primarySelect = document.getElementById("llm-binding-primary-model-id");
        const fallbackSelect = document.getElementById("llm-binding-fallback-model-id");
        if (!primarySelect || !fallbackSelect) {
            return;
        }
        if (!state.llmModels || state.llmModels.length === 0) {
            primarySelect.innerHTML = "<option value=''>暂无模型，请先创建</option>";
            fallbackSelect.innerHTML = "<option value=''>不配置</option>";
            return;
        }
        const options = state.llmModels.map(function (item) {
            return "<option value='" + escapeHtml(String(item.id)) + "'>"
                    + escapeHtml(item.modelCode + " -> " + item.modelName)
                    + "</option>";
        }).join("");
        primarySelect.innerHTML = options;
        fallbackSelect.innerHTML = "<option value=''>不配置</option>" + options;
    }

    function renderLlmConnectionList(items) {
        const container = document.getElementById("llm-connection-list");
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有连接配置，可以先新增一个 Provider 连接。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>编码</th><th>Provider</th><th>Base URL</th><th>API Key</th><th>状态</th><th>操作</th></tr></thead>"
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
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有模型配置，可以先为连接添加一个模型。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>模型编码</th><th>模型名称</th><th>连接</th><th>价格</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.modelCode) + "</td>"
                            + "<td>" + escapeHtml(item.modelName) + "</td>"
                            + "<td>" + escapeHtml(String(item.connectionId)) + "</td>"
                            + "<td>in " + escapeHtml(String(item.inputPricePer1kTokens || "-"))
                            + " / out " + escapeHtml(String(item.outputPricePer1kTokens || "-")) + "</td>"
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
        if (!container) {
            return;
        }
        if (!items || items.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>还没有 Agent 绑定，compile 侧会继续走本地 fallback 配置。</p></div>";
            return;
        }
        container.innerHTML = "<table class='simple-table'>"
                + "<thead><tr><th>场景</th><th>角色</th><th>主模型</th><th>备用</th><th>路由标签</th><th>状态</th><th>操作</th></tr></thead>"
                + "<tbody>"
                + items.map(function (item) {
                    return "<tr>"
                            + "<td>" + escapeHtml(item.scene) + "</td>"
                            + "<td>" + escapeHtml(item.agentRole) + "</td>"
                            + "<td>" + escapeHtml(String(item.primaryModelProfileId)) + "</td>"
                            + "<td>" + escapeHtml(String(item.fallbackModelProfileId || "-")) + "</td>"
                            + "<td>" + escapeHtml(item.routeLabel) + "</td>"
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
        document.getElementById("llm-connection-remarks").value = item.remarks || "";
        document.getElementById("llm-connection-operator").value = item.updatedBy || "admin";
        document.getElementById("llm-connection-enabled").checked = !!item.enabled;
        activateTab("llm");
    }

    function editLlmModel(id) {
        const item = state.llmModels.find(function (entry) {
            return String(entry.id) === String(id);
        });
        if (!item) {
            return;
        }
        document.getElementById("llm-model-id").value = item.id;
        document.getElementById("llm-model-code").value = item.modelCode || "";
        document.getElementById("llm-model-connection-id").value = String(item.connectionId || "");
        document.getElementById("llm-model-name").value = item.modelName || "";
        document.getElementById("llm-model-temperature").value = item.temperature || "";
        document.getElementById("llm-model-max-tokens").value = item.maxTokens || "";
        document.getElementById("llm-model-timeout-seconds").value = item.timeoutSeconds || "";
        document.getElementById("llm-model-input-price").value = item.inputPricePer1kTokens || "";
        document.getElementById("llm-model-output-price").value = item.outputPricePer1kTokens || "";
        document.getElementById("llm-model-extra-options").value = item.extraOptionsJson || "{}";
        document.getElementById("llm-model-remarks").value = item.remarks || "";
        document.getElementById("llm-model-operator").value = item.updatedBy || "admin";
        document.getElementById("llm-model-enabled").checked = !!item.enabled;
        activateTab("llm");
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
        document.getElementById("llm-binding-agent-role").value = item.agentRole || "writer";
        document.getElementById("llm-binding-primary-model-id").value = String(item.primaryModelProfileId || "");
        document.getElementById("llm-binding-fallback-model-id").value = item.fallbackModelProfileId
                ? String(item.fallbackModelProfileId)
                : "";
        document.getElementById("llm-binding-route-label").value = item.routeLabel || "";
        document.getElementById("llm-binding-remarks").value = item.remarks || "";
        document.getElementById("llm-binding-operator").value = item.updatedBy || "admin";
        document.getElementById("llm-binding-enabled").checked = !!item.enabled;
        activateTab("llm");
    }

    function resetLlmConnectionForm() {
        document.getElementById("llm-connection-id").value = "";
        document.getElementById("llm-connection-code").value = "";
        document.getElementById("llm-provider-type").value = "openai";
        document.getElementById("llm-base-url").value = "";
        document.getElementById("llm-api-key").value = "";
        document.getElementById("llm-connection-remarks").value = "";
        document.getElementById("llm-connection-operator").value = "admin";
        document.getElementById("llm-connection-enabled").checked = true;
    }

    function resetLlmModelForm() {
        document.getElementById("llm-model-id").value = "";
        document.getElementById("llm-model-code").value = "";
        document.getElementById("llm-model-name").value = "";
        document.getElementById("llm-model-temperature").value = "";
        document.getElementById("llm-model-max-tokens").value = "";
        document.getElementById("llm-model-timeout-seconds").value = "";
        document.getElementById("llm-model-input-price").value = "";
        document.getElementById("llm-model-output-price").value = "";
        document.getElementById("llm-model-extra-options").value = "{}";
        document.getElementById("llm-model-remarks").value = "";
        document.getElementById("llm-model-operator").value = "admin";
        document.getElementById("llm-model-enabled").checked = true;
        renderLlmConnectionOptions();
    }

    function resetLlmBindingForm() {
        document.getElementById("llm-binding-id").value = "";
        document.getElementById("llm-binding-scene").value = "compile";
        document.getElementById("llm-binding-agent-role").value = "writer";
        document.getElementById("llm-binding-route-label").value = "";
        document.getElementById("llm-binding-remarks").value = "";
        document.getElementById("llm-binding-operator").value = "admin";
        document.getElementById("llm-binding-enabled").checked = true;
        renderLlmModelOptions();
    }

    function activateTab(tabName) {
        document.querySelectorAll(".tab-btn").forEach(function (button) {
            if (!button.dataset.tab) {
                return;
            }
            button.classList.toggle("active", button.dataset.tab === tabName);
        });
        document.querySelectorAll(".tab-panel").forEach(function (panel) {
            panel.classList.toggle("active", panel.id === "tab-" + tabName);
        });
    }

    function jumpToTab(tabName) {
        if (!tabName) {
            return;
        }
        activateTab(tabName);
        const workspacePanel = document.querySelector(".workspace-panel");
        if (workspacePanel && typeof workspacePanel.scrollIntoView === "function") {
            workspacePanel.scrollIntoView({behavior: "smooth", block: "start"});
        }
    }

    function activateGovernanceTab(tabName) {
        document.querySelectorAll("[data-governance-tab]").forEach(function (button) {
            button.classList.toggle("active", button.dataset.governanceTab === tabName);
        });
        document.querySelectorAll(".governance-panel").forEach(function (panel) {
            panel.classList.toggle("active", panel.id === "governance-tab-" + tabName);
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

    function buildOverviewStory(status, report, pending, coveragePercent, trend, omittedSourceFileCount) {
        const articleCount = status.articleCount || 0;
        const sourceFileCount = status.sourceFileCount || 0;
        const contributionCount = status.contributionCount || 0;
        const pendingCount = pending.count || 0;
        const manualReviewCount = report.needsHumanReviewArticles || status.reviewPendingArticleCount || 0;

        if (articleCount === 0 && sourceFileCount === 0) {
            return {
                title: "知识库还是空的，先导入第一批资料",
                copy: "可以直接去“编译导入”上传文件或指定源目录。完成后，首页会开始展示文章、覆盖率和待处理信号。"
            };
        }

        if (pendingCount > 0 || manualReviewCount > 0 || omittedSourceFileCount > 0) {
            return {
                title: "知识库已有沉淀，但还有一些内容需要你接手",
                copy: "当前管理 " + articleCount + " 篇知识文章、" + sourceFileCount + " 份源文件，"
                        + pendingCount + " 条待处理反馈，"
                        + manualReviewCount + " 篇需人工复核，"
                        + omittedSourceFileCount + " 个源文件尚未被文章覆盖。来源覆盖率 "
                        + formatPercent(coveragePercent, 1)
                        + "，近 7 天文章变化 "
                        + formatSignedInteger(trend.totalArticlesDelta || 0)
                        + "。"
            };
        }

        return {
            title: "知识库运行平稳，可以继续扩充和巡检",
            copy: "当前已管理 " + articleCount + " 篇知识文章、"
                    + sourceFileCount + " 份源文件，并沉淀了 "
                    + contributionCount + " 条确认反馈。来源覆盖率 "
                    + formatPercent(coveragePercent, 1)
                    + "，目前没有待处理反馈或明显积压。"
        };
    }

    function renderOverviewFocus(pending, manualReviewCount, omittedSourceFileCount) {
        const items = [];
        (pending.items || []).slice(0, 3).forEach(function (item) {
            items.push({
                title: item.question || "待处理反馈",
                badge: renderBadge(item.reviewStatus),
                meta: "<span class='pill'>" + escapeHtml(item.queryId || "-") + "</span>",
                copy: "这条反馈还没有完成修正或确认，建议优先处理。",
                tab: "pending",
                actionLabel: "去处理"
            });
        });

        if (manualReviewCount > 0) {
            items.push({
                title: "有 " + manualReviewCount + " 篇文章需要人工复核",
                badge: renderBadge("needs_human_review"),
                meta: "<span class='pill'>文章治理</span>",
                copy: "可以进入知识文章或治理修复页，优先处理审查未闭环的内容。",
                tab: "articles",
                actionLabel: "查看文章"
            });
        }

        if (omittedSourceFileCount > 0) {
            items.push({
                title: "有 " + omittedSourceFileCount + " 个源文件尚未被文章覆盖",
                badge: renderBadge("coverage_gap"),
                meta: "<span class='pill'>覆盖缺口</span>",
                copy: "说明还有素材尚未沉淀成知识，可去编译导入或治理修复继续推进。",
                tab: "compile",
                actionLabel: "去导入"
            });
        }

        if (items.length === 0) {
            return "<div class='focus-empty'><h4>当前没有高优先级积压</h4>"
                    + "<p>首页已经比较干净了。接下来更适合去文章页抽样检查内容，或继续导入新知识。</p></div>";
        }

        return items.map(function (item) {
            return "<div class='focus-item'>"
                    + "<div class='meta-row'>" + item.meta + item.badge + "</div>"
                    + "<h4>" + escapeHtml(item.title) + "</h4>"
                    + "<p>" + escapeHtml(item.copy) + "</p>"
                    + "<div class='card-actions'>"
                    + "<button class='ghost-btn jump-btn' data-jump-tab='" + escapeHtml(item.tab) + "' type='button'>"
                    + escapeHtml(item.actionLabel)
                    + "</button>"
                    + "</div></div>";
        }).join("");
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

    function setStatus(message) {
        document.getElementById("global-status").textContent = message;
    }

    function showError(prefix, error) {
        const message = error && error.message ? error.message : String(error);
        setStatus(prefix + "：" + message);
    }

    function parseOptionalInteger(value) {
        const text = String(value || "").trim();
        if (!text) {
            return null;
        }
        const parsed = Number(text);
        return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
    }

    function parseOptionalDecimal(value) {
        const text = String(value || "").trim();
        if (!text) {
            return null;
        }
        const parsed = Number(text);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function formatNumber(value, digits) {
        return Number(value).toFixed(digits);
    }

    function formatPercent(value, digits) {
        return formatNumber(value || 0, digits) + "%";
    }

    function formatSignedInteger(value) {
        const number = Number(value || 0);
        if (number > 0) {
            return "+" + number;
        }
        return String(number);
    }

    function formatSignedPercent(value) {
        const number = Number(value || 0);
        const formatted = formatNumber(Math.abs(number), 2) + "pt";
        if (number > 0) {
            return "+" + formatted;
        }
        if (number < 0) {
            return "-" + formatted;
        }
        return formatted;
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

    function formatLifecycleAction(value) {
        if (value === "activate") {
            return "激活";
        }
        if (value === "deprecate") {
            return "废弃";
        }
        if (value === "archive") {
            return "归档";
        }
        return value || "-";
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
            NEEDS_REVIEW: "待复核",
            SUCCEEDED: "成功",
            FAILED: "失败",
            RUNNING: "进行中",
            QUEUED: "排队中",
            CONFIRMED: "已确认",
            DISCARDED: "已丢弃",
            ISSUES_FOUND: "发现问题",
            PARSE_RESCUED: "解析兜底",
            PARSE_FAILED: "解析失败",
            TIMEOUT_FALLBACK: "超时兜底",
            COVERAGE_GAP: "覆盖缺口"
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

    function cssEscape(value) {
        return String(value).replaceAll("'", "\\'");
    }
})();
