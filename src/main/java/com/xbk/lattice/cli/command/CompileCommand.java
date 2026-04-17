package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.api.compiler.CompileRequest;
import com.xbk.lattice.api.compiler.CompileResponse;
import com.xbk.lattice.compiler.service.CompilePipelineService;
import com.xbk.lattice.compiler.service.CompileResult;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.nio.file.Path;

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
            CompilePipelineService compilePipelineService = context.getBean(CompilePipelineService.class);
            CompileResult compileResult = incremental
                    ? compilePipelineService.incrementalCompile(sourceDir)
                    : compilePipelineService.compile(sourceDir);
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
}
