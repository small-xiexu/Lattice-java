package com.xbk.lattice.api.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * admin-tabs.js 运行态回归测试
 *
 * 职责：验证标签切换在保留 URL 状态的同时，能够把视图滚动到对应功能区块
 *
 * @author xiexu
 */
class AdminTabsJsRuntimeTests {

    @Test
    void shouldActivateAndScrollTabPanelsViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path adminTabsJsPath = Path.of(userDir, "src/main/resources/static/admin/admin-tabs.js");
        assertThat(Files.exists(adminTabsJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("admin-tabs-js-runtime-test.js");
        Files.writeString(harnessScriptPath, buildHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                adminTabsJsPath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as(output)
                .isZero();
        assertThat(output).contains("admin-tabs-js-runtime-tests:ok");
    }

    private String buildHarnessScript() {
        return """
                const fs = require("fs");
                const vm = require("vm");

                const source = fs.readFileSync(process.argv[2], "utf8");

                function createClassList(initialNames) {
                    const set = new Set(initialNames || []);
                    return {
                        add: function (name) { set.add(name); },
                        remove: function (name) { set.delete(name); },
                        contains: function (name) { return set.has(name); },
                        toggle: function (name, force) {
                            if (force === true) {
                                set.add(name);
                                return true;
                            }
                            if (force === false) {
                                set.delete(name);
                                return false;
                            }
                            if (set.has(name)) {
                                set.delete(name);
                                return false;
                            }
                            set.add(name);
                            return true;
                        }
                    };
                }

                let root = null;

                function createElement(options) {
                    const listeners = {};
                    const attributes = Object.assign({}, options && options.attributes ? options.attributes : {});
                    const element = {
                        dataset: Object.assign({}, options && options.dataset ? options.dataset : {}),
                        hidden: Boolean(options && options.hidden),
                        id: options && options.id ? options.id : "",
                        classList: createClassList(options && options.classNames ? options.classNames : []),
                        scrollCalls: [],
                        addEventListener: function (eventName, handler) {
                            listeners[eventName] = listeners[eventName] || [];
                            listeners[eventName].push(handler);
                        },
                        dispatch: function (eventName, event) {
                            (listeners[eventName] || []).forEach(function (handler) {
                                handler(event || {});
                            });
                        },
                        getAttribute: function (name) {
                            if (name === "id") {
                                return this.id || null;
                            }
                            return Object.prototype.hasOwnProperty.call(attributes, name) ? attributes[name] : null;
                        },
                        setAttribute: function (name, value) {
                            if (name === "id") {
                                this.id = String(value);
                                return;
                            }
                            attributes[name] = String(value);
                        },
                        closest: function (selector) {
                            return selector === "[data-tab-group]" ? root : null;
                        },
                        scrollIntoView: function (optionsValue) {
                            this.scrollCalls.push(optionsValue);
                        },
                        focus: function () {
                            this.focused = true;
                        }
                    };
                    return element;
                }

                const uploadTrigger = createElement({
                    id: "knowledge-tab-upload",
                    dataset: { tabTrigger: "knowledge-upload", tabScroll: "true" },
                    classNames: ["tab-btn", "active"]
                });
                const runsTrigger = createElement({
                    id: "knowledge-tab-runs",
                    dataset: { tabTrigger: "knowledge-runs", tabScroll: "true" },
                    classNames: ["tab-btn"]
                });
                const uploadPanel = createElement({
                    id: "knowledge-upload-panel",
                    dataset: { tabPanel: "knowledge-upload" }
                });
                const runsPanel = createElement({
                    id: "knowledge-runs-panel",
                    dataset: { tabPanel: "knowledge-runs" },
                    hidden: true
                });
                const quickOpen = createElement({
                    dataset: { tabOpen: "knowledge-runs" }
                });

                root = {
                    dataset: { tabGroup: "knowledge-console", tabQueryKey: "tab" },
                    querySelectorAll: function (selector) {
                        if (selector === "[data-tab-trigger]") {
                            return [uploadTrigger, runsTrigger];
                        }
                        if (selector === "[data-tab-panel]") {
                            return [uploadPanel, runsPanel];
                        }
                        if (selector === "[data-tab-open]") {
                            return [quickOpen];
                        }
                        return [];
                    }
                };

                const elementsById = {
                    "knowledge-upload-panel": uploadPanel,
                    "knowledge-runs-panel": runsPanel
                };

                let domContentLoadedHandler = null;
                const historyCalls = [];

                const sandbox = {
                    console: console,
                    URLSearchParams: URLSearchParams,
                    window: {
                        location: { search: "", pathname: "/admin", hash: "" },
                        history: {
                            replaceState: function (_state, _title, url) {
                                historyCalls.push(url);
                            }
                        },
                        requestAnimationFrame: function (callback) {
                            callback();
                            return 1;
                        }
                    },
                    document: {
                        addEventListener: function (eventName, handler) {
                            if (eventName === "DOMContentLoaded") {
                                domContentLoadedHandler = handler;
                            }
                        },
                        querySelectorAll: function (selector) {
                            return selector === "[data-tab-group]" ? [root] : [];
                        },
                        getElementById: function (id) {
                            return elementsById[id] || null;
                        }
                    },
                    globalThis: null
                };

                sandbox.window.document = sandbox.document;
                sandbox.globalThis = sandbox;

                vm.runInNewContext(source, sandbox, { filename: "admin-tabs.js" });

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(typeof domContentLoadedHandler === "function",
                    "DOMContentLoaded handler should be registered");
                domContentLoadedHandler();

                assert(uploadPanel.hidden === false,
                    "initial active panel should stay visible after initialization");
                assert(runsPanel.hidden === true,
                    "inactive panel should stay hidden after initialization");

                runsTrigger.dispatch("click");
                assert(runsTrigger.classList.contains("active") === true,
                    "tab trigger click should activate target tab");
                assert(runsPanel.hidden === false,
                    "tab trigger click should reveal target panel");
                assert(runsPanel.scrollCalls.length === 1,
                    "tab trigger click should scroll to target panel");

                quickOpen.dispatch("click");
                assert(runsPanel.scrollCalls.length === 2,
                    "data-tab-open action should also scroll to target panel");
                assert(historyCalls.some(function (url) {
                    return url.includes("tab=knowledge-runs");
                }), "tab switching should keep URL query state in sync");

                sandbox.window.AdminTabs.activate("knowledge-console", "knowledge-upload", { scroll: true });
                assert(uploadTrigger.classList.contains("active") === true,
                    "programmatic activation should switch the active trigger");
                assert(uploadPanel.hidden === false,
                    "programmatic activation should reveal target panel");
                assert(uploadPanel.scrollCalls.length === 1,
                    "programmatic activation should support explicit scroll");

                console.log("admin-tabs-js-runtime-tests:ok");
                """;
    }

    private String readProcessOutput(Process process) throws IOException {
        InputStream inputStream = process.getInputStream();
        byte[] content = inputStream.readAllBytes();
        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
