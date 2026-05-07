package com.xbk.lattice.source.infra.mapper;

import com.xbk.lattice.source.domain.KnowledgeSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资料源 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 knowledge_sources 表
 *
 * @author xiexu
 */
@Mapper
public interface KnowledgeSourceMapper {

    /**
     * 查询全部资料源。
     *
     * @return 资料源列表
     */
    List<KnowledgeSource> findAll();

    /**
     * 按主键查询资料源。
     *
     * @param id 主键
     * @return 资料源
     */
    KnowledgeSource findById(@Param("id") Long id);

    /**
     * 按编码查询资料源。
     *
     * @param sourceCode 资料源编码
     * @return 资料源
     */
    KnowledgeSource findBySourceCode(@Param("sourceCode") String sourceCode);

    /**
     * 统计资料源数量。
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @return 资料源总数
     */
    long countAll(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("sourceType") String sourceType
    );

    /**
     * 分页查询资料源。
     *
     * @param keyword 关键词
     * @param status 状态过滤
     * @param sourceType 类型过滤
     * @param offset 偏移量
     * @param limit 分页大小
     * @return 资料源列表
     */
    List<KnowledgeSource> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("sourceType") String sourceType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 插入资料源。
     *
     * @param source 资料源
     * @return 主键
     */
    Long insert(@Param("source") KnowledgeSource source);

    /**
     * 更新资料源。
     *
     * @param source 资料源
     * @return 影响行数
     */
    int update(@Param("source") KnowledgeSource source);
}
