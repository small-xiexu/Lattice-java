package com.xbk.lattice.api.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理侧 usage 查询服务
 *
 * 职责：汇总 llm_usage 表中的总体、用途与模型维度指标
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class AdminUsageQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建管理侧 usage 查询服务。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public AdminUsageQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汇总当前 usage 数据。
     *
     * @return usage 汇总
     */
    public AdminUsageResponse summarize() {
        UsageTotals usageTotals = jdbcTemplate.queryForObject(
                """
                        select count(*) as total_calls,
                               coalesce(sum(input_tokens), 0) as total_input_tokens,
                               coalesce(sum(output_tokens), 0) as total_output_tokens,
                               coalesce(sum(cost_usd), 0) as total_cost_usd
                        from llm_usage
                        """,
                (resultSet, rowNum) -> new UsageTotals(
                        resultSet.getInt("total_calls"),
                        resultSet.getInt("total_input_tokens"),
                        resultSet.getInt("total_output_tokens"),
                        resultSet.getDouble("total_cost_usd")
                )
        );
        List<AdminUsageByPurposeResponse> purposes = jdbcTemplate.query(
                """
                        select purpose,
                               count(*) as call_count,
                               coalesce(sum(input_tokens), 0) as input_tokens,
                               coalesce(sum(output_tokens), 0) as output_tokens,
                               coalesce(sum(cost_usd), 0) as cost_usd
                        from llm_usage
                        group by purpose
                        order by cost_usd desc, purpose asc
                        """,
                (resultSet, rowNum) -> new AdminUsageByPurposeResponse(
                        resultSet.getString("purpose"),
                        resultSet.getInt("call_count"),
                        resultSet.getInt("input_tokens"),
                        resultSet.getInt("output_tokens"),
                        resultSet.getDouble("cost_usd")
                )
        );
        List<AdminUsageByModelResponse> models = jdbcTemplate.query(
                """
                        select model,
                               count(*) as call_count,
                               coalesce(sum(input_tokens), 0) as input_tokens,
                               coalesce(sum(output_tokens), 0) as output_tokens,
                               coalesce(sum(cost_usd), 0) as cost_usd
                        from llm_usage
                        group by model
                        order by cost_usd desc, model asc
                        """,
                (resultSet, rowNum) -> new AdminUsageByModelResponse(
                        resultSet.getString("model"),
                        resultSet.getInt("call_count"),
                        resultSet.getInt("input_tokens"),
                        resultSet.getInt("output_tokens"),
                        resultSet.getDouble("cost_usd")
                )
        );
        UsageTotals safeTotals = usageTotals == null ? new UsageTotals(0, 0, 0, 0.0D) : usageTotals;
        return new AdminUsageResponse(
                safeTotals.getTotalCalls(),
                safeTotals.getTotalInputTokens(),
                safeTotals.getTotalOutputTokens(),
                safeTotals.getTotalCostUsd(),
                purposes,
                models
        );
    }

    /**
     * usage 总计。
     *
     * @param totalCalls 总调用次数
     * @param totalInputTokens 总输入 token
     * @param totalOutputTokens 总输出 token
     * @param totalCostUsd 总成本
     */
    private static class UsageTotals {

        private final int totalCalls;

        private final int totalInputTokens;

        private final int totalOutputTokens;

        private final double totalCostUsd;

        /**
         * 创建 usage 总计。
         *
         * @param totalCalls 总调用次数
         * @param totalInputTokens 总输入 token
         * @param totalOutputTokens 总输出 token
         * @param totalCostUsd 总成本
         */
        private UsageTotals(
                int totalCalls,
                int totalInputTokens,
                int totalOutputTokens,
                double totalCostUsd
        ) {
            this.totalCalls = totalCalls;
            this.totalInputTokens = totalInputTokens;
            this.totalOutputTokens = totalOutputTokens;
            this.totalCostUsd = totalCostUsd;
        }

        /**
         * 获取总调用次数。
         *
         * @return 总调用次数
         */
        private int getTotalCalls() {
            return totalCalls;
        }

        /**
         * 获取总输入 token。
         *
         * @return 总输入 token
         */
        private int getTotalInputTokens() {
            return totalInputTokens;
        }

        /**
         * 获取总输出 token。
         *
         * @return 总输出 token
         */
        private int getTotalOutputTokens() {
            return totalOutputTokens;
        }

        /**
         * 获取总成本。
         *
         * @return 总成本
         */
        private double getTotalCostUsd() {
            return totalCostUsd;
        }
    }
}
