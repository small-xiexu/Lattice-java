package com.xbk.lattice.query.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 查询语义规则配置
 *
 * 职责：从 config/lattice-query-semantic.yml 加载通用中文语义信号，
 * 供分类器/路由器/规划器统一使用，避免在 Java 主链中硬编码中文关键词。
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.query.semantic")
public class QuerySemanticRules {

    /** 计数信号。用于识别用户意图为统计/计数的 query。 */
    private List<String> countSignals = List.of(
            "多少条", "多少个", "数量", "计数", "几条", "几行", "几条数据", "多少行", "行数"
    );

    /** 对比信号。用于识别用户意图为对比/比较的 query。 */
    private List<String> comparisonSignals = List.of(
            "比较", "对比", "区别", "差异", "有何不同", "有什么不同"
    );

    /** 深度研究触发信号。用于路由到 deep research 流程（排查/调用链/影响分析/原因探究）。 */
    private List<String> deepResearchSignals = List.of(
            "排查", "调用链", "影响", "为什么"
    );

    /** 精确配置标识信号。用于路由到配置查询流程（接口路径/配置项/取值）。 */
    private List<String> configIdentifierSignals = List.of(
            "接口路径", "配置项", "取值为", "在哪里配置", "配置文件", "路径是什么", "什么路径", "配置在哪里"
    );

    /** 顺序/步骤信号。用于识别用户意图为流程/步骤的 query。 */
    private List<String> sequenceSignals = List.of(
            "步骤", "流程", "顺序", "先后", "阶段", "第一步", "第二步", "第三步", "执行顺序", "怎么走", "如何推进",
            "sequence", "step", "steps", "process", "workflow", "timeline"
    );

    /** 架构信号。用于路由到架构分析流程（组件职责/服务边界/技术选型）。 */
    private List<String> architectureSignals = List.of(
            "架构", "消息队列", "解耦", "同步调用", "异步通信", "组件职责",
            "服务边界", "系统设计", "技术选型", "交互方式", "为什么不直接"
    );

    /** 枚举信号。用于识别用户意图为列出/枚举的 query。 */
    private List<String> enumSignals = List.of(
            "有哪些", "哪些", "清单", "列表", "包括什么", "包含什么", "列出", "枚举", "包括哪些", "包含哪些",
            "list", "items", "options", "types", "categories"
    );

    /** 状态信号。用于识别用户意图为查询当前状态/进展的 query。 */
    private List<String> statusSignals = List.of(
            "状态", "现状", "是否启用", "是否生效", "是否完成", "结论", "结果", "进展", "被调整", "调整",
            "修正", "降级", "status", "state", "current", "enabled", "disabled", "done", "pending"
    );

    /** 规则信号。用于识别用户意图为查询规则/约束/政策的 query。 */
    private List<String> policySignals = List.of(
            "规则", "原则", "约束", "要求", "必须", "禁止", "不得", "不允许", "适用范围", "边界",
            "必须遵守", "policy", "rule", "constraint", "requirement", "must", "forbid", "forbidden", "allowed"
    );

    /** 能力/接入信号。用于识别用户意图为查询接入方式的 query。 */
    private List<String> capabilitySignals = List.of(
            "接入方式", "哪些入口", "开发接入", "支持哪些", "集成方式", "接入能力", "入口方式"
    );

    /** 流程/流转信号。用于识别用户意图为查询运行流程的 query。 */
    private List<String> flowSignals = List.of(
            "运行流程", "流程是什么", "流程", "链路", "流转", "怎么走"
    );

    /** 多焦点分隔信号。用于识别 query 中含多个独立子问题（如"和"分隔的多主题）。 */
    private List<String> multiFocusSignals = List.of(
            "和"
    );

    /** 数值意图信号。用于识别 query 中含极值/边界查询意图。 */
    private List<String> numericValueIntentSignals = List.of(
            "多少", "最大", "最小", "最长", "最短", "上限", "下限"
    );

    /** 文档附录/结构标记信号。用于识别文本行是否为附录标记（如"附录A"），在解析时跳过。 */
    private List<String> boilerplateSectionSignals = List.of(
            "附录"
    );

    /**
     * 获取计数信号列表。
     *
     * @return 计数信号
     */
    public List<String> getCountSignals() {
        return countSignals;
    }

    /**
     * 设置计数信号列表。
     *
     * @param countSignals 计数信号
     */
    public void setCountSignals(List<String> countSignals) {
        this.countSignals = countSignals == null ? List.of() : countSignals;
    }

    /**
     * 获取对比信号列表。
     *
     * @return 对比信号
     */
    public List<String> getComparisonSignals() {
        return comparisonSignals;
    }

    /**
     * 设置对比信号列表。
     *
     * @param comparisonSignals 对比信号
     */
    public void setComparisonSignals(List<String> comparisonSignals) {
        this.comparisonSignals = comparisonSignals == null ? List.of() : comparisonSignals;
    }

    /**
     * 获取深度研究触发信号列表。
     *
     * @return 深度研究触发信号
     */
    public List<String> getDeepResearchSignals() {
        return deepResearchSignals;
    }

    /**
     * 设置深度研究触发信号列表。
     *
     * @param deepResearchSignals 深度研究触发信号
     */
    public void setDeepResearchSignals(List<String> deepResearchSignals) {
        this.deepResearchSignals = deepResearchSignals == null ? List.of() : deepResearchSignals;
    }

    /**
     * 获取精确配置标识信号列表。
     *
     * @return 配置标识信号
     */
    public List<String> getConfigIdentifierSignals() {
        return configIdentifierSignals;
    }

    /**
     * 设置精确配置标识信号列表。
     *
     * @param configIdentifierSignals 配置标识信号
     */
    public void setConfigIdentifierSignals(List<String> configIdentifierSignals) {
        this.configIdentifierSignals = configIdentifierSignals == null ? List.of() : configIdentifierSignals;
    }

    /**
     * 获取架构信号列表。
     *
     * @return 架构信号
     */
    public List<String> getArchitectureSignals() {
        return architectureSignals;
    }

    /**
     * 设置架构信号列表。
     *
     * @param architectureSignals 架构信号
     */
    public void setArchitectureSignals(List<String> architectureSignals) {
        this.architectureSignals = architectureSignals == null ? List.of() : architectureSignals;
    }

    /**
     * 判断问题是否包含任意架构信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyArchitectureSignal(String question) {
        return containsAnySignal(question, architectureSignals);
    }

    /**
     * 判断问题是否包含任意计数信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyCountSignal(String question) {
        return containsAnySignal(question, countSignals);
    }

    /**
     * 判断问题是否包含任意对比信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyComparisonSignal(String question) {
        return containsAnySignal(question, comparisonSignals);
    }

    /**
     * 判断问题是否包含任意深度研究触发信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyDeepResearchSignal(String question) {
        return containsAnySignal(question, deepResearchSignals);
    }

    /**
     * 判断问题是否包含任意精确配置标识信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyConfigIdentifierSignal(String question) {
        return containsAnySignal(question, configIdentifierSignals);
    }

    /**
     * 获取顺序信号列表。
     *
     * @return 顺序信号
     */
    public List<String> getSequenceSignals() {
        return sequenceSignals;
    }

    /**
     * 设置顺序信号列表。
     *
     * @param sequenceSignals 顺序信号
     */
    public void setSequenceSignals(List<String> sequenceSignals) {
        this.sequenceSignals = sequenceSignals == null ? List.of() : sequenceSignals;
    }

    /**
     * 获取枚举信号列表。
     *
     * @return 枚举信号
     */
    public List<String> getEnumSignals() {
        return enumSignals;
    }

    /**
     * 设置枚举信号列表。
     *
     * @param enumSignals 枚举信号
     */
    public void setEnumSignals(List<String> enumSignals) {
        this.enumSignals = enumSignals == null ? List.of() : enumSignals;
    }

    /**
     * 获取状态信号列表。
     *
     * @return 状态信号
     */
    public List<String> getStatusSignals() {
        return statusSignals;
    }

    /**
     * 设置状态信号列表。
     *
     * @param statusSignals 状态信号
     */
    public void setStatusSignals(List<String> statusSignals) {
        this.statusSignals = statusSignals == null ? List.of() : statusSignals;
    }

    /**
     * 获取规则信号列表。
     *
     * @return 规则信号
     */
    public List<String> getPolicySignals() {
        return policySignals;
    }

    /**
     * 设置规则信号列表。
     *
     * @param policySignals 规则信号
     */
    public void setPolicySignals(List<String> policySignals) {
        this.policySignals = policySignals == null ? List.of() : policySignals;
    }

    /**
     * 获取能力/接入信号列表。
     *
     * @return 能力信号
     */
    public List<String> getCapabilitySignals() {
        return capabilitySignals;
    }

    /**
     * 设置能力/接入信号列表。
     *
     * @param capabilitySignals 能力信号
     */
    public void setCapabilitySignals(List<String> capabilitySignals) {
        this.capabilitySignals = capabilitySignals == null ? List.of() : capabilitySignals;
    }

    /**
     * 判断问题是否包含任意能力/接入信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyCapabilitySignal(String question) {
        return containsAnySignal(question, capabilitySignals);
    }

    /**
     * 获取流程/流转信号列表。
     *
     * @return 流程信号
     */
    public List<String> getFlowSignals() {
        return flowSignals;
    }

    /**
     * 设置流程/流转信号列表。
     *
     * @param flowSignals 流程信号
     */
    public void setFlowSignals(List<String> flowSignals) {
        this.flowSignals = flowSignals == null ? List.of() : flowSignals;
    }

    /**
     * 判断问题是否包含任意流程/流转信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyFlowSignal(String question) {
        return containsAnySignal(question, flowSignals);
    }

    /**
     * 获取多焦点分隔信号列表。
     *
     * @return 多焦点分隔信号
     */
    public List<String> getMultiFocusSignals() {
        return multiFocusSignals;
    }

    /**
     * 设置多焦点分隔信号列表。
     *
     * @param multiFocusSignals 多焦点分隔信号
     */
    public void setMultiFocusSignals(List<String> multiFocusSignals) {
        this.multiFocusSignals = multiFocusSignals == null ? List.of() : multiFocusSignals;
    }

    /**
     * 判断问题是否包含任意多焦点分隔信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyMultiFocusSeparator(String question) {
        return containsAnySignal(question, multiFocusSignals);
    }

    /**
     * 获取数值意图信号列表。
     *
     * @return 数值意图信号
     */
    public List<String> getNumericValueIntentSignals() {
        return numericValueIntentSignals;
    }

    /**
     * 设置数值意图信号列表。
     *
     * @param numericValueIntentSignals 数值意图信号
     */
    public void setNumericValueIntentSignals(List<String> numericValueIntentSignals) {
        this.numericValueIntentSignals = numericValueIntentSignals == null ? List.of() : numericValueIntentSignals;
    }

    /**
     * 判断问题是否包含任意数值意图信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyNumericValueIntentSignal(String question) {
        return containsAnySignal(question, numericValueIntentSignals);
    }

    /**
     * 获取文档附录/结构标记信号列表。
     *
     * @return 附录信号
     */
    public List<String> getBoilerplateSectionSignals() {
        return boilerplateSectionSignals;
    }

    /**
     * 设置文档附录/结构标记信号列表。
     *
     * @param boilerplateSectionSignals 附录信号
     */
    public void setBoilerplateSectionSignals(List<String> boilerplateSectionSignals) {
        this.boilerplateSectionSignals = boilerplateSectionSignals == null ? List.of() : boilerplateSectionSignals;
    }

    /**
     * 判断文本行是否以任意附录/文档结构标记开头。
     *
     * @param line 候选文本行
     * @return 以标记开头则返回 true
     */
    public boolean startsWithAnyBoilerplateSectionSignal(String line) {
        if (!StringUtils.hasText(line) || boilerplateSectionSignals == null || boilerplateSectionSignals.isEmpty()) {
            return false;
        }
        for (String signal : boilerplateSectionSignals) {
            if (signal != null && !signal.isBlank() && line.startsWith(signal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否包含任意顺序信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnySequenceSignal(String question) {
        return containsAnySignal(question, sequenceSignals);
    }

    /**
     * 判断问题是否包含任意枚举信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyEnumSignal(String question) {
        return containsAnySignal(question, enumSignals);
    }

    /**
     * 判断问题是否包含任意状态信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyStatusSignal(String question) {
        return containsAnySignal(question, statusSignals);
    }

    /**
     * 判断问题是否包含任意规则信号。
     *
     * @param question 查询问题
     * @return 包含则返回 true
     */
    public boolean containsAnyPolicySignal(String question) {
        return containsAnySignal(question, policySignals);
    }

    /**
     * 检查问题文本是否包含信号列表中的任意一个信号。
     *
     * @param question 查询问题
     * @param signals  信号列表
     * @return 包含任意一个则返回 true
     */
    private boolean containsAnySignal(String question, List<String> signals) {
        if (!StringUtils.hasText(question) || signals == null || signals.isEmpty()) {
            return false;
        }
        for (String signal : signals) {
            if (signal != null && !signal.isBlank() && question.contains(signal)) {
                return true;
            }
        }
        return false;
    }
}
