package com.xbk.lattice.documentparse.infra.persistence;

import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import com.xbk.lattice.documentparse.infra.persistence.mapper.DocumentParseRoutePolicyMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档解析路由策略 JDBC 仓储
 *
 * 职责：提供 document_parse_route_policies 表的读写能力
 *
 * @author xiexu
 */
@Repository
public class DocumentParseRoutePolicyJdbcRepository {

    private final DocumentParseRoutePolicyMapper documentParseRoutePolicyMapper;

    /**
     * 创建文档解析路由策略 JDBC 仓储。
     *
     * @param documentParseRoutePolicyMapper 文档解析路由策略 Mapper
     */
    public DocumentParseRoutePolicyJdbcRepository(DocumentParseRoutePolicyMapper documentParseRoutePolicyMapper) {
        this.documentParseRoutePolicyMapper = documentParseRoutePolicyMapper;
    }

    /**
     * 查询默认作用域策略。
     *
     * @return 默认作用域策略
     */
    public Optional<ParseRoutePolicy> findDefault() {
        return findByScope(ParseRoutePolicy.DEFAULT_SCOPE);
    }

    /**
     * 保存策略。
     *
     * @param policy 路由策略
     * @return 保存后的路由策略
     */
    public ParseRoutePolicy save(ParseRoutePolicy policy) {
        Optional<ParseRoutePolicy> existing = findByScope(policy.getPolicyScope());
        if (existing.isPresent()) {
            update(policy);
        }
        else {
            insert(policy);
        }
        return findByScope(policy.getPolicyScope()).orElseThrow();
    }

    /**
     * 查询指定作用域策略。
     *
     * @param policyScope 作用域
     * @return 路由策略
     */
    private Optional<ParseRoutePolicy> findByScope(String policyScope) {
        return Optional.ofNullable(documentParseRoutePolicyMapper.findByScope(policyScope));
    }

    /**
     * 插入路由策略。
     *
     * @param policy 路由策略
     */
    private void insert(ParseRoutePolicy policy) {
        documentParseRoutePolicyMapper.insert(policy);
    }

    /**
     * 更新路由策略。
     *
     * @param policy 路由策略
     */
    private void update(ParseRoutePolicy policy) {
        documentParseRoutePolicyMapper.update(policy);
    }
}
