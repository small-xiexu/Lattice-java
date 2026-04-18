package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.api.compiler.CompileRequest;
import com.xbk.lattice.api.compiler.CompileResponse;
import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.compiler.service.CompileResult;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.Duration;

/**
 * compile 命令
 *
 * 职责：触发全量或增量编译
 *
 * @author xiexu
 */
@CommandLine.Command(name = "compile", description = "编译知识源目录")
public class CompileCommand extends AbstractCliCommand {

    @CommandLine.Option(names = {"-s", "--source"}, required = true, description = "源目录路径")
    private Path sourceDir;

    @CommandLine.Option(names = "--incremental", description = "是否执行增量编译")
    private boolean incremental;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            CompileApplicationFacade compileApplicationFacade = context.getBean(CompileApplicationFacade.class);
            CompileResult compileResult = compileApplicationFacade.compile(sourceDir, incremental, null);
            printJson(compileResult);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        CompileRequest compileRequest = new CompileRequest();
        compileRequest.setSourceDir(sourceDir.toString());
        compileRequest.setIncremental(incremental);
        CompileResponse compileResponse = latticeHttpClient.post("/api/v1/compile", compileRequest, CompileResponse.class);
        printJson(compileResponse);
        return CliExitCodes.SUCCESS;
    }

    /**
     * 返回 compile 命令远程模式默认超时时间。
     *
     * @return 默认超时时间
     */
    @Override
    protected Duration defaultRemoteRequestTimeout() {
        return Duration.ofMinutes(30);
    }
}
