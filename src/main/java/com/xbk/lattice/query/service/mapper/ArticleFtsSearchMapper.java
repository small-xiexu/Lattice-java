package com.xbk.lattice.query.service.mapper;

import com.xbk.lattice.query.service.QueryArticleHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章 FTS 检索 MyBatis Mapper
 *
 * 职责：通过 XML SQL 执行 articles 全文检索
 *
 * @author xiexu
 */
@Mapper
public interface ArticleFtsSearchMapper {

    /**
     * 执行全文检索。
     *
     * @param tsConfig FTS 配置
     * @param question 查询问题
     * @param limit 返回上限
     * @return 文章命中列表
     */
    List<QueryArticleHit> search(
            @Param("tsConfig") String tsConfig,
            @Param("question") String question,
            @Param("limit") int limit
    );
}
