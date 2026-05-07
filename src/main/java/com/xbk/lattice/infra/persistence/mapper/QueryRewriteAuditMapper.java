package com.xbk.lattice.infra.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Query Rewrite 审计 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_rewrite_audits 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryRewriteAuditMapper {

    /**
     * 写入改写审计。
     *
     * @param queryId 查询标识
     * @param originalQuestion 原始问题
     * @param rewrittenQuestion 改写问题
     * @param matchedRuleCodesJson 命中规则编码 JSON
     * @param rewriteApplied 是否应用改写
     * @return 审计主键
     */
    Long insert(
            @Param("queryId") String queryId,
            @Param("originalQuestion") String originalQuestion,
            @Param("rewrittenQuestion") String rewrittenQuestion,
            @Param("matchedRuleCodesJson") String matchedRuleCodesJson,
            @Param("rewriteApplied") boolean rewriteApplied
    );
}
