package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.FactCardCountRow;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 事实证据卡 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 fact_cards 表
 *
 * @author xiexu
 */
@Mapper
public interface FactCardMapper {

    /**
     * 保存或更新事实证据卡。
     *
     * @param record 事实证据卡记录
     * @param searchText 检索文本
     * @return 入库后的事实证据卡记录
     */
    FactCardRecord upsert(@Param("record") FactCardRecord record, @Param("searchText") String searchText);

    /**
     * 按证据卡稳定标识查询。
     *
     * @param cardId 证据卡稳定标识
     * @return 事实证据卡记录
     */
    FactCardRecord findByCardId(@Param("cardId") String cardId);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 事实证据卡记录
     */
    FactCardRecord findById(@Param("id") Long id);

    /**
     * 查询全部事实证据卡。
     *
     * @return 事实证据卡列表
     */
    List<FactCardRecord> findAll();

    /**
     * 按源文件主键查询事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @return 事实证据卡列表
     */
    List<FactCardRecord> findBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 按源文件主键删除事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @return 删除数量
     */
    int deleteBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 删除全部事实证据卡。
     *
     * @return 删除数量
     */
    int deleteAll();

    /**
     * 统计事实证据卡总数。
     *
     * @return 总数
     */
    int countAll();

    /**
     * 按证据卡类型统计。
     *
     * @return 统计行列表
     */
    List<FactCardCountRow> countByCardType();

    /**
     * 按审查状态统计。
     *
     * @return 统计行列表
     */
    List<FactCardCountRow> countByReviewStatus();

    /**
     * 统计没有 source chunk 回指的事实证据卡数量。
     *
     * @return 统计数量
     */
    int countWithoutSourceChunks();

    /**
     * 统计指定审查状态的事实证据卡数量。
     *
     * @param reviewStatus 审查状态数据库值
     * @return 统计数量
     */
    int countByReviewStatusValue(@Param("reviewStatus") String reviewStatus);

    /**
     * 执行 fact card lexical 检索。
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
}
