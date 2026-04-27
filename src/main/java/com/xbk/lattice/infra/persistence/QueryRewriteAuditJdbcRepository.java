package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.query.service.QueryRewriteResult;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Query Rewrite 审计 JDBC 仓储
 *
 * 职责：记录单次查询改写的输入、输出与命中规则
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryRewriteAuditJdbcRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Query Rewrite 审计 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryRewriteAuditJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存改写审计。
     *
     * @param queryId 查询标识
     * @param queryRewriteResult 改写结果
     * @return 审计引用
     */
    public String save(String queryId, QueryRewriteResult queryRewriteResult) {
        if (jdbcTemplate == null || queryRewriteResult == null || queryId == null || queryId.isBlank()) {
            return null;
        }
        Long auditId = jdbcTemplate.queryForObject(
                """
                        insert into query_rewrite_audits (
                            query_id, original_question, rewritten_question,
                            matched_rule_codes, rewrite_applied, created_at
                        ) values (?, ?, ?, cast(? as jsonb), ?, current_timestamp)
                        returning audit_id
                        """,
                Long.class,
                queryId,
                queryRewriteResult.getOriginalQuestion(),
                queryRewriteResult.getRewrittenQuestion(),
                toJson(queryRewriteResult),
                Boolean.valueOf(queryRewriteResult.isRewriteApplied())
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
