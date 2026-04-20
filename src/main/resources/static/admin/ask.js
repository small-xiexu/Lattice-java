(function () {
    let canAsk = true;
    let pageNoticeTimer = null;
    const state = {
        knowledgeReady: false,
        knowledgeProcessing: false,
        knowledgeWaitingConfirm: false,
        lastQueryFailed: false,
        lastQueryError: "",
        lastAnswerHasCitation: false,
        lastAnswerEmpty: true
    };

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        refreshReadiness();
        renderReadinessCard();
        renderResultGuide();
    });

    function bindEvents() {
        document.getElementById("refresh-qa-status").addEventListener("click", refreshReadiness);
        document.getElementById("submit-question").addEventListener("click", submitQuestion);
        document.getElementById("clear-question").addEventListener("click", clearQuestion);
        document.addEventListener("click", handleAskHelpActionClick);
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
        document.getElementById("ask-answer").textContent = "正在生成回答...";
        document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>正在整理引用来源...</p></div>";
        setStatus("正在生成回答...");
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
            renderAnswer(results[0]);
            renderSources(results[1].items || [], results[0].sources || []);
            renderGlobalResult(results[0]);
            state.lastQueryFailed = false;
            state.lastQueryError = "";
            state.lastAnswerHasCitation = hasAskCitations(results[1].items || [], results[0].sources || []);
            state.lastAnswerEmpty = !String(results[0] && results[0].answer ? results[0].answer : "").trim();
            renderResultGuide();
            syncAskFaqOpenState();
            setStatus("回答已生成", "success");
        }
        catch (error) {
            document.getElementById("ask-answer").textContent = "暂时无法生成回答，请稍后再试。";
            document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>本次未能加载引用来源。</p></div>";
            renderGlobalResultError("知识问答失败", error);
            state.lastQueryFailed = true;
            state.lastQueryError = error && error.message ? error.message : "";
            state.lastAnswerHasCitation = false;
            state.lastAnswerEmpty = true;
            renderReadinessCard();
            renderResultGuide();
            syncAskFaqOpenState();
            showError("知识问答失败", error);
        }
    }

    function clearQuestion() {
        document.getElementById("ask-question").value = "";
        document.getElementById("ask-answer").textContent = "还没有回答，先输入一个问题。";
        document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>还没有引用来源，先提交一个问题。</p></div>";
        state.lastQueryFailed = false;
        state.lastQueryError = "";
        state.lastAnswerHasCitation = false;
        state.lastAnswerEmpty = true;
        renderReadinessCard();
        renderResultGuide();
        syncAskFaqOpenState();
        setStatus("已清空问题", "success");
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
            hint.textContent = "当前还没有可用资料，请先去“知识库管理”上传文档并等待处理完成。";
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
        let description = "页面会根据知识库状态、最近同步运行和本次问答结果，告诉你现在应该直接提问、等待处理完成，还是先回知识库管理。";
        let actions = [
            {label: "查看知识库状态", action: "refresh-readiness", className: "secondary-btn"}
        ];

        if (state.lastQueryFailed) {
            tone = "danger";
            title = "这次提问失败了";
            description = "先判断是知识库未准备好，还是服务或配置问题。如果是资料相关问题，优先回知识库管理；如果是模型连接或解析问题，再去管理员设置。";
            actions = [
                {label: "去知识库管理", action: "go-management", className: "primary-btn"},
                {label: "去管理员设置", action: "go-settings", className: "ghost-btn"}
            ];
        }
        else if (!state.knowledgeReady) {
            tone = "warning";
            title = "当前还不能稳定提问";
            description = "知识库里还没有可用内容，或者资料还没有真正进入可问答状态。先回知识库管理确认资料是否已经成功入库。";
            actions = [
                {label: "去知识库管理", action: "go-management", className: "primary-btn"},
                {label: "刷新知识库状态", action: "refresh-readiness", className: "ghost-btn"}
            ];
        }
        else if (state.knowledgeWaitingConfirm) {
            tone = "warning";
            title = "最新资料还在等待人工确认";
            description = "现在可以继续提问，但最新那批资料可能还没有进入知识库。先看最近同步运行，确认是否还有待人工判断的任务。";
            actions = [
                {label: "查看最近同步运行", action: "go-runs", className: "primary-btn"},
                {label: "继续提问", action: "retry-question", className: "ghost-btn"}
            ];
        }
        else if (state.knowledgeProcessing) {
            tone = "warning";
            title = "知识库还在处理中";
            description = "现在可以先试着提问；如果答案不完整，等处理完成后再试一次。不要把处理中直接误判成问答系统故障。";
            actions = [
                {label: "查看知识库状态", action: "refresh-readiness", className: "primary-btn"},
                {label: "查看最近同步运行", action: "go-runs", className: "ghost-btn"}
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
        let description = "提交一个问题后，这里会告诉你本次结果更像是“可以继续看引用”，还是“应该先回知识库管理检查资料”。";
        let actions = [];

        if (state.lastQueryFailed) {
            title = "这次没有成功返回结果";
            description = "先看顶部报错信息，再判断是知识库没准备好，还是模型连接、文档解析或后台配置有问题。";
            actions = [
                {label: "去知识库管理", action: "go-management", className: "secondary-btn"},
                {label: "去管理员设置", action: "go-settings", className: "ghost-btn"}
            ];
        }
        else if (!state.lastAnswerEmpty && state.lastAnswerHasCitation) {
            title = "这次回答带了引用来源";
            description = "可以继续结合引用来源判断回答是否可靠；如果答案仍然不准，再回知识库管理核对对应资料是否缺失或过旧。";
            actions = [
                {label: "去已入库内容", action: "go-articles", className: "secondary-btn"}
            ];
        }
        else if (!state.lastAnswerEmpty) {
            title = "这次回答没有稳定引用";
            description = "优先检查相关资料是否真的已经入库，而不是先怀疑模型坏了。回答有内容但没有引用时，通常说明命中还不够稳。";
            actions = [
                {label: "去已入库内容", action: "go-articles", className: "secondary-btn"},
                {label: "回知识库管理", action: "go-management", className: "ghost-btn"}
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
        else if (!state.lastAnswerEmpty && !state.lastAnswerHasCitation) {
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
        document.getElementById("ask-answer").textContent = result && result.answer
                ? result.answer
                : "本次没有返回可展示的回答。";
    }

    function renderSources(searchItems, responseSources) {
        const container = document.getElementById("ask-sources");
        if (searchItems && searchItems.length > 0) {
            container.innerHTML = searchItems.map(function (item) {
                return "<article class='source-card'>"
                        + "<div class='meta-row'>"
                        + renderBadge(item.evidenceType || "SOURCE")
                        + "<span class='pill'>" + escapeHtml(item.title || item.conceptId || "未命名来源") + "</span>"
                        + "</div>"
                        + "<p class='source-snippet'>" + escapeHtml(trimSnippet(item.content || "暂无片段")) + "</p>"
                        + "<div class='tag-list'>" + renderTagGroup(item.sourcePaths || []) + "</div>"
                        + "</article>";
            }).join("");
            return;
        }
        if (responseSources && responseSources.length > 0) {
            container.innerHTML = responseSources.map(function (item) {
                return "<article class='source-card'>"
                        + "<div class='meta-row'>"
                        + "<span class='pill'>" + escapeHtml(item.title || item.conceptId || "未命名来源") + "</span>"
                        + "</div>"
                        + "<div class='tag-list'>" + renderTagGroup(item.sourcePaths || []) + "</div>"
                        + "</article>";
            }).join("");
            return;
        }
        container.innerHTML = "<div class='job-card'><p class='item-summary'>本次没有返回可展示的引用来源。</p></div>";
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

    function renderBadge(value) {
        const normalized = (value || "").toUpperCase();
        let className = "badge";
        if (normalized === "ARTICLE_VECTOR" || normalized === "CHUNK_VECTOR" || normalized === "SOURCE") {
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
            FTS: "全文检索",
            REFKEY: "关键词",
            SOURCE: "来源命中",
            CONTRIBUTION: "反馈命中",
            ARTICLE_VECTOR: "文章向量",
            CHUNK_VECTOR: "片段向量"
        };
        return labels[normalized] || value || "-";
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
