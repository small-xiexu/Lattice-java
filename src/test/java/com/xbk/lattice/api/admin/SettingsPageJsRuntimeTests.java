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
 * settings-page.js 运行态回归测试
 *
 * 职责：验证设置页对 deep research 绑定场景的文案映射与角色选项
 *
 * @author xiexu
 */
class SettingsPageJsRuntimeTests {

    @Test
    void shouldExposeDeepResearchBindingLabelsViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path settingsPageJsPath = Path.of(userDir, "src/main/resources/static/admin/settings-page.js");
        assertThat(Files.exists(settingsPageJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("settings-page-js-runtime-test.js");
        Files.writeString(harnessScriptPath, buildHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                settingsPageJsPath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as(output)
                .isZero();
        assertThat(output).contains("settings-page-js-runtime-tests:ok");
    }

    private String buildHarnessScript() {
        return """
                const fs = require("fs");
                const vm = require("vm");

                const source = fs.readFileSync(process.argv[2], "utf8");
                const adminTabsCalls = [];
                const sandbox = {
                    console: console,
                    URLSearchParams: URLSearchParams,
                    setTimeout: function () { return 0; },
                    clearTimeout: function () {},
                    window: {
                        setTimeout: function () { return 0; },
                        clearTimeout: function () {},
                        location: { search: "" },
                        AdminTabs: {
                            activate: function (groupName, tabName, options) {
                                adminTabsCalls.push({ groupName, tabName, options });
                            }
                        }
                    },
                    document: {
                        addEventListener: function () {},
                        getElementById: function () { return null; },
                        querySelector: function () { return null; },
                        querySelectorAll: function () { return []; },
                        createElement: function () { return { classList: { add: function () {} } }; },
                        body: { appendChild: function () {} }
                    },
                    navigator: {},
                    fetch: function () {
                        return Promise.reject(new Error("fetch not available in test harness"));
                    },
                    globalThis: null,
                    __LATTICE_ADMIN_TEST__: {}
                };

                sandbox.window.document = sandbox.document;
                sandbox.globalThis = sandbox;

                vm.runInNewContext(source, sandbox, { filename: "settings-page.js" });

                const settingsPage = sandbox.__LATTICE_ADMIN_TEST__.settingsPage;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(settingsPage, "missing __LATTICE_ADMIN_TEST__.settingsPage export");
                assert(settingsPage.getBindingSceneLabel("deep_research") === "深度研究",
                    "deep research scene should have localized label");
                assert(settingsPage.getBindingRoleLabel("deep_research", "planner") === "研究规划",
                    "planner role should have localized label");
                assert(settingsPage.getBindingRoleSummary("deep_research", "synthesizer") === "汇总各层结果并形成答案",
                    "synthesizer role should expose localized summary");
                assert(settingsPage.getBindingRoleOptions("deep_research").length === 4,
                    "deep research scene should expose four role options");
                settingsPage.activateSettingsTab("settings-parse", { scroll: true });
                assert(adminTabsCalls.length === 1,
                    "settings page should delegate tab activation to AdminTabs");
                assert(adminTabsCalls[0].groupName === "admin-console",
                    "settings page should activate the admin console tab group");
                assert(adminTabsCalls[0].tabName === "settings-parse",
                    "settings page should activate the requested tab");
                assert(adminTabsCalls[0].options && adminTabsCalls[0].options.scroll === true,
                    "settings page should preserve the scroll option when activating tabs");

                console.log("settings-page-js-runtime-tests:ok");
                """;
    }

    private String readProcessOutput(Process process) throws IOException {
        InputStream inputStream = process.getInputStream();
        byte[] content = inputStream.readAllBytes();
        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
