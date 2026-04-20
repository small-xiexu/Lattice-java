const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function loadArticleHooks() {
    const managementScriptPath = path.join(
        __dirname,
        "..",
        "src",
        "main",
        "resources",
        "static",
        "admin",
        "management.js"
    );
    const scriptContent = fs.readFileSync(managementScriptPath, "utf8");
    const sandbox = createSandbox();
    vm.runInNewContext(scriptContent, sandbox, {filename: "management.js"});
    return sandbox.__LATTICE_ADMIN_TEST__.article;
}

function createSandbox() {
    const sandbox = {
        __LATTICE_ADMIN_TEST__: {},
        console: console,
        setTimeout: setTimeout,
        clearTimeout: clearTimeout,
        Date: Date,
        Math: Math,
        Number: Number,
        String: String,
        Boolean: Boolean,
        JSON: JSON,
        Array: Array,
        Set: Set,
        Map: Map,
        URLSearchParams: URLSearchParams,
        FormData: class FormData {},
        fetch: function () {
            throw new Error("unexpected fetch in presentation test");
        },
        document: {
            addEventListener: function () {},
            getElementById: function () {
                return null;
            },
            querySelectorAll: function () {
                return [];
            },
            querySelector: function () {
                return null;
            }
        }
    };
    sandbox.globalThis = sandbox;
    sandbox.window = sandbox;
    return sandbox;
}

test("泛化标题会回退到主要参考文件名", function () {
    const hooks = loadArticleHooks();
    const displayTitle = hooks.resolveArticleDisplayTitle({
        title: "knowledge-schema--payments",
        conceptId: "knowledge-schema--payments",
        primarySourcePath: "payments/PaymentRetryPolicy.java"
    });
    const summary = hooks.resolveArticleSummary({
        title: "knowledge-schema--payments",
        conceptId: "knowledge-schema--payments",
        summary: "",
        primarySourcePath: "payments/PaymentRetryPolicy.java"
    });

    assert.equal(displayTitle, "PaymentRetryPolicy");
    assert.equal(summary, "用于说明“PaymentRetryPolicy”相关的知识内容。");
});

test("多来源条目会聚合来源类型并保留文件级追溯说明", function () {
    const hooks = loadArticleHooks();
    const article = {
        sourceCount: 4,
        sourcePaths: [
            "payments/PaymentRetryPolicy.java",
            "docs/payment/PaymentRetryPolicy.md",
            "excel/payment_retry_policy.xlsx",
            "pdf/payment-retry-policy.pdf"
        ]
    };

    assert.deepEqual(
        Array.from(hooks.collectArticleSourceTypes(article)),
        ["Java", "Markdown", "Excel", "PDF"]
    );
    assert.equal(hooks.buildArticleSourceCountText(article), "关联来源 4 个文件");
    assert.equal(
        hooks.buildArticleSourceOverview(article),
        "关联来源 4 个文件。来源类型包括 Java、Markdown、Excel、PDF。当前追溯停留在文件级，尚未细化到 Excel 的 Sheet / 单元格，也未细化到 PDF 页码或段落。"
    );
    assert.equal(
        hooks.buildArticleTraceabilityNote(article),
        "以下列出这条知识条目的完整来源文件。首个文件作为主要参考文件展示；当前追溯停留在文件级，尚未细化到 Excel 的 Sheet / 单元格，也未细化到 PDF 页码或段落。"
    );
});

test("Excel 与 PDF 来源会给出一致的颗粒度说明", function () {
    const hooks = loadArticleHooks();

    assert.equal(hooks.resolveSourceTypeLabel("excel/payment_retry_policy.xlsx"), "Excel");
    assert.equal(hooks.resolveSourceTypeLabel("pdf/payment-retry-policy.pdf"), "PDF");
    assert.equal(
        hooks.buildSourceGranularityNote("excel/payment_retry_policy.xlsx"),
        "当前展示的是文件级来源，还未细化到 Sheet、表格区域或单元格。"
    );
    assert.equal(
        hooks.buildSourceGranularityNote("pdf/payment-retry-policy.pdf"),
        "当前展示的是文件级来源，还未细化到页码范围或段落片段。"
    );
});

test("可用状态说明会按生命周期与审查状态输出用户文案", function () {
    const hooks = loadArticleHooks();

    assert.equal(
        hooks.buildArticleAvailabilitySummary({
            lifecycle: "ACTIVE",
            reviewStatus: "PENDING"
        }),
        "当前已入库，仍在等待进一步审查。"
    );
    assert.equal(
        hooks.buildArticleAvailabilitySummary({
            lifecycle: "DEPRECATED",
            reviewStatus: "PASSED"
        }),
        "当前已废弃，除排查历史问题外不建议继续使用。"
    );
});
