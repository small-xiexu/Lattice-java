package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.StructuredTableCellRecord;
import com.xbk.lattice.infra.persistence.StructuredTableFilterParam;
import com.xbk.lattice.infra.persistence.StructuredTableGroupCountRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRowRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRowShell;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 结构化表格 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 structured_tables、structured_table_rows 与 structured_table_cells 表
 *
 * @author xiexu
 */
@Mapper
public interface StructuredTableMapper {

    /**
     * 删除指定源文件的结构化表格。
     *
     * @param sourceFileId 源文件主键
     * @return 删除数量
     */
    int deleteBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 写入结构化表格。
     *
     * @param record 表格记录
     * @param sourcePathNorm 归一化路径
     * @param searchText 检索文本
     * @return 表格主键
     */
    Long insertTable(
            @Param("record") StructuredTableRecord record,
            @Param("sourcePathNorm") String sourcePathNorm,
            @Param("searchText") String searchText
    );

    /**
     * 写入结构化表格行。
     *
     * @param record 行记录
     * @param searchText 检索文本
     * @return 行主键
     */
    Long insertRow(@Param("record") StructuredTableRowRecord record, @Param("searchText") String searchText);

    /**
     * 写入结构化单元格。
     *
     * @param record 单元格记录
     * @param columnNameNorm 归一化列名
     * @param searchText 检索文本
     * @return 影响行数
     */
    int insertCell(
            @Param("record") StructuredTableCellRecord record,
            @Param("columnNameNorm") String columnNameNorm,
            @Param("searchText") String searchText
    );

    /**
     * 查询指定源文件下的结构化表。
     *
     * @param sourceFileId 源文件主键
     * @return 表格记录列表
     */
    List<StructuredTableRecord> findTablesBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 查询指定表格下的结构化行。
     *
     * @param tableId 表格主键
     * @return 行记录列表
     */
    List<StructuredTableRowRecord> findRowsByTableId(@Param("tableId") Long tableId);

    /**
     * 查询指定表格下的结构化单元格。
     *
     * @param tableId 表格主键
     * @return 单元格记录列表
     */
    List<StructuredTableCellRecord> findCellsByTableId(@Param("tableId") Long tableId);

    /**
     * 按列名和值查询单元格。
     *
     * @param sourceFileId 源文件主键
     * @param columnName 列名
     * @param normalizedValue 归一化值
     * @return 单元格记录列表
     */
    List<StructuredTableCellRecord> findCellsByColumnValue(
            @Param("sourceFileId") Long sourceFileId,
            @Param("columnName") String columnName,
            @Param("normalizedValue") String normalizedValue
    );

    /**
     * 按过滤条件查询表格行壳。
     *
     * @param filters 过滤条件
     * @param limit 返回上限
     * @return 表格行壳列表
     */
    List<StructuredTableRowShell> findRowsByFilters(
            @Param("filters") List<StructuredTableFilterParam> filters,
            @Param("limit") int limit
    );

    /**
     * 统计满足过滤条件的行数。
     *
     * @param filters 过滤条件
     * @return 行数
     */
    long countRowsByFilters(@Param("filters") List<StructuredTableFilterParam> filters);

    /**
     * 按指定列进行分组计数。
     *
     * @param filters 过滤条件
     * @param groupByFieldNorm 归一化分组列名
     * @param limit 返回上限
     * @return 分组计数列表
     */
    List<StructuredTableGroupCountRecord> groupCountByField(
            @Param("filters") List<StructuredTableFilterParam> filters,
            @Param("groupByFieldNorm") String groupByFieldNorm,
            @Param("limit") int limit
    );

    /**
     * 按行主键批量查询单元格。
     *
     * @param rowIds 行主键列表
     * @return 单元格记录列表
     */
    List<StructuredTableCellRecord> findCellsByRowIds(@Param("rowIds") List<Long> rowIds);
}
