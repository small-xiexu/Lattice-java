package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Contribution MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 contributions 表
 *
 * @author xiexu
 */
@Mapper
public interface ContributionMapper {

    /**
     * 写入贡献记录。
     *
     * @param record 贡献记录
     * @return 影响行数
     */
    int insert(@Param("record") ContributionRecord record);

    /**
     * 查询全部贡献记录。
     *
     * @return 贡献记录列表
     */
    List<ContributionRecord> findAll();

    /**
     * 执行 contribution lexical 检索。
     *
     * @param tsConfig FTS 配置
     * @param question 查询问题
     * @param likeTokens LIKE 模式列表
     * @param limit 返回上限
     * @return lexical 命中列表
     */
    List<LexicalSearchRecord> searchLexical(
            @Param("tsConfig") String tsConfig,
            @Param("question") String question,
            @Param("likeTokens") List<String> likeTokens,
            @Param("limit") int limit
    );

    /**
     * 清空全部贡献记录。
     */
    void deleteAll();
}
