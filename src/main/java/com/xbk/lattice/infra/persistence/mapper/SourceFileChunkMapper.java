package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 源文件分块 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 source_file_chunks 表
 *
 * @author xiexu
 */
@Mapper
public interface SourceFileChunkMapper {

    /**
     * 按文件路径删除分块。
     *
     * @param filePath 文件路径
     * @return 影响行数
     */
    int deleteByFilePath(@Param("filePath") String filePath);

    /**
     * 按源文件主键删除分块。
     *
     * @param sourceFileId 源文件主键
     * @return 影响行数
     */
    int deleteBySourceFileId(@Param("sourceFileId") Long sourceFileId);

    /**
     * 写入源文件分块。
     *
     * @param record 源文件分块
     * @param filePathNorm 归一化路径文本
     * @param searchText 检索文本
     * @return 影响行数
     */
    int insert(
            @Param("record") SourceFileChunkRecord record,
            @Param("filePathNorm") String filePathNorm,
            @Param("searchText") String searchText
    );

    /**
     * 清空全部源文件分块。
     */
    void truncateAll();

    /**
     * 统计全部源文件分块。
     *
     * @return 分块数量
     */
    int countAll();

    /**
     * 查询全部源文件分块。
     *
     * @return 分块记录列表
     */
    List<SourceFileChunkRecord> findAll();

    /**
     * 按文件路径列表查询源文件分块。
     *
     * @param filePaths 文件路径列表
     * @return 分块记录列表
     */
    List<SourceFileChunkRecord> findByFilePaths(@Param("filePaths") List<String> filePaths);

    /**
     * 执行源文件分块 lexical 检索。
     *
     * @param tsConfig FTS 配置
     * @param question 查询问题
     * @param likeTokens LIKE 模式列表
     * @param assignmentTokens 结构化赋值 LIKE 模式列表
     * @param limit 返回上限
     * @return lexical 命中列表
     */
    List<LexicalSearchRecord> searchLexical(
            @Param("tsConfig") String tsConfig,
            @Param("question") String question,
            @Param("likeTokens") List<String> likeTokens,
            @Param("assignmentTokens") List<String> assignmentTokens,
            @Param("limit") int limit
    );

    /**
     * 查询同一文件邻近分块。
     *
     * @param filePath 文件路径
     * @param chunkIndex 当前分块序号
     * @param startIndex 起始分块序号
     * @param endIndex 结束分块序号
     * @param limit 返回上限
     * @return lexical 命中列表
     */
    List<LexicalSearchRecord> findNeighborChunks(
            @Param("filePath") String filePath,
            @Param("chunkIndex") int chunkIndex,
            @Param("startIndex") int startIndex,
            @Param("endIndex") int endIndex,
            @Param("limit") int limit
    );
}
