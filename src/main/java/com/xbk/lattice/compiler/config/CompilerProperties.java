package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 编译流程配置
 *
 * 职责：承载 B1 最小编译闭环所需的基础参数
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.compiler")
public class CompilerProperties {

    private int ingestMaxChars = 8192;

    private int batchMaxChars = 40000;

    private String defaultGroup = "defaultGroup";

    private List<GroupingRule> groupingRules = new ArrayList<GroupingRule>();

    /**
     * 获取单文件最大采集字符数。
     *
     * @return 单文件最大采集字符数
     */
    public int getIngestMaxChars() {
        return ingestMaxChars;
    }

    /**
     * 设置单文件最大采集字符数。
     *
     * @param ingestMaxChars 单文件最大采集字符数
     */
    public void setIngestMaxChars(int ingestMaxChars) {
        this.ingestMaxChars = ingestMaxChars;
    }

    /**
     * 获取分批最大字符数。
     *
     * @return 分批最大字符数
     */
    public int getBatchMaxChars() {
        return batchMaxChars;
    }

    /**
     * 设置分批最大字符数。
     *
     * @param batchMaxChars 分批最大字符数
     */
    public void setBatchMaxChars(int batchMaxChars) {
        this.batchMaxChars = batchMaxChars;
    }

    /**
     * 获取默认分组名称。
     *
     * @return 默认分组名称
     */
    public String getDefaultGroup() {
        return defaultGroup;
    }

    /**
     * 设置默认分组名称。
     *
     * @param defaultGroup 默认分组名称
     */
    public void setDefaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    /**
     * 获取显式分组规则。
     *
     * @return 显式分组规则
     */
    public List<GroupingRule> getGroupingRules() {
        return groupingRules;
    }

    /**
     * 设置显式分组规则。
     *
     * @param groupingRules 显式分组规则
     */
    public void setGroupingRules(List<GroupingRule> groupingRules) {
        this.groupingRules = groupingRules;
    }

    /**
     * 分组规则
     *
     * 职责：描述路径模式与 groupKey 的映射
     *
     * @author xiexu
     */
    public static class GroupingRule {

        private String pattern;

        private String groupKey;

        /**
         * 获取匹配模式。
         *
         * @return 匹配模式
         */
        public String getPattern() {
            return pattern;
        }

        /**
         * 设置匹配模式。
         *
         * @param pattern 匹配模式
         */
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        /**
         * 获取分组键。
         *
         * @return 分组键
         */
        public String getGroupKey() {
            return groupKey;
        }

        /**
         * 设置分组键。
         *
         * @param groupKey 分组键
         */
        public void setGroupKey(String groupKey) {
            this.groupKey = groupKey;
        }
    }
}
