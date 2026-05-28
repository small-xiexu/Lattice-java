package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 事实证据卡终端字段证据单元 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 fact_card_terminal_units 表
 *
 * @author xiexu
 */
@Mapper
public interface FactCardTerminalUnitMapper {

    /**
     * 判断当前 schema 下 terminal unit 表是否存在。
     *
     * @return 表存在返回 true
     */
    boolean tableExists();

    /**
     * 保存或更新终端字段证据单元。
     *
     * @param record 终端字段证据单元记录
     * @return 入库后的终端字段证据单元记录
     */
    FactCardTerminalUnitRecord upsert(@Param("record") FactCardTerminalUnitRecord record);

    /**
     * 按稳定业务标识查询终端字段证据单元。
     *
     * @param unitId 稳定业务标识
     * @return 终端字段证据单元记录
     */
    FactCardTerminalUnitRecord findByUnitId(@Param("unitId") String unitId);

    /**
     * 按事实卡主键查询终端字段证据单元。
     *
     * @param factCardId 事实卡主键
     * @return 终端字段证据单元列表
     */
    List<FactCardTerminalUnitRecord> findByFactCardId(@Param("factCardId") Long factCardId);

    /**
     * 按源文件主键查询终端字段证据单元。
     *
     * @param sourceFileId 源文件主键
     * @return 终端字段证据单元列表
     */
    List<FactCardTerminalUnitRecord> findBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 按事实卡主键删除终端字段证据单元。
     *
     * @param factCardId 事实卡主键
     * @return 删除数量
     */
    int deleteByFactCardId(@Param("factCardId") Long factCardId);

    /**
     * 按源文件主键删除终端字段证据单元。
     *
     * @param sourceFileId 源文件主键
     * @return 删除数量
     */
    int deleteBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 删除全部终端字段证据单元。
     *
     * @return 删除数量
     */
    int deleteAll();

    /**
     * 统计全部终端字段证据单元。
     *
     * @return 统计数量
     */
    int countAll();

    /**
     * 执行 terminal unit lexical 检索。
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
