package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.ContributionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contribution JDBC 仓储
 *
 * 职责：提供确认后贡献的最小写入与查询能力
 *
 * @author xiexu
 */
@Repository
public class ContributionJdbcRepository {

    private final ContributionMapper contributionMapper;

    /**
     * 创建 Contribution JDBC 仓储。
     *
     * @param contributionMapper Contribution Mapper
     */
    public ContributionJdbcRepository(ContributionMapper contributionMapper) {
        this.contributionMapper = contributionMapper;
    }

    /**
     * 保存贡献记录。
     *
     * @param contributionRecord 贡献记录
     */
    public void save(ContributionRecord contributionRecord) {
        contributionMapper.insert(contributionRecord);
    }

    /**
     * 查询全部贡献记录。
     *
     * @return 贡献记录列表
     */
    public List<ContributionRecord> findAll() {
        return contributionMapper.findAll();
    }

    /**
     * 执行 contribution 数据库侧 lexical 检索。
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
        if (contributionMapper == null) {
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
        return contributionMapper.searchLexical(
                normalizeTsConfig(tsConfig),
                question == null ? "" : question,
                likePatterns,
                safeLimit(limit)
        );
    }

    /**
     * 清空全部贡献记录。
     */
    public void deleteAll() {
        contributionMapper.deleteAll();
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
