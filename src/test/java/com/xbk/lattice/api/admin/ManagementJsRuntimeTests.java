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
     * 验证运行态回退、疑似卡住提示、稳定错误文案与重新同步入口都会按预期工作。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldVerifyRunFallbackAndErrorPresentationViaNode(@TempDir Path tempDir) throws Exception {
        String userDir = System.getProperty("user.dir");
        Path managementJsPath = Path.of(userDir, "src/main/resources/static/admin/management.js");
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
                managementJsPath.toString()
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
                const vm = require("vm");

                const source = fs.readFileSync(process.argv[2], "utf8");
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

                vm.runInNewContext(source, sandbox, { filename: "management.js" });

                const runs = sandbox.__LATTICE_ADMIN_TEST__.runs;

                function assert(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }

                assert(runs, "missing __LATTICE_ADMIN_TEST__.runs export");

                const fallbackRun = { status: "RUNNING" };
                assert(runs.resolveRunDisplayStatus(fallbackRun) === "RUNNING",
                    "should fallback to base run status when derived fields are absent");
                assert(runs.resolveRunStepLabel(fallbackRun) === "编译入库中",
                    "should provide fallback step label for running jobs");
                assert(runs.resolveRunProgressText({ status: "COMPILE_QUEUED" }) === "等待后台 worker 领取",
                    "should provide queued progress fallback");

                const stalledRun = {
                    status: "RUNNING",
                    compileDerivedStatus: "STALLED",
                    compileCurrentStep: "review_articles",
                    compileProgressCurrent: 2,
                    compileProgressTotal: 6,
                    compileProgressMessage: "正在审查第 2 篇文章",
                    compileLastHeartbeatAt: "2026-04-24T08:00:00+08:00",
                    sourceId: 12
                };
                const runtimeSnapshot = runs.buildRunRuntimeSnapshot(stalledRun);
                assert(runtimeSnapshot.includes("编译态"),
                    "runtime snapshot should expose derived status");
                assert(runtimeSnapshot.includes("审查文章草稿"),
                    "runtime snapshot should expose current step label");
                assert(runtimeSnapshot.includes("2 / 6"),
                    "runtime snapshot should expose current progress");
                assert(runtimeSnapshot.includes("原因摘要"),
                    "runtime snapshot should expose reason summary");
                assert(runs.buildRunReasonSummary(stalledRun).includes("长时间没有新的心跳"),
                    "stalled run should explain stalled reason");
                assert(runs.shouldShowResyncAction(stalledRun) === true,
                    "stalled run should expose resync action");

                const failedRun = {
                    status: "FAILED",
                    compileErrorCode: "LLM_TRANSPORT_ERROR"
                };
                assert(runs.buildRunReasonSummary(failedRun).includes("链路异常"),
                    "failed run should map stable error code to friendly copy");

                const standaloneCompileRun = {
                    taskType: "STANDALONE_COMPILE",
                    status: "RUNNING",
                    sourceId: 1
                };
                assert(runs.shouldShowResyncAction(standaloneCompileRun) === false,
                    "standalone compile task should not expose source resync action");

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

                console.log("management-js-runtime-tests:ok");
                """;
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
