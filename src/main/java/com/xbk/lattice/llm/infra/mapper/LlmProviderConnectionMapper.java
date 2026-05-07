package com.xbk.lattice.llm.infra.mapper;

import com.xbk.lattice.llm.domain.LlmProviderConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Provider 连接 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 llm_provider_connections 表
 *
 * @author xiexu
 */
@Mapper
public interface LlmProviderConnectionMapper {

    /**
     * 插入连接配置。
     *
     * @param connection 连接配置
     * @return 主键
     */
    Long insert(@Param("connection") LlmProviderConnection connection);

    /**
     * 更新连接配置。
     *
     * @param connection 连接配置
     * @return 影响行数
     */
    int update(@Param("connection") LlmProviderConnection connection);

    /**
     * 查询全部连接配置。
     *
     * @return 连接配置列表
     */
    List<LlmProviderConnection> findAll();

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    LlmProviderConnection findById(@Param("id") Long id);

    /**
     * 按主键查询启用中的连接配置。
     *
     * @param id 主键
     * @return 启用中的连接配置
     */
    LlmProviderConnection findEnabledById(@Param("id") Long id);

    /**
     * 删除连接配置。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
