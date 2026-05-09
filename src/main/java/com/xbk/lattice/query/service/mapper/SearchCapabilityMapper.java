package com.xbk.lattice.query.service.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 检索能力探测 MyBatis Mapper
 *
 * 职责：通过 PostgreSQL 元数据探测 FTS 与 pgvector 能力
 *
 * @author xiexu
 */
@Mapper
public interface SearchCapabilityMapper {

    /**
     * 判断默认 schema 下的文本搜索配置是否存在。
     *
     * @param configName 配置名
     * @return 是否存在
     */
    boolean textSearchConfigExists(@Param("configName") String configName);

    /**
     * 判断指定 schema 下的文本搜索配置是否存在。
     *
     * @param schemaName schema 名称
     * @param configName 配置名
     * @return 是否存在
     */
    boolean schemaTextSearchConfigExists(
            @Param("schemaName") String schemaName,
            @Param("configName") String configName
    );

    /**
     * 判断 vector 类型是否可用。
     *
     * @return 是否可用
     */
    boolean vectorTypeAvailable();

    /**
     * 判断当前 schema 下表是否存在。
     *
     * @param tableName 表名
     * @return 是否存在
     */
    boolean tableExists(@Param("tableName") String tableName);
}
