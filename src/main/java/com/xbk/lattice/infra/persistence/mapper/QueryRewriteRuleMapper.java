package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QueryRewriteRuleRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Query Rewrite 规则 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_rewrite_rules 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryRewriteRuleMapper {

    /**
     * 查询启用中的改写规则。
     *
     * @return 改写规则
     */
    List<QueryRewriteRuleRecord> findActiveRules();
}
