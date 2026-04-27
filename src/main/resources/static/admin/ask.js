(function () {
    let canAsk = true;
    let pageNoticeTimer = null;
    const state = {
        knowledgeReady: false,
        knowledgeProcessing: false,
        knowledgeWaitingConfirm: false,
        hasSubmitted: false,
        lastQueryFailed: false,
        lastQueryError: "",
        lastAnswerHasCitation: false,
        lastAnswerEmpty: true,
        lastAnswerOutcome: "",
        lastGenerationMode: "",
        lastModelExecutionStatus: "",
        lastReviewStatus: "",
        lastQueryId: "",
        lastSupportSourceCount: 0,
        lastEvidenceSourceCount: 0,
        lastEvidenceWeak: false
    };

    document.addEventListener("DOMContentLoaded", async function () {
        bindEvents();
        resetAnswerExperience();
        await refreshReadiness();
        renderReadinessCard();
        renderResultGuide();
        bootstrapQuestionFromUrl();
    });

    function bindEvents() {
        document.getElementById("refresh-qa-status").addEventListener("click", refreshReadiness);
        document.getElementById("submit-question").addEventListener("click", submitQuestion);
        document.getElementById("clear-question").addEventListener("click", clearQuestion);
        document.addEventListener("click", handleAskHelpActionClick);
    }

    function bootstrapQuestionFromUrl() {
        const params = new URLSearchParams(window.location.search);
        const question = String(params.get("question") || "").trim();
        const autorun = ["1", "true", "yes"].includes(String(params.get("autorun") || "").toLowerCase());
        if (!question) {
            return;
        }
        document.getElementById("ask-question").value = question;
        if (autorun && canAsk) {
            submitQuestion();
            return;
        }
        if (autorun && !canAsk) {
            setStatus("已从链接填入问题，但当前知识库还没有准备好自动发问。", "warning");
        }
    }

    async function refreshReadiness() {
        try {
            const results = await Promise.all([
                fetchJson("/api/v1/admin/overview"),
                fetchJson("/api/v1/admin/source-runs?limit=10")
            ]);
            renderReadiness(results[0], results[1] || []);
        }
        catch (error) {
            showError("刷新知识库状态失败", error);
        }
    }

    async function submitQuestion() {
        const question = document.getElementById("ask-question").value.trim();
        if (!question) {
            setStatus("请输入问题", "warning");
            return;
        }
        if (!canAsk) {
            setStatus("当前知识库还没有可用资料，请先上传并处理文档", "warning");
            return;
        }
        state.hasSubmitted = true;
        toggleAskResultExperience(true);
        renderLoadingState();
        setStatus("正在生成回答...", "info");
        state.lastQueryFailed = false;
        state.lastQueryError = "";
        try {
            const encodedQuestion = encodeURIComponent(question);
            const results = await Promise.all([
                fetchJson("/api/v1/query", {
                    method: "POST",
                    body: JSON.stringify({question: question})
                }),
                fetchJson("/api/v1/search?question=" + encodedQuestion + "&limit=5")
            ]);
            const queryResponse = results[0] || {};
            const searchItems = results[1].items || [];
            const responseSources = queryResponse.sources || [];
            renderAnswer(queryResponse);
            renderAnswerMetrics(queryResponse, searchItems, responseSources);
            renderAnswerSupport(queryResponse, searchItems, responseSources);
            renderSourceSummary(queryResponse, searchItems, responseSources);
            renderSources(searchItems, responseSources);
            renderGlobalResult(queryResponse);
            state.lastQueryFailed = false;
            state.lastQueryError = "";
            state.lastAnswerOutcome = queryResponse.answerOutcome || "";
            state.lastGenerationMode = queryResponse.generationMode || "";
            state.lastModelExecutionStatus = queryResponse.modelExecutionStatus || "";
            state.lastReviewStatus = queryResponse.reviewStatus || "";
            state.lastQueryId = queryResponse.queryId || "";
            state.lastAnswerHasCitation = hasAskCitations(searchItems, responseSources);
            state.lastAnswerEmpty = !String(queryResponse.answer || "").trim();
            state.lastSupportSourceCount = responseSources.length;
            state.lastEvidenceSourceCount = uniqueSourceCount(searchItems, responseSources);
            state.lastEvidenceWeak = shouldTreatAsEvidenceWeak(queryResponse, searchItems, responseSources);
            renderReadinessCard();
            renderResultGuide();
            syncAskFaqOpenState();
            setStatus(state.lastEvidenceWeak ? "回答已生成，但当前证据仍偏弱，请结合来源继续判断" : "回答已生成", state.lastEvidenceWeak ? "warning" : "success");
        }
        catch (error) {
            renderFailureState();
            renderGlobalResultError("知识问答失败", error);
            state.lastQueryFailed = true;
            state.lastQueryError = error && error.message ? error.message : "";
            state.lastAnswerOutcome = "";
            state.lastGenerationMode = "";
            state.lastModelExecutionStatus = "";
            state.lastReviewStatus = "";
            state.lastQueryId = "";
            state.lastAnswerHasCitation = false;
            state.lastAnswerEmpty = true;
            state.lastSupportSourceCount = 0;
            state.lastEvidenceSourceCount = 0;
            state.lastEvidenceWeak = true;
            renderReadinessCard();
            renderResultGuide();
            syncAskFaqOpenState();
            showError("知识问答失败", error);
        }
    }

    function clearQuestion() {
        document.getElementById("ask-question").value = "";
        state.lastQueryFailed = false;
        state.lastQueryError = "";
        state.lastAnswerOutcome = "";
        state.lastGenerationMode = "";
        state.lastModelExecutionStatus = "";
        state.lastReviewStatus = "";
        state.lastQueryId = "";
        state.lastAnswerHasCitation = false;
        state.lastAnswerEmpty = true;
        state.lastSupportSourceCount = 0;
        state.lastEvidenceSourceCount = 0;
        state.lastEvidenceWeak = false;
        state.hasSubmitted = false;
        resetAnswerExperience();
        renderReadinessCard();
        renderResultGuide();
        syncAskFaqOpenState();
        setStatus("已清空问题", "success");
    }

    function renderLoadingState() {
        document.getElementById("ask-answer").innerHTML = "<p>正在生成回答...</p>";
        document.getElementById("ask-answer-metrics").innerHTML = "<div class='job-card'><p class='item-summary'>正在判断这次回答的结果态、模型态、证据态和复核态...</p></div>";
        document.getElementById("ask-answer-support").innerHTML = "<strong>回答依据</strong><p>正在整理本次回答最直接依赖的来源...</p>";
        document.getElementById("ask-source-summary").innerHTML = "<strong>证据分层</strong><p>正在区分直接支撑来源和补充检索命中...</p>";
        document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>正在整理引用来源...</p></div>";
    }

    function renderFailureState() {
        document.getElementById("ask-answer").innerHTML = "<p>暂时无法生成回答，请稍后再试。</p>";
        document.getElementById("ask-answer-metrics").innerHTML = [
            renderMetricCard("回答状态", "回答失败", "本次没有拿到可用答案，请先看顶部报错信息。", "danger"),
            renderMetricCard("生成方式", "未生成", "主链在返回结果前已经中断，没有可展示的生成方式。", "danger"),
            renderMetricCard("模型执行", "未执行", "本次没有拿到可展示的模型执行状态。", "warning"),
            renderMetricCard("证据状态", "未返回", "这次没有成功拿到可展示的直接来源或检索证据。", "danger"),
            renderMetricCard("复核状态", "未执行", "主链在生成回答前就已经中断。", "warning")
        ].join("");
        document.getElementById("ask-answer-support").innerHTML = "<strong>回答依据</strong><p>这次没有成功拿到可用来源。先判断是知识库未准备好，还是服务 / 配置层面的问题。</p>";
        document.getElementById("ask-source-summary").innerHTML = "<strong>证据分层</strong><p>本次没有返回可展示的证据。优先先看报错，再决定是回工作台还是去系统配置。</p>";
        document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>本次未能加载引用来源。</p></div>";
    }

    function resetAnswerExperience() {
        toggleAskResultExperience(false);
        document.getElementById("ask-answer").innerHTML = "<p>还没有回答，先输入一个问题。</p>";
        document.getElementById("ask-answer-metrics").innerHTML = [
            renderMetricCard("回答状态", "等待提问", "提交问题后，这里会显示这次是“回答成功”“证据不足”“无相关知识”还是“部分答案”。", "info"),
            renderMetricCard("生成方式", "等待返回", "会区分这次答案是模型生成、规则直出，还是模型失败后的降级结果。", "info"),
            renderMetricCard("模型执行", "等待返回", "如果主链返回 modelExecutionStatus，这里会同步标出来。", "info"),
            renderMetricCard("证据状态", "等待命中", "会区分“直接支撑回答”的来源和“补充检索命中”的资料。", "info"),
            renderMetricCard("复核状态", "等待返回", "如果主链返回 reviewStatus，这里会同步标出来。", "info")
        ].join("");
        document.getElementById("ask-answer-support").innerHTML = "<strong>回答依据</strong><p>提交问题后，这里会显示这次回答最直接依赖的来源，以及是否已经形成稳定引用。</p>";
        document.getElementById("ask-source-summary").innerHTML = "<strong>证据分层</strong><p>这里会区分“回答直接引用的来源”和“本次检索命中的补充证据”，避免所有来源平铺在一起。</p>";
        document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>还没有引用来源，先提交一个问题。</p></div>";
    }

    function toggleAskResultExperience(visible) {
        const resultPanel = document.getElementById("ask-result-panel");
        const sourcePanel = document.getElementById("ask-source-panel");
        if (resultPanel) {
            resultPanel.hidden = !visible;
        }
        if (sourcePanel) {
            sourcePanel.hidden = !visible;
        }
    }

    function renderReadiness(overview, jobs) {
        const status = overview.status || {};
        const activeJobs = jobs.filter(function (item) {
            const normalized = String(item.status || "").toUpperCase();
            return normalized === "MATCHING"
                    || normalized === "MATERIALIZING"
                    || normalized === "COMPILE_QUEUED"
                    || normalized === "QUEUED"
                    || normalized === "RUNNING";
        });
        const waitConfirmJobs = jobs.filter(function (item) {
            return String(item.status || "").toUpperCase() === "WAIT_CONFIRM";
        });
        const hint = document.getElementById("ask-ready-hint");
        const askButton = document.getElementById("submit-question");
        state.knowledgeReady = (status.articleCount || 0) > 0 && (status.sourceFileCount || 0) > 0;
        state.knowledgeProcessing = activeJobs.length > 0;
        state.knowledgeWaitingConfirm = waitConfirmJobs.length > 0;
        if ((status.articleCount || 0) === 0 || (status.sourceFileCount || 0) === 0) {
            canAsk = false;
            askButton.disabled = true;
            hint.textContent = "当前还没有可用资料，请先去“工作台”上传文档并等待处理完成。";
            renderReadinessCard();
            syncAskFaqOpenState();
            return;
        }
        canAsk = true;
        askButton.disabled = false;
        if (waitConfirmJobs.length > 0) {
            hint.textContent = "当前有资料包等待人工确认归并方式，部分最新资料可能尚未入库。";
            renderReadinessCard();
            syncAskFaqOpenState();
            return;
        }
        if (activeJobs.length > 0) {
            hint.textContent = "知识库仍在处理中，你可以先提问；如果回答不完整，等任务完成后再试一次。";
            renderReadinessCard();
            syncAskFaqOpenState();
            return;
        }
        hint.textContent = "知识库已经准备就绪，可以直接提问。";
        renderReadinessCard();
        syncAskFaqOpenState();
    }

    function handleAskHelpActionClick(event) {
        const trigger = event.target.closest("[data-ask-help-action]");
        if (!trigger) {
            return;
        }
        const action = trigger.dataset.askHelpAction;
        if (action === "go-management") {
            window.location.assign("/admin");
            return;
        }
        if (action === "go-articles") {
            window.location.assign("/admin?tab=knowledge-articles");
            return;
        }
        if (action === "go-runs") {
            window.location.assign("/admin?tab=knowledge-runs");
            return;
        }
        if (action === "go-settings") {
            window.location.assign("/admin/settings");
            return;
        }
        if (action === "refresh-readiness") {
            refreshReadiness();
            return;
        }
        if (action === "retry-question") {
            submitQuestion();
        }
    }

    function renderReadinessCard() {
        const container = document.getElementById("ask-help-card");
        if (!container) {
            return;
        }
        let tone = "info";
        let title = "正在检查知识库是否已经准备就绪";
        let description = "页面会根据知识库状态、当前处理任务和本次问答结果，告诉你现在应该直接提问、等待处理完成，还是先回工作台。";
        let actions = [
            {label: "查看知识库状态", action: "refresh-readiness", className: "secondary-btn"}
        ];

        if (state.lastQueryFailed) {
            tone = "danger";
            title = "这次提问失败了";
            description = "先判断是知识库未准备好，还是服务或配置问题。如果是资料相关问题，优先回工作台；如果是模型连接或解析问题，再去系统配置。";
            actions = [
                {label: "回工作台", action: "go-management", className: "primary-btn"},
                {label: "去系统配置", action: "go-settings", className: "ghost-btn"}
            ];
        }
        else if (!state.knowledgeReady) {
            tone = "warning";
            title = "当前还不能稳定提问";
            description = "知识库里还没有可用内容，或者资料还没有真正进入可问答状态。先回工作台确认资料是否已经成功入库。";
            actions = [
                {label: "回工作台", action: "go-management", className: "primary-btn"},
                {label: "刷新知识库状态", action: "refresh-readiness", className: "ghost-btn"}
            ];
        }
        else if (state.knowledgeWaitingConfirm) {
            tone = "warning";
            title = "最新资料还在等待人工确认";
            description = "现在可以继续提问，但最新那批资料可能还没有进入知识库。先看当前处理任务，确认是否还有待人工判断的任务。";
            actions = [
                {label: "查看当前处理任务", action: "go-runs", className: "primary-btn"},
                {label: "继续提问", action: "retry-question", className: "ghost-btn"}
            ];
        }
        else if (state.knowledgeProcessing) {
            tone = "warning";
            title = "知识库还在处理中";
            description = "现在可以先试着提问；如果答案不完整，等处理完成后再试一次。不要把处理中直接误判成问答系统故障。";
            actions = [
                {label: "查看知识库状态", action: "refresh-readiness", className: "primary-btn"},
                {label: "查看当前处理任务", action: "go-runs", className: "ghost-btn"}
            ];
        }
        else if (state.knowledgeReady) {
            tone = "success";
            title = "知识库已经准备就绪";
            description = "现在可以直接提问；如果回答没有引用或明显不准，优先检查资料是否缺失、过旧或尚未稳定入库。";
            actions = [
                {label: "查看已入库内容", action: "go-articles", className: "primary-btn"},
                {label: "刷新知识库状态", action: "refresh-readiness", className: "ghost-btn"}
            ];
        }

        container.setAttribute("data-help-tone", tone);
        container.innerHTML = "<p class='help-card-eyebrow'>可否开始提问</p>"
                + "<h2 class='help-card-title'>" + escapeHtml(title) + "</h2>"
                + "<p class='help-card-description'>" + escapeHtml(description) + "</p>"
                + renderAskHelpActions(actions);
    }

    function renderResultGuide() {
        const container = document.getElementById("ask-result-guide");
        if (!container) {
            return;
        }
        let title = "这次结果怎么看";
        let description = "提交一个问题后，这里会告诉你本次结果更像是“可以继续看直接来源”，还是“应该先回工作台检查资料”。";
        let actions = [];

        if (state.lastQueryFailed) {
            title = "这次没有成功返回结果";
            description = "先看顶部报错信息，再判断是知识库没准备好，还是模型连接、文档解析或后台配置有问题。";
            actions = [
                {label: "回工作台", action: "go-management", className: "secondary-btn"},
                {label: "去系统配置", action: "go-settings", className: "ghost-btn"}
            ];
        }
        else if (!state.lastAnswerEmpty && !state.lastEvidenceWeak && state.lastAnswerHasCitation) {
            title = "这次回答已经带了稳定来源";
            description = "先看“回答依据”里的直接来源，再往下看补充检索证据。若答案仍不准，再回工作台核对对应资料是否缺失或过旧。";
            actions = [
                {label: "去已入库内容", action: "go-articles", className: "secondary-btn"}
            ];
        }
        else if (!state.lastAnswerEmpty && state.lastEvidenceWeak) {
            title = "这次有回答，但当前证据仍偏弱";
            description = "优先检查资料是否真的已经入库，以及这次是否只有检索命中、没有直接来源。证据不足时，不要把回答内容直接当成确定结论。";
            actions = [
                {label: "去已入库内容", action: "go-articles", className: "secondary-btn"},
                {label: "回工作台", action: "go-management", className: "ghost-btn"}
            ];
        }

        container.innerHTML = "<strong>" + escapeHtml(title) + "</strong>"
                + "<p>" + escapeHtml(description) + "</p>"
                + renderAskHelpActions(actions);
    }

    function renderAskHelpActions(actions) {
        if (!actions || actions.length === 0) {
            return "";
        }
        return "<div class='help-action-row'>"
                + actions.map(function (item) {
                    return "<button class='" + escapeHtml(item.className || "ghost-btn")
                            + "' type='button' data-ask-help-action='" + escapeHtml(item.action || "") + "'>"
                            + escapeHtml(item.label || "继续查看")
                            + "</button>";
                }).join("")
                + "</div>";
    }

    function hasAskCitations(searchItems, responseSources) {
        return (Array.isArray(searchItems) && searchItems.length > 0)
                || (Array.isArray(responseSources) && responseSources.length > 0);
    }

    function uniqueSourceCount(searchItems, responseSources) {
        const keys = new Set();
        (responseSources || []).forEach(function (item) {
            keys.add(buildSourceIdentity(item));
        });
        (searchItems || []).forEach(function (item) {
            keys.add(buildSourceIdentity(item));
        });
        return keys.size;
    }

    function shouldTreatAsEvidenceWeak(result, searchItems, responseSources) {
        const answer = String(result && result.answer ? result.answer : "").trim();
        const answerOutcome = normalizeStatusValue(result && result.answerOutcome);
        const generationMode = normalizeStatusValue(result && result.generationMode);
        const modelExecutionStatus = normalizeStatusValue(result && result.modelExecutionStatus);
        if (!answer) {
            return true;
        }
        if (answerOutcome === "INSUFFICIENT_EVIDENCE"
                || answerOutcome === "NO_RELEVANT_KNOWLEDGE"
                || answerOutcome === "PARTIAL_ANSWER") {
            return true;
        }
        if (generationMode === "FALLBACK" || modelExecutionStatus === "FAILED") {
            return true;
        }
        if (containsWeakAnswerMarker(answer)) {
            return true;
        }
        if ((responseSources || []).length > 0) {
            return false;
        }
        if ((searchItems || []).length === 0) {
            return true;
        }
        const reviewStatus = String(result && result.reviewStatus ? result.reviewStatus : "").toUpperCase();
        return reviewStatus === "ISSUES_FOUND"
                || reviewStatus === "PARSE_FAILED"
                || reviewStatus === "TIMEOUT_FALLBACK";
    }

    function containsWeakAnswerMarker(answer) {
        const markers = [
            "未找到相关知识",
            "当前证据不足",
            "暂无法确认",
            "本次没有返回可展示的回答"
        ];
        return markers.some(function (marker) {
            return answer.indexOf(marker) >= 0;
        });
    }

    function syncAskFaqOpenState() {
        const container = document.getElementById("ask-faq-list");
        if (!container) {
            return;
        }
        let targetKey = "answer-quality";
        if (state.lastQueryFailed) {
            targetKey = "query-failed";
        }
        else if (!state.knowledgeReady) {
            targetKey = "not-ready";
        }
        else if (!state.lastAnswerEmpty && (state.lastEvidenceWeak || !state.lastAnswerHasCitation)) {
            targetKey = "no-citation";
        }
        const panels = Array.from(container.querySelectorAll("[data-help-faq-key]"));
        const target = panels.find(function (panel) {
            return panel.dataset.helpFaqKey === targetKey;
        }) || panels[0];
        panels.forEach(function (panel) {
            panel.open = panel === target;
        });
    }

    function renderAnswer(result) {
        const answer = String(result && result.answer ? result.answer : "").trim();
        const container = document.getElementById("ask-answer");
        if (!answer) {
            container.innerHTML = "<p>本次没有返回可展示的回答。</p>";
            return;
        }
        container.innerHTML = renderMarkdownLite(answer);
    }

    function renderAnswerMetrics(result, searchItems, responseSources) {
        const answerMeta = getAnswerOutcomeMeta(result && result.answerOutcome);
        const generationMeta = getGenerationModeMeta(result && result.generationMode);
        const modelExecutionMeta = getModelExecutionStatusMeta(result && result.modelExecutionStatus, result && result.generationMode);
        const reviewMeta = getReviewStatusMeta(result && result.reviewStatus);
        const citationCount = uniqueSourceCount(searchItems, responseSources);
        const metrics = [
            renderMetricCard(
                    "回答状态",
                    answerMeta.label,
                    answerMeta.note,
                    answerMeta.tone
            ),
            renderMetricCard(
                    "生成方式",
                    generationMeta.label,
                    generationMeta.note,
                    generationMeta.tone
            ),
            renderMetricCard(
                    "模型执行",
                    modelExecutionMeta.label,
                    modelExecutionMeta.note,
                    modelExecutionMeta.tone
            ),
            renderMetricCard(
                    "证据状态",
                    responseSources.length > 0 ? "直接来源 " + responseSources.length + " 条" : "直接来源不足",
                    responseSources.length > 0
                            ? "另有补充检索命中 " + Math.max(citationCount - responseSources.length, 0) + " 条。"
                            : "当前只有补充命中或完全没有命中，可信度需要更谨慎判断。",
                    responseSources.length > 0 ? "success" : "warning"
            ),
            renderMetricCard(
                    "复核状态",
                    reviewMeta.label,
                    reviewMeta.note,
                    reviewMeta.tone
            ),
            renderMetricCard(
                    "检索覆盖",
                    citationCount > 0 ? citationCount + " 条来源" : "未命中来源",
                    citationCount > 0
                            ? "这次共整理出 " + citationCount + " 条可展示来源。"
                            : "当前没有稳定命中的来源，需要先回工作台排查。",
                    citationCount > 0 ? "info" : "danger"
            )
        ];
        document.getElementById("ask-answer-metrics").innerHTML = metrics.join("");
    }

    function renderAnswerSupport(result, searchItems, responseSources) {
        const container = document.getElementById("ask-answer-support");
        const primaryLabels = (responseSources || []).map(function (item) {
            return item.title || item.conceptId || "未命名来源";
        }).slice(0, 4);
        const answerMeta = getAnswerOutcomeMeta(result && result.answerOutcome);
        if (primaryLabels.length > 0) {
            container.innerHTML = "<strong>回答依据</strong>"
                    + "<p>这次回答状态为“" + escapeHtml(answerMeta.label) + "”，并且已经直接挂上了以下来源，你可以先从这里判断答案是否站得住：</p>"
                    + "<div class='help-action-row'>"
                    + primaryLabels.map(function (label) {
                        return "<span class='pill'>" + escapeHtml(label) + "</span>";
                    }).join("")
                    + "</div>";
            return;
        }
        if ((searchItems || []).length > 0) {
            container.innerHTML = "<strong>回答依据</strong>"
                    + "<p>这次回答状态为“" + escapeHtml(answerMeta.label) + "”，虽然命中了资料，但还没有形成稳定的“直接来源”。请优先往下看补充检索证据，再决定这次回答能不能直接采信。</p>";
            return;
        }
        const reviewMeta = getReviewStatusMeta(result && result.reviewStatus);
        container.innerHTML = "<strong>回答依据</strong>"
                + "<p>这次回答状态为“" + escapeHtml(answerMeta.label) + "”，且没有成功拿到稳定来源。"
                + escapeHtml(reviewMeta.label ? "复核状态：" + reviewMeta.label + "。" : "")
                + "优先回工作台确认对应资料是否真的已经入库。</p>";
    }

    function renderSourceSummary(result, searchItems, responseSources) {
        const container = document.getElementById("ask-source-summary");
        const answerMeta = getAnswerOutcomeMeta(result && result.answerOutcome);
        const reviewMeta = getReviewStatusMeta(result && result.reviewStatus);
        if ((responseSources || []).length > 0) {
            container.innerHTML = "<strong>证据分层</strong>"
                    + "<p>先看“直接支撑本次回答”的来源，再看“补充检索命中”的资料。当前回答状态："
                    + escapeHtml(answerMeta.label)
                    + "；复核状态："
                    + escapeHtml(reviewMeta.label)
                    + "。</p>";
            return;
        }
        if ((searchItems || []).length > 0) {
            container.innerHTML = "<strong>证据分层</strong>"
                    + "<p>这次只有补充检索命中，没有稳定的直接来源。当前回答状态是“"
                    + escapeHtml(answerMeta.label)
                    + "”，更适合把结果当成“继续追资料的线索”，而不是最终结论。</p>";
            return;
        }
        container.innerHTML = "<strong>证据分层</strong><p>本次回答状态是“"
                + escapeHtml(answerMeta.label)
                + "”，但没有返回可展示的证据。优先先看报错或回工作台确认资料入库状态。</p>";
    }

    function renderSources(searchItems, responseSources) {
        const container = document.getElementById("ask-sources");
        const searchMap = buildSearchSnippetMap(searchItems || []);
        const primaryCards = buildPrimarySourceCards(responseSources || [], searchMap);
        const secondaryCards = buildSecondarySourceCards(searchItems || [], responseSources || []);
        if (primaryCards.length === 0 && secondaryCards.length === 0) {
            container.innerHTML = "<div class='job-card'><p class='item-summary'>本次没有返回可展示的引用来源。</p></div>";
            return;
        }
        const sections = [];
        if (primaryCards.length > 0) {
            sections.push(renderSourceSection(
                    "直接支撑本次回答",
                    "这些来源已经直接出现在最终回答的来源列表里，优先先看这里。",
                    primaryCards
            ));
        }
        if (secondaryCards.length > 0) {
            sections.push(renderSourceSection(
                    primaryCards.length > 0 ? "补充检索命中" : "本次命中的检索证据",
                    primaryCards.length > 0
                            ? "这些资料说明本次检索还命中了更多证据，可继续往下追溯。"
                            : "这次只有检索命中，还没有形成稳定的直接来源。",
                    secondaryCards
            ));
        }
        container.innerHTML = sections.join("");
    }

    function buildSearchSnippetMap(searchItems) {
        const map = new Map();
        (searchItems || []).forEach(function (item) {
            const key = buildSourceIdentity(item);
            if (!map.has(key)) {
                map.set(key, trimSnippet(item.content || "暂无片段"));
            }
        });
        return map;
    }

    function buildPrimarySourceCards(responseSources, searchMap) {
        return (responseSources || []).map(function (item, index) {
            const key = buildSourceIdentity(item);
            return renderSourceCard({
                title: item.title || item.conceptId || "未命名来源",
                snippet: searchMap.get(key) || "这条来源已经被最终回答直接引用，可继续结合原始资料判断答案是否可靠。",
                sourcePaths: item.sourcePaths || [],
                badge: "DIRECT",
                rankLabel: "直接依据 " + (index + 1),
                primary: true
            });
        });
    }

    function buildSecondarySourceCards(searchItems, responseSources) {
        const primaryKeys = new Set((responseSources || []).map(function (item) {
            return buildSourceIdentity(item);
        }));
        return (searchItems || []).filter(function (item) {
            return !primaryKeys.has(buildSourceIdentity(item));
        }).map(function (item, index) {
            return renderSourceCard({
                title: item.title || item.conceptId || "未命名来源",
                snippet: trimSnippet(item.content || "暂无片段"),
                sourcePaths: item.sourcePaths || [],
                badge: item.evidenceType || "SOURCE",
                rankLabel: "补充证据 " + (index + 1),
                primary: false
            });
        });
    }

    function renderSourceSection(title, description, cards) {
        return "<section class='source-section'>"
                + "<div class='panel-title-row'>"
                + "<div>"
                + "<h3>" + escapeHtml(title) + "</h3>"
                + "<p>" + escapeHtml(description) + "</p>"
                + "</div>"
                + "</div>"
                + "<div class='source-grid top-gap'>" + cards.join("") + "</div>"
                + "</section>";
    }

    function renderSourceCard(options) {
        return "<article class='source-card" + (options.primary ? " source-card-primary" : "") + "'>"
                + "<div class='meta-row'>"
                + "<span class='pill'>" + escapeHtml(options.rankLabel || "来源") + "</span>"
                + renderBadge(options.badge || "SOURCE")
                + "</div>"
                + "<h4>" + escapeHtml(options.title || "未命名来源") + "</h4>"
                + "<p class='source-snippet'>" + escapeHtml(options.snippet || "暂无片段") + "</p>"
                + "<div class='tag-list'>" + renderTagGroup(options.sourcePaths || []) + "</div>"
                + "</article>";
    }

    function buildSourceIdentity(item) {
        const title = item && item.title ? item.title : "";
        const conceptId = item && item.conceptId ? item.conceptId : "";
        const paths = Array.isArray(item && item.sourcePaths ? item.sourcePaths : [])
                ? item.sourcePaths.join("|")
                : "";
        return title + "::" + conceptId + "::" + paths;
    }

    function trimSnippet(content) {
        const normalized = String(content || "").trim();
        if (normalized.length <= 180) {
            return normalized || "暂无片段";
        }
        return normalized.substring(0, 180) + "...";
    }

    function renderTagGroup(items) {
        if (!items || items.length === 0) {
            return "<span class='pill'>暂无</span>";
        }
        return items.map(function (item) {
            return "<span class='pill'>" + escapeHtml(item) + "</span>";
        }).join("");
    }

    function renderMetricCard(label, value, note, tone) {
        return "<article class='metric-card" + (tone ? " " + tone : "") + "'>"
                + "<span class='label'>" + escapeHtml(label || "-") + "</span>"
                + "<p class='value'>" + escapeHtml(value || "-") + "</p>"
                + "<p class='note'>" + escapeHtml(note || "") + "</p>"
                + "</article>";
    }

    function normalizeStatusValue(value) {
        return String(value || "").trim().toUpperCase();
    }

    function getAnswerOutcomeMeta(value) {
        const normalized = normalizeStatusValue(value);
        const mapping = {
            SUCCESS: {
                label: "回答成功",
                note: "这次已经拿到了可直接阅读的答案，优先继续结合直接来源确认细节。",
                tone: "success"
            },
            INSUFFICIENT_EVIDENCE: {
                label: "证据不足",
                note: "当前知识库里有相关资料，但还不足以支撑完整结论，需要继续补资料或人工判断。",
                tone: "warning"
            },
            NO_RELEVANT_KNOWLEDGE: {
                label: "无相关知识",
                note: "当前知识库没有命中可直接回答这个问题的资料，优先先补知识。",
                tone: "warning"
            },
            PARTIAL_ANSWER: {
                label: "部分答案",
                note: "这次只拿到了部分可用信息，仍需要结合来源继续判断。",
                tone: "warning"
            }
        };
        return mapping[normalized] || {
            label: normalized ? normalized : "未标注",
            note: normalized ? "当前返回了 answerOutcome，但还没有专门的人话说明。" : "这次没有返回额外的回答状态。",
            tone: normalized ? "warning" : "info"
        };
    }

    function getGenerationModeMeta(value) {
        const normalized = normalizeStatusValue(value);
        const mapping = {
            LLM: {
                label: "模型生成",
                note: "答案来自主链模型生成，通常要继续结合来源判断是否可靠。",
                tone: "success"
            },
            RULE_BASED: {
                label: "规则直出",
                note: "这次没有走模型生成，而是直接使用规则或已有证据拼出了结果。",
                tone: "info"
            },
            FALLBACK: {
                label: "降级结果",
                note: "主链没有拿到稳定的结构化输出，当前结果来自降级兜底。",
                tone: "warning"
            }
        };
        return mapping[normalized] || {
            label: normalized ? normalized : "未标注",
            note: normalized ? "当前返回了 generationMode，但还没有专门的人话说明。" : "这次没有返回额外的生成方式。",
            tone: normalized ? "warning" : "info"
        };
    }

    function getModelExecutionStatusMeta(value, generationMode) {
        const normalized = normalizeStatusValue(value);
        const normalizedGenerationMode = normalizeStatusValue(generationMode);
        const mapping = {
            SUCCESS: {
                label: "模型执行成功",
                note: "模型这次成功返回了结果，当前可以重点看答案状态和来源状态。",
                tone: "success"
            },
            SKIPPED: {
                label: "未调用模型",
                note: normalizedGenerationMode === "RULE_BASED"
                        ? "这次直接走了规则路径，没有消耗模型调用。"
                        : "这次没有执行模型调用。",
                tone: "info"
            },
            FAILED: {
                label: "模型失败已降级",
                note: "模型执行没有拿到稳定结果，系统已经降级为兜底答案，请谨慎使用。",
                tone: "warning"
            }
        };
        return mapping[normalized] || {
            label: normalized ? normalized : "未标注",
            note: normalized ? "当前返回了 modelExecutionStatus，但还没有专门的人话说明。" : "这次没有返回额外的模型执行状态。",
            tone: normalized ? "warning" : "info"
        };
    }

    function getReviewStatusMeta(value) {
        const normalized = normalizeStatusValue(value);
        const mapping = {
            PASSED: {
                label: "已复核通过",
                note: "回答与当前证据基本一致，可以继续结合直接来源判断细节。",
                tone: "success"
            },
            ISSUES_FOUND: {
                label: "复核发现风险",
                note: "主链检测到需要继续修正的问题，这次回答更适合作为参考线索而非最终定论。",
                tone: "warning"
            },
            PARSE_RESCUED: {
                label: "解析已兜底",
                note: "复核结果经过兜底解析，建议更谨慎地结合来源继续判断。",
                tone: "warning"
            },
            PARSE_FAILED: {
                label: "复核解析失败",
                note: "复核结果没有稳定解析出来，请优先结合原始来源判断可靠性。",
                tone: "danger"
            },
            TIMEOUT_FALLBACK: {
                label: "复核超时回退",
                note: "本次复核没有按正常路径完成，回答可信度需要结合来源再确认。",
                tone: "warning"
            }
        };
        return mapping[normalized] || {
            label: normalized ? normalized : "未标注",
            note: normalized ? "当前返回了 reviewStatus，但还没有专门的人话说明。" : "这次没有返回额外的复核状态。",
            tone: normalized ? "warning" : "info"
        };
    }

    function renderBadge(value) {
        const normalized = (value || "").toUpperCase();
        let className = "badge";
        if (normalized === "ARTICLE_VECTOR" || normalized === "CHUNK_VECTOR" || normalized === "SOURCE" || normalized === "DIRECT") {
            className += " success";
        }
        else if (normalized) {
            className += " warning";
        }
        return "<span class='" + className + "'>" + escapeHtml(getBadgeLabel(value)) + "</span>";
    }

    function getBadgeLabel(value) {
        const normalized = (value || "").toUpperCase();
        const labels = {
            DIRECT: "回答直接来源",
            FTS: "全文检索",
            REFKEY: "关键词",
            SOURCE: "来源命中",
            CONTRIBUTION: "反馈命中",
            ARTICLE_VECTOR: "文章向量",
            CHUNK_VECTOR: "片段向量"
        };
        return labels[normalized] || value || "-";
    }

    function renderMarkdownLite(markdown) {
        const lines = String(markdown || "").replace(/\r/g, "").split("\n");
        const parts = [];
        let paragraphBuffer = [];
        let listItems = [];
        let listTag = "";

        function flushParagraph() {
            if (paragraphBuffer.length === 0) {
                return;
            }
            parts.push("<p>" + formatInlineMarkdown(paragraphBuffer.join(" ")) + "</p>");
            paragraphBuffer = [];
        }

        function flushList() {
            if (listItems.length === 0) {
                return;
            }
            parts.push("<" + listTag + ">" + listItems.join("") + "</" + listTag + ">");
            listItems = [];
            listTag = "";
        }

        lines.forEach(function (rawLine) {
            const line = String(rawLine || "");
            const trimmed = line.trim();
            if (!trimmed) {
                flushParagraph();
                flushList();
                return;
            }
            const headingMatch = trimmed.match(/^(#{1,3})\s+(.*)$/);
            if (headingMatch) {
                flushParagraph();
                flushList();
                const level = Math.min(headingMatch[1].length + 1, 4);
                parts.push("<h" + level + ">" + formatInlineMarkdown(headingMatch[2]) + "</h" + level + ">");
                return;
            }
            const bulletMatch = trimmed.match(/^[-*]\s+(.*)$/);
            if (bulletMatch) {
                flushParagraph();
                if (listTag && listTag !== "ul") {
                    flushList();
                }
                listTag = "ul";
                listItems.push("<li>" + formatInlineMarkdown(bulletMatch[1]) + "</li>");
                return;
            }
            const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/);
            if (orderedMatch) {
                flushParagraph();
                if (listTag && listTag !== "ol") {
                    flushList();
                }
                listTag = "ol";
                listItems.push("<li>" + formatInlineMarkdown(orderedMatch[1]) + "</li>");
                return;
            }
            if (trimmed.startsWith(">")) {
                flushParagraph();
                flushList();
                parts.push("<blockquote><p>" + formatInlineMarkdown(trimmed.replace(/^>\s?/, "")) + "</p></blockquote>");
                return;
            }
            if (listItems.length > 0) {
                flushList();
            }
            paragraphBuffer.push(trimmed);
        });
        flushParagraph();
        flushList();
        return parts.join("") || "<p>本次没有返回可展示的回答。</p>";
    }

    function formatInlineMarkdown(value) {
        return escapeHtml(value)
                .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
                .replace(/`([^`]+)`/g, "<code>$1</code>");
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

    function renderGlobalResult(result) {
        return result;
    }

    function renderGlobalResultError(prefix, error) {
        return {prefix: prefix, error: error};
    }

    function setStatus(message, tone) {
        const resolvedTone = tone || "info";
        const persist = resolvedTone === "danger" || resolvedTone === "warning";
        renderPageNotice(message, resolvedTone, persist);
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

    function escapeHtml(value) {
        return String(value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#39;");
    }
})();
