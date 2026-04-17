package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliOutputFormatter;
import com.xbk.lattice.cli.CliRuntimeSupport;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * CLI 命令抽象基类
 *
 * 职责：统一处理独立模式、远程模式占位和输出异常
 *
 * @author xiexu
 */
public abstract class AbstractCliCommand implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "显示帮助信息"
    )
    protected boolean helpRequested;

    @CommandLine.Option(
            names = "--server",
            description = "远程模式服务地址，例如 http://localhost:8080"
    )
    protected String serverUrl;

    @Override
    public final Integer call() {
        String effectiveServerUrl = resolveServerUrl();
        if (effectiveServerUrl != null && !effectiveServerUrl.isBlank()) {
            return CliRuntimeSupport.runWithContext(() -> runInRemoteMode(new LatticeHttpClient(effectiveServerUrl)));
        }
        return CliRuntimeSupport.runWithContext(this::runInStandaloneMode);
    }

    /**
     * 在独立模式中执行命令。
     *
     * @return 退出码
     * @throws Exception 执行异常
     */
    protected abstract Integer runInStandaloneMode() throws Exception;

    /**
     * 在远程模式中执行命令。
     *
     * @param latticeHttpClient 远程 HTTP 客户端
     * @return 退出码
     * @throws Exception 执行异常
     */
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        throw new UnsupportedOperationException("当前命令暂不支持远程模式");
    }

    /**
     * 输出 JSON。
     *
     * @param value 对象
     */
    protected void printJson(Object value) {
        CliOutputFormatter.printLine(CliOutputFormatter.toJson(value));
    }

    private String resolveServerUrl() {
        if (serverUrl != null && !serverUrl.isBlank()) {
            return serverUrl.trim();
        }
        String envServerUrl = System.getenv("LATTICE_SERVER_URL");
        if (envServerUrl == null || envServerUrl.isBlank()) {
            return null;
        }
        return envServerUrl.trim();
    }
}
