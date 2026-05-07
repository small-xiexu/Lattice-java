package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SourceFile MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 source_files 表
 *
 * @author xiexu
 */
@Mapper
public interface SourceFileMapper {

    /**
     * 保存或更新源文件。
     *
     * @param record 源文件记录
     * @param filePathNorm 归一化路径文本
     * @param searchText 检索文本
     * @return 落盘后的源文件记录
     */
    SourceFileRecord upsert(
            @Param("record") SourceFileRecord record,
            @Param("filePathNorm") String filePathNorm,
            @Param("searchText") String searchText
    );

    /**
     * 按路径查询源文件。
     *
     * @param filePath 文件路径
     * @return 源文件记录
     */
    SourceFileRecord findByPath(@Param("filePath") String filePath);

    /**
     * 按资料源和相对路径查询源文件。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @return 源文件记录
     */
    SourceFileRecord findBySourceIdAndRelativePath(
            @Param("sourceId") Long sourceId,
            @Param("relativePath") String relativePath
    );

    /**
     * 查询全部源文件。
     *
     * @return 源文件列表
     */
    List<SourceFileRecord> findAll();

    /**
     * 按资料源查询源文件。
     *
     * @param sourceId 资料源主键
     * @return 源文件列表
     */
    List<SourceFileRecord> findBySourceId(@Param("sourceId") Long sourceId);

    /**
     * 按主键批量查询源文件。
     *
     * @param sourceFileIds 源文件主键列表
     * @return 源文件列表
     */
    List<SourceFileRecord> findByIds(@Param("sourceFileIds") List<Long> sourceFileIds);

    /**
     * 执行源文件 lexical 检索。
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
     * 查询 legacy-default 资料源主键。
     *
     * @return 资料源主键
     */
    Long findLegacyDefaultSourceId();

    /**
     * 确保 legacy-default 资料源存在。
     *
     * @return 影响行数
     */
    int insertLegacyDefaultSource();
}
