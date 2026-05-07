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

                const exactLookupFallbackMeta = ask.getFallbackReasonMeta("DETERMINISTIC_EXACT_LOOKUP_PREFERRED");
                assert(exactLookupFallbackMeta.note.includes("精确查值"),
                    "fallback reason meta should explain deterministic exact lookup fallback");
                const invalidOutputFallbackMeta = ask.getFallbackReasonMeta("LLM_OUTPUT_INVALID");
                assert(invalidOutputFallbackMeta.note.includes("输出格式"),
                    "fallback reason meta should explain invalid structured output fallback");

                const renderedAnswer = ask.renderMarkdownLite(
                    "RoutePlanner 暴露了 /payments 路径 [[payment-routing]][→ src/main/java/payment/RoutePlanner.java]",
                    [
                        {
                            markerOrdinal: 1,
                            markerId: "citation-marker-1",
                            citationLiteral: "[[payment-routing]][→ src/main/java/payment/RoutePlanner.java]",
                            citationLiterals: [
                                "[[payment-routing]]",
                                "[→ src/main/java/payment/RoutePlanner.java]"
                            ],
                            sourceCount: 2,
                            sources: [
                                {
                                    sourceType: "ARTICLE",
                                    title: "Payment Routing",
                                    sourcePaths: ["src/main/java/payment/RoutePlanner.java"],
                                    matchedExcerpt: "RoutePlanner 暴露了 /payments 路径",
                                    validationStatus: "VERIFIED"
                                },
                                {
                                    sourceType: "SOURCE_FILE",
                                    title: "RoutePlanner.java",
                                    sourcePaths: ["src/main/java/payment/RoutePlanner.java"],
                                    matchedExcerpt: "RoutePlanner 暴露了 /payments 路径",
                                    validationStatus: "SKIPPED"
                                }
                            ]
                        }
                    ]
                );
                assert(renderedAnswer.includes("citation-marker-count"), "answer should render citation marker count");
                assert(renderedAnswer.includes(">2<"), "citation marker count should show cited source count");
                assert(renderedAnswer.includes("Payment Routing"), "citation popover should include source title");
                assert(renderedAnswer.includes("这处引用 · 2 份资料"), "citation popover should show source count");
                assert(!renderedAnswer.includes("[[payment-routing]]"),
                    "answer should hide raw article citation literal when marker metadata exists");

                const renderedSourceSectionAnswer = ask.renderMarkdownLite(
                    "FC 是履约中台 [[fc-fulfillment-digital]][→ 卡券三期-迁移方案.md, 1.1 业务背景]",
                    [
                        {
                            markerOrdinal: 1,
                            markerId: "citation-marker-1",
                            citationLiteral: "[[fc-fulfillment-digital]][→ 卡券三期-迁移方案.md, 1.1 业务背景]",
                            citationLiterals: [
                                "[[fc-fulfillment-digital]]",
                                "[→ 卡券三期-迁移方案.md]"
                            ],
                            sourceCount: 1,
                            sources: [
                                {
                                    sourceType: "ARTICLE",
                                    title: "卡券三期-迁移方案.md",
                                    sourcePaths: ["卡券三期-迁移方案.md"],
                                    matchedExcerpt: "FC 是履约中台系统",
                                    validationStatus: "VERIFIED"
                                }
                            ]
                        }
                    ]
                );
                assert(renderedSourceSectionAnswer.includes("citation-marker-count"),
                    "section-scoped citation should render marker count");
                assert(!renderedSourceSectionAnswer.includes("[[fc-fulfillment-digital]]"),
                    "section-scoped answer should hide raw article literal");
                assert(!renderedSourceSectionAnswer.includes("1.1 业务背景"),
                    "section-scoped answer should hide source section suffix from body");
                assert(ask.shouldAlignCitationPopoverRight({left: 900}, 1000),
                    "citation popover should align right near viewport edge");
                assert(!ask.shouldAlignCitationPopoverRight({left: 120}, 1000),
                    "citation popover should keep default placement when there is enough room");
                ask.setLastAnswerContext({
                    queryId: "query-123",
                    question: "这是什么接口",
                    answer: "接口用于提交请求",
                    articles: [{ articleKey: "article-001" }],
                    sources: [{ articleKey: "article-002", sourcePaths: ["docs/api.md"] }],
                    searchItems: [{ articleKey: "article-001", sourcePaths: ["docs/api.md", "docs/extra.md"] }]
                });
                const feedbackRequest = ask.buildAnswerFeedbackRequest("source_conflict", "来源不一致", "reviewer");
                assert(feedbackRequest.queryId === "query-123",
                    "feedback request should keep query id");
                assert(feedbackRequest.articleKeys.length === 2 && feedbackRequest.articleKeys.includes("article-002"),
                    "feedback request should collect unique article keys from answer context");
                assert(feedbackRequest.sourcePaths.length === 2 && feedbackRequest.sourcePaths.includes("docs/extra.md"),
                    "feedback request should collect unique source paths from answer context");
                assert(feedbackRequest.feedbackType === "source_conflict",
                    "feedback request should carry selected feedback type");

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
