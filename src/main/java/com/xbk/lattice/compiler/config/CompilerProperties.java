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

    private int ingestMaxChars = 65536;

    private int batchMaxChars = 40000;

    private String defaultGroup = "defaultGroup";

    private List<GroupingRule> groupingRules = new ArrayList<GroupingRule>();

    private FileRanking fileRanking = new FileRanking();

    private DocumentTopics documentTopics = new DocumentTopics();

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
     * 获取文件优先级排序配置。
     *
     * @return 文件优先级排序配置
     */
    public FileRanking getFileRanking() {
        return fileRanking;
    }

    /**
     * 设置文件优先级排序配置。
     *
     * @param fileRanking 文件优先级排序配置
     */
    public void setFileRanking(FileRanking fileRanking) {
        this.fileRanking = fileRanking;
    }

    /**
     * 获取长文档专题拆分配置。
     *
     * @return 长文档专题拆分配置
     */
    public DocumentTopics getDocumentTopics() {
        return documentTopics;
    }

    /**
     * 设置长文档专题拆分配置。
     *
     * @param documentTopics 长文档专题拆分配置
     */
    public void setDocumentTopics(DocumentTopics documentTopics) {
        this.documentTopics = documentTopics;
    }

    /**
     * 长文档专题拆分配置。
     *
     * 职责：承载文档结构切分的通用阈值，不绑定具体领域
     *
     * @author xiexu
     */
    public static class DocumentTopics {

        private boolean enabled = true;

        private int longDocumentMinChars = 12000;

        private int mediumDocumentMinChars = 6000;

        private int minHeadingsForMediumDocument = 5;

        private int minTopicChars = 700;

        private int maxTopicChars = 22000;

        private int maxSnippetChars = 2400;

        private int maxSectionLines = 80;

        private int maxLineChars = 280;

        private int minHeadingChars = 2;

        private int maxHeadingChars = 90;

        private int maxLayoutHeadingChars = 42;

        private int minLayoutHeadingLetters = 2;

        private int nearbyHeadingLineDistance = 2;

        private int childHeadingMaxLevel = 3;

        private String pageMarkerPattern;

        private List<HeadingPatternRule> headingPatterns = new ArrayList<HeadingPatternRule>();

        private List<String> ignoredLinePrefixes = new ArrayList<String>();

        private List<String> headingTerminalPunctuations = new ArrayList<String>();

        private List<String> bodyTerminalPunctuations = new ArrayList<String>();

        private String headingBoundaryPattern;

        /**
         * 判断是否启用长文档专题拆分。
         *
         * @return 启用返回 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用长文档专题拆分。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取长文档最小字符数。
         *
         * @return 长文档最小字符数
         */
        public int getLongDocumentMinChars() {
            return longDocumentMinChars;
        }

        /**
         * 设置长文档最小字符数。
         *
         * @param longDocumentMinChars 长文档最小字符数
         */
        public void setLongDocumentMinChars(int longDocumentMinChars) {
            this.longDocumentMinChars = longDocumentMinChars;
        }

        /**
         * 获取中等长度结构化文档最小字符数。
         *
         * @return 中等长度结构化文档最小字符数
         */
        public int getMediumDocumentMinChars() {
            return mediumDocumentMinChars;
        }

        /**
         * 设置中等长度结构化文档最小字符数。
         *
         * @param mediumDocumentMinChars 中等长度结构化文档最小字符数
         */
        public void setMediumDocumentMinChars(int mediumDocumentMinChars) {
            this.mediumDocumentMinChars = mediumDocumentMinChars;
        }

        /**
         * 获取中等长度文档触发拆分所需的最少标题数。
         *
         * @return 最少标题数
         */
        public int getMinHeadingsForMediumDocument() {
            return minHeadingsForMediumDocument;
        }

        /**
         * 设置中等长度文档触发拆分所需的最少标题数。
         *
         * @param minHeadingsForMediumDocument 最少标题数
         */
        public void setMinHeadingsForMediumDocument(int minHeadingsForMediumDocument) {
            this.minHeadingsForMediumDocument = minHeadingsForMediumDocument;
        }

        /**
         * 获取专题最小字符数。
         *
         * @return 专题最小字符数
         */
        public int getMinTopicChars() {
            return minTopicChars;
        }

        /**
         * 设置专题最小字符数。
         *
         * @param minTopicChars 专题最小字符数
         */
        public void setMinTopicChars(int minTopicChars) {
            this.minTopicChars = minTopicChars;
        }

        /**
         * 获取专题最大字符数。
         *
         * @return 专题最大字符数
         */
        public int getMaxTopicChars() {
            return maxTopicChars;
        }

        /**
         * 设置专题最大字符数。
         *
         * @param maxTopicChars 专题最大字符数
         */
        public void setMaxTopicChars(int maxTopicChars) {
            this.maxTopicChars = maxTopicChars;
        }

        /**
         * 获取片段最大字符数。
         *
         * @return 片段最大字符数
         */
        public int getMaxSnippetChars() {
            return maxSnippetChars;
        }

        /**
         * 设置片段最大字符数。
         *
         * @param maxSnippetChars 片段最大字符数
         */
        public void setMaxSnippetChars(int maxSnippetChars) {
            this.maxSnippetChars = maxSnippetChars;
        }

        /**
         * 获取章节最大行数。
         *
         * @return 章节最大行数
         */
        public int getMaxSectionLines() {
            return maxSectionLines;
        }

        /**
         * 设置章节最大行数。
         *
         * @param maxSectionLines 章节最大行数
         */
        public void setMaxSectionLines(int maxSectionLines) {
            this.maxSectionLines = maxSectionLines;
        }

        /**
         * 获取单行最大字符数。
         *
         * @return 单行最大字符数
         */
        public int getMaxLineChars() {
            return maxLineChars;
        }

        /**
         * 设置单行最大字符数。
         *
         * @param maxLineChars 单行最大字符数
         */
        public void setMaxLineChars(int maxLineChars) {
            this.maxLineChars = maxLineChars;
        }

        /**
         * 获取标题最小字符数。
         *
         * @return 标题最小字符数
         */
        public int getMinHeadingChars() {
            return minHeadingChars;
        }

        /**
         * 设置标题最小字符数。
         *
         * @param minHeadingChars 标题最小字符数
         */
        public void setMinHeadingChars(int minHeadingChars) {
            this.minHeadingChars = minHeadingChars;
        }

        /**
         * 获取标题最大字符数。
         *
         * @return 标题最大字符数
         */
        public int getMaxHeadingChars() {
            return maxHeadingChars;
        }

        /**
         * 设置标题最大字符数。
         *
         * @param maxHeadingChars 标题最大字符数
         */
        public void setMaxHeadingChars(int maxHeadingChars) {
            this.maxHeadingChars = maxHeadingChars;
        }

        /**
         * 获取版式短标题最大字符数。
         *
         * @return 版式短标题最大字符数
         */
        public int getMaxLayoutHeadingChars() {
            return maxLayoutHeadingChars;
        }

        /**
         * 设置版式短标题最大字符数。
         *
         * @param maxLayoutHeadingChars 版式短标题最大字符数
         */
        public void setMaxLayoutHeadingChars(int maxLayoutHeadingChars) {
            this.maxLayoutHeadingChars = maxLayoutHeadingChars;
        }

        /**
         * 获取版式标题最少有效字符数。
         *
         * @return 最少有效字符数
         */
        public int getMinLayoutHeadingLetters() {
            return minLayoutHeadingLetters;
        }

        /**
         * 设置版式标题最少有效字符数。
         *
         * @param minLayoutHeadingLetters 最少有效字符数
         */
        public void setMinLayoutHeadingLetters(int minLayoutHeadingLetters) {
            this.minLayoutHeadingLetters = minLayoutHeadingLetters;
        }

        /**
         * 获取相邻重复标题判定行距。
         *
         * @return 相邻重复标题判定行距
         */
        public int getNearbyHeadingLineDistance() {
            return nearbyHeadingLineDistance;
        }

        /**
         * 设置相邻重复标题判定行距。
         *
         * @param nearbyHeadingLineDistance 相邻重复标题判定行距
         */
        public void setNearbyHeadingLineDistance(int nearbyHeadingLineDistance) {
            this.nearbyHeadingLineDistance = nearbyHeadingLineDistance;
        }

        /**
         * 获取超大专题下钻允许的最大标题层级。
         *
         * @return 最大标题层级
         */
        public int getChildHeadingMaxLevel() {
            return childHeadingMaxLevel;
        }

        /**
         * 设置超大专题下钻允许的最大标题层级。
         *
         * @param childHeadingMaxLevel 最大标题层级
         */
        public void setChildHeadingMaxLevel(int childHeadingMaxLevel) {
            this.childHeadingMaxLevel = childHeadingMaxLevel;
        }

        /**
         * 获取页码标记识别正则。
         *
         * @return 页码标记识别正则
         */
        public String getPageMarkerPattern() {
            return pageMarkerPattern;
        }

        /**
         * 设置页码标记识别正则。
         *
         * @param pageMarkerPattern 页码标记识别正则
         */
        public void setPageMarkerPattern(String pageMarkerPattern) {
            this.pageMarkerPattern = pageMarkerPattern;
        }

        /**
         * 获取标题识别规则。
         *
         * @return 标题识别规则
         */
        public List<HeadingPatternRule> getHeadingPatterns() {
            return headingPatterns;
        }

        /**
         * 设置标题识别规则。
         *
         * @param headingPatterns 标题识别规则
         */
        public void setHeadingPatterns(List<HeadingPatternRule> headingPatterns) {
            this.headingPatterns = headingPatterns;
        }

        /**
         * 获取忽略行前缀。
         *
         * @return 忽略行前缀
         */
        public List<String> getIgnoredLinePrefixes() {
            return ignoredLinePrefixes;
        }

        /**
         * 设置忽略行前缀。
         *
         * @param ignoredLinePrefixes 忽略行前缀
         */
        public void setIgnoredLinePrefixes(List<String> ignoredLinePrefixes) {
            this.ignoredLinePrefixes = ignoredLinePrefixes;
        }

        /**
         * 获取标题结尾排除标点。
         *
         * @return 标题结尾排除标点
         */
        public List<String> getHeadingTerminalPunctuations() {
            return headingTerminalPunctuations;
        }

        /**
         * 设置标题结尾排除标点。
         *
         * @param headingTerminalPunctuations 标题结尾排除标点
         */
        public void setHeadingTerminalPunctuations(List<String> headingTerminalPunctuations) {
            this.headingTerminalPunctuations = headingTerminalPunctuations;
        }

        /**
         * 获取正文结尾标点。
         *
         * @return 正文结尾标点
         */
        public List<String> getBodyTerminalPunctuations() {
            return bodyTerminalPunctuations;
        }

        /**
         * 设置正文结尾标点。
         *
         * @param bodyTerminalPunctuations 正文结尾标点
         */
        public void setBodyTerminalPunctuations(List<String> bodyTerminalPunctuations) {
            this.bodyTerminalPunctuations = bodyTerminalPunctuations;
        }

        /**
         * 获取标题边界清理正则。
         *
         * @return 标题边界清理正则
         */
        public String getHeadingBoundaryPattern() {
            return headingBoundaryPattern;
        }

        /**
         * 设置标题边界清理正则。
         *
         * @param headingBoundaryPattern 标题边界清理正则
         */
        public void setHeadingBoundaryPattern(String headingBoundaryPattern) {
            this.headingBoundaryPattern = headingBoundaryPattern;
        }
    }

    /**
     * 标题识别规则配置。
     *
     * 职责：描述一类标题行的正则、标题分组和层级计算方式
     *
     * @author xiexu
     */
    public static class HeadingPatternRule {

        private String name;

        private String pattern;

        private int titleGroup = 1;

        private int fixedLevel = 1;

        private int levelGroup = 1;

        private String levelStrategy = "fixed";

        /**
         * 创建空标题识别规则。
         */
        public HeadingPatternRule() {
        }

        /**
         * 创建标题识别规则。
         *
         * @param name 规则名称
         * @param pattern 正则表达式
         * @param titleGroup 标题文本分组
         * @param fixedLevel 固定标题层级
         * @param levelGroup 层级计算分组
         * @param levelStrategy 层级计算策略
         */
        public HeadingPatternRule(
                String name,
                String pattern,
                int titleGroup,
                int fixedLevel,
                int levelGroup,
                String levelStrategy
        ) {
            this.name = name;
            this.pattern = pattern;
            this.titleGroup = titleGroup;
            this.fixedLevel = fixedLevel;
            this.levelGroup = levelGroup;
            this.levelStrategy = levelStrategy;
        }

        /**
         * 获取规则名称。
         *
         * @return 规则名称
         */
        public String getName() {
            return name;
        }

        /**
         * 设置规则名称。
         *
         * @param name 规则名称
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取正则表达式。
         *
         * @return 正则表达式
         */
        public String getPattern() {
            return pattern;
        }

        /**
         * 设置正则表达式。
         *
         * @param pattern 正则表达式
         */
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        /**
         * 获取标题文本分组。
         *
         * @return 标题文本分组
         */
        public int getTitleGroup() {
            return titleGroup;
        }

        /**
         * 设置标题文本分组。
         *
         * @param titleGroup 标题文本分组
         */
        public void setTitleGroup(int titleGroup) {
            this.titleGroup = titleGroup;
        }

        /**
         * 获取固定标题层级。
         *
         * @return 固定标题层级
         */
        public int getFixedLevel() {
            return fixedLevel;
        }

        /**
         * 设置固定标题层级。
         *
         * @param fixedLevel 固定标题层级
         */
        public void setFixedLevel(int fixedLevel) {
            this.fixedLevel = fixedLevel;
        }

        /**
         * 获取层级计算分组。
         *
         * @return 层级计算分组
         */
        public int getLevelGroup() {
            return levelGroup;
        }

        /**
         * 设置层级计算分组。
         *
         * @param levelGroup 层级计算分组
         */
        public void setLevelGroup(int levelGroup) {
            this.levelGroup = levelGroup;
        }

        /**
         * 获取层级计算策略。
         *
         * @return 层级计算策略
         */
        public String getLevelStrategy() {
            return levelStrategy;
        }

        /**
         * 设置层级计算策略。
         *
         * @param levelStrategy 层级计算策略
         */
        public void setLevelStrategy(String levelStrategy) {
            this.levelStrategy = levelStrategy;
        }
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

    /**
     * 文件优先级排序配置。
     *
     * 职责：承载 pattern -> score 的排序覆盖规则
     *
     * @author xiexu
     */
    public static class FileRanking {

        private List<FileRankingRule> rules = new ArrayList<FileRankingRule>();

        public List<FileRankingRule> getRules() {
            return rules;
        }

        public void setRules(List<FileRankingRule> rules) {
            this.rules = rules;
        }
    }

    /**
     * 单条文件优先级规则。
     *
     * 职责：描述 glob 模式与对应优先级分值
     *
     * @author xiexu
     */
    public static class FileRankingRule {

        private String pattern;

        private int score;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }
}
