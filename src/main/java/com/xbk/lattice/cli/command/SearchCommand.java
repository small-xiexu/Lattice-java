package com.xbk.lattice.cli.command;

import com.xbk.lattice.api.query.SearchHitResponse;
import com.xbk.lattice.api.query.SearchResponse;
import com.xbk.lattice.cli.CliExitCodes;
import com.xbk.lattice.cli.remote.LatticeHttpClient;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

/**
 * search 命令
 *
 * 职责：执行不生成答案的融合搜索
 *
 * @author xiexu
 */
@CommandLine.Command(name = "search", description = "搜索知识库")
public class SearchCommand extends AbstractCliCommand {

    @CommandLine.Parameters(index = "0", description = "搜索问题")
    private String question;

    @CommandLine.Option(names = {"-n", "--limit"}, defaultValue = "5", description = "返回条数")
    private int limit;

    @Override
    protected Integer runInStandaloneMode() {
        try (ConfigurableApplicationContext context = com.xbk.lattice.cli.CliRuntimeSupport.createContext()) {
            KnowledgeSearchService knowledgeSearchService = context.getBean(KnowledgeSearchService.class);
            List<QueryArticleHit> hits = knowledgeSearchService.search(question, limit);
            List<SearchHitResponse> items = new ArrayList<SearchHitResponse>();
            for (QueryArticleHit hit : hits) {
                items.add(new SearchHitResponse(
                        hit.getEvidenceType().name(),
                        hit.getConceptId(),
                        hit.getTitle(),
                        hit.getContent(),
                        hit.getMetadataJson(),
                        hit.getSourcePaths(),
                        hit.getScore()
                ));
            }
            printJson(new SearchResponse(items.size(), items));
            return CliExitCodes.SUCCESS;
        }
    }

    @Override
    protected Integer runInRemoteMode(LatticeHttpClient latticeHttpClient) throws Exception {
        SearchResponse searchResponse = latticeHttpClient.get(
                "/api/v1/search",
                java.util.Map.of(
                        "question", question,
                        "limit", String.valueOf(limit)
                ),
                SearchResponse.class
        );
        printJson(searchResponse);
        return CliExitCodes.SUCCESS;
    }
}
