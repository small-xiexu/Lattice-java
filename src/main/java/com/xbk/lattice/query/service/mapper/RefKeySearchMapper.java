package com.xbk.lattice.query.service.mapper;

import com.xbk.lattice.query.service.QueryArticleHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 关键引用词检索 MyBatis Mapper
 *
 * 职责：通过 XML SQL 执行 refkey 动态打分检索
 *
 * @author xiexu
 */
@Mapper
public interface RefKeySearchMapper {

    /**
     * 执行关键引用词检索。
     *
     * @param likePatterns LIKE 模式列表
     * @param limit 返回上限
     * @return 文章命中列表
     */
    List<QueryArticleHit> search(
            @Param("likePatterns") List<String> likePatterns,
            @Param("limit") int limit
    );
}
