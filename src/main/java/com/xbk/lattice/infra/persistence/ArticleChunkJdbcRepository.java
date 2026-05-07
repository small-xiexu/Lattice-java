package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.chunking.SemanticChunker;
import com.xbk.lattice.infra.chunking.TextChunk;
import com.xbk.lattice.infra.persistence.mapper.ArticleChunkMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * ArticleChunk JDBC 仓储
 *
 * 职责：提供最小 article chunk 替换写入与读取能力
 *
 * @author xiexu
 */
@Repository
public class ArticleChunkJdbcRepository {

    private static final int DEFAULT_MAX_CHARS = 3600;

    private static final float DEFAULT_OVERLAP_RATIO = 0.15f;

    private final ArticleChunkMapper articleChunkMapper;

    private final SemanticChunker semanticChunker;

    /**
     * 创建 ArticleChunk JDBC 仓储。
     *
     * @param articleChunkMapper 文章分块 Mapper
     */
    public ArticleChunkJdbcRepository(ArticleChunkMapper articleChunkMapper) {
        this.articleChunkMapper = articleChunkMapper;
        this.semanticChunker = new SemanticChunker();
    }

    /**
     * 按 conceptId 替换文章 chunk。
     *
     * @param conceptId 概念标识
     * @param chunkTexts chunk 文本集合
     */
    public void replaceChunks(String conceptId, List<String> chunkTexts) {
        replaceChunks(null, conceptId, chunkTexts);
    }

    /**
     * 按文章唯一键或 conceptId 替换文章 chunk。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param chunkTexts chunk 文本集合
     */
    public void replaceChunks(String articleKey, String conceptId, List<String> chunkTexts) {
        if (articleChunkMapper == null) {
            return;
        }
        boolean useArticleKey = hasText(articleKey);
        if (useArticleKey) {
            articleChunkMapper.deleteByArticleKey(articleKey);
        }
        else {
            articleChunkMapper.deleteByConceptId(conceptId);
        }
        for (int index = 0; index < chunkTexts.size(); index++) {
            String chunkText = chunkTexts.get(index);
            if (useArticleKey) {
                articleChunkMapper.insertByArticleKey(articleKey, chunkText, index);
            }
            else {
                articleChunkMapper.insertByConceptId(conceptId, chunkText, index);
            }
        }
    }

    /**
     * 按文章正文替换语义分块。
     *
     * @param conceptId 概念标识
     * @param content 文章正文
     */
    public void replaceChunksFromContent(String conceptId, String content) {
        replaceChunksFromContent(null, conceptId, content);
    }

    /**
     * 按文章正文替换语义分块。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param content 文章正文
     */
    public void replaceChunksFromContent(String articleKey, String conceptId, String content) {
        if (articleChunkMapper == null) {
            return;
        }
        List<TextChunk> textChunks = semanticChunker.chunk(content, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_RATIO);
        List<String> chunkTexts = new ArrayList<String>();
        for (TextChunk textChunk : textChunks) {
            chunkTexts.add(textChunk.getText());
        }
        replaceChunks(articleKey, conceptId, chunkTexts);
    }

    /**
     * 按当前文章正文全量重建全部 article chunks。
     *
     * @param articleRecords 文章记录列表
     * @return 重建的文章数量
     */
    public int rebuildAll(List<ArticleRecord> articleRecords) {
        if (articleChunkMapper == null) {
            return 0;
        }

        articleChunkMapper.truncateAll();
        int rebuiltCount = 0;
        for (ArticleRecord articleRecord : articleRecords) {
            replaceChunksFromContent(articleRecord.getArticleKey(), articleRecord.getConceptId(), articleRecord.getContent());
            rebuiltCount++;
        }
        return rebuiltCount;
    }

    /**
     * 统计全部 article chunk 数量。
     *
     * @return chunk 数量
     */
    public int countAll() {
        if (articleChunkMapper == null) {
            return 0;
        }
        return articleChunkMapper.countAll();
    }

    /**
     * 按 conceptId 查询 chunk 文本。
     *
     * @param conceptId 概念标识
     * @return chunk 文本集合
     */
    public List<String> findChunkTexts(String conceptId) {
        if (articleChunkMapper == null) {
            return List.of();
        }
        return articleChunkMapper.findChunkTexts(conceptId);
    }

    /**
     * 按 conceptId 查询完整 chunk 记录。
     *
     * @param conceptId 概念标识
     * @return chunk 记录列表
     */
    public List<ArticleChunkRecord> findByConceptId(String conceptId) {
        if (articleChunkMapper == null) {
            return List.of();
        }
        return articleChunkMapper.findByConceptId(conceptId);
    }

    /**
     * 按文章唯一键查询完整 chunk 记录。
     *
     * @param articleKey 文章唯一键
     * @return chunk 记录列表
     */
    public List<ArticleChunkRecord> findByArticleKey(String articleKey) {
        if (articleChunkMapper == null) {
            return List.of();
        }
        return articleChunkMapper.findByArticleKey(articleKey);
    }

    /**
     * 查询全部 chunk 记录。
     *
     * @return 全部 chunk 记录
     */
    public List<ArticleChunkRecord> findAllRecords() {
        if (articleChunkMapper == null) {
            return List.of();
        }
        return articleChunkMapper.findAllRecords();
    }

    /**
     * 执行 article chunk 数据库侧 lexical 检索。
     *
     * @param question 查询问题
     * @param queryTokens 查询 token
     * @param limit 返回数量
     * @param tsConfig FTS 配置
     * @return lexical 命中记录
     */
    public List<LexicalSearchRecord> searchLexical(
            String question,
            List<String> queryTokens,
            int limit,
            String tsConfig
    ) {
        if (articleChunkMapper == null) {
            return List.of();
        }
        List<String> normalizedTokens = LexicalSearchTokenBudget.normalize(queryTokens);
        if (!hasText(question) && normalizedTokens.isEmpty()) {
            return List.of();
        }
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(normalizedTokens);
        List<String> likePatterns = likeTokens.stream()
                .map(this::likePattern)
                .toList();
        return articleChunkMapper.searchLexical(
                normalizeTsConfig(tsConfig),
                question == null ? "" : question,
                likePatterns,
                safeLimit(limit)
        );
    }

    /**
     * 规范化 FTS 配置。
     *
     * @param tsConfig FTS 配置
     * @return 规范化配置
     */
    private String normalizeTsConfig(String tsConfig) {
        return hasText(tsConfig) ? tsConfig.trim() : "simple";
    }

    /**
     * 计算安全返回数量。
     *
     * @param limit 原始数量
     * @return 安全数量
     */
    private int safeLimit(int limit) {
        return limit <= 0 ? 5 : limit;
    }

    /**
     * 构造 LIKE 匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 模式
     */
    private String likePattern(String queryToken) {
        return "%" + escapeLikePattern(queryToken) + "%";
    }

    /**
     * 转义 LIKE 模式中的通配符。
     *
     * @param value 原始值
     * @return 转义后的 LIKE 片段
     */
    private String escapeLikePattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 判断文本是否有值。
     *
     * @param value 文本
     * @return 是否有值
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
