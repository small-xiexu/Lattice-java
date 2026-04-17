(function () {
    const state = {
        selectedConceptId: null
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
    }

    async function refreshAll() {
        setStatus("正在刷新后台数据...");
        await Promise.all([
            refreshOverview(),
            loadArticles(),
            loadPendingQueries(),
            loadJobs(),
            refreshGovernance()
        ]);
        setStatus("后台数据已刷新");
    }

    async function refreshOverview() {
        try {
            const overview = await fetchJson("/api/v1/admin/overview");
            const usage = await fetchJson("/api/v1/admin/usage");
            renderOverview(overview);
            renderUsage(usage);
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
        const confirmed = window.confirm("将对文章执行生命周期变更 " + action + "，确认继续吗？");
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
            showError("加载待处理查询失败", error);
        }
    }

    async function correctPending(queryId) {
        const textarea = document.querySelector("[data-correction-for='" + cssEscape(queryId) + "']");
        const correction = textarea ? textarea.value.trim() : "";
        if (!correction) {
            setStatus("请输入修正内容");
            return;
        }
        const confirmed = window.confirm("将提交该待处理查询的修正内容，确认继续吗？");
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
        const confirmed = window.confirm("将确认该待处理查询并沉淀为 contribution，确认继续吗？");
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
            setStatus("待处理查询已确认");
            await Promise.all([loadPendingQueries(), refreshOverview()]);
        }
        catch (error) {
            renderGlobalResultError("确认失败", error);
            showError("确认失败", error);
        }
    }

    async function discardPending(queryId) {
        const confirmed = window.confirm("将丢弃该待处理查询，确认继续吗？");
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
            setStatus("待处理查询已丢弃");
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
        const confirmed = window.confirm("将基于当前 articles/source_files 正文重建全部 chunks，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/compile/rebuild-chunks", {
                method: "POST"
            });
            document.getElementById("rebuild-result").textContent = JSON.stringify(result, null, 2);
            setStatus("chunks 重建已完成");
            await Promise.all([loadJobs(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("rebuild-result", "chunks 重建失败", error);
            showError("chunks 重建失败", error);
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
            setStatus("Vault 导出已完成");
        }
        catch (error) {
            renderResultError("vault-admin-result", "Vault 导出失败", error);
            showError("Vault 导出失败", error);
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
                        ? "将强制覆盖冲突并回写 Vault，确认继续吗？"
                        : "将执行 Vault inbound sync，确认继续吗？"
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
            setStatus("Vault 回写已完成");
            await Promise.all([loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("vault-admin-result", "Vault 回写失败", error);
            showError("Vault 回写失败", error);
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
            setStatus("Repo diff 已加载");
        }
        catch (error) {
            renderResultError("vault-admin-result", "加载 Repo diff 失败", error);
            showError("加载 Repo diff 失败", error);
        }
    }

    async function rollbackRepoFromAdmin() {
        const vaultDir = readVaultDir();
        const snapshotId = readRepoSnapshotId();
        if (!vaultDir || !snapshotId) {
            return;
        }
        const confirmed = window.confirm("将整库回滚到 snapshot " + snapshotId + "，确认继续吗？");
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
                setStatus("Lint 已完成");
            }
        }
        catch (error) {
            renderResultError("governance-lint-result", "执行 Lint 失败", error);
            showError("执行 Lint 失败", error);
        }
    }

    async function runLintFix() {
        const confirmed = window.confirm("将执行 Lint Fix，确认继续吗？");
        if (!confirmed) {
            return;
        }
        try {
            const result = await fetchJson("/api/v1/admin/lint/fix", {
                method: "POST",
                body: JSON.stringify({})
            });
            document.getElementById("governance-lint-result").textContent = JSON.stringify(result, null, 2);
            setStatus("Lint Fix 已完成");
            await Promise.all([loadArticles(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("governance-lint-result", "执行 Lint Fix 失败", error);
            showError("执行 Lint Fix 失败", error);
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
                setStatus("Inspection 列表已加载");
            }
        }
        catch (error) {
            renderResultError("governance-inspect-result", "加载 Inspection 失败", error);
            showError("加载 Inspection 失败", error);
        }
    }

    async function importInspectionAnswer() {
        const inspectionId = document.getElementById("inspection-id").value.trim();
        const finalAnswer = document.getElementById("inspection-answer").value.trim();
        const confirmedBy = document.getElementById("inspection-confirmed-by").value.trim() || "admin";
        if (!inspectionId || !finalAnswer) {
            setStatus("请输入 Inspection ID 和最终答案");
            return;
        }
        const confirmed = window.confirm("将导入 Inspection 最终答案并写入 contribution，确认继续吗？");
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
            setStatus("Inspection 答案已导入");
            await Promise.all([loadPendingQueries(), refreshOverview()]);
        }
        catch (error) {
            renderResultError("governance-inspect-result", "导入 Inspection 答案失败", error);
            showError("导入 Inspection 答案失败", error);
        }
    }

    async function loadArticleSnapshots() {
        try {
            const result = await fetchJson("/api/v1/admin/snapshot/article?limit=10");
            document.getElementById("governance-inspect-result").textContent = JSON.stringify(result, null, 2);
            setStatus("文章快照已加载");
        }
        catch (error) {
            renderResultError("governance-inspect-result", "加载文章快照失败", error);
            showError("加载文章快照失败", error);
        }
    }

    async function loadRepoSnapshots(silent) {
        try {
            const result = await fetchJson("/api/v1/admin/snapshot/repo?limit=10");
            document.getElementById("governance-snapshot-result").textContent = JSON.stringify(result, null, 2);
            if (!silent) {
                setStatus("Repo 快照已加载");
            }
        }
        catch (error) {
            renderResultError("governance-snapshot-result", "加载 Repo 快照失败", error);
            showError("加载 Repo 快照失败", error);
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
        const confirmed = window.confirm("将文章 " + conceptId + " 回滚到 snapshot " + snapshotId + "，确认继续吗？");
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
            const confirmed = window.confirm("将持久化 Link Enhance 结果并改写文章内容，确认继续吗？");
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
            setStatus("Link Enhance 已完成");
            await loadArticles();
        }
        catch (error) {
            renderResultError("governance-link-result", "执行 Link Enhance 失败", error);
            showError("执行 Link Enhance 失败", error);
        }
    }

    function renderOverview(overview) {
        const status = overview.status || {};
        const quality = overview.quality || {};
        const cards = [
            {label: "文章数", value: status.articleCount || 0},
            {label: "源文件数", value: status.sourceFileCount || 0},
            {label: "贡献数", value: status.contributionCount || 0},
            {label: "Pending 查询", value: status.pendingQueryCount || 0},
            {label: "待人工文章", value: status.reviewPendingArticleCount || 0}
        ];
        document.getElementById("overview-cards").innerHTML = cards.map(renderMetricCard).join("");

        const qualityCards = [
            {label: "总文章", value: quality.totalArticles || 0},
            {label: "已通过", value: quality.passedArticles || 0},
            {label: "待审查", value: quality.pendingReviewArticles || 0},
            {label: "需人工", value: quality.needsHumanReviewArticles || 0},
            {label: "贡献数", value: quality.contributionCount || 0},
            {label: "源文件", value: quality.sourceFileCount || 0}
        ];
        document.getElementById("quality-cards").innerHTML = qualityCards.map(renderMetricCard).join("");
    }

    function renderUsage(usage) {
        const summaryCards = [
            {label: "调用次数", value: usage.totalCalls || 0},
            {label: "输入 Token", value: usage.totalInputTokens || 0},
            {label: "输出 Token", value: usage.totalOutputTokens || 0},
            {label: "总成本 USD", value: formatNumber(usage.totalCostUsd || 0, 4)}
        ];
        document.getElementById("usage-summary").innerHTML = summaryCards.map(renderMetricCard).join("");
        document.getElementById("usage-purpose-table").innerHTML = renderUsageTable(
                usage.purposes || [],
                "purpose"
        );
        document.getElementById("usage-model-table").innerHTML = renderUsageTable(
                usage.models || [],
                "model"
        );
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
                [detail.conceptId, detail.lifecycle, detail.reviewStatus, detail.compiledAt]
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
            setStatus("请输入 Vault 目录");
            return "";
        }
        return vaultDir;
    }

    function readRepoSnapshotId() {
        const snapshotId = document.getElementById("repo-snapshot-id").value.trim();
        if (!snapshotId) {
            setStatus("请输入 Repo Snapshot ID");
            return "";
        }
        return snapshotId;
    }

    function readArticleConceptId() {
        const conceptId = document.getElementById("article-snapshot-concept-id").value.trim();
        if (!conceptId) {
            setStatus("请输入 Article Concept ID");
            return "";
        }
        return conceptId;
    }

    function readArticleSnapshotId() {
        const snapshotId = document.getElementById("article-snapshot-id").value.trim();
        if (!snapshotId) {
            setStatus("请输入 Article Snapshot ID");
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
            container.innerHTML = "<div class='pending-card'><p class='item-summary'>当前没有待处理查询</p></div>";
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
            container.innerHTML = "<div class='job-card'><p class='item-summary'>暂无编译作业</p></div>";
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
                    + "<span class='pill'>" + escapeHtml(item.orchestrationMode || "-") + "</span>"
                    + "</div>"
                    + "<h4>" + escapeHtml(item.sourceDir || "-") + "</h4>"
                    + "<p class='item-summary'>持久化: " + escapeHtml(String(item.persistedCount || 0))
                    + " | 尝试次数: " + escapeHtml(String(item.attemptCount || 0))
                    + " | 增量: " + escapeHtml(String(item.incremental)) + "</p>"
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

    function activateGovernanceTab(tabName) {
        document.querySelectorAll("[data-governance-tab]").forEach(function (button) {
            button.classList.toggle("active", button.dataset.governanceTab === tabName);
        });
        document.querySelectorAll(".governance-panel").forEach(function (panel) {
            panel.classList.toggle("active", panel.id === "governance-tab-" + tabName);
        });
    }

    function renderMetricCard(item) {
        return "<div class='metric-card'><span class='label'>" + escapeHtml(item.label)
                + "</span><span class='value'>" + escapeHtml(String(item.value)) + "</span></div>";
    }

    function renderUsageTable(items, keyField) {
        if (!items || items.length === 0) {
            return "<p class='item-summary'>暂无数据</p>";
        }
        return "<table class='simple-table'><thead><tr><th>维度</th><th>调用</th><th>输入</th><th>输出</th><th>成本</th></tr></thead><tbody>"
                + items.map(function (item) {
                    return "<tr><td>" + escapeHtml(item[keyField] || "-")
                            + "</td><td>" + escapeHtml(String(item.callCount || 0))
                            + "</td><td>" + escapeHtml(String(item.inputTokens || 0))
                            + "</td><td>" + escapeHtml(String(item.outputTokens || 0))
                            + "</td><td>" + escapeHtml(formatNumber(item.costUsd || 0, 4)) + "</td></tr>";
                }).join("")
                + "</tbody></table>";
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
        return "<span class='" + className + "'>" + escapeHtml(value || "-") + "</span>";
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

    function formatNumber(value, digits) {
        return Number(value).toFixed(digits);
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
