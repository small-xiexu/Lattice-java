package com.xbk.lattice.cli.command;

import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.source.domain.SourceSyncRunDetail;
import com.xbk.lattice.source.service.SourceSyncWorkflowService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.time.Duration;
import java.util.Map;

/**
 * source-sync 命令
 *
 * 职责：对指定资料源发起一次同步
 *
 * @author xiexu
 */
@CommandLine.Command(name = "source-sync", description = "对指定资料源发起同步")
public class SourceSyncCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--source-id", required = true, description = "资料源主键")
    private Long sourceId;

    @Override
    protected Integer runInStandaloneMode() throws Exception {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            SourceSyncWorkflowService sourceSyncWorkflowService = context.getBean(SourceSyncWorkflowService.class);
            SourceSyncRunDetail detail = sourceSyncWorkflowService.syncSource(sourceId);
            printJson(detail);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        SourceSyncRunDetail detail = latticeHttpClient.post(
                "/api/v1/admin/sources/" + sourceId + "/sync",
                Map.of(),
                SourceSyncRunDetail.class
        );
        printJson(detail);
        return CliExitCodes.SUCCESS;
    }

    /**
     * 返回 source-sync 命令默认远程超时时间。
     *
     * @return 默认远程超时时间
     */
    @Override
    protected Duration defaultRemoteRequestTimeout() {
        return Duration.ofMinutes(5);
    }
}
