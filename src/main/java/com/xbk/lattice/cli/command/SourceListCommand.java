package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.admin.AdminSourceController;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.source.domain.KnowledgeSourcePage;
import com.xbk.lattice.source.service.SourceService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * source-list 命令
 *
 * 职责：查看资料源列表、分页与过滤结果
 *
 * @author xiexu
 */
@CommandLine.Command(name = "source-list", description = "查看资料源列表")
public class SourceListCommand extends AbstractCliCommand {

    @CommandLine.Option(names = "--keyword", description = "按 sourceCode / name 过滤")
    private String keyword;

    @CommandLine.Option(names = "--status", description = "按状态过滤，例如 ACTIVE / DISABLED / ARCHIVED")
    private String status;

    @CommandLine.Option(names = "--source-type", description = "按类型过滤，例如 UPLOAD / GIT / SERVER_DIR")
    private String sourceType;

    @CommandLine.Option(names = "--page", defaultValue = "1", description = "页码，从 1 开始")
    private int page;

    @CommandLine.Option(names = "--size", defaultValue = "20", description = "每页大小，最大 100")
    private int size;

    @Override
    protected Integer runInStandaloneMode() {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            SourceService sourceService = context.getBean(SourceService.class);
            KnowledgeSourcePage result = sourceService.listSources(keyword, status, sourceType, page, size);
            printJson(result);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        Map<String, String> queryParams = new LinkedHashMap<String, String>();
        queryParams.put("keyword", keyword);
        queryParams.put("status", status);
        queryParams.put("sourceType", sourceType);
        queryParams.put("page", String.valueOf(page));
        queryParams.put("size", String.valueOf(size));
        AdminSourceController.AdminKnowledgeSourcePageResponse response = latticeHttpClient.get(
                "/api/v1/admin/sources",
                queryParams,
                AdminSourceController.AdminKnowledgeSourcePageResponse.class
        );
        printJson(response);
        return CliExitCodes.SUCCESS;
    }
}
