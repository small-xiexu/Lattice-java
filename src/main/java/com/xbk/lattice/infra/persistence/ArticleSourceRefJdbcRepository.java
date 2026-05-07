package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ArticleSourceRefMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章来源关联 JDBC 仓储
 *
 * 职责：维护 article_source_refs 表中的来源溯源映射
 *
 * @author xiexu
 */
@Repository
public class ArticleSourceRefJdbcRepository {

    private final ArticleSourceRefMapper articleSourceRefMapper;

    /**
     * 创建文章来源关联 JDBC 仓储。
     *
     * @param articleSourceRefMapper 文章来源关联 Mapper
     */
    public ArticleSourceRefJdbcRepository(ArticleSourceRefMapper articleSourceRefMapper) {
        this.articleSourceRefMapper = articleSourceRefMapper;
    }

    /**
     * 替换文章的全部来源关联。
     *
     * @param articleKey 文章唯一键
     * @param refRecords 关联记录列表
     */
    public void replaceRefs(String articleKey, List<ArticleSourceRefRecord> refRecords) {
        if (articleSourceRefMapper == null || articleKey == null || articleKey.isBlank()) {
            return;
        }
        articleSourceRefMapper.deleteByArticleKey(articleKey);
        if (refRecords == null || refRecords.isEmpty()) {
            return;
        }
        for (ArticleSourceRefRecord refRecord : refRecords) {
            articleSourceRefMapper.insert(refRecord);
        }
    }

    /**
     * 查询文章当前已有的全部来源关联。
     *
     * @param articleKey 文章唯一键
     * @return 来源关联列表
     */
    public List<ArticleSourceRefRecord> findByArticleKey(String articleKey) {
        if (articleSourceRefMapper == null || articleKey == null || articleKey.isBlank()) {
            return List.of();
        }
        return articleSourceRefMapper.findByArticleKey(articleKey);
    }

    /**
     * 按源文件主键批量查询关联的文章键。
     *
     * @param sourceFileIds 源文件主键列表
     * @return sourceFileId -> articleKey 列表
     */
    public Map<Long, List<String>> findArticleKeysBySourceFileIds(List<Long> sourceFileIds) {
        Map<Long, List<String>> articleKeysBySourceFileId = new LinkedHashMap<Long, List<String>>();
        if (articleSourceRefMapper == null || sourceFileIds == null || sourceFileIds.isEmpty()) {
            return articleKeysBySourceFileId;
        }
        List<ArticleSourceFileArticleKeyRow> rows = articleSourceRefMapper.findArticleKeysBySourceFileIds(sourceFileIds);
        for (ArticleSourceFileArticleKeyRow row : rows) {
            Long sourceFileId = row.getSourceFileId();
            if (sourceFileId == null) {
                continue;
            }
            List<String> articleKeys = articleKeysBySourceFileId.computeIfAbsent(
                    sourceFileId,
                    ignored -> new ArrayList<String>()
            );
            String articleKey = row.getArticleKey();
            if (articleKey != null) {
                articleKeys.add(articleKey);
            }
        }
        return articleKeysBySourceFileId;
    }
}
