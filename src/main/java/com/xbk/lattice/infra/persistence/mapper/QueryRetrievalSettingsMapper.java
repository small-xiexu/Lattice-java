package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Query 检索配置 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_retrieval_settings 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryRetrievalSettingsMapper {

    /**
     * 查询默认配置。
     *
     * @return 检索配置
     */
    QueryRetrievalSettingsState findDefault();

    /**
     * 保存默认配置。
     *
     * @param state 检索配置
     * @return 影响行数
     */
    int saveDefault(@Param("state") QueryRetrievalSettingsState state);
}
