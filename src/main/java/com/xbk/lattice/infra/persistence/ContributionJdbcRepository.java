package com.xbk.lattice.infra.persistence;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Contribution JDBC 仓储
 *
 * 职责：提供确认后贡献的最小写入与查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ContributionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Contribution JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ContributionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存贡献记录。
     *
     * @param contributionRecord 贡献记录
     */
    public void save(ContributionRecord contributionRecord) {
        String sql = """
                insert into contributions (
                    id, question, answer, corrections, confirmed_by, confirmed_at,
                    question_tsv, answer_tsv, corrections_tsv
                )
                values (
                    ?, ?, ?, ?, ?, ?,
                    to_tsvector('simple'::regconfig, ?),
                    to_tsvector('simple'::regconfig, ?),
                    to_tsvector('simple'::regconfig, ?)
                )
                """;
        jdbcTemplate.update(connection -> {
            PGobject correctionsObject = new PGobject();
            correctionsObject.setType("jsonb");
            correctionsObject.setValue(contributionRecord.getCorrectionsJson());

            java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setObject(1, contributionRecord.getId());
            preparedStatement.setString(2, contributionRecord.getQuestion());
            preparedStatement.setString(3, contributionRecord.getAnswer());
            preparedStatement.setObject(4, correctionsObject);
            preparedStatement.setString(5, contributionRecord.getConfirmedBy());
            preparedStatement.setObject(6, contributionRecord.getConfirmedAt());
            preparedStatement.setString(7, contributionRecord.getQuestion());
            preparedStatement.setString(8, contributionRecord.getAnswer());
            preparedStatement.setString(9, contributionRecord.getCorrectionsJson());
            return preparedStatement;
        });
    }

    /**
     * 查询全部贡献记录。
     *
     * @return 贡献记录列表
     */
    public List<ContributionRecord> findAll() {
        String sql = """
                select id, question, answer, corrections::text as corrections_json, confirmed_by, confirmed_at
                from contributions
                order by confirmed_at desc, id desc
                """;
        return jdbcTemplate.query(sql, this::mapContributionRecord);
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
        if (jdbcTemplate == null) {
            return List.of();
        }
        List<String> normalizedTokens = normalizeTokens(queryTokens);
        if (!hasText(question) && normalizedTokens.isEmpty()) {
            return List.of();
        }

        List<Object> parameters = new ArrayList<Object>();
        parameters.add(normalizeTsConfig(tsConfig));
        parameters.add(question == null ? "" : question);
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                with query as (
                    select plainto_tsquery(cast(? as regconfig), ?) as tsq
                )
                select id,
                       question,
                       answer,
                       corrections::text as corrections_json,
                       confirmed_by,
                       confirmed_at,
                       ts_rank_cd(question_tsv, query.tsq) * 3.0
                       + ts_rank_cd(answer_tsv, query.tsq) * 4.0
                       + ts_rank_cd(corrections_tsv, query.tsq) * 1.5
                """);
        appendTokenScore(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("lower(question)", "lower(answer)", "lower(corrections::text)"),
                List.of(Double.valueOf(3.0D), Double.valueOf(4.0D), Double.valueOf(1.5D))
        );
        sqlBuilder.append("""
                       as score
                from contributions
                cross join query
                where question_tsv @@ query.tsq
                   or answer_tsv @@ query.tsq
                   or corrections_tsv @@ query.tsq
                """);
        appendTokenWhere(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("lower(question)", "lower(answer)", "lower(corrections::text)")
        );
        sqlBuilder.append("""
                order by score desc, confirmed_at desc, id desc
                limit ?
                """);
        parameters.add(Integer.valueOf(safeLimit(limit)));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapLexicalSearchRecord, parameters.toArray());
    }

    /**
     * 清空全部贡献记录。
     */
    public void deleteAll() {
        jdbcTemplate.execute("TRUNCATE TABLE contributions");
    }

    /**
     * 映射贡献记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 贡献记录
     * @throws SQLException SQL 异常
     */
    private ContributionRecord mapContributionRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ContributionRecord(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("question"),
                resultSet.getString("answer"),
                resultSet.getString("corrections_json"),
                resultSet.getString("confirmed_by"),
                resultSet.getObject("confirmed_at", java.time.OffsetDateTime.class)
        );
    }

    /**
     * 映射 lexical 搜索记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return lexical 搜索记录
     * @throws SQLException SQL 异常
     */
    private LexicalSearchRecord mapLexicalSearchRecord(ResultSet resultSet, int rowNum) throws SQLException {
        String contributionId = String.valueOf(resultSet.getObject("id", java.util.UUID.class));
        String contributionQuestion = resultSet.getString("question");
        return new LexicalSearchRecord(
                null,
                contributionId,
                "contribution:" + contributionId,
                "用户反馈：" + contributionQuestion,
                resultSet.getString("answer"),
                buildMetadataJson(contributionQuestion, resultSet.getString("confirmed_by")),
                List.of("[用户反馈]"),
                null,
                null,
                resultSet.getDouble("score")
        );
    }

    /**
     * 拼接 token 评分表达式。
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters SQL 参数
     * @param queryTokens 查询 token
     * @param columns 参与匹配的列
     * @param weights 列对应权重
     */
    private void appendTokenScore(
            StringBuilder sqlBuilder,
            List<Object> parameters,
            List<String> queryTokens,
            List<String> columns,
            List<Double> weights
    ) {
        for (String queryToken : queryTokens) {
            String pattern = likePattern(queryToken);
            for (int index = 0; index < columns.size(); index++) {
                sqlBuilder.append(" + case when ")
                        .append(columns.get(index))
                        .append(" like ? then ")
                        .append(weights.get(index).doubleValue())
                        .append(" else 0 end\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 拼接 token 过滤条件。
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters SQL 参数
     * @param queryTokens 查询 token
     * @param columns 参与匹配的列
     */
    private void appendTokenWhere(
            StringBuilder sqlBuilder,
            List<Object> parameters,
            List<String> queryTokens,
            List<String> columns
    ) {
        for (String queryToken : queryTokens) {
            String pattern = likePattern(queryToken);
            for (String column : columns) {
                sqlBuilder.append("                  or ").append(column).append(" like ?\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 规范化查询 token。
     *
     * @param queryTokens 原始 token
     * @return 规范化 token
     */
    private List<String> normalizeTokens(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTokens = new ArrayList<String>();
        for (String queryToken : queryTokens) {
            if (hasText(queryToken)) {
                normalizedTokens.add(queryToken.toLowerCase());
            }
        }
        return normalizedTokens;
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
        return "%" + queryToken + "%";
    }

    /**
     * 构建 Contribution 元数据 JSON。
     *
     * @param question 贡献问题
     * @param confirmedBy 确认人
     * @return 元数据 JSON
     */
    private String buildMetadataJson(String question, String confirmedBy) {
        return "{\"question\":\""
                + escapeJson(question)
                + "\",\"confirmedBy\":\""
                + escapeJson(confirmedBy)
                + "\"}";
    }

    /**
     * 转义 JSON 字符串。
     *
     * @param value 原始文本
     * @return 转义后文本
     */
    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
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
