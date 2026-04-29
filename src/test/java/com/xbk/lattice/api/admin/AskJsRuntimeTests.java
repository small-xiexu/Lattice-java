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
 * ask.js 运行态回归测试
 *
 * 职责：验证问答页的提交态锁定、来源去重与摘要清洗逻辑
 *
 * @author xiexu
 */
class AskJsRuntimeTests {

    @Test
    void shouldVerifyAskPageRuntimeHelpersViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path askJsPath = Path.of(userDir, "src/main/resources/static/admin/ask.js");
        assertThat(Files.exists(askJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("ask-js-runtime-test.js");
        Files.writeString(harnessScriptPath, buildHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                askJsPath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as(output)
                .isZero();
        assertThat(output).contains("ask-js-runtime-tests:ok");
    }

    private String buildHarnessScript() {
        return """
                const fs = require("fs");
                const vm = require("vm");

                const source = fs.readFileSync(process.argv[2], "utf8");
                const elements = {
                    "submit-question": { disabled: false },
                    "clear-question": { disabled: false },
                    "ask-question": { disabled: false }
                };
                const sandbox = {
                    console: console,
                    URLSearchParams: URLSearchParams,
                    setTimeout: function () { return 0; },
                    clearTimeout: function () {},
                    window: {
                        setTimeout: function () { return 0; },
                        clearTimeout: function () {},
                        location: { search: "" }
                    },
                    document: {
                        addEventListener: function () {},
                        getElementById: function (id) { return elements[id] || null; },
                        querySelector: function () { return null; },
                        querySelectorAll: function () { return []; },
                        body: {}
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

                vm.runInNewContext(source, sandbox, { filename: "ask.js" });

                const ask = sandbox.__LATTICE_ADMIN_TEST__.ask;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(ask, "missing __LATTICE_ADMIN_TEST__.ask export");

                const sanitized = ask.trimSnippet(`---
                title: "项目启动配置清单"
                summary: "项目启动配置清单"
                referential_keywords: []
                ---

                # 项目启动配置清单

                项目启动配置清单`);
                assert(!sanitized.includes("title:"), "trimSnippet should strip front matter metadata");
                assert(sanitized.includes("项目启动配置清单"), "trimSnippet should keep readable content");

                const cards = ask.buildSecondarySourceCards([
                    {
                        title: "项目启动配置清单.md",
                        conceptId: "项目启动配置清单.md",
                        sourcePaths: ["项目启动配置清单.md"],
                        content: "第一段内容"
                    },
                    {
                        title: "项目启动配置清单.md",
                        conceptId: "项目启动配置清单.md",
                        sourcePaths: ["项目启动配置清单.md"],
                        content: "重复片段"
                    },
                    {
                        title: "README.md",
                        conceptId: "README.md",
                        sourcePaths: ["README.md"],
                        content: "README 片段"
                    }
                ], []);
                assert(cards.length === 2, "secondary source cards should deduplicate identical sources");

                ask.setCanAsk(true);
                ask.setSubmitting(true);
                assert(elements["submit-question"].disabled === true,
                    "submit button should be disabled while a question is in flight");
                assert(elements["clear-question"].disabled === true,
                    "clear button should be disabled while a question is in flight");
                assert(elements["ask-question"].disabled === true,
                    "question input should be disabled while a question is in flight");

                ask.setSubmitting(false);
                assert(elements["submit-question"].disabled === false,
                    "submit button should recover after the in-flight request finishes");
                assert(elements["clear-question"].disabled === false,
                    "clear button should recover after the in-flight request finishes");
                assert(elements["ask-question"].disabled === false,
                    "question input should recover after the in-flight request finishes");

                ask.setCanAsk(false);
                assert(elements["submit-question"].disabled === true,
                    "submit button should remain disabled when knowledge is not ready");
                assert(elements["clear-question"].disabled === false,
                    "clear button should stay available when only readiness blocks asking");

                console.log("ask-js-runtime-tests:ok");
                """;
    }

    private String readProcessOutput(Process process) throws IOException {
        InputStream inputStream = process.getInputStream();
        byte[] content = inputStream.readAllBytes();
        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
