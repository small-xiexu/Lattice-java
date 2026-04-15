package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import com.xbk.lattice.query.service.QueryFacadeService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Lattice MCP 工具集
 *
 * 职责：将知识查询与反馈闭环能力通过 MCP 协议暴露给外部 AI 客户端（Claude Desktop / Cursor）
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LatticeMcpTools {

    private final QueryFacadeService queryFacadeService;

    private final PendingQueryManager pendingQueryManager;

    /**
     * 创建 Lattice MCP 工具集。
     *
     * @param queryFacadeService 查询门面服务
     * @param pendingQueryManager PendingQuery 管理器
     */
    public LatticeMcpTools(QueryFacadeService queryFacadeService, PendingQueryManager pendingQueryManager) {
        this.queryFacadeService = queryFacadeService;
        this.pendingQueryManager = pendingQueryManager;
    }

    /**
     * 向知识库发起查询，返回答案、来源数量与待确认查询标识。
     *
     * @param question 查询问题
     * @return JSON 字符串，包含 answer / queryId / reviewStatus / sourceCount
     */
    @McpTool(name = "lattice_query", description = "Query the Lattice knowledge base and return an answer with source count and a queryId for the pending review lifecycle")
    public String query(@McpToolParam(description = "The question to ask the knowledge base") String question) {
        QueryResponse response = queryFacadeService.query(question);
        int sourceCount = response.getSources() == null ? 0 : response.getSources().size();
        return "{"
                + "\"answer\":" + jsonString(response.getAnswer()) + ","
                + "\"queryId\":" + jsonString(response.getQueryId()) + ","
                + "\"reviewStatus\":" + jsonString(response.getReviewStatus()) + ","
                + "\"sourceCount\":" + sourceCount
                + "}";
    }

    /**
     * 查询待确认记录的当前状态，用于在纠错前确认当前答案内容。
     *
     * @param queryId 待确认查询标识
     * @return JSON 字符串，包含 queryId / question / answer / reviewStatus
     */
    @McpTool(name = "lattice_query_pending", description = "Retrieve the current state of a pending query by its queryId")
    public String queryPending(@McpToolParam(description = "The queryId returned by lattice_query") String queryId) {
        PendingQueryRecord record = pendingQueryManager.findPendingQuery(queryId);
        return "{"
                + "\"queryId\":" + jsonString(record.getQueryId()) + ","
                + "\"question\":" + jsonString(record.getQuestion()) + ","
                + "\"answer\":" + jsonString(record.getAnswer()) + ","
                + "\"reviewStatus\":" + jsonString(record.getReviewStatus())
                + "}";
    }

    /**
     * 对待确认查询提交纠正内容，修订答案并保持 PENDING 状态。
     *
     * @param queryId 待确认查询标识
     * @param correction 纠正内容
     * @return JSON 字符串，包含 queryId / revisedAnswer / status
     */
    @McpTool(name = "lattice_query_correct", description = "Submit a correction to a pending query answer; the query remains pending until confirmed")
    public String correct(
            @McpToolParam(description = "The queryId of the pending query to correct") String queryId,
            @McpToolParam(description = "The correction text to append to the answer") String correction
    ) {
        PendingQueryRecord updated = pendingQueryManager.correct(queryId, correction);
        return "{"
                + "\"queryId\":" + jsonString(updated.getQueryId()) + ","
                + "\"revisedAnswer\":" + jsonString(updated.getAnswer()) + ","
                + "\"status\":\"PENDING\""
                + "}";
    }

    /**
     * 确认待确认查询，将其沉淀为贡献记录并从 pending 队列中移除。
     *
     * @param queryId 待确认查询标识
     * @return JSON 字符串，包含 queryId / status
     */
    @McpTool(name = "lattice_query_confirm", description = "Confirm a pending query answer, persisting it as a contribution and removing it from the pending queue")
    public String confirm(@McpToolParam(description = "The queryId of the pending query to confirm") String queryId) {
        pendingQueryManager.confirm(queryId);
        return "{"
                + "\"queryId\":" + jsonString(queryId) + ","
                + "\"status\":\"confirmed\""
                + "}";
    }

    /**
     * 将字符串转义为 JSON 字符串值（含双引号），处理 null 值与特殊字符。
     *
     * @param value 原始字符串
     * @return JSON 字符串表达
     */
    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
