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
    void shouldKeepAskPageUiRegressionGuards() throws Exception {
        String userDir = System.getProperty("user.dir");
        Path askHtmlPath = Path.of(userDir, "src/main/resources/static/admin/ask.html");
        Path adminCssPath = Path.of(userDir, "src/main/resources/static/admin/admin.css");
        assertThat(Files.exists(askHtmlPath)).isTrue();
        assertThat(Files.exists(adminCssPath)).isTrue();

        String askHtml = Files.readString(askHtmlPath, StandardCharsets.UTF_8);
        String adminCss = Files.readString(adminCssPath, StandardCharsets.UTF_8);
        assertThat(askHtml)
                .contains("<body class=\"admin-page ask-page\">")
                .contains("/admin/admin.css?v=20260610-admin-design-system-9")
                .contains("<details id=\"ask-feedback-panel\"")
                .contains("class=\"detail-section answer-feedback-panel top-gap\"")
                .contains("class=\"ask-feedback-summary\"")
                .contains("class=\"ask-feedback-toggle\"");
        assertThat(adminCss)
                .doesNotContain("/* ---- Ask page UX redesign ---- */")
                .contains(".admin-page .simple-table thead")
                .contains(".admin-page .query-audit-hit-table .query-audit-col-title")
                .contains(".admin-page .query-audit-title-cell .query-audit-cell-text")
                .contains("font-weight: 400;")
                .contains(".admin-page.ask-page .answer-feedback-panel[hidden]");
        assertThat(adminCss)
                .doesNotContain("/* ---- Admin design system v2 ---- */")
                .contains("/* ---- Admin design system v3: shared tokens and components ---- */")
                .contains("--admin-color-bg")
                .contains("--admin-color-surface")
                .contains("--admin-color-surface-elevated")
                .contains("--admin-radius-sm")
                .contains("--admin-shadow-card")
                .contains("--admin-transition-fast")
                .contains(".admin-page.ask-page")
                .contains("--ask-surface: var(--admin-color-surface);");
    }

    @Test
    void shouldVerifyAskPageRuntimeHelpersViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path adminCommonJsPath = Path.of(userDir, "src/main/resources/static/admin/admin-common.js");
        Path adminModuleDir = Path.of(userDir, "src/main/resources/static/admin/modules");
        Path askJsPath = Path.of(userDir, "src/main/resources/static/admin/ask.js");
        assertThat(Files.exists(adminCommonJsPath)).isTrue();
        assertThat(Files.exists(adminModuleDir)).isTrue();
        assertThat(Files.exists(askJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("ask-js-runtime-test.js");
        Files.writeString(harnessScriptPath, buildHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                adminCommonJsPath.toString(),
                adminModuleDir.toString(),
                "ask"
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
                const path = require("path");
                const vm = require("vm");

                const commonSource = fs.readFileSync(process.argv[2], "utf8");
                const moduleDir = process.argv[3];
                const modulePrefix = process.argv[4];
                const source = fs.readdirSync(moduleDir)
                    .filter(function (name) { return name.startsWith(modulePrefix + "-runtime-part-"); })
                    .sort()
                    .map(function (name) {
                        const moduleSource = fs.readFileSync(path.join(moduleDir, name), "utf8");
                        const matched = moduleSource.match(/export default (.*);\\s*$/s);
                        if (!matched) {
                            throw new Error("invalid runtime module: " + name);
                        }
                        return JSON.parse(matched[1]);
                    })
                    .join("\\n");
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

                vm.runInNewContext(commonSource, sandbox, { filename: "admin-common.js" });
                vm.runInNewContext(source, sandbox, { filename: "ask.js" });

                const ask = sandbox.__LATTICE_ADMIN_TEST__.ask;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(ask, "missing __LATTICE_ADMIN_TEST__.ask export");
                assert(source.includes("setAskResultDetailsVisible(false)"),
                    "loading state should hide secondary answer detail panels");
                assert(source.includes("setAskResultDetailsVisible(true);\\n            renderAnswer"),
                    "answer detail panels should be shown again before rendering the answer");
                assert(!source.includes("正在判断这次回答的结果态、模型态、证据态和复核态"),
                    "loading state should not render a second progress row under the answer");
                assert(!source.includes("setStatus(\\"正在生成回答...\\", \\"info\\")"),
                    "submitting state should not render a duplicate page-level loading notice");
                assert(!source.includes("回答已生成，但当前证据仍偏弱"),
                    "completed weak-evidence state should stay in the result guide instead of page-level notice");
                assert(source.includes("clearStatus();\\n        toggleAskResultExperience(true);"),
                    "submitting a new question should clear stale page-level notices");
                assert(source.includes("syncAskFaqOpenState();\\n            clearStatus();"),
                    "successful answers should not keep page-level completion notices visible");
                assert(source.includes("renderAuditTableTextCell(title, \\"query-audit-title-cell\\")"),
                    "retrieval audit table should wrap title cells for stable truncation");
                assert(source.includes("<col class='query-audit-col-source'>"),
                    "retrieval audit table should define stable column widths");
                assert(source.includes("query-audit-run-table"),
                    "retrieval channel runs should render as a compact table instead of a card wall");
                assert(!source.includes("channelRuns.map(renderRetrievalChannelRunCard)"),
                    "retrieval channel runs should not render a dense wall of cards");
                assert(source.includes("function getRetrievalChannelMeta(channelName)"),
                    "retrieval audit should expose human-readable channel names");
                assert(source.includes("article_chunk_fts: {label: \\"文章段落\\""),
                    "retrieval audit should show Chinese labels beside channel ids");
                assert(source.includes("<details class='query-audit-hit-details'>"),
                    "retrieval channel hit details should be collapsed behind a disclosure");
                assert(!source.includes("<details class='query-audit-hit-details' open>"),
                    "retrieval channel hit details should not expand by default");
                assert(source.includes("data-full-text='"),
                    "retrieval audit cells should keep full text without showing every tooltip by default");
                assert(source.includes("function syncAuditOverflowTooltips(root)"),
                    "retrieval audit should enable tooltips only after checking rendered overflow");
                assert(source.includes("function isAuditTextOverflowing(element)"),
                    "retrieval audit should detect whether a cell is actually truncated");
                assert(source.includes("cell.removeAttribute(\\"data-tooltip\\")"),
                    "retrieval audit should remove tooltips from cells that are fully visible");
                assert(source.includes("bindAuditHitDetailsTooltips(container);"),
                    "collapsed hit details should measure overflow after the user expands them");
                assert(source.includes("panel.open = false;"),
                    "answer feedback disclosure should collapse again when the answer experience resets");
                assert(source.includes("function bindFeedbackDisclosureToggle()"),
                    "answer feedback disclosure should bind a real toggle label instead of CSS-only text");
                assert(source.includes("syncFeedbackDisclosureLabel(panel);"),
                    "answer feedback disclosure should keep the visible toggle label in sync");

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
                const renderedTableAnswer = ask.renderMarkdownLite(
                    "| 字段 | 说明 |\\n|---|---|\\n| `due_date` | 应还日期 |\\n| `return_date` | 实际归还日期 |",
                    []
                );
                assert(renderedTableAnswer.includes("answer-markdown-table"),
                    "markdown table should render as a table");
                assert(renderedTableAnswer.includes("<code>due_date</code>"),
                    "markdown table cells should keep inline code formatting");
                assert(!renderedTableAnswer.includes("|---|---|"),
                    "markdown table separator should not render as plain text");
                const renderedPipeParagraph = ask.renderMarkdownLite("字段 A | 字段 B 只是普通说明", []);
                assert(!renderedPipeParagraph.includes("answer-markdown-table"),
                    "single pipe paragraph should not be treated as a table");
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
