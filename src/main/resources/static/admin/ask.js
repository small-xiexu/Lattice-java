(function () {
    let canAsk = true;
    let pageNoticeTimer = null;

    document.addEventListener("DOMContentLoaded", function () {
        bindEvents();
        refreshReadiness();
    });

    function bindEvents() {
        document.getElementById("refresh-qa-status").addEventListener("click", refreshReadiness);
        document.getElementById("submit-question").addEventListener("click", submitQuestion);
        document.getElementById("clear-question").addEventListener("click", clearQuestion);
    }

    async function refreshReadiness() {
        try {
            const results = await Promise.all([
                fetchJson("/api/v1/admin/overview"),
                fetchJson("/api/v1/admin/jobs")
            ]);
            renderReadiness(results[0], results[1].items || []);
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
            setStatus("回答已生成", "success");
        }
        catch (error) {
            document.getElementById("ask-answer").textContent = "暂时无法生成回答，请稍后再试。";
            document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>本次未能加载引用来源。</p></div>";
            renderGlobalResultError("知识问答失败", error);
            showError("知识问答失败", error);
        }
    }

    function clearQuestion() {
        document.getElementById("ask-question").value = "";
        document.getElementById("ask-answer").textContent = "还没有回答，先输入一个问题。";
        document.getElementById("ask-sources").innerHTML = "<div class='job-card'><p class='item-summary'>还没有引用来源，先提交一个问题。</p></div>";
        setStatus("已清空问题", "success");
    }

    function renderReadiness(overview, jobs) {
        const status = overview.status || {};
        const activeJobs = jobs.filter(function (item) {
            const normalized = String(item.status || "").toUpperCase();
            return normalized === "QUEUED" || normalized === "RUNNING";
        });
        const hint = document.getElementById("ask-ready-hint");
        const askButton = document.getElementById("submit-question");
        if ((status.articleCount || 0) === 0 || (status.sourceFileCount || 0) === 0) {
            canAsk = false;
            askButton.disabled = true;
            hint.textContent = "当前还没有可用资料，请先去“知识库管理”上传文档并等待处理完成。";
            return;
        }
        canAsk = true;
        askButton.disabled = false;
        if (activeJobs.length > 0) {
            hint.textContent = "知识库仍在处理中，你可以先提问；如果回答不完整，等任务完成后再试一次。";
            return;
        }
        hint.textContent = "知识库已经准备就绪，可以直接提问。";
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
