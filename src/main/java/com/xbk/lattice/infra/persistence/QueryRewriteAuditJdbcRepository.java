package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.mapper.QueryRewriteAuditMapper;
import com.xbk.lattice.query.service.QueryRewriteResult;
import org.springframework.stereotype.Repository;

/**
 * Query Rewrite 审计 JDBC 仓储
 *
 * 职责：记录单次查询改写的输入、输出与命中规则
 *
 * @author xiexu
 */
@Repository
public class QueryRewriteAuditJdbcRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final QueryRewriteAuditMapper queryRewriteAuditMapper;

    /**
     * 创建 Query Rewrite 审计 JDBC 仓储。
     *
     * @param queryRewriteAuditMapper Query Rewrite 审计 Mapper
     */
    public QueryRewriteAuditJdbcRepository(QueryRewriteAuditMapper queryRewriteAuditMapper) {
        this.queryRewriteAuditMapper = queryRewriteAuditMapper;
    }

    /**
     * 保存改写审计。
     *
     * @param queryId 查询标识
     * @param queryRewriteResult 改写结果
     * @return 审计引用
     */
    public String save(String queryId, QueryRewriteResult queryRewriteResult) {
        if (queryRewriteResult == null || queryId == null || queryId.isBlank()) {
            return null;
        }
        String matchedRuleCodesJson = toJson(queryRewriteResult);
        Long auditId = queryRewriteAuditMapper.insert(
                queryId,
                queryRewriteResult.getOriginalQuestion(),
                queryRewriteResult.getRewrittenQuestion(),
                matchedRuleCodesJson,
                queryRewriteResult.isRewriteApplied()
        );
        return auditId == null ? null : "query_rewrite_audits:" + auditId;
    }

    /**
     * 把命中规则编码序列化为 JSON。
     *
     * @param queryRewriteResult 改写结果
     * @return JSON 字符串
     */
    private String toJson(QueryRewriteResult queryRewriteResult) {
        try {
            return OBJECT_MAPPER.writeValueAsString(queryRewriteResult.getMatchedRuleCodes());
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("matchedRuleCodes 序列化失败", exception);
        }
    }
}
