(function () {
    const DEVELOPER_SECTION_GROUP = "developer-access-sections";
    const DEFAULT_TOAST_DURATION_MS = 2400;
    const state = {
        baseUrl: "",
        mcpUrl: "",
        checkedAt: ""
    };

    document.addEventListener("DOMContentLoaded", function () {
        if (!document.querySelector("[data-tab-group='developer-access-sections']")) {
            return;
        }
        computeContext();
        bindEvents();
        renderDeveloperAccess();
        refreshHealthStatus();
        applyInitialRoute();
    });

    function bindEvents() {
        bindIfPresent("refresh-developer-access", "click", function () {
            computeContext();
            renderDeveloperAccess();
            refreshHealthStatus();
            showToast("开发接入信息已刷新", "success");
        });
        bindIfPresent("refresh-ai", "click", function () {
            computeContext();
            renderDeveloperAccess();
            refreshHealthStatus();
        });

        document.querySelectorAll("[data-copy-template]").forEach(function (button) {
            button.addEventListener("click", function () {
                copyTemplate(button.dataset.copyTemplate);
            });
        });

        document.querySelectorAll("[data-developer-scroll]").forEach(function (button) {
            button.addEventListener("click", function () {
                scrollToSection(button.dataset.developerScroll);
            });
        });
    }

    function bindIfPresent(id, eventName, handler) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.addEventListener(eventName, handler);
    }

    function computeContext() {
        state.baseUrl = resolveBaseUrl();
        state.mcpUrl = state.baseUrl + "/mcp";
    }

    function resolveBaseUrl() {
        if (typeof window === "undefined" || !window.location) {
            return "";
        }
        return window.location.origin;
    }

    function renderDeveloperAccess() {
        setText("developer-base-url", state.baseUrl || "未识别");
        setText("developer-mcp-url", state.mcpUrl || "未识别");
        document.querySelectorAll("[data-developer-template]").forEach(function (element) {
            element.textContent = buildTemplate(element.dataset.developerTemplate);
        });
    }

    async function refreshHealthStatus() {
        setHealthStatus("检查中", "warning", "正在通过 /actuator/health 检查服务状态。");
        const checkedAt = new Date();
        state.checkedAt = checkedAt.toISOString();
        try {
            const response = await fetch("/actuator/health", {
                method: "GET",
                headers: {"Accept": "application/json"}
            });
            const payload = await parseResponseBody(response);
            const status = normalizeHealthStatus(response.status, payload, checkedAt);
            setHealthStatus(status.label, status.tone, status.note);
        }
        catch (error) {
            setHealthStatus(
                    "不可达",
                    "danger",
                    "无法连接服务：" + compactMessage(error && error.message ? error.message : "未知错误")
                            + " · "
                            + formatDateTime(checkedAt)
            );
        }
    }

    async function parseResponseBody(response) {
        const contentType = response.headers.get("content-type") || "";
        if (contentType.indexOf("application/json") >= 0) {
            return response.json();
        }
        return response.text();
    }

    function normalizeHealthStatus(httpStatus, payload, checkedAt) {
        const rawStatus = payload && typeof payload === "object" && payload.status
                ? String(payload.status).toUpperCase()
                : httpStatus >= 200 && httpStatus < 300 ? "UP" : "DOWN";
        const unhealthyComponents = extractUnhealthyComponents(payload);
        const detail = typeof payload === "string"
                ? compactMessage(payload)
                : unhealthyComponents.length > 0
                ? "异常组件：" + unhealthyComponents.join("、")
                : "HTTP " + httpStatus;
        return {
            label: formatHealthLabel(rawStatus),
            tone: formatHealthTone(rawStatus),
            note: "Actuator: " + rawStatus + " · " + detail + " · " + formatDateTime(checkedAt)
        };
    }

    function extractUnhealthyComponents(payload) {
        if (!payload || typeof payload !== "object" || !payload.components || typeof payload.components !== "object") {
            return [];
        }
        return Object.keys(payload.components).filter(function (name) {
            const component = payload.components[name];
            const status = component && component.status ? String(component.status).toUpperCase() : "";
            return status && status !== "UP";
        });
    }

    function formatHealthLabel(status) {
        if (status === "UP") {
            return "正常";
        }
        if (status === "UNKNOWN") {
            return "未知";
        }
        if (status === "UNREACHABLE") {
            return "不可达";
        }
        if (status === "OUT_OF_SERVICE") {
            return "不可用";
        }
        return "异常";
    }

    function formatHealthTone(status) {
        if (status === "UP") {
            return "success";
        }
        if (status === "UNKNOWN") {
            return "warning";
        }
        return "danger";
    }

    function setHealthStatus(label, tone, note) {
        const statusElement = document.getElementById("developer-service-status");
        const noteElement = document.getElementById("developer-service-status-note");
        if (statusElement) {
            statusElement.className = "badge" + (tone ? " " + tone : "");
            statusElement.textContent = label;
        }
        if (noteElement) {
            noteElement.textContent = note;
        }
    }

    async function copyTemplate(templateName) {
        const content = buildTemplate(templateName);
        if (!content) {
            showToast("没有可复制的内容", "warning");
            return;
        }
        try {
            await writeClipboard(content);
            showToast(resolveCopyMessage(templateName), "success");
        }
        catch (error) {
            showToast("复制失败，请手动复制代码块内容", "danger");
        }
    }

    async function writeClipboard(content) {
        if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
            try {
                await navigator.clipboard.writeText(content);
                return;
            }
            catch (error) {
                // 某些浏览器或无头环境会拒绝 Clipboard API，这里自动回退到 execCommand 方案。
            }
        }
        const textarea = document.createElement("textarea");
        textarea.value = content;
        textarea.setAttribute("readonly", "readonly");
        textarea.style.position = "fixed";
        textarea.style.top = "-9999px";
        document.body.appendChild(textarea);
        textarea.select();
        const copied = document.execCommand("copy");
        document.body.removeChild(textarea);
        if (!copied) {
            throw new Error("copy failed");
        }
    }

    function buildTemplate(templateName) {
        const builders = {
            "service-url": function () {
                return state.baseUrl;
            },
            "mcp-url": function () {
                return state.mcpUrl;
            },
            "bridge-command": function () {
                return "./bin/lattice-mcp-bridge " + state.mcpUrl;
            },
            "mcp-http-config": function () {
                return [
                    "{",
                    "  \"name\": \"lattice-http\",",
                    "  \"transport\": {",
                    "    \"type\": \"streamable-http\",",
                    "    \"url\": \"" + state.mcpUrl + "\"",
                    "  }",
                    "}"
                ].join("\n");
            },
            "mcp-bridge-config": function () {
                return [
                    "{",
                    "  \"mcpServers\": {",
                    "    \"lattice-java\": {",
                    "      \"command\": \"bash\",",
                    "      \"args\": [",
                    "        \"-lc\",",
                    "        \"cd /path/to/lattice-java && ./bin/lattice-mcp-bridge " + state.mcpUrl + "\"",
                    "      ]",
                    "    }",
                    "  }",
                    "}"
                ].join("\n");
            },
            "mcp-verify-steps": function () {
                return [
                    "1. 在客户端里先接通当前 MCP 地址：" + state.mcpUrl,
                    "2. 连接成功后先执行 tools/list，确认能看到 lattice_status、lattice_query 等工具",
                    "3. 再调用 lattice_status，确认返回健康状态与知识库统计",
                    "4. 再调用 lattice_query，确认返回 answer、sourcePaths 或 pendingQueryId",
                    "5. 如果 query 产生 pending，请继续 confirm、correct 或 discard，不要把待处理记录一直留在队列里"
                ].join("\n");
            },
            "cli-status-command": function () {
                return "./bin/lattice-cli status --server " + state.baseUrl;
            },
            "cli-query-command": function () {
                return "./bin/lattice-cli query --server " + state.baseUrl
                        + " \"邪修智库支持哪些开发者接入方式？\"";
            },
            "cli-source-list-command": function () {
                return "./bin/lattice-cli source-list --server " + state.baseUrl + " --page 1 --size 20";
            },
            "cli-source-sync-command": function () {
                return "./bin/lattice-cli source-sync --server " + state.baseUrl + " --source-id 1";
            },
            "cli-env-example": function () {
                return [
                    "export LATTICE_SERVER_URL=" + state.baseUrl,
                    "./bin/lattice-cli status",
                    "./bin/lattice-cli query \"邪修智库支持哪些开发者接入方式？\"",
                    "./bin/lattice-cli source-list --page 1 --size 20"
                ].join("\n");
            },
            "cli-advanced-command": function () {
                return "java -Dloader.main=com.xbk.lattice.cli.LatticeCliMain"
                        + " -cp ./target/lattice-java-1.0-SNAPSHOT.jar"
                        + " org.springframework.boot.loader.launch.PropertiesLauncher"
                        + " status --server " + state.baseUrl;
            },
            "mcp-bridge-advanced-command": function () {
                return "java -Dloader.main=com.xbk.lattice.cli.LatticeCliMain"
                        + " -cp ./target/lattice-java-1.0-SNAPSHOT.jar"
                        + " org.springframework.boot.loader.launch.PropertiesLauncher"
                        + " mcp-stdio --bridge " + state.mcpUrl;
            },
            "cli-verify-steps": function () {
                return [
                    "1. 在仓库根目录执行：./bin/lattice-cli status --server " + state.baseUrl,
                    "2. 看到 overview、quality 和 pending 摘要后，再执行首次问答命令",
                    "3. 如果返回 answer、引用来源或 reviewStatus，说明 CLI 已接通",
                    "4. 需要同步资料源时，先用 source-list 找到真实 sourceId，再替换 source-sync 里的 --source-id"
                ].join("\n");
            },
            "http-api-example": function () {
                return [
                    "curl -X POST \"" + state.baseUrl + "/api/v1/query\" \\",
                    "  -H \"Content-Type: application/json\" \\",
                    "  -d '{\"question\":\"邪修智库支持哪些开发者接入方式？\"}'",
                    "",
                    "# 健康检查",
                    "curl \"" + state.baseUrl + "/actuator/health\""
                ].join("\n");
            }
        };
        return builders[templateName] ? builders[templateName]() : "";
    }

    function resolveCopyMessage(templateName) {
        const messages = {
            "service-url": "当前服务地址已复制",
            "mcp-url": "当前 MCP 地址已复制",
            "bridge-command": "Bridge 命令已复制",
            "mcp-http-config": "MCP HTTP 配置已复制",
            "mcp-bridge-config": "STDIO Bridge 配置已复制",
            "mcp-verify-steps": "MCP 验证步骤已复制",
            "cli-status-command": "状态检查命令已复制",
            "cli-query-command": "首次问答命令已复制",
            "cli-source-list-command": "资料源列表命令已复制",
            "cli-source-sync-command": "资料源同步命令已复制",
            "cli-env-example": "环境变量示例已复制",
            "cli-advanced-command": "高级模式命令已复制",
            "mcp-bridge-advanced-command": "Bridge 高级命令已复制",
            "cli-verify-steps": "CLI 验证步骤已复制",
            "http-api-example": "HTTP API 示例已复制"
        };
        return messages[templateName] || "内容已复制";
    }

    function applyInitialRoute() {
        if (typeof window === "undefined" || !window.location || typeof URLSearchParams !== "function") {
            return;
        }
        const params = new URLSearchParams(window.location.search || "");
        const targetTab = normalizeDeveloperTab(params.get("tab"));
        const targetSection = normalizeSectionTarget(params.get("section"));
        if (!targetTab && !targetSection) {
            return;
        }
        window.requestAnimationFrame(function () {
            if (window.AdminTabs && typeof window.AdminTabs.activate === "function") {
                if (targetTab) {
                    window.AdminTabs.activate(DEVELOPER_SECTION_GROUP, targetTab);
                }
            }
            if (targetSection) {
                window.setTimeout(function () {
                    scrollToSection(targetSection);
                }, 60);
            }
        });
    }

    function normalizeDeveloperTab(tabName) {
        const normalized = String(tabName || "").trim();
        const allowedTabs = [
            "developer-access-overview",
            "developer-access-mcp",
            "developer-access-cli",
            "developer-access-http",
            "developer-access-faq"
        ];
        return allowedTabs.indexOf(normalized) >= 0 ? normalized : null;
    }

    function normalizeSectionTarget(sectionId) {
        const normalized = String(sectionId || "").trim();
        const allowedTargets = [
            "developer-mcp-anchor",
            "developer-cli-anchor",
            "developer-http-anchor",
            "developer-faq-anchor"
        ];
        return allowedTargets.indexOf(normalized) >= 0 ? normalized : null;
    }

    function scrollToSection(sectionId) {
        const developerSectionTab = resolveDeveloperSectionTab(sectionId);
        const section = document.getElementById(sectionId);
        if (window.AdminTabs && typeof window.AdminTabs.activate === "function") {
            if (developerSectionTab) {
                window.AdminTabs.activate(DEVELOPER_SECTION_GROUP, developerSectionTab);
            }
        }
        if (!section) {
            return;
        }
        section.scrollIntoView({behavior: "smooth", block: "start"});
    }

    function resolveDeveloperSectionTab(sectionId) {
        const mapping = {
            "developer-mcp-anchor": "developer-access-mcp",
            "developer-cli-anchor": "developer-access-cli",
            "developer-http-anchor": "developer-access-http",
            "developer-faq-anchor": "developer-access-faq"
        };
        return mapping[sectionId] || null;
    }

    function showToast(message, tone) {
        const stack = ensureToastStack();
        const toast = document.createElement("div");
        toast.className = "developer-toast" + (tone ? " " + tone : "");
        toast.textContent = message;
        stack.appendChild(toast);
        window.setTimeout(function () {
            toast.classList.add("closing");
            window.setTimeout(function () {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            }, 220);
        }, DEFAULT_TOAST_DURATION_MS);
    }

    function ensureToastStack() {
        let stack = document.getElementById("developer-toast-stack");
        if (stack) {
            return stack;
        }
        stack = document.createElement("div");
        stack.id = "developer-toast-stack";
        stack.className = "developer-toast-stack";
        document.body.appendChild(stack);
        return stack;
    }

    function setText(id, value) {
        const element = document.getElementById(id);
        if (!element) {
            return;
        }
        element.textContent = value;
    }

    function compactMessage(message) {
        const normalized = String(message || "").replace(/\s+/g, " ").trim();
        if (!normalized) {
            return "未返回更多信息";
        }
        return normalized.length > 120 ? normalized.slice(0, 120) + "..." : normalized;
    }

    function formatDateTime(value) {
        if (!(value instanceof Date) || Number.isNaN(value.getTime())) {
            return "";
        }
        return value.toLocaleString("zh-CN", {
            hour12: false
        });
    }
})();
