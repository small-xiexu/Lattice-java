package com.xbk.lattice.documentparse.infra.persistence.mapper;

import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档解析连接 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 document_parse_connections 表
 *
 * @author xiexu
 */
@Mapper
public interface DocumentParseConnectionMapper {

    /**
     * 插入连接配置。
     *
     * @param connection 连接配置
     * @return 主键
     */
    Long insert(@Param("connection") ProviderConnection connection);

    /**
     * 更新连接配置。
     *
     * @param connection 连接配置
     * @return 影响行数
     */
    int update(@Param("connection") ProviderConnection connection);

    /**
     * 查询全部连接配置。
     *
     * @return 连接配置列表
     */
    List<ProviderConnection> findAll();

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    ProviderConnection findById(@Param("id") Long id);

    /**
     * 删除连接配置。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
