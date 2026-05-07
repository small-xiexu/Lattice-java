package com.xbk.lattice.documentparse.infra.persistence.mapper;

import com.xbk.lattice.documentparse.domain.model.ParseRoutePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档解析路由策略 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 document_parse_route_policies 表
 *
 * @author xiexu
 */
@Mapper
public interface DocumentParseRoutePolicyMapper {

    /**
     * 查询指定作用域策略。
     *
     * @param policyScope 作用域
     * @return 路由策略
     */
    ParseRoutePolicy findByScope(@Param("policyScope") String policyScope);

    /**
     * 插入路由策略。
     *
     * @param policy 路由策略
     * @return 影响行数
     */
    int insert(@Param("policy") ParseRoutePolicy policy);

    /**
     * 更新路由策略。
     *
     * @param policy 路由策略
     * @return 影响行数
     */
    int update(@Param("policy") ParseRoutePolicy policy);
}
