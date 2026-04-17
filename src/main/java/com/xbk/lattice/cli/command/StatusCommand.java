package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.admin.AdminOverviewPendingItemResponse;
import com.xbk.lattice.api.admin.AdminOverviewPendingResponse;
import com.xbk.lattice.api.admin.AdminOverviewResponse;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.governance.QualityMetricsService;
import com.xbk.lattice.governance.StatusService;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

/**
 * status 命令
 *
 * 职责：输出知识库总览状态
 *
 * @author xiexu
 */
@CommandLine.Command(name = "status", description = "查看知识库总览状态")
public class StatusCommand extends AbstractCliCommand {

    @Override
    protected Integer runInStandaloneMode() {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            StatusService statusService = context.getBean(StatusService.class);
            QualityMetricsService qualityMetricsService = context.getBean(QualityMetricsService.class);
            PendingQueryManager pendingQueryManager = context.getBean(PendingQueryManager.class);
            List<PendingQueryRecord> pendingRecords = pendingQueryManager.listPendingQueries();
            List<AdminOverviewPendingItemResponse> items = new ArrayList<AdminOverviewPendingItemResponse>();
            for (PendingQueryRecord pendingRecord : pendingRecords) {
                items.add(new AdminOverviewPendingItemResponse(
                        pendingRecord.getQueryId(),
                        pendingRecord.getQuestion(),
                        pendingRecord.getReviewStatus()
                ));
            }
            printJson(new AdminOverviewResponse(
                    statusService.snapshot(),
                    qualityMetricsService.measure(),
                    new AdminOverviewPendingResponse(items.size(), items)
            ));
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        AdminOverviewResponse adminOverviewResponse = latticeHttpClient.get(
                "/api/v1/admin/overview",
                java.util.Map.of(),
                AdminOverviewResponse.class
        );
        printJson(adminOverviewResponse);
        return CliExitCodes.SUCCESS;
    }
}
