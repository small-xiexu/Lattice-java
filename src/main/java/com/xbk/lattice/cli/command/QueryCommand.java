package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.api.query.QueryRequest;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.query.service.QueryFacadeService;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

/**
 * query 命令
 *
 * 职责：执行知识查询并输出答案与证据
 *
 * @author xiexu
 */
@CommandLine.Command(name = "query", description = "查询知识库")
public class QueryCommand extends AbstractCliCommand {

    @CommandLine.Parameters(index = "0", description = "查询问题")
    private String question;

    @Override
    protected Integer runInStandaloneMode() {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            QueryFacadeService queryFacadeService = context.getBean(QueryFacadeService.class);
            QueryResponse queryResponse = queryFacadeService.query(question);
            printJson(queryResponse);
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion(question);
        QueryResponse queryResponse = latticeHttpClient.post("/api/v1/query", queryRequest, QueryResponse.class);
        printJson(queryResponse);
        return CliExitCodes.SUCCESS;
    }
}
