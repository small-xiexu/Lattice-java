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
        return """
                const fs = require("fs");
                const path = require("path");
                const vm = require("vm");

                const commonSource = fs.readFileSync(process.argv[2], "utf8");
                const moduleDir = process.argv[3];
                const modulePrefix = process.argv[4];
                function parsePart(name) {
                    const moduleSource = fs.readFileSync(path.join(moduleDir, name), "utf8");
                    const matched = moduleSource.match(/export default (.*);\\s*$/s);
                    if (!matched) {
                        throw new Error("invalid runtime module: " + name);
                    }
                    return JSON.parse(matched[1]);
                }

                const runtimeModuleNames = fs.readdirSync(moduleDir)
                    .filter(function (name) { return name.startsWith(modulePrefix + "-runtime-part-"); })
                    .sort();
                const lastName = runtimeModuleNames[runtimeModuleNames.length - 1];
                const namesWithoutLast = runtimeModuleNames.slice(0, -1);
                const runtimeParts = namesWithoutLast.map(parsePart);

                let historyPart = "";
                try {
                    const historyModuleSource = fs.readFileSync(path.join(moduleDir, "management-history-part.js"), "utf8");
                    const historyMatched = historyModuleSource.match(/export default (.*);\\s*$/s);
                    if (historyMatched) {
                        historyPart = JSON.parse(historyMatched[1]);
                    }
                } catch (e) {}

                if (historyPart) {
                    runtimeParts.push(historyPart);
                }
                runtimeParts.push(parsePart(lastName));
                const source = runtimeParts.join("\\n");
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
                        getElementById: function () { return null; },
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
                vm.runInNewContext(source, sandbox, { filename: "management.js" });

                const runs = sandbox.__LATTICE_ADMIN_TEST__.runs;
                const sourceUi = sandbox.__LATTICE_ADMIN_TEST__.source;
                const knowledgeUi = sandbox.__LATTICE_ADMIN_TEST__.knowledge;
                const feedbackUi = sandbox.__LATTICE_ADMIN_TEST__.feedback;
                const articleUi = sandbox.__LATTICE_ADMIN_TEST__.article;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(runs, "missing __LATTICE_ADMIN_TEST__.runs export");
                assert(sourceUi, "missing __LATTICE_ADMIN_TEST__.source export");
                assert(knowledgeUi, "missing __LATTICE_ADMIN_TEST__.knowledge export");
                assert(feedbackUi, "missing __LATTICE_ADMIN_TEST__.feedback export");
                assert(articleUi, "missing __LATTICE_ADMIN_TEST__.article export");
                assert(typeof sourceUi.focusSourceRunDetail === "function",
                    "missing focusSourceRunDetail export");
                assert(typeof runs.selectRecentRunBoardItems === "function",
                    "missing selectRecentRunBoardItems export");

                const fallbackRun = { status: "RUNNING", currentStepLabel: "写入知识库", progressText: "等待下一步刷新" };
                assert(runs.resolveRunDisplayStatus(fallbackRun) === "RUNNING",
                    "should fallback to base run status when derived fields are absent");
                assert(runs.resolveRunStepLabel(fallbackRun) === "写入知识库",
                    "should prefer backend-provided current step label");
                assert(runs.resolveRunProgressText({ progressText: "等待后台 worker 领取" }) === "等待后台 worker 领取",
                    "should prefer backend-provided progress text");
                const duplicateSummaryRun = {
                    status: "RUNNING",
                    currentStepLabel: "质量检查",
                    progressText: "13 / 15 · 正在修复文章（13/15）：卡券三期-迁移方案-场景8-星礼包退款链路",
                    reasonSummary: "正在修复文章（13/15）：卡券三期-迁移方案-场景8-星礼包退款链路"
                };
                assert(runs.resolveRunSpotlightSummaryText(duplicateSummaryRun) === "",
                    "spotlight summary should be hidden when it duplicates current progress");
                assert(runs.resolveRunNextStepText({
                    nextStepHint: "继续等待当前真实步骤推进",
                    progressText: "13 / 15 · 正在修复文章（13/15）：卡券三期-迁移方案-场景8-星礼包退款链路"
                }, {
                    nextStep: "继续等待当前真实步骤推进"
                }) === "",
                    "placeholder next step should be hidden");
                const runningDuplicateReasonRun = {
                    status: "RUNNING",
                    displayStatus: "RUNNING",
                    currentStepLabel: "内容生成",
                    progressText: "1 / 20 · 正在生成文章（1/20）：卡券三期-迁移方案-srkit-svc-卡履约链路从-fc-平移至-dpfm",
                    reasonSummary: "正在生成文章（1/20）：卡券三期-迁移方案-srkit-svc-卡履约链路从-fc-平移至-dpfm"
                };
                const runningDuplicateSnapshot = runs.buildRunRuntimeSnapshot(runningDuplicateReasonRun);
                assert(!runs.shouldRenderRunReasonSummary(runningDuplicateReasonRun),
                    "running duplicated progress reason should be hidden");
                assert(runningDuplicateSnapshot.includes("当前进度"),
                    "running snapshot should keep current progress");
                assert(!runningDuplicateSnapshot.includes("原因摘要"),
                    "running duplicated reason should not render reason summary");
                assert(runs.shouldRenderRunAsBoardFocus(runningDuplicateReasonRun),
                    "running task should stay as the focused processing task card");
                const runningWarningProgressStrip = runs.buildRunProgressStrip({
                    status: "RUNNING",
                    displayStatus: "RUNNING",
                    compileDerivedStatus: "RUNNING",
                    displayTone: "warning",
                    progressSteps: [{
                        key: "TASK_RECEIVED",
                        label: "资料接收",
                        status: "COMPLETED",
                        detail: ""
                    }, {
                        key: "COMPILE_NEW_ARTICLES",
                        label: "内容生成",
                        status: "ACTIVE",
                        detail: "正在生成文章草稿"
                    }, {
                        key: "REVIEW_ARTICLES",
                        label: "质量检查",
                        status: "PENDING",
                        detail: ""
                    }]
                }, {
                    label: "进行中",
                    nextStep: "继续等待当前真实步骤推进",
                    tone: "warning"
                });
                assert(runningWarningProgressStrip.includes("run-progress-step active"),
                    "running warning tone should keep the active progress step");
                assert(!runningWarningProgressStrip.includes("run-progress-status-mark warning"),
                    "running warning tone should not be rendered as stalled");
                assert(!runningWarningProgressStrip.includes(">卡住<"),
                    "running warning tone should not show stalled copy");

                const stalledRun = {
                    status: "RUNNING",
                    displayStatus: "STALLED",
                    currentStepLabel: "质量检查",
                    progressText: "2 / 6 · 正在审查第 2 篇文章",
                    reasonSummary: "任务长时间没有新的心跳或进度更新，建议重新同步资料源。",
                    compileDerivedStatus: "STALLED",
                    compileCurrentStep: "review_articles",
                    compileProgressCurrent: 2,
                    compileProgressTotal: 6,
                    compileProgressMessage: "正在审查第 2 篇文章",
                    compileLastHeartbeatAt: "2026-04-24T08:00:00+08:00",
                    sourceNames: ["docs/payment/order-guide.md", "docs/payment/retry.md"],
                    sourceId: 12,
                    progressSteps: [{
                        key: "INITIALIZE_JOB",
                        label: "资料接收",
                        status: "COMPLETED",
                        detail: ""
                    }, {
                        key: "INGEST_SOURCES",
                        label: "资料接收",
                        status: "COMPLETED",
                        detail: ""
                    }, {
                        key: "REVIEW_ARTICLES",
                        label: "质量检查",
                        status: "FAILED",
                        detail: "细分状态：正在审查文章草稿"
                    }],
                    actions: [{
                        actionKey: "RESYNC_SOURCE",
                        label: "重新同步当前资料源",
                        buttonClass: "secondary-btn",
                        runId: 12,
                        sourceId: 12,
                        uploadRetry: false
                    }]
                };
                const runtimeSnapshot = runs.buildRunRuntimeSnapshot(stalledRun);
                assert(!runtimeSnapshot.includes("编译态"),
                    "runtime snapshot should hide duplicate derived status");
                assert(!runtimeSnapshot.includes("当前步骤"),
                    "runtime snapshot should hide current step when progress strip is present");
                assert(runtimeSnapshot.includes("2 / 6"),
                    "runtime snapshot should expose current progress");
                assert(!runtimeSnapshot.includes("原因摘要"),
                    "runtime snapshot should hide reason summary when failure panel already covers it");
                const progressStrip = runs.buildRunProgressStrip(stalledRun, {
                        label: "失败",
                        nextStep: "查看最近推进时间并重新同步资料源",
                        tone: "danger"
                    });
                assert(progressStrip.includes("run-progress-detail"),
                    "progress strip should expose detail copy for current real sub-step");
                assert(progressStrip.includes("质量检查"),
                    "progress strip should show current real compile step under grouped stage");
                assert(progressStrip.includes("run-progress-status-mark warning"),
                    "progress strip should expose an explicit stalled status mark");
                assert(progressStrip.includes(">卡住<"),
                    "progress strip should show stalled copy only for STALLED status");
                assert(!progressStrip.includes("run-progress-status-mark failed"),
                    "stalled progress strip should not be mislabeled as a generic failure");
                assert(progressStrip.includes("正在审查文章草稿"),
                    "progress strip should keep cleaned detail copy");
                assert(!progressStrip.includes("细分状态"),
                    "progress strip should not render redundant detail label");
                assert(runs.buildRunReasonSummary(stalledRun).includes("长时间没有新的心跳"),
                    "stalled run should explain stalled reason");
                assert(!runs.shouldRenderRunReasonSummary(stalledRun),
                    "stalled run should hide duplicate reason summary when failure panel covers it");
                assert(runs.shouldRenderRunAsBoardFocus(stalledRun),
                    "stalled run should stay as a focused processing task card");
                const compactRunMarkup = runs.renderSourceRunListItem(stalledRun, true);
                assert(compactRunMarkup.includes("detail-compact-item active"),
                    "source run list should render compact active rows");
                assert(!compactRunMarkup.includes("run-runtime-grid"),
                    "source run list row should stay compact and not inline runtime snapshot");
                assert(compactRunMarkup.includes("docs/payment/order-guide.md"),
                    "upload run title should prefer current imported file name");
                const runDetailMarkup = runs.buildSourceRunDetailCard(stalledRun, {
                    label: "失败",
                    nextStep: "查看最近推进时间并重新同步资料源",
                    tone: "danger"
                });
                assert(runDetailMarkup.includes("run-runtime-summary"),
                    "selected source run detail should render compact runtime summary");
                assert(runDetailMarkup.includes("本次文件"),
                    "selected source run detail should expose processed file summary");
                assert(runDetailMarkup.includes("最近更新时间"),
                    "selected source run detail should merge timestamps into updated-at copy");
                assert(runDetailMarkup.includes("card-actions"),
                    "selected source run detail should keep action buttons");
                const structuredActionMarkup = runs.buildSourceRunDetailCard({
                    status: "FAILED",
                    displayStatus: "FAILED",
                    displayStatusLabel: "失败",
                    nextStepHint: "检查处理提示后重新同步资料源",
                    reasonSummary: "编译执行过程中出现异常",
                    actions: [{
                        actionKey: "RESYNC_SOURCE",
                        label: "重新同步当前资料源",
                        buttonClass: "secondary-btn",
                        runId: 12,
                        sourceId: 99,
                        uploadRetry: false
                    }]
                }, {
                    label: "处理失败",
                    nextStep: "检查处理提示后重新同步资料源",
                    tone: "danger"
                });
                assert(structuredActionMarkup.includes("data-resync-source='99'"),
                    "detail card should render backend-provided structured action");

                const succeededRun = {
                    status: "SUCCEEDED",
                    displayStatus: "SUCCEEDED",
                    sourceType: "UPLOAD",
                    resolverDecision: "NEW_SOURCE",
                    title: "卡券三期-迁移方案.md",
                    message: "处理成功，资料已写入知识库",
                    updatedAt: "2026-05-02T15:08:00+08:00"
                };
                assert(!runs.shouldRenderRunAsBoardFocus(succeededRun),
                    "succeeded run should not occupy the strong current-task card");
                assert(runs.shouldRenderRunAsCompletionNotice(succeededRun),
                    "succeeded run should render as lightweight completion notice");
                const succeededRunMarkup = runs.renderRecentRunBoardItem(succeededRun);
                assert(succeededRunMarkup.includes("run-completion-notice"),
                    "succeeded run should use the completion notice presentation");
                assert(!succeededRunMarkup.includes("run-progress-strip"),
                    "succeeded completion notice should not duplicate full history details");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.recentRuns = [succeededRun];
                assert(runs.shouldPromoteCompletionRunAsBoardFocus(succeededRun),
                    "latest completion run should be promoted when there are no active focus tasks");
                const promotedRunMarkup = runs.renderRecentRunBoardItem(succeededRun);
                assert(promotedRunMarkup.includes("run-spotlight-card"),
                    "promoted completion run should render as a spotlight card");
                assert(!promotedRunMarkup.includes("当前阶段"),
                    "promoted completion run should hide duplicated stage highlight");
                const olderSucceededRun = Object.assign({}, succeededRun, {
                    title: "较早完成任务.md",
                    updatedAt: "2026-05-01T15:08:00+08:00",
                    requestedAt: "2026-05-01T15:00:00+08:00"
                });
                const latestSucceededRun = Object.assign({}, succeededRun, {
                    title: "最新完成任务.md",
                    updatedAt: "2026-05-02T15:08:00+08:00",
                    requestedAt: "2026-05-02T15:00:00+08:00"
                });
                const selectedCompletionItems = runs.selectRecentRunBoardItems([olderSucceededRun, latestSucceededRun]);
                assert(selectedCompletionItems.length === 1,
                    "run board should show only the latest completion when there is no active task");
                assert(selectedCompletionItems[0].title === "最新完成任务.md",
                    "run board should keep the newest completion notice only");
                const operationalNoteMarkup = runs.renderRecentRunBoardItem({
                    status: "RUNNING",
                    displayStatus: "RUNNING",
                    sourceType: "UPLOAD",
                    title: "正在处理的资料.md",
                    progressText: "正在整理资料",
                    operationalNote: "请等待本轮处理完成后再提问",
                    requestedAt: "2026-05-02T16:08:00+08:00"
                });
                assert(operationalNoteMarkup.includes("请等待本轮处理完成后再提问"),
                    "operational note should still render useful action copy");
                assert(!operationalNoteMarkup.includes("任务线索"),
                    "operational note should not expose the old task-clue label");
                const waitConfirmSnapshot = runs.buildRunRuntimeSnapshot({
                    status: "WAIT_CONFIRM",
                    displayStatus: "WAIT_CONFIRM",
                    progressText: "等待人工确认",
                    reasonSummary: "需要选择这批资料的归并方式"
                });
                assert(waitConfirmSnapshot.includes("处理提示"),
                    "runtime snapshot should use user-facing processing hint label");
                assert(!waitConfirmSnapshot.includes("原因摘要"),
                    "runtime snapshot should not expose engineering-style reason summary label");
                const waitConfirmMarkup = runs.renderRecentRunBoardItem({
                    status: "WAIT_CONFIRM",
                    displayStatus: "WAIT_CONFIRM",
                    title: "待确认资料.md",
                    reasonSummary: "需要选择这批资料的归并方式"
                });
                assert(waitConfirmMarkup.includes("待人工确认"),
                    "wait-confirm status should render the unified human-confirmation label");
                assert(!waitConfirmMarkup.includes(">待确认<"),
                    "wait-confirm status should not render the old short confirmation label");
                const publishReviewMarkup = runs.renderRecentRunBoardItem({
                    taskType: "SOURCE_SYNC",
                    status: "SUCCEEDED",
                    displayStatus: "SUCCEEDED",
                    displayStatusLabel: "待人工确认",
                    requiresManualAction: true,
                    sourceType: "UPLOAD",
                    syncAction: "UPDATE",
                    title: "待人工确认任务",
                    reasonSummary: "质量检查已完成，等待人工确认后决定是否入库",
                    pendingHumanReviewCount: 2,
                    requestedAt: "2026-05-02T16:08:00+08:00"
                });
                assert(publishReviewMarkup.includes("待人工确认草稿"),
                    "task card should distinguish draft count from top-level task count");
                assert(publishReviewMarkup.includes("2 篇"),
                    "task card should render pending draft count");

                const failedRun = {
                    status: "FAILED",
                    reasonSummary: "调用模型时发生链路异常，请检查网络、路由配置或模型服务可用性。",
                    compileErrorCode: "LLM_TRANSPORT_ERROR"
                };
                assert(runs.buildRunReasonSummary(failedRun).includes("链路异常"),
                    "failed run should prefer backend-provided reason summary");
                assert(!runs.shouldRenderRunReasonSummary(failedRun),
                    "failed run should hide duplicate reason summary when failure panel covers it");
                const sanitized = runs.sanitizeDisplayMessage(
                    "java.net.SocketTimeoutException: Read timed out\\n at com.example.Test"
                );
                assert(sanitized === "Read timed out",
                    "sanitizeDisplayMessage should strip exception class and stack trace");

                const conflictMessage = runs.resolveHttpErrorDisplayMessage({
                    payload: {
                        code: "SOURCE_SYNC_CONFLICT",
                        message: "java.lang.IllegalStateException: conflict"
                    },
                    message: "boom"
                });
                assert(conflictMessage.includes("已经有运行中的同步任务"),
                    "should use stable conflict message instead of raw backend message");

                const elementState = {};
                sandbox.document.getElementById = function (id) {
                    if (!elementState[id]) {
                        elementState[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false
                        };
                    }
                    return elementState[id];
                };
                articleUi.renderArticleDetail({
                    articleKey: "article-001",
                    conceptId: "article-001",
                    title: "入库时间测试",
                    content: "正文",
                    lifecycle: "ACTIVE",
                    reviewStatus: "passed",
                    summary: "摘要",
                    sourceCount: 1,
                    sourcePaths: ["docs/demo.md"],
                    riskLevel: "low",
                    riskReasons: [],
                    updatedAt: "2026-05-02T15:08:00+08:00",
                    compiledAt: "2026-05-10T22:30:00+08:00"
                });
                assert(elementState["article-detail-meta"].textContent.includes("入库时间：05/02 15:08"),
                    "article detail should render stored updatedAt as ingestion time");
                assert(!elementState["article-detail-meta"].textContent.includes("05/10 22:30"),
                    "article detail should not render compiledAt as ingestion time");
                assert(articleUi.buildArticleListRequestUrl("订单", "ACTIVE", "12", "needs_human_review")
                        === "/api/v1/admin/articles?query=%E8%AE%A2%E5%8D%95&lifecycle=ACTIVE&sourceId=12&reviewStatus=needs_human_review",
                    "article list request should include generic reviewStatus filter");
                assert(articleUi.buildArticleListRequestUrl("订单", "ACTIVE", "12", "passed", "riskReason:user_reported")
                        === "/api/v1/admin/articles?query=%E8%AE%A2%E5%8D%95&lifecycle=ACTIVE&sourceId=12&reviewStatus=passed&riskReason=user_reported",
                    "article list request should include generic risk filter");
                assert(articleUi.buildArticleRiskSummary({
                    riskLevel: "high",
                    riskReasons: ["source_conflict", "low_traceability"],
                    isHotspot: true,
                    requiresResultVerification: true
                }).includes("来源冲突"),
                    "risk summary should render generic risk reasons");
                // buildArticleRiskSummary: new terms, no old internal terms
                var hotspotRiskSummary = articleUi.buildArticleRiskSummary({
                    riskLevel: "medium",
                    riskReasons: [],
                    isHotspot: true,
                    requiresResultVerification: true
                });
                assert(hotspotRiskSummary.includes("高频问题相关"),
                    "risk summary should use 高频问题相关 not 高频热点");
                assert(!hotspotRiskSummary.includes("高频热点"),
                    "risk summary should not expose internal term 高频热点");
                assert(hotspotRiskSummary.includes("需关注"),
                    "risk summary should use 需关注 not 需要结果抽检");
                assert(!hotspotRiskSummary.includes("需要结果抽检"),
                    "risk summary should not expose internal term 需要结果抽检");
                assert(!hotspotRiskSummary.includes("抽检"),
                    "risk summary should not expose internal term 抽检");
                var lowRiskNoFlags = articleUi.buildArticleRiskSummary({
                    riskLevel: "low",
                    riskReasons: [],
                    isHotspot: false,
                    requiresResultVerification: false
                });
                assert(lowRiskNoFlags.includes("暂无额外关注原因"),
                    "low risk summary should use 暂无额外关注原因 not 暂无额外抽检原因");
                assert(!lowRiskNoFlags.includes("抽检"),
                    "low risk summary should not expose 抽检");
                var hotspotFnSrc = String(articleUi.buildArticleRiskSummary);
                assert(!hotspotFnSrc.includes("高频热点"),
                    "buildArticleRiskSummary source should not contain 高频热点");
                assert(!hotspotFnSrc.includes("需要结果抽检"),
                    "buildArticleRiskSummary source should not contain 需要结果抽检");
                assert(articleUi.shouldShowArticleReviewPanel({ reviewStatus: "needs_human_review" }),
                    "needs_human_review article should show manual review panel");
                assert(articleUi.shouldShowArticleReviewPanel({ reviewStatus: "needs_review" }),
                    "needs_review article should show manual review panel");
                assert(!articleUi.shouldShowArticleReviewPanel({ reviewStatus: "passed" }),
                    "passed article should hide manual review panel");
                assert(articleUi.buildArticleReviewNote({ reviewStatus: "needs_review" }).includes("提交过修正"),
                    "needs_review note should explain correction state");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedArticleId = "article-001";
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedArticleSourceId = 7;
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedArticleReviewStatus = "needs_human_review";
                elementState["article-reviewer"].value = "";
                elementState["article-review-comment"].value = "证据稳定";
                elementState["article-correction-summary"].value = "补充来源";
                const articleReviewRequest = articleUi.buildArticleReviewRequest(true);
                assert(articleReviewRequest.sourceId === 7,
                    "manual review request should keep selected source id");
                assert(articleReviewRequest.reviewedBy === "admin",
                    "manual review request should default reviewer");
                assert(articleReviewRequest.expectedReviewStatus === "needs_human_review",
                    "manual review request should carry expected status");
                assert(articleReviewRequest.correctionSummary === "补充来源",
                    "request-changes payload should include correction summary");
                articleUi.renderArticleDetail({
                    articleKey: "article-002",
                    conceptId: "article-002",
                    title: "人工复核测试",
                    content: "正文",
                    lifecycle: "ACTIVE",
                    reviewStatus: "needs_human_review",
                    summary: "摘要",
                    sourceCount: 1,
                    sourcePaths: ["docs/review.md"],
                    riskLevel: "medium",
                    riskReasons: ["user_reported"],
                    isHotspot: true,
                    requiresResultVerification: true,
                    updatedAt: "2026-05-05T10:00:00+08:00"
                }, {
                    items: [{
                        action: "approve",
                        previousReviewStatus: "needs_human_review",
                        nextReviewStatus: "passed",
                        reviewedBy: "reviewer",
                        reviewedAt: "2026-05-05T10:20:00+08:00",
                        comment: "确认通过"
                    }]
                });
                assert(elementState["article-review-panel"].hidden === false,
                    "manual review panel should be visible for needs_human_review detail");
                assert(elementState["article-reviewer"].value === "admin",
                    "manual review panel should set default reviewer");
                assert(elementState["article-review-history"].innerHTML.includes("确认通过"),
                    "review history should render readable approve action");
                assert(elementState["article-review-history"].innerHTML.includes("review-history-row"),
                    "review history should render compact timeline row structure");
                assert(elementState["article-review-history"].innerHTML.includes("review-history-action"),
                    "review history should render action label");
                assert(elementState["article-review-history"].innerHTML.includes("review-history-status"),
                    "review history should render status change");
                assert(elementState["article-review-history"].innerHTML.includes("review-history-time"),
                    "review history should render timestamp");
                assert(!elementState["article-review-history"].innerHTML.includes("review-history-head"),
                    "review history should not use old dark gray card structure");
                assert(elementState["article-risk-summary"].innerHTML.includes("用户反馈"),
                    "detail should render readable risk notice");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.sourceFiles = [{
                    sourceId: 7,
                    relativePath: "docs/review.md",
                    format: "md",
                    contentPreview: "preview text"
                }];
                const sourceReferenceMarkup = articleUi.renderArticleSourceReferences({
                    sourceId: 7,
                    sourcePaths: ["docs/review.md"]
                });
                assert(sourceReferenceMarkup.includes("data-article-source-path"),
                    "source references should expose preview trigger for matching source file");
                articleUi.renderArticleSourcePreview({
                    relativePath: "docs/review.md",
                    format: "md",
                    contentPreview: "preview text"
                });
                assert(elementState["article-source-preview"].hidden === false,
                    "source preview panel should become visible");
                assert(elementState["article-source-preview"].innerHTML.includes("preview text"),
                    "source preview panel should render contentPreview only");
                // 二次渲染回归：验证 closest(".detail-section") 路径连续两次渲染
                // 不会产生 details 嵌套、id 丢失或 TypeError
                delete elementState["article-metadata"];
                var metadataSectionState = { innerHTML: "" };
                var technicalInfoState = { innerHTML: "" };
                sandbox.document.getElementById = function (id) {
                    if (id === "article-technical-info") {
                        elementState[id] = technicalInfoState;
                        return technicalInfoState;
                    }
                    if (!elementState[id]) {
                        var el = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false
                        };
                        if (id === "article-metadata") {
                            el.closest = function (selector) {
                                if (selector === ".detail-section") {
                                    return metadataSectionState;
                                }
                                return null;
                            };
                        }
                        elementState[id] = el;
                    }
                    return elementState[id];
                };
                articleUi.renderArticleDetail({
                    articleKey: "article-010",
                    conceptId: "article-010",
                    title: "二次渲染测试 A",
                    content: "正文",
                    lifecycle: "ACTIVE",
                    summary: "摘要",
                    sourceCount: 1,
                    sourcePaths: ["docs/a.md"],
                    updatedAt: "2026-05-02T16:00:00+08:00",
                    metadataJson: "a",
                    isHotspot: true,
                    requiresResultVerification: true
                });
                var _round1Html = metadataSectionState.innerHTML;
                assert(_round1Html.includes("article-metadata-toggle"),
                    "first render should wrap metadata in details");
                assert(_round1Html.includes("a"),
                    "first render should contain first metadata text");
                articleUi.renderArticleDetail({
                    articleKey: "article-011",
                    conceptId: "article-011",
                    title: "二次渲染测试 B",
                    content: "正文B",
                    lifecycle: "ACTIVE",
                    summary: "摘要B",
                    sourceCount: 1,
                    sourcePaths: ["docs/b.md"],
                    updatedAt: "2026-05-02T17:00:00+08:00",
                    metadataJson: "b"
                });
                var _round2Html = metadataSectionState.innerHTML;
                assert(_round2Html.includes("article-metadata-toggle"),
                    "second render should still wrap metadata in details");
                assert(_round2Html.includes("b"),
                    "second render should show second metadata text");
                var _toggleCount = (_round2Html.match(/article-metadata-toggle/g) || []).length;
                assert(_toggleCount === 1,
                    "second render should not nest multiple article-metadata-toggle, got " + _toggleCount);
                assert(elementState["article-metadata"] !== undefined,
                    "article-metadata element should still exist after two renders");
                const summaryElements = {};
                sandbox.document.getElementById = function (id) {
                    if (!summaryElements[id]) {
                        summaryElements[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false,
                            dataset: {},
                            setAttribute: function (name, value) {
                                this[name] = value;
                            },
                            querySelectorAll: function () { return []; }
                        };
                    }
                    return summaryElements[id];
                };
                sandbox.__LATTICE_ADMIN_TEST_STATE__.sources = [];
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview = {
                    status: {
                        articleCount: 3,
                        sourceFileCount: 2,
                        contributionCount: 0,
                        pendingQueryCount: 0,
                        reviewPendingArticleCount: 1,
                        humanReviewDraftPendingCount: 2,
                        highRiskArticleCount: 2,
                        hotspotPendingVerificationCount: 1,
                        userReportedAnswerCount: 1,
                        answerFeedbackPendingCount: 2
                    }
                };
                knowledgeUi.renderSummary(sandbox.__LATTICE_ADMIN_TEST_STATE__.overview, {});
                assert(summaryElements["summary-cards"].innerHTML.includes("summary-primary-grid"),
                    "summary cards should render primary metric section");
                assert(summaryElements["summary-cards"].innerHTML.includes("summary-secondary-panel"),
                    "summary cards should fold lower-priority governance metrics");
                assert(summaryElements["summary-cards"].innerHTML.includes("待人工确认草稿"),
                    "summary cards should expose compile review queue pending draft count");
                assert(summaryElements["summary-cards"].innerHTML.includes("质量检查需要人工确认的草稿"),
                    "summary card should distinguish unpublished compile review drafts");
                assert(summaryElements["summary-cards"].innerHTML.includes("答案反馈待处理"),
                    "summary cards should expose answer feedback pending count with user-facing copy");
                assert(summaryElements["summary-cards"].innerHTML.includes("已确认修正"),
                    "summary cards should rename feedback contribution to confirmed fixes");
                assert(summaryElements["summary-cards"].innerHTML.includes("待分析提问"),
                    "summary cards should rename pending query backlog");
                assert(summaryElements["summary-cards"].innerHTML.includes("已入库待复核"),
                    "summary cards should keep article review backlog as a secondary governance metric");
                assert(summaryElements["summary-cards"].innerHTML.includes("高风险内容"),
                    "summary cards should expose high risk count");
                assert(summaryElements["summary-cards"].innerHTML.includes("复核状态筛选"),
                    "summary card should guide to review status filter");
                assert(!summaryElements["summary-cards"].innerHTML.includes("反馈沉淀"),
                    "summary cards should avoid old engineering-style contribution copy");
                assert(!summaryElements["summary-cards"].innerHTML.includes("待处理反馈"),
                    "summary cards should avoid ambiguous pending feedback copy");
                assert(!summaryElements["summary-cards"].innerHTML.includes("结果反馈待处理"),
                    "summary cards should use clearer answer feedback copy");

                const metricCardWithAction = knowledgeUi.renderMetricCard({
                    label: "待人工确认草稿",
                    value: 2,
                    action: "{\\"tab\\":\\"knowledge-runs\\",\\"scrollTo\\":\\"review-queue-list\\"}",
                    actionHint: "去处理 →"
                });
                assert(metricCardWithAction.includes("<button type='button'"),
                    "metric card with action should render as button element");
                assert(metricCardWithAction.includes("data-metric-action="),
                    "metric card with action should render data-metric-action attribute");
                assert(metricCardWithAction.includes("clickable"),
                    "metric card with action should have clickable class");
                assert(metricCardWithAction.includes("action-hint"),
                    "metric card with actionHint should render action-hint span");
                assert(metricCardWithAction.includes("去处理 →"),
                    "metric card action hint should be visible");

                const metricCardWithoutAction = knowledgeUi.renderMetricCard({
                    label: "知识条目",
                    value: 100
                });
                assert(metricCardWithoutAction.includes("<div"),
                    "metric card without action should render as div");
                assert(!metricCardWithoutAction.includes("<button"),
                    "metric card without action should not contain button tag");
                assert(!metricCardWithoutAction.includes("data-metric-action="),
                    "metric card without action should not render data-metric-action attribute");
                assert(!metricCardWithoutAction.includes("clickable"),
                    "metric card without action should not have clickable class");
                assert(!metricCardWithoutAction.includes("action-hint"),
                    "metric card without actionHint should not render action-hint span");

                const metricCardZeroValue = knowledgeUi.renderMetricCard({
                    label: "待分析提问",
                    value: 0,
                    action: undefined,
                    actionHint: undefined
                });
                assert(metricCardZeroValue.includes("<div"),
                    "metric card with undefined action should render as div");
                assert(!metricCardZeroValue.includes("<button"),
                    "metric card with undefined action should not contain button tag");
                assert(!metricCardZeroValue.includes("data-metric-action="),
                    "metric card with undefined action should not render data-metric-action");
                assert(!metricCardZeroValue.includes("clickable"),
                    "metric card with undefined action should not have clickable class");
                assert(!metricCardZeroValue.includes("action-hint"),
                    "metric card with undefined actionHint should not render action-hint span");

                const summaryHtml = summaryElements["summary-cards"].innerHTML;
                const actionAttrCount = (summaryHtml.match(/data-metric-action='/g) || []).length;
                assert(actionAttrCount === 6,
                    "summary should render exactly 6 clickable metric cards (pendingQueryCount=0 excluded), got " + actionAttrCount);

                const expectedLabels = ["待人工确认草稿", "答案反馈待处理", "待分析提问",
                    "已入库待复核", "高风险内容", "关注内容", "用户反馈风险"];
                expectedLabels.forEach(function (label) {
                    assert(summaryHtml.indexOf(label) !== -1,
                        "summary should include metric card label: " + label);
                });

                var _prevGetElementById = sandbox.document.getElementById;
                var filterEl = { value: "" };
                sandbox.document.getElementById = function (id) {
                    if (id === "article-review-status" || id === "article-risk-filter"
                        || id === "query-feedback-status-filter") {
                        return filterEl;
                    }
                    if (id === "search-articles" || id === "refresh-query-feedback") {
                        return { click: function () {} };
                    }
                    return _prevGetElementById(id);
                };
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-articles\\",\\"filters\\":{\\"article-review-status\\":\\"pending\\"}}");
                assert(filterEl.value === "pending",
                    "handleMetricCardAction should set article-review-status filter");

                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-feedback\\",\\"filters\\":{\\"query-feedback-status-filter\\":\\"PENDING\\"}}");
                assert(filterEl.value === "PENDING",
                    "handleMetricCardAction should set query-feedback-status-filter filter");

                knowledgeUi.handleMetricCardAction("not-json");
                assert(true, "handleMetricCardAction should not throw on invalid JSON");

                knowledgeUi.handleMetricCardAction("{}");
                assert(true, "handleMetricCardAction should not throw on empty config");

                const recentRunOverview = { innerHTML: "", dataset: {} };
                summaryElements["recent-run-overview"] = recentRunOverview;
                runs.renderRecentRunOverview({
                    cards: [{
                        label: "待确认",
                        value: 1,
                        note: "仍有任务等待人工处理",
                        tone: "warning"
                    }, {
                        label: "已完成",
                        value: 3,
                        note: "最近已有资料任务处理结束",
                        tone: "success"
                    }]
                });
                assert(recentRunOverview.innerHTML.includes("待人工确认任务"),
                    "processing-task overview should express waiting count as task count");
                assert(!recentRunOverview.innerHTML.includes(">待确认<"),
                    "processing-task overview should not keep the old short waiting label");
                const helpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(helpState.description.includes("待人工确认"),
                    "help state should guide to compile review queue before article review backlog");
                assert(helpState.actions[0].action === "knowledge-runs",
                    "help state should route compile review queue backlog to current processing tasks");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.humanReviewDraftPendingCount = 0;
                const articleReviewHelpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(articleReviewHelpState.description.includes("复核状态筛选"),
                    "help state should guide to article review status filter after compile drafts are cleared");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.reviewPendingArticleCount = 0;
                const feedbackHelpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(feedbackHelpState.actions[0].action === "knowledge-feedback",
                    "help state should route to answer feedback queue when only result feedback is pending");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.overview.status.answerFeedbackPendingCount = 0;
                const hotspotHelpState = knowledgeUi.deriveKnowledgeHelpState();
                assert(hotspotHelpState.title.includes("高频问题"),
                    "help state should expose hotspot verification entry before generic high-risk entry");
                assert(hotspotHelpState.description.includes("需关注"),
                    "hotspot help state should guide to result verification filter");
                assert(feedbackUi.buildQueryFeedbackListRequestUrl("PENDING", 20)
                    === "/api/v1/admin/query-feedback?status=PENDING&limit=20",
                    "feedback list request should include generic status filter");
                const feedbackMarkup = feedbackUi.renderQueryFeedbackListItem({
                    id: 9,
                    status: "PENDING",
                    feedbackType: "answer_problem",
                    question: "接口用途是什么",
                    answerSummary: "答案混入了不相关内容",
                    queryId: "query-9",
                    reportedBy: "reviewer",
                    createdAt: "2026-05-05T11:20:00+08:00"
                });
                assert(feedbackMarkup.includes("答案有问题"),
                    "feedback list should render readable feedback type");
                summaryElements["query-feedback-handler"] = { value: "handler" };
                summaryElements["query-feedback-resolution-comment"] = { value: "已补充回归" };
                const feedbackHandleRequest = feedbackUi.buildQueryFeedbackHandleRequest();
                assert(feedbackHandleRequest.handledBy === "handler",
                    "feedback handle request should keep handler");
                assert(feedbackHandleRequest.comment === "已补充回归",
                    "feedback handle request should keep resolution comment");

                const sourceFile = {
                    relativePath: "docs/payment/order-guide.md",
                    format: "md",
                    fileSize: 2048,
                    parseMode: "text_read",
                    parseProvider: "filesystem"
                };
                const compactFileMarkup = sourceUi.renderSourceFileListItem(sourceFile, true);
                assert(compactFileMarkup.includes("order-guide.md"),
                    "source file list should render file base name");
                assert(compactFileMarkup.includes("Markdown"),
                    "source file list should render readable file format");
                assert(compactFileMarkup.includes("文本读取"),
                    "source file list should render readable parse mode");
                assert(!compactFileMarkup.includes(">text_read<"),
                    "source file list should not expose raw parse mode badges");
                assert(!compactFileMarkup.includes("run-runtime-grid"),
                    "source file list row should stay compact");
                const fileDetailMarkup = sourceUi.buildSourceFileDetailCard(sourceFile);
                assert(fileDetailMarkup.includes("完整路径"),
                    "selected source file detail should expose full relative path");
                assert(fileDetailMarkup.includes("本地文件系统"),
                    "selected source file detail should render readable parse provider");
                const uploadedSource = {
                    name: "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
                    sourceCode: "srkit-svc-fc-dpfm",
                    primaryDocumentTitle: "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
                    metadataJson: JSON.stringify({
                        bundleSummary: {
                            displayName: "卡券三期-迁移方案",
                            relativePathsSample: ["卡券三期-迁移方案.md"],
                            titleHints: ["SRKIT/SVC 卡履约链路从 FC 平移至 DPFM"]
                        }
                    })
                };
                assert(sourceUi.resolveSourceDisplayName(uploadedSource) === "卡券三期-迁移方案",
                    "source display name should prefer bundle file-oriented display name");
                assert(sourceUi.resolveSourceDocumentTitle(uploadedSource) === "SRKIT/SVC 卡履约链路从 FC 平移至 DPFM",
                    "source document title should stay available as secondary metadata");
                const sourceListContainer = { innerHTML: "", querySelectorAll: function () { return []; } };
                sandbox.document.getElementById = function (id) {
                    if (id === "source-list") {
                        return sourceListContainer;
                    }
                    return null;
                };
                sourceUi.renderSourceList([Object.assign({
                    id: 1,
                    status: "ACTIVE",
                    sourceType: "UPLOAD",
                    contentProfile: "DOCUMENT",
                    defaultSyncMode: "AUTO",
                    lastSyncStatus: "RUNNING",
                    lastSyncAt: "2026-05-05T16:54:00+08:00"
                }, uploadedSource)]);
                assert(sourceListContainer.innerHTML.includes("卡券三期-迁移方案"),
                    "source list should render source-level display name");
                assert(sourceListContainer.innerHTML.includes("source-document-title"),
                    "source list should keep document title as secondary copy");
                const uploadedSourceDetailElements = {};
                sandbox.document.getElementById = function (id) {
                    if (!uploadedSourceDetailElements[id]) {
                        uploadedSourceDetailElements[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false,
                            closest: function () {
                                return { hidden: false };
                            }
                        };
                    }
                    return uploadedSourceDetailElements[id];
                };
                sourceUi.renderSourceDetail(Object.assign({
                    id: 1,
                    status: "ACTIVE",
                    sourceType: "UPLOAD",
                    contentProfile: "DOCUMENT",
                    defaultSyncMode: "AUTO",
                    configJson: "{}",
                    lastSyncAt: "2026-05-05T16:54:00+08:00"
                }, uploadedSource), [], []);
                assert(uploadedSourceDetailElements["source-detail-title"].textContent === "卡券三期-迁移方案",
                    "source detail title should render source-level display name");
                assert(uploadedSourceDetailElements["source-detail-meta"].textContent.includes("文档标题：SRKIT/SVC 卡履约链路从 FC 平移至 DPFM"),
                    "source detail meta should keep document title separately");
                assert(sourceUi.isUploadSource({ sourceType: "UPLOAD" }),
                    "upload source helper should identify upload sources");
                assert(!sourceUi.isUploadSource({ sourceType: "GIT" }),
                    "upload source helper should ignore non-upload sources");
                assert(sourceUi.resolveSourceProcessingHistoryItems({
                    items: [{ taskId: "compile-job:1" }]
                })[0].taskId === "compile-job:1",
                    "source detail should accept unified processing task list response");
                assert(sourceUi.resolveSourceProcessingHistoryItems([{ runId: 1 }])[0].runId === 1,
                    "source detail should remain compatible with source run arrays");
                assert(sourceUi.shouldFollowLatestSourceRun([], "latest"),
                    "empty source runs should always follow latest");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunKey = null;
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunMode = "auto";
                const failedRunAt = {
                    runId: 11,
                    requestedAt: "2026-05-04T08:05:54+08:00",
                    updatedAt: "2026-05-04T08:15:19+08:00"
                };
                const succeededRunAt = {
                    runId: 12,
                    requestedAt: "2026-05-04T09:38:33+08:00",
                    updatedAt: "2026-05-04T10:10:07+08:00"
                };
                assert(sourceUi.shouldFollowLatestSourceRun([succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(succeededRunAt)),
                    "auto mode should follow the newest run");
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunKey = sourceUi.resolveSourceRunKey(failedRunAt);
                sandbox.__LATTICE_ADMIN_TEST_STATE__.selectedSourceRunMode = "manual";
                sandbox.__LATTICE_ADMIN_TEST_STATE__.latestSourceRunKey = sourceUi.resolveSourceRunKey(succeededRunAt);
                assert(sourceUi.shouldFollowLatestSourceRun([succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(succeededRunAt)) === false,
                    "manual mode should keep current selection when latest run itself has not changed");
                assert(sourceUi.shouldFollowLatestSourceRun([succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(succeededRunAt)) === false,
                    "manual mode should not jump back to current latest when latest run itself has not changed");
                const newerSucceededRunAt = {
                    runId: 13,
                    requestedAt: "2026-05-04T11:38:33.848574+08:00",
                    updatedAt: "2026-05-04T12:10:07.834603+08:00"
                };
                assert(sourceUi.shouldFollowLatestSourceRun([newerSucceededRunAt, succeededRunAt, failedRunAt], sourceUi.resolveSourceRunKey(newerSucceededRunAt)),
                    "manual mode should follow latest only when a newer run actually arrives");
                const detailElement = {
                    hidden: false,
                    scrollIntoViewCalled: false,
                    scrollIntoView: function () {
                        this.scrollIntoViewCalled = true;
                    }
                };
                sandbox.document.getElementById = function (id) {
                    if (id === "source-run-detail") {
                        return detailElement;
                    }
                    return null;
                };
                sourceUi.focusSourceRunDetail();
                assert(detailElement.scrollIntoViewCalled,
                    "clicking source run rows should focus the detail panel");

                const historyUi = sandbox.__LATTICE_ADMIN_TEST__.history;
                assert(historyUi, "missing __LATTICE_ADMIN_TEST__.history export");
                assert(typeof historyUi.loadProcessingHistory === "function",
                    "missing loadProcessingHistory export");
                assert(typeof historyUi.applyHistoryFilterAndRender === "function",
                    "missing applyHistoryFilterAndRender export");
                assert(typeof historyUi.renderHistoryItem === "function",
                    "missing renderHistoryItem export");
                assert(typeof historyUi.formatElapsed === "function",
                    "missing formatElapsed export");

                assert(historyUi.formatElapsed(null, null) === "\\u2014",
                    "formatElapsed should return em-dash for null arguments");

                const historyItemMarkup = historyUi.renderHistoryItem({
                    sourceName: "测试资料.md",
                    title: "测试资料.md",
                    sourceType: "UPLOAD",
                    displayStatus: "SUCCEEDED",
                    requestedAt: "2026-05-20T08:00:00+08:00",
                    updatedAt: "2026-05-20T08:05:30+08:00",
                    persistedArticleCount: 3,
                    sourceId: 42
                });
                assert(historyItemMarkup.includes("测试资料.md"),
                    "history item should render source name");
                assert(historyItemMarkup.includes("资料同步"),
                    "history item should show source type label");
                assert(historyItemMarkup.includes("data-history-source-id"),
                    "history item should render detail button with source id");

                const noSourceItemMarkup = historyUi.renderHistoryItem({
                    sourceName: "独立编译任务.md",
                    title: "独立编译任务.md",
                    sourceType: "DIRECT_COMPILE",
                    displayStatus: "FAILED",
                    requestedAt: "2026-05-20T08:00:00+08:00",
                    updatedAt: "2026-05-20T08:05:30+08:00",
                    persistedArticleCount: 0
                });
                assert(noSourceItemMarkup.includes("独立编译"),
                    "history item should show standalone compile type");
                assert(!noSourceItemMarkup.includes("data-history-source-id"),
                    "history item without sourceId should not render detail button");

                const loadFnSource = String(historyUi.loadProcessingHistory);
                assert(loadFnSource.includes("/api/v1/admin/processing-tasks"),
                    "loadProcessingHistory should fetch processing-tasks endpoint");
                assert(loadFnSource.includes("status=terminal"),
                    "loadProcessingHistory should include status=terminal filter");
                assert(loadFnSource.includes("limit=50"),
                    "loadProcessingHistory should include limit=50");

                const historyModuleSourceText = fs.readFileSync(path.join(moduleDir, "management-history-part.js"), "utf8");
                assert(historyModuleSourceText.includes("processing-history-panel"),
                    "history module should bind the collapsed processing history panel");
                assert(historyModuleSourceText.includes("historyPanel.open"),
                    "history module should load terminal tasks when the history panel is opened");

                // History panel should always call loadProcessingHistory when opened (no _historyLoaded guard)
                assert(!loadFnSource.includes("_historyLoaded"),
                    "loadProcessingHistory should not gate on _historyLoaded; opened panel always loads");

                // History empty state rendering
                var emptyHistoryContainer = { innerHTML: "" };
                var prevGetEl5 = sandbox.document.getElementById;
                sandbox.document.getElementById = function (id) {
                    if (id === "history-list") { return emptyHistoryContainer; }
                    if (id === "history-status") { return { textContent: "" }; }
                    return prevGetEl5 ? prevGetEl5(id) : null;
                };
                sandbox.__LATTICE_ADMIN_TEST__.history.applyHistoryFilterAndRender();
                assert(emptyHistoryContainer.innerHTML.includes("暂无已结束的处理任务"),
                    "history empty state should explain how tasks end up here");
                sandbox.document.getElementById = prevGetEl5;

                // Hotspot copy: old internal terms must be absent from runtime
                var summarySrc = String(knowledgeUi.renderSummary);
                assert(!summarySrc.includes("抽检"),
                    "renderSummary should not expose internal term: 抽检");
                assert(!summarySrc.includes("待验证"),
                    "renderSummary should not expose internal term: 待验证");
                assert(!summarySrc.includes("刷新热点"),
                    "renderSummary should not expose internal term: 刷新热点");
                assert(!summarySrc.includes("待结果抽检"),
                    "renderSummary should not expose internal term: 待结果抽检");

                // Hotspot copy: new user-facing terms present
                assert(summarySrc.includes("关注内容") || summarySrc.includes("需关注") || summarySrc.includes("高频问题相关"),
                    "renderSummary should use user-facing hotspot terms (关注内容/需关注/高频问题相关)");

                // Part-04: old terms absent from buildArticleRiskSummary
                var part04RiskSrc = String(articleUi.buildArticleRiskSummary);
                assert(!part04RiskSrc.includes("结果抽检"),
                    "part-04 should not expose internal term: 结果抽检");
                assert(!part04RiskSrc.includes("需要结果抽检"),
                    "part-04 should not expose internal term: 需要结果抽检");
                assert(!part04RiskSrc.includes("暂无额外抽检原因"),
                    "part-04 should not expose internal term: 暂无额外抽检原因");
                assert(!part04RiskSrc.includes("高频热点"),
                    "part-04 should not expose internal term: 高频热点");
                assert(part04RiskSrc.includes("高频问题相关"),
                    "buildArticleRiskSummary should use 高频问题相关");
                assert(part04RiskSrc.includes("暂无额外关注原因"),
                    "buildArticleRiskSummary should use 暂无额外关注原因");

                // Part-04: review history compact timeline structure
                var _part04ReviewHistorySrc = String(articleUi.renderArticleReviewHistory);
                assert(_part04ReviewHistorySrc.includes("review-history-row"),
                    "renderArticleReviewHistoryItem should use compact timeline row class");
                assert(_part04ReviewHistorySrc.includes("review-history-action"),
                    "renderArticleReviewHistoryItem should render action label");
                assert(_part04ReviewHistorySrc.includes("review-history-meta"),
                    "renderArticleReviewHistoryItem should use metadata row for comment/reviewer");
                assert(!_part04ReviewHistorySrc.includes("review-history-head"),
                    "renderArticleReviewHistoryItem should not use old dark gray card class");

                // getBadgeLabel HOTSPOT_UNVERIFIED verified via risk summary output:
                // hotspot flag → "高频问题相关" (not "高频热点" or "热点未验证")
                var hotspotOnlySummary = articleUi.buildArticleRiskSummary({
                    riskLevel: "low",
                    riskReasons: [],
                    isHotspot: true,
                    requiresResultVerification: false
                });
                assert(hotspotOnlySummary.includes("高频问题相关"),
                    "getBadgeLabel via riskSummary should map hotspot to 高频问题相关");
                assert(!hotspotOnlySummary.includes("高频热点"),
                    "riskSummary should not expose 高频热点");

                // isTechKeyword tests
                assert(typeof articleUi.isTechKeyword === "function",
                    "missing isTechKeyword export");
                assert(articleUi.isTechKeyword("docs/readme.md") === true,
                    "isTechKeyword should detect file extension");
                assert(articleUi.isTechKeyword("app.config.key") === true,
                    "isTechKeyword should detect dotted config key");
                assert(articleUi.isTechKeyword("my_variable_name") === true,
                    "isTechKeyword should detect snake_case");
                assert(articleUi.isTechKeyword("/path/to/file") === true,
                    "isTechKeyword should detect path with slash");
                assert(articleUi.isTechKeyword("foo=bar") === true,
                    "isTechKeyword should detect key=value");
                assert(articleUi.isTechKeyword("https://example.com") === true,
                    "isTechKeyword should detect URL");
                assert(articleUi.isTechKeyword("机器学习") === false,
                    "isTechKeyword should not flag Chinese text");
                assert(articleUi.isTechKeyword("payment") === false,
                    "isTechKeyword should not flag simple word");
                assert(articleUi.isTechKeyword("order-processing") === false,
                    "isTechKeyword should not flag kebab-case");
                assert(articleUi.isTechKeyword("") === true,
                    "isTechKeyword should treat empty string as tech");

                // Verify normalizeArticleKeywords is exported
                assert(typeof articleUi.normalizeArticleKeywords === "function",
                    "missing normalizeArticleKeywords export");

                // Mock page-notice element to verify setStatus in handleMetricCardAction
                var _pageNoticeEl = {
                    hidden: true,
                    textContent: "",
                    className: ""
                };
                var _prevGetElementById3 = sandbox.document.getElementById;
                sandbox.document.getElementById = function (id) {
                    if (id === "page-notice") {
                        return _pageNoticeEl;
                    }
                    if (id === "article-review-status" || id === "article-risk-filter"
                        || id === "query-feedback-status-filter") {
                        return filterEl;
                    }
                    if (id === "search-articles" || id === "refresh-query-feedback") {
                        return { click: function () {} };
                    }
                    return _prevGetElementById3(id);
                };
                filterEl.value = "";
                _pageNoticeEl.textContent = "";
                _pageNoticeEl.className = "";
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-articles\\",\\"filters\\":{\\"article-review-status\\":\\"pending\\"}}");
                assert(_pageNoticeEl.hidden === false,
                    "handleMetricCardAction should show page-notice for articles tab");
                assert(_pageNoticeEl.textContent !== "",
                    "handleMetricCardAction should set status message for articles tab");

                _pageNoticeEl.textContent = "";
                _pageNoticeEl.className = "";
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-feedback\\",\\"filters\\":{\\"query-feedback-status-filter\\":\\"PENDING\\"}}");
                assert(_pageNoticeEl.hidden === false,
                    "handleMetricCardAction should show page-notice for feedback tab");
                assert(_pageNoticeEl.textContent !== "",
                    "handleMetricCardAction should set status message for feedback tab");

                // resolveArticleMetricFilterMessage tests
                assert(typeof knowledgeUi.resolveArticleMetricFilterMessage === "function",
                    "missing resolveArticleMetricFilterMessage export");
                var highRiskMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-risk-filter": "riskLevel:high"});
                assert(highRiskMsg.includes("高风险"),
                    "resolveArticleMetricFilterMessage should mention high risk for riskLevel:high");
                var hotspotMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-risk-filter": "requiresResultVerification:true"});
                assert(hotspotMsg.includes("高频问题相关内容"),
                    "resolveArticleMetricFilterMessage should mention hotspot verification for requiresResultVerification:true");
                assert(hotspotMsg.includes("仅用于查看"),
                    "resolveArticleMetricFilterMessage should clarify view-only intent for hotspot verification");
                var userReportedMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-risk-filter": "riskReason:user_reported"});
                assert(userReportedMsg.includes("用户反馈风险"),
                    "resolveArticleMetricFilterMessage should mention user reported for riskReason:user_reported");
                var reviewMsg = knowledgeUi.resolveArticleMetricFilterMessage({"article-review-status": "pending"});
                assert(reviewMsg.includes("复核状态"),
                    "resolveArticleMetricFilterMessage should mention review status filter");
                var emptyMsg = knowledgeUi.resolveArticleMetricFilterMessage(null);
                assert(emptyMsg.includes("已切换到已入库内容"),
                    "resolveArticleMetricFilterMessage should return default message for null filters");
                assert(emptyMsg.includes("如列表为空"),
                    "resolveArticleMetricFilterMessage should include empty-result hint");

                // HandleMetricCardAction for hotspot (requiresResultVerification)
                _pageNoticeEl.textContent = "";
                _pageNoticeEl.className = "";
                filterEl.value = "";
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-articles\\",\\"filters\\":{\\"article-risk-filter\\":\\"requiresResultVerification:true\\"}}");
                assert(_pageNoticeEl.textContent.includes("高频问题相关内容"),
                    "handleMetricCardAction should show hotspot-specific status for requiresResultVerification:true");
                assert(_pageNoticeEl.textContent.includes("仅用于查看"),
                    "handleMetricCardAction should include view-only hint for hotspot without processing closure");

                // HandleMetricCardAction for user-reported risk
                _pageNoticeEl.textContent = "";
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-articles\\",\\"filters\\":{\\"article-risk-filter\\":\\"riskReason:user_reported\\"}}");
                assert(_pageNoticeEl.textContent.includes("用户反馈风险"),
                    "handleMetricCardAction should show user-reported-specific status");

                // HandleMetricCardAction for high risk
                _pageNoticeEl.textContent = "";
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-articles\\",\\"filters\\":{\\"article-risk-filter\\":\\"riskLevel:high\\"}}");
                assert(_pageNoticeEl.textContent.includes("高风险"),
                    "handleMetricCardAction should show high-risk-specific status");

                // pendingQueryCount > 0 but no data-metric-action
                var pendingCardMarkup = knowledgeUi.renderMetricCard({
                    label: "待分析提问",
                    value: 27,
                    action: undefined,
                    actionHint: undefined
                });
                assert(pendingCardMarkup.includes("<div"),
                    "pendingQuery card with count>0 should render as div when action is undefined");
                assert(!pendingCardMarkup.includes("data-metric-action="),
                    "pendingQuery card with count>0 should not render data-metric-action");

                // normalizeArticleKeywords test: verify keywords from raw data (no DOM scanning)
                var _prevGetEl4 = sandbox.document.getElementById;
                var _articleRelationsEl = { innerHTML: "" };
                sandbox.document.getElementById = function (id) {
                    if (id === "article-relations") {
                        return _articleRelationsEl;
                    }
                    if (id === "page-notice") {
                        return _pageNoticeEl;
                    }
                    if (id === "article-review-status" || id === "article-risk-filter"
                        || id === "query-feedback-status-filter") {
                        return filterEl;
                    }
                    if (id === "search-articles" || id === "refresh-query-feedback") {
                        return { click: function () {} };
                    }
                    return _prevGetEl4 ? _prevGetEl4(id) : null;
                };
                // Simulate article with keywords including tech keywords
                sandbox.__LATTICE_ADMIN_TEST_STATE__._articleKeywordData = {
                    keywords: ["机器学习", "支付系统", "订单处理", "用户认证", "数据同步", "缓存策略", "消息队列"],
                    dependsOn: ["docs/readme.md"],
                    related: ["app.config.key", "my_variable_name"]
                };
                articleUi.normalizeArticleKeywords();
                var keywordHtml = _articleRelationsEl.innerHTML;
                assert(keywordHtml.includes("article-keyword-section"),
                    "normalizeArticleKeywords should render keyword section");
                assert(keywordHtml.includes("article-keyword-visible"),
                    "normalizeArticleKeywords should render visible keyword area");
                // Should show max 6 visible normal keywords
                assert(keywordHtml.includes("机器学习"),
                    "normalizeArticleKeywords should include Chinese keyword");
                // Should NOT include "关键词:" prefix (clean text only)
                assert(!keywordHtml.includes("关键词: 机器学习"),
                    "normalizeArticleKeywords should NOT prefix with '关键词: '");
                // "还有 N 个关键词" should be in a details/summary, not a pill
                assert(keywordHtml.includes("article-keyword-toggle"),
                    "normalizeArticleKeywords should render expandable toggle for extra keywords");
                assert(keywordHtml.includes("还有 "),
                    "toggle should include '还有 N 个关键词' label");
                // The count should include tech + overflow keywords
                assert(keywordHtml.includes("article-relations-aux"),
                    "normalizeArticleKeywords should render auxiliary relations section");
                assert(keywordHtml.includes("关联信息"),
                    "auxiliary section should include '关联信息' label");

                // Verify pendingQueryCount card has no dev-facing copy
                assert(!pendingCardMarkup.includes("去处理"),
                    "pendingQuery card with count>0 should not contain '去处理' action hint");
                assert(!pendingCardMarkup.includes("待开放"),
                    "pendingQuery card should not contain dev-facing '待开放' copy");
                assert(!summaryHtml.includes("处理入口待开放"),
                    "summary should not contain dev-facing '处理入口待开放' copy");

                // Verify actionHint semantics: only cards with backend closure use "去处理"
                assert(summaryHtml.includes("去确认 \\u2192"),
                    "draft card should use '去确认' action hint");
                assert(summaryHtml.includes("查看反馈 \\u2192"),
                    "feedback card should use '查看反馈' action hint");
                // 3 cards have backend processing closures: 已入库待复核, 高风险内容, 用户反馈风险
                var quChuLiCount = (summaryHtml.match(/去处理 \\u2192/g) || []).length;
                assert(quChuLiCount === 3,
                    "exactly 3 cards (manualReview, highRisk, userReported) should use 去处理, found: " + quChuLiCount);
                assert(summaryHtml.includes("查看内容 \\u2192"),
                    "hotspot card should use '查看内容' action hint");

                // handleMetricCardAction scrollTo for articles and feedback tabs
                var scrollTargetEl = { scrollIntoViewCalled: false, scrollIntoView: function () { this.scrollIntoViewCalled = true; } };
                sandbox.document.getElementById = function (id) {
                    if (id === "article-list" || id === "query-feedback-list") {
                        return scrollTargetEl;
                    }
                    if (id === "page-notice") {
                        return _pageNoticeEl;
                    }
                    if (id === "article-review-status" || id === "article-risk-filter"
                        || id === "query-feedback-status-filter") {
                        return filterEl;
                    }
                    if (id === "search-articles" || id === "refresh-query-feedback") {
                        return { click: function () {} };
                    }
                    return _prevGetEl4 ? _prevGetEl4(id) : null;
                };
                scrollTargetEl.scrollIntoViewCalled = false;
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-articles\\",\\"filters\\":{\\"article-review-status\\":\\"pending\\"},\\"scrollTo\\":\\"article-list\\"}");
                // scrollTo is async (setTimeout 200ms), so assert no immediate error
                assert(true, "handleMetricCardAction with articles scrollTo should not throw");

                scrollTargetEl.scrollIntoViewCalled = false;
                knowledgeUi.handleMetricCardAction(
                    "{\\"tab\\":\\"knowledge-feedback\\",\\"filters\\":{\\"query-feedback-status-filter\\":\\"PENDING\\"},\\"scrollTo\\":\\"query-feedback-list\\"}");
                assert(true, "handleMetricCardAction with feedback scrollTo should not throw");

                // Verify article detail metadata h4 renamed to dev-facing "开发诊断信息"
                assert(metadataSectionState.innerHTML.includes("开发诊断信息"),
                    "metadata section h4 should use '开发诊断信息' not '技术元数据'");
                assert(!metadataSectionState.innerHTML.includes("技术元数据"),
                    "metadata section should not contain old '技术元数据' copy");
                assert(!metadataSectionState.innerHTML.includes("技术信息"),
                    "metadata summary should not contain old '技术信息' copy");
                // details should be closed by default (no 'open' attribute)
                assert(!metadataSectionState.innerHTML.includes("<details open"),
                    "metadata details should be closed by default");
                // article-technical-info now rendered inside 开发诊断信息 details
                assert(metadataSectionState.innerHTML.includes("article-technical-info"),
                    "article-technical-info div should exist inside metadata details");
                assert(metadataSectionState.innerHTML.includes("article-metadata-toggle"),
                    "metadata section should still wrap details toggle");
                // Verify renderArticleDetail source still references article-technical-info
                // (now rendered inside the metadata collapsible section, not standalone)
                var renderDetailSrc = String(articleUi.renderArticleDetail);
                assert(renderDetailSrc.includes("article-technical-info"),
                    "renderArticleDetail should render technical info inside metadata details");

                // clearArticleDetail null-guard: should not throw when article-technical-info is missing
                var _prevGetElForClear = sandbox.document.getElementById;
                sandbox.document.getElementById = function (id) {
                    if (id === "article-technical-info") {
                        return null;
                    }
                    if (!elementState[id]) {
                        elementState[id] = {
                            textContent: "",
                            innerHTML: "",
                            hidden: false
                        };
                    }
                    return elementState[id];
                };
                var clearErr = null;
                try {
                    articleUi.clearArticleDetail();
                } catch (e) {
                    clearErr = e;
                }
                assert(clearErr === null,
                    "clearArticleDetail should not throw when article-technical-info is missing, got: " + (clearErr && clearErr.message));
                // Verify clearArticleDetail source contains null guard
                var clearDetailSrc = String(articleUi.clearArticleDetail);
                assert(clearDetailSrc.includes("_techInfo"),
                    "clearArticleDetail should null-guard article-technical-info write");
                sandbox.document.getElementById = _prevGetEl4;

                console.log("management-js-runtime-tests:ok");
                """;
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
