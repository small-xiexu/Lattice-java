(function (window) {
    function fetchJson(url, options) {
        const requestOptions = options || {};
        return fetch(url, {
            method: requestOptions.method || "GET",
            headers: requestOptions.isFormData ? {} : {"Content-Type": "application/json"},
            body: requestOptions.body
        }).then(function (response) {
            if (!response.ok) {
                return buildHttpError(response).then(function (error) {
                    throw error;
                });
            }
            const contentType = response.headers.get("content-type") || "";
            if (contentType.indexOf("application/json") >= 0) {
                return response.json();
            }
            return response.text();
        });
    }

    function buildHttpError(response) {
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            return response.json().then(function (payload) {
                const fallback = "HTTP " + response.status;
                const message = buildJsonErrorMessage(payload, fallback);
                const error = new Error(message || ("HTTP " + response.status));
                error.status = response.status;
                error.payload = payload;
                return error;
            });
        }
        return response.text().then(function (text) {
            const compactText = compactErrorText(text);
            const error = new Error(compactText || ("HTTP " + response.status));
            error.status = response.status;
            return error;
        });
    }

    function buildJsonErrorMessage(payload, fallback) {
        if (!payload || typeof payload !== "object") {
            return fallback;
        }
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
        return JSON.stringify(payload);
    }

    function compactErrorText(text) {
        const normalized = String(text || "").trim();
        return normalized.length > 180 ? normalized.slice(0, 180) + "..." : normalized;
    }

    function createPageNoticeApi(noticeId) {
        let noticeTimer = null;

        function resolveNotice() {
            return document.getElementById(noticeId);
        }

        function clearNoticeTimer() {
            if (!noticeTimer) {
                return;
            }
            window.clearTimeout(noticeTimer);
            noticeTimer = null;
        }

        function renderPageNotice(message, tone, persist) {
            const notice = resolveNotice();
            if (!notice) {
                return;
            }
            clearNoticeTimer();
            notice.hidden = false;
            notice.className = "page-notice" + (tone ? " " + tone : "");
            notice.textContent = message || "";
            if (!persist) {
                noticeTimer = window.setTimeout(function () {
                    notice.hidden = true;
                    notice.className = "page-notice";
                    notice.textContent = "";
                    noticeTimer = null;
                }, 3200);
            }
        }

        function setStatus(message, tone, persist) {
            const resolvedTone = tone || "info";
            const resolvedPersist = typeof persist === "boolean"
                    ? persist
                    : resolvedTone === "danger" || resolvedTone === "warning";
            renderPageNotice(message, resolvedTone, resolvedPersist);
        }

        function clearStatus() {
            const notice = resolveNotice();
            if (!notice) {
                return;
            }
            clearNoticeTimer();
            notice.hidden = true;
            notice.className = "page-notice";
            notice.textContent = "";
        }

        function showError(prefix, error) {
            const message = error && error.message ? error.message : String(error);
            setStatus(prefix + "：" + message, "danger");
        }

        return {
            clearStatus: clearStatus,
            renderPageNotice: renderPageNotice,
            setStatus: setStatus,
            showError: showError
        };
    }

    function escapeHtml(value) {
        return String(value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#39;");
    }

    function formatDateTime(value) {
        if (!value) {
            return "暂无";
        }
        const date = value instanceof Date ? value : new Date(value);
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

    function formatRefreshTime(value) {
        const date = value instanceof Date ? value : new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "刚刚";
        }
        return date.toLocaleTimeString("zh-CN", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        });
    }

    function formatFullDateTime(value) {
        const date = value instanceof Date ? value : new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "";
        }
        return date.toLocaleString("zh-CN", {
            hour12: false
        });
    }

    window.AdminCommon = {
        buildHttpError: buildHttpError,
        createPageNoticeApi: createPageNoticeApi,
        escapeHtml: escapeHtml,
        fetchJson: fetchJson,
        formatDateTime: formatDateTime,
        formatFullDateTime: formatFullDateTime,
        formatRefreshTime: formatRefreshTime
    };
})(window);
