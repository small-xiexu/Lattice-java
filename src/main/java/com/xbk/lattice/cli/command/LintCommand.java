package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.governance.LintService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

/**
 * lint 命令
 *
 * 职责：执行治理检查并输出结果
 *
 * @author xiexu
 */
@CommandLine.Command(name = "lint", description = "执行知识库治理检查")
public class LintCommand extends AbstractCliCommand {

    @Override
    protected Integer runInStandaloneMode() {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            LintService lintService = context.getBean(LintService.class);
            LintReport lintReport = lintService.lint();
            printJson(lintReport);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        LintReport lintReport = latticeHttpClient.get("/api/v1/admin/lint", java.util.Map.of(), LintReport.class);
        printJson(lintReport);
        return CliExitCodes.SUCCESS;
    }
}
