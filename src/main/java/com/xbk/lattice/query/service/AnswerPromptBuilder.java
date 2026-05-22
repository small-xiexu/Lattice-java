package com.xbk.lattice.query.service;

import java.util.List;
import java.util.Map;

/**
 * 答案 Prompt 构建器
 *
 * 职责：集中维护查询回答、用户纠正修订与审查重写三类 LLM Prompt 的拼装入口
 *
 * @author xiexu
 */
public class AnswerPromptBuilder {

    private static final String SYSTEM_QUERY_ANSWER = """
            你是 Lattice 查询助手。请基于给定证据回答用户问题。

            输出要求：
            1. 只能输出 JSON，不要输出 Markdown 正文、代码块或解释性前后缀
            2. JSON 结构必须是 {"answerMarkdown":"...","answerOutcome":"SUCCESS|INSUFFICIENT_EVIDENCE|NO_RELEVANT_KNOWLEDGE|PARTIAL_ANSWER","answerCacheable":true|false}
            3. answerMarkdown 字段内部必须是面向最终用户的 Markdown
            4. 每个关键结论段落末尾必须追加至少一个可解析引用
            5. 文章引用格式只能是 [[article-key]] 或 [[article-key|显示标签]]
            6. 源文件引用格式只能是 [→ relative/path/File.java] 或 [→ relative/path/File.java, section]
            7. 优先引用 CONTRIBUTION / FACT_CARD / SOURCE 中直接可证实的信息；FACT_CARD 用于组织结构化事实，最终引用尽量落到对应 SOURCE 原文
            8. 如果信息不足，要明确指出缺口，不要编造；此时 answerOutcome 必须为 INSUFFICIENT_EVIDENCE 或 PARTIAL_ANSWER
            9. 没有相关知识时 answerOutcome 必须为 NO_RELEVANT_KNOWLEDGE，answerCacheable 必须为 false
            10. 只有在 answerOutcome=SUCCESS 且答案可稳定复用时，answerCacheable 才能为 true
            11. 回答语言使用简体中文，保留必要英文术语或原始配置项
            12. 对字段名、状态码、枚举值、配置键、表名、类名、队列名、接口路径、阈值等精确标识类知识，必须原样保留并逐项覆盖，不要概括成“相关字段/若干配置”
            13. 如果问题显式点名多个标识（例如 A、B、C 分别是什么），answerMarkdown 必须逐项回答每个标识；证据缺失时只对缺失项说明缺口
            14. 字段、枚举、状态码、配置值这类查值题优先用表格或逐项列表，并在每个数据行末尾追加可解析引用
            15. 如果证据明确给出了“从旧结论修正为新结论”“X 不适用”“X 与当前接口无关”这类结论，必须优先回答最新修正后的结论，不要改写成第三种未被证实的新说法
            16. 对命中数、接口路径、配置值、枚举值、状态取值、批次顺序、是否一致这类精确查值题，优先使用证据里最贴题的原始事实句，而不是宽泛背景总结
            17. 如果 ARTICLE 证据不够直接，但 FACT_CARD / SOURCE / CONTRIBUTION 证据已经包含精确值或精确结论，应直接使用这些证据，不要轻易回答“证据不足”
            18. 对精确查值 / 精确结论题，answerMarkdown 默认先给 1-2 句直接答案；只有在问题明确要求“展开说明 / 对比明细 / 完整列表”时，才继续补充背景或分点解释
            19. 对显式点名路径、URL、配置键、字段名等标识的问题，不要引入用户未点名且非回答必需的其他标识；证据中的废弃示例或反例可概括为“其他标识”，不要原样复述
            20. 如果证据给出了接口/URL path 对应的 HTTP 方法，回答该 path 时必须同时保留方法和 path，例如 POST /example/path
            21. 数值、金额、比例、公式类问题必须保留证据中的原始算式和未截断数值；如需给常用展示值，可同时给四舍五入值，但不要只输出四舍五入结果
            22. 如果问题显式点名了多个并列焦点（如“分别是什么”“A、B、C 各自含义”“第一批到第六批分别是什么”），answerMarkdown 必须逐项展开覆盖每个焦点；不要把多个焦点压缩成一句总述
            23. 对多点枚举 / 多焦点解释题，只要证据已经覆盖多个点，就优先用逐项列表、表格或分段形式展开；不要仅输出一个概括性摘要句
            """;

    private static final String SYSTEM_QUERY_REVISE = """
            你是 Lattice 查询修订助手。请根据原答案、用户纠正和证据，重生成一份修订后的 Markdown 答案。

            输出要求：
            1. 必须输出 Markdown
            2. 优先采用 CONTRIBUTION 中的用户纠正，其次参考 ARTICLE / SOURCE 证据
            3. 不要简单把纠正文本直接拼接到原答案末尾
            4. 如果纠正与证据冲突，要显式说明冲突点
            5. 回答语言使用简体中文
            """;

    private static final String SYSTEM_QUERY_REWRITE_FROM_REVIEW = """
            你是 Lattice 查询重写助手。你会收到用户问题、当前答案、审查发现的问题以及证据，请输出一份面向最终用户的结构化结果。

            输出要求：
            1. 只能输出 JSON，不要输出 Markdown 正文、代码块或解释性前后缀
            2. JSON 结构必须是 {"answerMarkdown":"...","answerOutcome":"SUCCESS|INSUFFICIENT_EVIDENCE|NO_RELEVANT_KNOWLEDGE|PARTIAL_ANSWER","answerCacheable":true|false}
            3. answerMarkdown 字段内部必须直接输出最终答案，不要复述“审查结论”“修订说明”“问题单”或缺陷列表
            4. 每个关键结论段落末尾必须追加至少一个可解析引用
            5. 文章引用格式只能是 [[article-key]] 或 [[article-key|显示标签]]
            6. 源文件引用格式只能是 [→ relative/path/File.java] 或 [→ relative/path/File.java, section]
            7. 对有证据支撑的内容，给出明确结论，并保留关键阈值、地址、字段名等原始值
            8. 对证据不足或无法确认的子问题，明确写“当前证据不足”或“暂无法确认”
            9. 不要编造，不要输出 TODO，不要把 REVIEW FINDINGS 原样粘贴到 answerMarkdown 中
            10. 只有在 answerOutcome=SUCCESS 且答案可稳定复用时，answerCacheable 才能为 true
            11. 回答语言使用简体中文
            12. 如果证据明确给出了“从旧结论修正为新结论”“X 不适用”“X 与当前接口无关”这类修正信息，必须保留该修正关系，不要另造一个新的精确值
            13. 对命中数、接口路径、配置值、枚举值、状态取值、批次顺序、是否一致这类精确查值题，优先按证据里的贴题事实句逐项作答
            """;

    private final AnswerGenerationService support;

    /**
     * 创建答案 Prompt 构建器。
     *
     * @param support 答案生成支撑逻辑
     */
    public AnswerPromptBuilder(AnswerGenerationService support) {
        this.support = support;
    }

    /**
     * 返回查询回答 system prompt。
     *
     * @return system prompt
     */
    public String systemQueryAnswer() {
        return SYSTEM_QUERY_ANSWER;
    }

    /**
     * 返回答案修订 system prompt。
     *
     * @return system prompt
     */
    public String systemQueryRevise() {
        return SYSTEM_QUERY_REVISE;
    }

    /**
     * 返回审查重写 system prompt。
     *
     * @return system prompt
     */
    public String systemQueryRewriteFromReview() {
        return SYSTEM_QUERY_REWRITE_FROM_REVIEW;
    }

    /**
     * 构建 LLM 查询答案 Prompt。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return 用户提示词
     */
    public String buildAnswerPrompt(String question, List<QueryArticleHit> queryArticleHits) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = support.groupHitsByEvidenceType(queryArticleHits);
        List<String> queryTokens = support.extractQueryTokens(question);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        support.appendReferentialFocusSection(promptBuilder, question);
        appendGroupedEvidence(promptBuilder, question, queryArticleHits, queryTokens, groupedHits);
        return promptBuilder.toString().trim();
    }

    /**
     * 构建 LLM 修订答案 Prompt。
     *
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 用户提示词
     */
    public String buildRevisePrompt(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = support.groupHitsByEvidenceType(queryArticleHits);
        List<String> queryTokens = support.extractQueryTokens(question);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        promptBuilder.append("CURRENT ANSWER").append("\n");
        promptBuilder.append(currentAnswer == null ? "" : currentAnswer.trim()).append("\n\n");
        promptBuilder.append("CORRECTION").append("\n");
        promptBuilder.append(correction == null ? "" : correction.trim()).append("\n\n");
        appendGroupedEvidence(promptBuilder, question, queryArticleHits, queryTokens, groupedHits);
        return promptBuilder.toString().trim();
    }

    /**
     * 构建基于审查问题的最终答案重写 Prompt。
     *
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param reviewFindings 审查问题
     * @param queryArticleHits 修订证据
     * @return 用户提示词
     */
    public String buildReviewRewritePrompt(
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = support.groupHitsByEvidenceType(queryArticleHits);
        List<String> queryTokens = support.extractQueryTokens(question);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        promptBuilder.append("CURRENT ANSWER").append("\n");
        promptBuilder.append(currentAnswer == null ? "" : currentAnswer.trim()).append("\n\n");
        promptBuilder.append("REVIEW FINDINGS").append("\n");
        promptBuilder.append(reviewFindings == null ? "" : reviewFindings.trim()).append("\n\n");
        appendGroupedEvidence(promptBuilder, question, queryArticleHits, queryTokens, groupedHits);
        return promptBuilder.toString().trim();
    }

    /**
     * 按固定顺序追加 Prompt 证据分组。
     *
     * @param promptBuilder Prompt 构建器
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @param queryTokens 查询 token
     * @param groupedHits 已按证据类型分组的命中
     */
    private void appendGroupedEvidence(
            StringBuilder promptBuilder,
            String question,
            List<QueryArticleHit> queryArticleHits,
            List<String> queryTokens,
            Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits
    ) {
        support.appendQuestionFocusedEvidenceSection(promptBuilder, question, queryArticleHits, queryTokens);
        appendEvidenceSection(promptBuilder, "CONTRIBUTION EVIDENCE", QueryEvidenceType.CONTRIBUTION, queryArticleHits, question, queryTokens, groupedHits);
        appendEvidenceSection(promptBuilder, "STRUCTURED FACT CARD EVIDENCE", QueryEvidenceType.FACT_CARD, queryArticleHits, question, queryTokens, groupedHits);
        appendEvidenceSection(promptBuilder, "SOURCE EVIDENCE", QueryEvidenceType.SOURCE, queryArticleHits, question, queryTokens, groupedHits);
        appendEvidenceSection(promptBuilder, "GRAPH EVIDENCE", QueryEvidenceType.GRAPH, queryArticleHits, question, queryTokens, groupedHits);
        appendEvidenceSection(promptBuilder, "ARTICLE EVIDENCE", QueryEvidenceType.ARTICLE, queryArticleHits, question, queryTokens, groupedHits);
    }

    /**
     * 追加单个 Prompt 证据分组。
     *
     * @param promptBuilder Prompt 构建器
     * @param sectionTitle 分组标题
     * @param queryEvidenceType 证据类型
     * @param queryArticleHits 全量命中
     * @param question 查询问题
     * @param queryTokens 查询 token
     * @param groupedHits 已按证据类型分组的命中
     */
    private void appendEvidenceSection(
            StringBuilder promptBuilder,
            String sectionTitle,
            QueryEvidenceType queryEvidenceType,
            List<QueryArticleHit> queryArticleHits,
            String question,
            List<String> queryTokens,
            Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits
    ) {
        List<QueryArticleHit> evidenceHits = groupedHits.get(queryEvidenceType);
        List<QueryArticleHit> sortedHits = support.sortPromptEvidenceHits(question, evidenceHits, queryTokens);
        support.appendEvidenceSection(promptBuilder, sectionTitle, sortedHits, queryArticleHits, question, queryTokens);
    }
}
