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
 * management.js 运行态回归测试
 *
 * 职责：通过 Node 执行前端测试钩子，验证运行态回退、疑似卡住提示与错误文案收口
 *
 * @author xiexu
 */
class ManagementJsRuntimeTests {

    /**
     * 验证待人工确认空态文案不再暴露 Reviewer 表述。
     *
     * @throws Exception 读取文件异常
     */
    @Test
    void shouldUseHumanReadableQualityCheckCopyInReviewQueuePlaceholder() throws Exception {
        String userDir = System.getProperty("user.dir");
        Path indexHtmlPath = Path.of(userDir, "src/main/resources/static/admin/index.html");
        assertThat(Files.exists(indexHtmlPath)).isTrue();

        String html = Files.readString(indexHtmlPath, StandardCharsets.UTF_8);
        assertThat(html).contains("待人工确认说明");
        assertThat(html).doesNotContain("value=\"needs_human_review\"");
        assertThat(html).doesNotContain("Reviewer 判定原因");
        assertThat(html).doesNotContain("质量检查给出的原因");
        // Article detail bar: button group still present
        assertThat(html).contains("article-detail-button-group");
        // Article detail: 不再出现"技术信息"独立标题
        assertThat(html).doesNotContain("<h4>技术信息</h4>");
        // Article detail: 仅保留一个"开发诊断信息"入口
        long devDiagCount = html.lines()
                .filter(line -> line.contains("<h4>开发诊断信息</h4>"))
                .count();
        assertThat(devDiagCount).as("article detail should have exactly one 开发诊断信息 section")
                .isEqualTo(1);
        // Article detail: 关联信息不再与"技术信息"组成强制双栏结构
        // （detail-section-grid 仍用于结果反馈等其他区域，仅验证 article-relations 不在其中）
        int articleRelationsIdx = html.indexOf("id=\"article-relations\"");
        assertThat(articleRelationsIdx).as("article-relations element should exist").isPositive();
        String beforeRelations = html.substring(Math.max(0, articleRelationsIdx - 300), articleRelationsIdx);
        assertThat(beforeRelations).doesNotContain("detail-section-grid");
        // Article detail: 开发诊断信息默认折叠（无 open 属性）
        // （其他区域如 FAQ 等处可能有 open，仅检查开发诊断信息段）
        int devDiagIdx = html.indexOf("<h4>开发诊断信息</h4>");
        assertThat(devDiagIdx).as("开发诊断信息 section should exist").isPositive();
        String devDiagSurrounding = html.substring(devDiagIdx, Math.min(html.length(), devDiagIdx + 400));
        assertThat(devDiagSurrounding).doesNotContain("<details open");
    }

    /**
     * 验证文章详情页 toggle / 折叠区不再使用冷蓝深底样式。
     *
     * @throws Exception 读取文件异常
     */
    @Test
    void shouldNotUseColdBlueBackgroundInArticleDetailToggles() throws Exception {
        String userDir = System.getProperty("user.dir");
        Path cssPath = Path.of(userDir, "src/main/resources/static/admin/admin.css");
        assertThat(Files.exists(cssPath)).isTrue();
        String css = Files.readString(cssPath, StandardCharsets.UTF_8);

        // Extract toggle rule blocks
        String metaToggleBlock = extractCssBlock(css, ".article-metadata-toggle {");
        String keyToggleBlock = extractCssBlock(css, ".article-keyword-toggle {");
        String relToggleBlock = extractCssBlock(css, ".article-relations-toggle {");
        String relToggleHoverBlock = extractCssBlock(css, ".article-relations-toggle:hover {");
        String metaToggleCodeViewBlock = extractCssBlock(css, ".article-metadata-toggle .code-view {");

        // 旧冷蓝底色不得出现
        assertThat(metaToggleBlock).doesNotContain("rgba(23, 39, 65");
        assertThat(metaToggleBlock).doesNotContain("rgba(143, 190, 255");
        assertThat(keyToggleBlock).doesNotContain("rgba(23, 39, 65");
        assertThat(keyToggleBlock).doesNotContain("rgba(143, 190, 255");
        assertThat(relToggleBlock).doesNotContain("rgba(41, 69, 112");
        assertThat(relToggleBlock).doesNotContain("rgba(126, 186, 255");
        assertThat(relToggleBlock).doesNotContain("#b4d6ff");
        assertThat(relToggleHoverBlock).doesNotContain("rgba(58, 96, 150");
        assertThat(relToggleHoverBlock).doesNotContain("#d0e4ff");

        // article-relations-toggle 不再使用 var(--primary-strong) 间接引用冷蓝
        assertThat(relToggleBlock).doesNotContain("var(--primary-strong)");

        // article-metadata-toggle .code-view 必须有显式暖色背景，不再继承深色底
        assertThat(metaToggleCodeViewBlock).contains("linear-gradient");
        assertThat(metaToggleCodeViewBlock).contains("rgba(41, 67, 56");
        assertThat(metaToggleCodeViewBlock).doesNotContain("rgba(8, 15, 29");

        // 新浅色体系应存在
        assertThat(metaToggleBlock).contains("rgba(67, 79, 68");
        assertThat(metaToggleBlock).contains("rgba(249, 244, 237");
        assertThat(keyToggleBlock).contains("rgba(67, 79, 68");
        assertThat(keyToggleBlock).contains("rgba(249, 244, 237");
        assertThat(relToggleBlock).contains("rgba(242, 235, 223");
    }

    private String extractCssBlock(String css, String selector) {
        return extractCssBlockFrom(css, selector, 0);
    }

    private String extractCssBlockFrom(String css, String selector, int fromIndex) {
        int start = css.indexOf(selector, fromIndex);
        if (start < 0) {
            return "";
        }
        int braceStart = css.indexOf("{", start);
        if (braceStart < 0) {
            return "";
        }
        int depth = 1;
        int i = braceStart + 1;
        while (i < css.length() && depth > 0) {
            char c = css.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            i++;
        }
        return css.substring(start, i);
    }

    /**
     * 验证 renderDescriptionList 空态文案已统一为"暂无开发诊断信息"，不再混用"技术信息"命名。
     *
     * @throws Exception 读取文件异常
     */
    @Test
    void shouldUseUnifiedDevDiagnosticCopyInDescriptionListEmptyState() throws Exception {
        String userDir = System.getProperty("user.dir");
        Path part04Path = Path.of(userDir, "src/main/resources/static/admin/modules/management-runtime-part-04.js");
        assertThat(Files.exists(part04Path)).isTrue();
        String source = Files.readString(part04Path, StandardCharsets.UTF_8);

        // 旧空态文案不得出现
        assertThat(source).doesNotContain("暂无技术信息");
        // 新空态文案必须出现
        assertThat(source).contains("暂无开发诊断信息");
    }

    /**
     * 验证"已入库内容"页已删除关注内容治理 UI（状态条、说明卡、重新分析按钮、需关注筛选项），
     * 页面回归核心路径：搜索、列表、详情、风险提示、来源、复核历史。
     *
     * @throws Exception 读取文件异常
     */
    @Test
    void shouldNotContainGovernanceAttentionUiInKnowledgeArticlesPage() throws Exception {
        String userDir = System.getProperty("user.dir");

        // 验证 index.html 不再包含治理 UI DOM
        Path indexHtmlPath = Path.of(userDir, "src/main/resources/static/admin/index.html");
        assertThat(Files.exists(indexHtmlPath)).isTrue();
        String html = Files.readString(indexHtmlPath, StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("hotspot-refresh-status");
        assertThat(html).doesNotContain("governance-explain-panel");
        assertThat(html).doesNotContain("governance-explain-body");
        assertThat(html).doesNotContain("governance-explain-dismiss");
        assertThat(html).doesNotContain("理解\"关注内容\"指标");
        assertThat(html).doesNotContain("重新分析关注内容");
        assertThat(html).doesNotContain("关注内容未分析");

        // 验证筛选下拉不再暴露"需关注"选项
        assertThat(html).doesNotContain("requiresResultVerification:true\">需关注");

        // 验证核心入口仍然保留
        assertThat(html).contains("去提问");
        assertThat(html).contains("已入库内容");
        assertThat(html).contains("article-risk-filter");
        assertThat(html).contains("搜索");
        assertThat(html).contains("article-list");

        // 验证 admin.css 不再包含治理说明面板样式
        Path cssPath = Path.of(userDir, "src/main/resources/static/admin/admin.css");
        assertThat(Files.exists(cssPath)).isTrue();
        String css = Files.readString(cssPath, StandardCharsets.UTF_8);
        assertThat(css).doesNotContain(".governance-explain-panel");
        assertThat(css).doesNotContain(".governance-explain-body");
        assertThat(css).doesNotContain(".governance-explain-item");
        assertThat(css).doesNotContain(".governance-explain-action");

        // 验证 part-04.js 不再保留治理展示/说明函数
        Path part04Path = Path.of(userDir, "src/main/resources/static/admin/modules/management-runtime-part-04.js");
        assertThat(Files.exists(part04Path)).isTrue();
        String part04Source = Files.readString(part04Path, StandardCharsets.UTF_8);
        assertThat(part04Source).doesNotContain("renderGovernanceExplainPanel");
        assertThat(part04Source).doesNotContain("buildGovernanceExplainContent");
        assertThat(part04Source).doesNotContain("syncGovernanceExplainPanel");
        assertThat(part04Source).doesNotContain("renderHotspotRefreshStatus");
        assertThat(part04Source).doesNotContain("buildHotspotRefreshStatusText");
        assertThat(part04Source).doesNotContain("governanceExplainDismissed");
        assertThat(part04Source).doesNotContain("lastHotspotResponse");
        assertThat(part04Source).doesNotContain("_originalActivateKnowledgeTab");

        // 四段式解释内容已移除
        assertThat(part04Source).doesNotContain("你现在还不能做什么");

        // 禁止旧术语（回归断言）
        assertThat(part04Source).doesNotContain("抽检");
        assertThat(part04Source).doesNotContain("待验证");
        assertThat(part04Source).doesNotContain("热点刷新");
        assertThat(part04Source).doesNotContain("热点未验证");
    }

    /**
     * 验证首页工作台右侧状态卡已从深色大 CTA 降级为浅色辅助状态摘要卡。
     *
     * @throws Exception 读取文件异常
     */
    @Test
    void shouldRenderRightStatusCardAsLightSummaryNotDarkHero() throws Exception {
        String userDir = System.getProperty("user.dir");

        // 验证 admin.css 中 .help-state-card 不再使用深色整块背景
        Path cssPath = Path.of(userDir, "src/main/resources/static/admin/admin.css");
        assertThat(Files.exists(cssPath)).isTrue();
        String css = Files.readString(cssPath, StandardCharsets.UTF_8);

        String helpStateCardBlock = extractCssBlock(css, ".help-state-card {");
        assertThat(helpStateCardBlock).isNotEmpty();

        // 禁止深色整块背景（冷蓝/深海军蓝/深绿底）
        assertThat(helpStateCardBlock).doesNotContain("rgba(14, 24, 44");
        assertThat(helpStateCardBlock).doesNotContain("rgba(9, 18, 34");
        assertThat(helpStateCardBlock).doesNotContain("rgba(11, 33, 30");
        assertThat(helpStateCardBlock).doesNotContain("rgba(41, 29, 13");
        assertThat(helpStateCardBlock).doesNotContain("rgba(43, 15, 19");
        // 禁止冷蓝色边框
        assertThat(helpStateCardBlock).doesNotContain("rgba(143, 190, 255");
        assertThat(helpStateCardBlock).doesNotContain("rgba(121, 210, 255");
        assertThat(helpStateCardBlock).doesNotContain("rgba(118, 240, 200");
        assertThat(helpStateCardBlock).doesNotContain("rgba(255, 197, 107");
        assertThat(helpStateCardBlock).doesNotContain("rgba(255, 141, 141");

        // 必须使用暖色浅底
        assertThat(helpStateCardBlock).contains("rgba(67, 79, 68");

        // help-state-card[data-help-tone] 变体也不再使用深色背景（扫描全文件）
        String secondSuccessBlock = extractCssBlockFrom(css, ".help-state-card[data-help-tone=\"success\"] {",
                css.indexOf(".help-state-card[data-help-tone=\"success\"] {") + 1);
        assertThat(secondSuccessBlock).doesNotContain("rgba(11, 33, 30");
        assertThat(secondSuccessBlock).doesNotContain("rgba(8, 23, 21");

        String secondWarningBlock = extractCssBlockFrom(css, ".help-state-card[data-help-tone=\"warning\"] {",
                css.indexOf(".help-state-card[data-help-tone=\"warning\"] {") + 1);
        assertThat(secondWarningBlock).doesNotContain("rgba(41, 29, 13");
        assertThat(secondWarningBlock).doesNotContain("rgba(27, 20, 10");

        String secondDangerBlock = extractCssBlockFrom(css, ".help-state-card[data-help-tone=\"danger\"] {",
                css.indexOf(".help-state-card[data-help-tone=\"danger\"] {") + 1);
        assertThat(secondDangerBlock).doesNotContain("rgba(43, 15, 19");
        assertThat(secondDangerBlock).doesNotContain("rgba(29, 11, 14");

        // .help-card-eyebrow 不再使用冷蓝色
        String eyebrowBlock = extractCssBlock(css, ".help-card-eyebrow {");
        assertThat(eyebrowBlock).doesNotContain("rgba(143, 190, 255");
        assertThat(eyebrowBlock).contains("var(--primary)");

        // 验证 part-02.js 中右侧卡片文案为状态摘要而非 CTA
        Path part02Path = Path.of(userDir, "src/main/resources/static/admin/modules/management-runtime-part-02.js");
        assertThat(Files.exists(part02Path)).isTrue();
        String part02Source = Files.readString(part02Path, StandardCharsets.UTF_8);
        // 状态摘要标题
        assertThat(part02Source).contains("当前状态");
        // 不再使用 CTA 式引导标题
        assertThat(part02Source).doesNotContain("现在该怎么做");

        // 验证 index.html 中右侧为辅助区，不乱入左侧主引导文案
        Path indexHtmlPath = Path.of(userDir, "src/main/resources/static/admin/index.html");
        assertThat(Files.exists(indexHtmlPath)).isTrue();
        String html = Files.readString(indexHtmlPath, StandardCharsets.UTF_8);
        // 右侧卡片作为 workbench-hero-side 存在
        assertThat(html).contains("workbench-hero-side");
        // 左侧主引导文案不在右侧
        String heroSideStart = html.substring(html.indexOf("workbench-hero-side"));
        String heroSideEnd = heroSideStart.substring(0, heroSideStart.indexOf("workbench-status-panel") > 0
                ? heroSideStart.indexOf("workbench-status-panel") + 30
                : Math.min(heroSideStart.length(), 1200));
        assertThat(heroSideEnd).doesNotContain("把资料放进来");
        assertThat(heroSideEnd).doesNotContain("先导入资料");
    }

    /**
     * 验证 compile-review-queue.js 的真实运行时渲染路径不再输出 Reviewer 文案。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldRenderHumanReadableQualityCheckCopyFromCompileReviewQueueRuntime(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path adminCommonJsPath = Path.of(userDir, "src/main/resources/static/admin/admin-common.js");
        Path compileReviewQueueJsPath = Path.of(userDir, "src/main/resources/static/admin/compile-review-queue.js");
        assertThat(Files.exists(adminCommonJsPath)).isTrue();
        assertThat(Files.exists(compileReviewQueueJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("compile-review-queue-runtime-test.js");
        Files.writeString(harnessScriptPath, buildCompileReviewQueueHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                adminCommonJsPath.toString(),
                compileReviewQueueJsPath.toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as(output)
                .isZero();
        assertThat(output).contains("compile-review-queue-runtime-tests:ok");
    }

    /**
     * 验证运行态回退、疑似卡住提示、稳定错误文案与重新同步入口都会按预期工作。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldVerifyRunFallbackAndErrorPresentationViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path adminCommonJsPath = Path.of(userDir, "src/main/resources/static/admin/admin-common.js");
        Path adminModuleDir = Path.of(userDir, "src/main/resources/static/admin/modules");
        Path managementJsPath = Path.of(userDir, "src/main/resources/static/admin/management.js");
        assertThat(Files.exists(adminCommonJsPath)).isTrue();
        assertThat(Files.exists(adminModuleDir)).isTrue();
        assertThat(Files.exists(managementJsPath)).isTrue();

        Path harnessScriptPath = tempDir.resolve("management-js-runtime-test.js");
        Files.writeString(harnessScriptPath, buildHarnessScript(), StandardCharsets.UTF_8);

        ProcessBuilder versionBuilder = new ProcessBuilder("node", "--version");
        versionBuilder.redirectErrorStream(true);
        Process versionProcess = versionBuilder.start();
        String versionOutput = readProcessOutput(versionProcess);
        int versionExitCode = versionProcess.waitFor();
        assertThat(versionExitCode).isZero();
        assertThat(versionOutput).startsWith("v");

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                harnessScriptPath.toString(),
                adminCommonJsPath.toString(),
                adminModuleDir.toString(),
                "management"
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .as(output)
                .isZero();
        assertThat(output).contains("management-js-runtime-tests:ok");
    }

    /**
     * 构造 Node 侧测试脚本。
     *
     * @return 测试脚本文本
     */
    private String buildHarnessScript() {
        return readClasspathResource("admin/management-js-runtime-test.js");
    }

    /**
     * 构造 compile review queue runtime 专用 Node 测试脚本。
     *
     * @return 测试脚本文本
     */
    private String buildCompileReviewQueueHarnessScript() {
        return """
                const fs = require("fs");
                const vm = require("vm");

                const commonSource = fs.readFileSync(process.argv[2], "utf8");
                const queueSource = fs.readFileSync(process.argv[3], "utf8");

                const elements = {};

                function createElement() {
                    return {
                        textContent: "",
                        innerHTML: "",
                        hidden: false,
                        disabled: false,
                        className: "",
                        style: {},
                        dataset: {},
                        addEventListener: function () {},
                        querySelectorAll: function () { return []; },
                        classList: {
                            toggle: function () {},
                            add: function () {},
                            remove: function () {}
                        }
                    };
                }

                function getElement(id) {
                    if (!elements[id]) {
                        elements[id] = createElement();
                    }
                    return elements[id];
                }

                const sandbox = {
                    console: console,
                    window: {
                        AdminCommon: {},
                        confirm: function () { return true; },
                        prompt: function () { return "admin"; }
                    },
                    document: {
                        addEventListener: function () {},
                        getElementById: function (id) {
                            return getElement(id);
                        },
                        querySelectorAll: function () { return []; }
                    },
                    globalThis: null,
                    __LATTICE_ADMIN_TEST__: {}
                };

                sandbox.window.document = sandbox.document;
                sandbox.globalThis = sandbox;

                vm.runInNewContext(commonSource, sandbox, { filename: "admin-common.js" });
                vm.runInNewContext(queueSource, sandbox, { filename: "compile-review-queue.js" });

                const queueUi = sandbox.__LATTICE_ADMIN_TEST__.compileReviewQueue;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(queueUi, "missing __LATTICE_ADMIN_TEST__.compileReviewQueue export");

                // ---- Existing assertions (preserved) ----

                queueUi.renderEmptyDetail();
                assert(elements["review-queue-detail"].innerHTML.includes("待人工确认说明"),
                    "empty detail should render human-review wording");
                assert(!elements["review-queue-detail"].innerHTML.includes("Reviewer 判定原因"),
                    "empty detail should not render reviewer wording");

                queueUi.state.items = [{
                    id: "draft-1",
                    title: "草稿一",
                    sourcePaths: ["docs/a.md"],
                    fixAttemptCount: 1,
                    maxFixRounds: 2,
                    updatedAt: "2026-05-20T16:00:00+08:00",
                    reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"false_provenance\\"}]"
                }];
                queueUi.renderReviewQueueList(1);
                var listItemHtml = elements["review-queue-list"].innerHTML;
                assert(listItemHtml.includes("草稿一"),
                    "list item should show draft title");
                assert(listItemHtml.includes("a.md"),
                    "list item should show source file name");
                assert(listItemHtml.includes("个问题"),
                    "list item should show issue count");
                assert(listItemHtml.includes("修复"),
                    "list item should show fix round info");
                assert(!listItemHtml.includes("Reviewer 判定需要人工确认"),
                    "list item should not render reviewer wording");

                queueUi.renderReviewQueueDetail({
                    id: "draft-1",
                    title: "草稿一",
                    content: "正文",
                    sourcePaths: ["docs/a.md"],
                    reviewIssuesJson: "[]",
                    fixAttemptCount: 1,
                    maxFixRounds: 2,
                    updatedAt: "2026-05-20T16:00:00+08:00"
                });
                assert(elements["review-queue-detail"].innerHTML.includes("待人工确认说明"),
                    "detail section should render human-review heading");
                assert(elements["review-queue-detail"].innerHTML.includes("质量检查需要人工确认，但未返回结构化问题详情。"),
                    "detail fallback should render quality-check copy");
                assert(!elements["review-queue-detail"].innerHTML.includes("Reviewer"),
                    "detail runtime should not render reviewer wording");

                assert(queueUi.buildDetailMeta({
                    reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\"}]",
                    updatedAt: "2026-05-20T16:00:00+08:00"
                }).includes("风险等级"),
                    "detail meta should include risk level");

                // Review issue scroll class and count
                assert(typeof queueUi.resolveReviewIssueCount === "function",
                    "missing resolveReviewIssueCount export");
                assert(queueUi.resolveReviewIssueCount("[{\\"severity\\":\\"HIGH\\"},{\\"severity\\":\\"MEDIUM\\"}]") === 2,
                    "resolveReviewIssueCount should count array items");
                assert(queueUi.resolveReviewIssueCount("[]") === 0,
                    "resolveReviewIssueCount should return 0 for empty array");
                assert(queueUi.resolveReviewIssueCount(null) === 0,
                    "resolveReviewIssueCount should return 0 for null");

                var issuesMarkup = queueUi.renderReviewIssues("[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"false_provenance\\",\\"description\\":\\"来源不一致问题\\"}]");
                assert(issuesMarkup.includes("review-issue-list-scroll"),
                    "renderReviewIssues should include review-issue-list-scroll class");

                // Render detail with issues and verify heading includes count
                queueUi.renderReviewQueueDetail({
                    id: "draft-2",
                    title: "草稿二",
                    content: "正文二",
                    sourcePaths: ["docs/b.md"],
                    reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"false_provenance\\",\\"description\\":\\"问题一\\"},{\\"severity\\":\\"MEDIUM\\",\\"category\\":\\"value_mismatch\\",\\"description\\":\\"问题二\\"},{\\"severity\\":\\"LOW\\",\\"category\\":\\"missing_referential\\",\\"description\\":\\"问题三\\"}]",
                    fixAttemptCount: 1,
                    maxFixRounds: 2,
                    updatedAt: "2026-05-20T16:00:00+08:00"
                });
                assert(elements["review-queue-detail"].innerHTML.includes("待人工确认说明（共 3 个问题）"),
                    "detail heading should include issue count");

                // ---- New assertions: Risk level helpers ----

                assert(typeof queueUi.resolveItemRiskLevel === "function",
                    "missing resolveItemRiskLevel export");
                assert(queueUi.resolveItemRiskLevel({ reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\"}]" }) === "HIGH",
                    "resolveItemRiskLevel should detect HIGH");
                assert(queueUi.resolveItemRiskLevel({ reviewIssuesJson: "[{\\"severity\\":\\"MEDIUM\\"},{\\"severity\\":\\"HIGH\\"}]" }) === "HIGH",
                    "resolveItemRiskLevel should detect highest severity");
                assert(queueUi.resolveItemRiskLevel({ reviewIssuesJson: "[{\\"severity\\":\\"CRITICAL\\"}]" }) === "CRITICAL",
                    "resolveItemRiskLevel should detect CRITICAL");
                assert(queueUi.resolveItemRiskLevel({ reviewIssuesJson: "[]" }) === null,
                    "resolveItemRiskLevel should return null for empty issues");
                assert(queueUi.resolveItemRiskLevel({}) === null,
                    "resolveItemRiskLevel should return null for no issues");

                assert(typeof queueUi.resolveItemIssueTypes === "function",
                    "missing resolveItemIssueTypes export");
                var types = queueUi.resolveItemIssueTypes({ reviewIssuesJson: "[{\\"category\\":\\"false_provenance\\"},{\\"category\\":\\"value_mismatch\\"}]" });
                assert(types.indexOf("false_provenance") >= 0 && types.indexOf("value_mismatch") >= 0,
                    "resolveItemIssueTypes should return unique categories");

                // ---- New assertions: Filtered items and grouping ----

                assert(typeof queueUi.getFilteredItems === "function",
                    "missing getFilteredItems export");
                queueUi.state.items = [
                    { id: "a", title: "A", sourcePaths: ["docs/x.md"], fixAttemptCount: 0, maxFixRounds: 2, reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"false_provenance\\"}]" },
                    { id: "b", title: "B", sourcePaths: ["docs/y.md"], fixAttemptCount: 0, maxFixRounds: 2, reviewIssuesJson: "[{\\"severity\\":\\"LOW\\",\\"category\\":\\"missing_referential\\"}]" }
                ];
                queueUi.state.filters.sourceFile = "";
                queueUi.state.filters.riskLevel = "";
                queueUi.state.filters.issueType = "";
                assert(queueUi.getFilteredItems().length === 2,
                    "getFilteredItems should return all items with no filter");
                queueUi.state.filters.sourceFile = "docs/x.md";
                assert(queueUi.getFilteredItems().length === 1,
                    "getFilteredItems should filter by source file");
                queueUi.state.filters.sourceFile = "";
                queueUi.state.filters.riskLevel = "LOW";
                assert(queueUi.getFilteredItems().length === 1,
                    "getFilteredItems should filter by risk level");
                queueUi.state.filters.riskLevel = "";

                assert(typeof queueUi.getFilterOptions === "function",
                    "missing getFilterOptions export");
                var opts = queueUi.getFilterOptions();
                assert(opts.sources.indexOf("docs/x.md") >= 0,
                    "getFilterOptions should list source files");

                // ---- New assertions: Enhanced list card structure ----

                queueUi.state.filters.groupBySource = false;
                queueUi.renderReviewQueueList(2);
                var listHtml = elements["review-queue-list"].innerHTML;
                assert(listHtml.includes(">A<") && listHtml.includes(">B<"),
                    "list should contain all items");
                // Enhanced cards show risk badge
                assert(listHtml.includes("review-queue-risk-badge"),
                    "list cards should include risk badge elements");
                // Enhanced cards show source file
                assert(listHtml.includes("x.md") || listHtml.includes("y.md"),
                    "list cards should include source file names");
                // Enhanced cards show issue count
                assert(listHtml.includes("个问题"),
                    "list cards should include issue count");
                // Enhanced cards show fix round info
                assert(listHtml.includes("修复"),
                    "list cards should include fix round info");
                // Enhanced cards show issue type tags
                assert(listHtml.includes("review-queue-issue-tag"),
                    "list cards should include issue type tags");

                // Grouped list rendering
                queueUi.state.filters.groupBySource = true;
                queueUi.renderReviewQueueList(2);
                var groupedHtml = elements["review-queue-list"].innerHTML;
                assert(groupedHtml.includes("review-queue-source-group"),
                    "grouped list should contain source group elements");
                assert(groupedHtml.includes("review-queue-source-group-header"),
                    "grouped list should contain source group headers");

                // ---- New assertions: Filter bar rendering ----

                assert(typeof queueUi.renderFilterBar === "function",
                    "missing renderFilterBar export");
                queueUi.renderFilterBar();
                var filterHtml = elements["review-queue-filter-bar"].innerHTML;
                assert(filterHtml.includes("review-queue-filter-source") && filterHtml.includes("review-queue-filter-risk"),
                    "filter bar should contain filter select elements");
                assert(filterHtml.includes("全部来源文件") && filterHtml.includes("docs/x.md"),
                    "source filter should contain all-source option and per-source options");
                assert(filterHtml.includes("review-queue-group-source"),
                    "filter bar should contain group toggle");

                // ---- New assertions: Detail order (review issues before content) ----

                queueUi.state.filters.groupBySource = false;
                queueUi.renderReviewQueueDetail({
                    id: "draft-order",
                    title: "排序测试",
                    content: "草稿正文内容",
                    sourcePaths: ["docs/test.md"],
                    reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"false_provenance\\",\\"description\\":\\"来源问题\\"}]",
                    fixAttemptCount: 1,
                    maxFixRounds: 2,
                    updatedAt: "2026-05-20T16:00:00+08:00"
                });
                var detailHtml = elements["review-queue-detail"].innerHTML;
                var issuesPos = detailHtml.indexOf("待人工确认说明");
                var contentPos = detailHtml.indexOf("草稿正文");
                assert(issuesPos >= 0 && contentPos >= 0,
                    "detail should contain both review issues and content sections");
                assert(issuesPos < contentPos,
                    "review issues should appear before article content in detail view");
                // Risk summary section exists
                assert(detailHtml.includes("审核概览"),
                    "detail should contain risk summary section");
                assert(detailHtml.includes("review-risk-summary-row"),
                    "detail should contain risk summary rows");

                // ---- Existing assertions (preserved): no window.prompt/confirm/alert ----

                var approveFnSrc = String(queueUi.approveSelectedReviewQueueItem);
                var rejectFnSrc = String(queueUi.rejectSelectedReviewQueueItem);
                assert(!approveFnSrc.includes("window.prompt") && !approveFnSrc.includes("window.confirm") && !approveFnSrc.includes("window.alert"),
                    "approveSelectedReviewQueueItem should not use window.prompt/confirm/alert");
                assert(!rejectFnSrc.includes("window.prompt") && !rejectFnSrc.includes("window.confirm") && !rejectFnSrc.includes("window.alert"),
                    "rejectSelectedReviewQueueItem should not use window.prompt/confirm/alert");
                assert(typeof queueUi.buildReviewActionModalHtml === "function",
                    "missing buildReviewActionModalHtml export");
                assert(typeof queueUi.openReviewActionModal === "function",
                    "missing openReviewActionModal export");
                assert(typeof queueUi.closeReviewActionModal === "function",
                    "missing closeReviewActionModal export");

                // ---- New assertions: Decision-panel approve modal ----

                var approveModalHtml = queueUi.buildReviewActionModalHtml({
                    id: "test-1",
                    title: "测试草稿",
                    sourcePaths: ["docs/test.md"],
                    reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"false_provenance\\",\\"description\\":\\"来源不一致\\"}]",
                    reviewStatus: "needs_human_review",
                    updatedAt: "2026-05-20T08:00:00+08:00"
                }, "approve");
                // Title
                assert(approveModalHtml.includes("确认入库"),
                    "approve modal should use confirm title");
                assert(!approveModalHtml.includes("确认这篇草稿可以入库？"),
                    "approve modal should not use old question-style title");
                // Draft title
                assert(approveModalHtml.includes("测试草稿"),
                    "approve modal should show draft title");

                // === Decision summary (top, main visual focus) ===
                assert(approveModalHtml.includes("review-decision-summary"),
                    "approve modal should have decision summary block");
                assert(approveModalHtml.includes("decision-summary-badges"),
                    "decision summary should contain badges row");
                assert(approveModalHtml.includes("高风险") || approveModalHtml.includes("中风险") || approveModalHtml.includes("低风险"),
                    "decision summary should show risk level label");
                assert(approveModalHtml.includes("decision-issue-count"),
                    "decision summary should show issue count");
                assert(approveModalHtml.includes("个待确认问题"),
                    "decision summary should show pending issue count text");
                assert(approveModalHtml.includes("decision-issue-tags") || approveModalHtml.includes("decision-issue-tag"),
                    "decision summary should show issue type tags");
                assert(approveModalHtml.includes("来源不一致"),
                    "decision summary should show mapped issue type name");
                assert(approveModalHtml.includes("decision-source-row"),
                    "decision summary should contain source info row");
                assert(approveModalHtml.includes("docs/test.md"),
                    "decision summary should show source file name");
                assert(approveModalHtml.includes("decision-context-hint"),
                    "decision summary should contain context hint text");
                // Source info is INSIDE decision summary (merged, not separate section)
                assert(!approveModalHtml.includes("来源摘要"),
                    "modal should not have separate source-summary section (merged into decision summary)");
                assert(!approveModalHtml.includes("为什么需要你确认"),
                    "modal should not have separate why-confirm section (replaced by decision summary)");

                // === Checklist (decision aid, middle) ===
                assert(approveModalHtml.includes("review-decision-checklist"),
                    "approve modal should have decision checklist block");
                assert(approveModalHtml.includes("核对清单"),
                    "approve modal should have checklist title");
                // Checklist items with risk-coded data attributes
                assert(approveModalHtml.includes("data-risk"),
                    "checklist items should have risk-level data attributes");
                assert(approveModalHtml.includes("是否超出源文范围"),
                    "checklist should include scope-boundary check");
                assert(approveModalHtml.includes("是否存在概念偏差"),
                    "checklist should include conceptual-distortion check");
                assert(approveModalHtml.includes("来源是否足以支撑正文"),
                    "checklist should include source-sufficiency check");

                // === Operation record (bottom, subdued) ===
                assert(approveModalHtml.includes("review-decision-record"),
                    "approve modal should have subdued record section");
                assert(approveModalHtml.includes("modal-operator"),
                    "approve modal should include operator input");
                assert(approveModalHtml.includes("操作人"),
                    "approve modal should include operator label");

                // Decision summary appears BEFORE record section in DOM order
                var summaryPos = approveModalHtml.indexOf("review-decision-summary");
                var recordPos = approveModalHtml.indexOf("review-decision-record");
                assert(summaryPos >= 0 && recordPos >= 0,
                    "modal should contain both summary and record sections");
                assert(summaryPos < recordPos,
                    "decision summary should appear before operation record");

                // Optional note field
                assert(approveModalHtml.includes("modal-approve-note") || approveModalHtml.includes("备注"),
                    "approve modal should include optional note field");
                // Action buttons
                assert(approveModalHtml.includes("确认入库"),
                    "approve modal should have confirm button");
                assert(approveModalHtml.includes("驳回"),
                    "approve modal should have reject button");
                // No old-style hint banner
                assert(!approveModalHtml.includes("确认后内容会进入正式知识库"),
                    "approve modal should not use old hint banner text");
                // No window.confirm
                assert(!approveModalHtml.includes("window.confirm"),
                    "approve modal html should not contain window.confirm");

                // ---- New assertions: Decision-panel reject modal ----

                var rejectModalHtml = queueUi.buildReviewActionModalHtml({
                    id: "test-2",
                    title: "测试草稿",
                    sourcePaths: ["docs/test.md"],
                    reviewIssuesJson: "[{\\"severity\\":\\"HIGH\\",\\"category\\":\\"conceptual_distortion\\"}]",
                    reviewStatus: "needs_human_review",
                    updatedAt: "2026-05-20T08:00:00+08:00"
                }, "reject");
                // Title
                assert(rejectModalHtml.includes("驳回草稿"),
                    "reject modal should use reject title");
                assert(!rejectModalHtml.includes("驳回这篇草稿？"),
                    "reject modal should not use old question-style title");
                // Decision summary present
                assert(rejectModalHtml.includes("review-decision-summary"),
                    "reject modal should have decision summary");
                // Checklist present with additional reject item
                assert(rejectModalHtml.includes("核对清单"),
                    "reject modal should have checklist");
                // Reason textarea with hint
                assert(rejectModalHtml.includes("modal-reject-reason"),
                    "reject modal should include reason textarea");
                assert(rejectModalHtml.includes("驳回原因"),
                    "reject modal should label reason field");
                assert(rejectModalHtml.includes("modal-field-hint"),
                    "reject modal should include reason hint text");
                assert(rejectModalHtml.includes("超出源文范围") || rejectModalHtml.includes("概念偏差"),
                    "reject modal hint should suggest reason categories");
                // Confirm button
                assert(rejectModalHtml.includes("确认驳回"),
                    "reject modal should have confirm reject button");
                // Record section present
                assert(rejectModalHtml.includes("review-decision-record"),
                    "reject modal should have record section");
                // Operator not the visual focus (record section comes after summary)
                var rejectRecordPos = rejectModalHtml.indexOf("review-decision-record");
                var rejectSummaryPos = rejectModalHtml.indexOf("review-decision-summary");
                assert(rejectSummaryPos >= 0 && rejectRecordPos > rejectSummaryPos,
                    "in reject modal, decision summary should appear before operation record");

                // ---- Existing assertions (preserved): modal ESC/keydown cleanup ----

                var closeModalSrc = String(queueUi.closeReviewActionModal);
                assert(closeModalSrc.includes("removeEventListener"),
                    "closeReviewActionModal should call removeEventListener for keydown cleanup");
                assert(closeModalSrc.includes("_modalKeydownHandler"),
                    "closeReviewActionModal should reference _modalKeydownHandler");

                var openModalSrc = String(queueUi.openReviewActionModal);
                assert(openModalSrc.includes("_modalKeydownHandler"),
                    "openReviewActionModal should clean up stale _modalKeydownHandler");

                var approveSrc = String(queueUi.approveSelectedReviewQueueItem);
                assert(!approveSrc.includes("window.confirm") && !approveSrc.includes("window.prompt"),
                    "approveSelectedReviewQueueItem should not use window.confirm/prompt");

                // ---- New assertion: keyboard navigation handler exists ----
                assert(queueSource.includes("handleListKeydown"),
                    "compile-review-queue.js should define keyboard navigation handler");
                assert(queueSource.includes("ArrowUp") && queueSource.includes("ArrowDown"),
                    "compile-review-queue.js should handle arrow key navigation");

                // ---- New assertion: no fake batch approve button ----
                assert(!queueSource.includes("批量确认") && !queueSource.includes("batch-approve") && !queueSource.includes("batchApprove"),
                    "compile-review-queue.js should not contain batch approve functionality");

                console.log("compile-review-queue-runtime-tests:ok");
                """;
    }

    /**
     * 验证 ask-runtime-part-02.js 中 renderMarkdownLite 的 flushParagraph
     * 在格式化后内容为空时不会生成空 {@code <p></p>}，且引用标记仍可正常渲染。
     *
     * @throws Exception 读取文件异常
     */
    @Test
    void shouldPreventEmptyParagraphInMarkdownRendering() throws Exception {
        String userDir = System.getProperty("user.dir");
        Path askRuntimePath = Path.of(userDir,
                "src/main/resources/static/admin/modules/ask-runtime-part-02.js");
        assertThat(Files.exists(askRuntimePath)).isTrue();

        String source = Files.readString(askRuntimePath, StandardCharsets.UTF_8);
        // flushParagraph must check content before pushing <p>
        assertThat(source).contains("_content.trim()");
        assertThat(source).contains("paragraphBuffer = []");
        // citation marker placeholder still preserved
        assertThat(source).contains("CITATION_MARKER_");
        // Must not generate empty <p></p> after trim check
        assertThat(source).doesNotContain("\"<p>\" + formatInlineMarkdown");
    }

    /**
     * 读取类路径资源内容。
     *
     * @param resourcePath 资源路径
     * @return 资源文本
     */
    private String readClasspathResource(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream)
                    .as("missing classpath resource: %s", resourcePath)
                    .isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read classpath resource: " + resourcePath, exception);
        }
    }

    /**
     * 读取子进程输出。
     *
     * @param process 子进程
     * @return 输出文本
     * @throws IOException IO 异常
     */
    private String readProcessOutput(Process process) throws IOException {
        InputStream inputStream = process.getInputStream();
        byte[] content = inputStream.readAllBytes();
        return new String(content, StandardCharsets.UTF_8).trim();
    }
}
