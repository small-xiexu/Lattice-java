package com.xbk.lattice.query.service;

import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最小答案生成服务
 *
 * 职责：基于命中文章生成可读答案
 *
 * @author xiexu
 */
@Service
@Slf4j
public class AnswerGenerationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern ARTICLE_CITATION_PATTERN = Pattern.compile("\\[\\[[^\\]]+]]");

    private static final Pattern SOURCE_CITATION_PATTERN = Pattern.compile("\\[→\\s*[^\\]]+]");

    private static final Pattern EXPLICIT_IDENTIFIER_PATTERN =
            Pattern.compile("`([^`]+)`|(?<![A-Za-z0-9_.=-])([A-Za-z][A-Za-z0-9_.=-]{2,})(?![A-Za-z0-9_.=-])");

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

    private static final String FALLBACK_REASON_LLM_CALL_FAILED = "LLM_CALL_FAILED";

    private static final String FALLBACK_REASON_LLM_OUTPUT_INVALID = "LLM_OUTPUT_INVALID";

    private static final String FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK = "LLM_UNSTRUCTURED_FALLBACK";

    private static final String FALLBACK_REASON_REWRITE_FAILED = "REWRITE_FAILED";

    private static final String FALLBACK_REASON_DETERMINISTIC_EXACT_LOOKUP_PREFERRED = "DETERMINISTIC_EXACT_LOOKUP_PREFERRED";

    private static final int PROMPT_USER_PROMPT_CHAR_LIMIT = 48000;

    private static final int PROMPT_EVIDENCE_SECTION_HIT_LIMIT = 6;

    private static final int PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200;

    private static final int PROMPT_EVIDENCE_METADATA_CHAR_LIMIT = 800;

    private static final String PROMPT_TRUNCATED_SUFFIX = "... [truncated]";

    private final LlmGateway llmGateway;

    /**
     * 创建答案生成服务（兼容无 LLM 的测试场景）。
     */
    public AnswerGenerationService() {
        this.llmGateway = null;
    }

    /**
     * 创建答案生成服务。
     *
     * @param llmGateway LLM 网关
     */
    @Autowired
    public AnswerGenerationService(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * 生成最小答案。
     *
     * @param question 查询问题
     * @param articleHit 文章命中
     * @return 答案
     */
    public String generate(String question, QueryArticleHit articleHit) {
        if (articleHit == null) {
            return "未找到相关知识";
        }

        List<String> queryTokens = extractQueryTokens(question);
        List<String> matchedLines = selectQuestionFocusedFallbackSnippets(
                question,
                articleHit,
                queryTokens,
                desiredStructuredFactCount(question)
        );
        if (matchedLines.isEmpty()) {
            matchedLines = selectMatchedLines(articleHit.getContent(), queryTokens);
        }

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append(articleHit.getTitle());
        if (!matchedLines.isEmpty()) {
            answerBuilder.append("：").append(String.join("；", matchedLines));
            answerBuilder.append(" ").append(resolveCitationLiteral(articleHit));
            return SensitiveTextMasker.mask(answerBuilder.toString());
        }

        String description = extractDescription(articleHit.getMetadataJson());
        if (!description.isEmpty()) {
            answerBuilder.append("：").append(description);
            answerBuilder.append(" ").append(resolveCitationLiteral(articleHit));
            return SensitiveTextMasker.mask(answerBuilder.toString());
        }

        answerBuilder.append("：").append(articleHit.getContent());
        answerBuilder.append(" ").append(resolveCitationLiteral(articleHit));
        return SensitiveTextMasker.mask(answerBuilder.toString());
    }

    /**
     * 基于多路证据生成 Markdown 答案。
     *
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return Markdown 答案
     */
    public String generate(String question, List<QueryArticleHit> queryArticleHits) {
        return generatePayload(
                null,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                question,
                queryArticleHits
        ).getAnswerMarkdown();
    }

    /**
     * 基于多路证据生成 Markdown 答案。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return Markdown 答案
     */
    public String generate(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        return generatePayload(scopeId, scene, agentRole, question, queryArticleHits).getAnswerMarkdown();
    }

    /**
     * 基于多路证据生成结构化答案载荷。
     *
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return 结构化答案载荷
     */
    public QueryAnswerPayload generatePayload(String question, List<QueryArticleHit> queryArticleHits) {
        return generatePayload(
                null,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                question,
                queryArticleHits
        );
    }

    /**
     * 基于当前命中构造确定性 fallback 答案载荷。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return fallback 答案载荷
     */
    public QueryAnswerPayload fallbackPayload(String question, List<QueryArticleHit> queryArticleHits) {
        return buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                null,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.DEGRADED,
                FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
        );
    }

    /**
     * 基于当前命中构造确定性 fallback 答案载荷，并允许保留原有负向 outcome。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @param answerOutcome 期望保留的答案语义
     * @return fallback 答案载荷
     */
    public QueryAnswerPayload fallbackPayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            AnswerOutcome answerOutcome
    ) {
        return buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                answerOutcome,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.DEGRADED,
                FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
        );
    }

    /**
     * 基于多路证据生成结构化答案载荷。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return 结构化答案载荷
     */
    public QueryAnswerPayload generatePayload(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        boolean llmExecutionFailed = false;
        boolean llmOutputInvalid = false;
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return QueryAnswerPayload.ruleBased("未找到相关知识", AnswerOutcome.NO_RELEVANT_KNOWLEDGE);
        }
        if (containsOnlyArticleEvidence(queryArticleHits)) {
            QueryArticleHit articleHit = queryArticleHits.get(0);
            return QueryAnswerPayload.ruleBased(
                    generate(question, articleHit),
                    resolveSingleArticleAnswerOutcome(question, articleHit)
            );
        }

        if (llmGateway != null) {
            try {
                LlmInvocationEnvelope envelope = llmGateway.invokeRawWithScope(
                        scopeId,
                        scene,
                        agentRole,
                        "query-answer-structured",
                        SYSTEM_QUERY_ANSWER,
                        buildAnswerPrompt(question, queryArticleHits)
                );
                String llmAnswer = envelope.getContent();
                QueryAnswerPayload parsedPayload = parseStructuredAnswerPayload(llmAnswer, question, queryArticleHits);
                if (parsedPayload != null) {
                    llmGateway.applyPromptCacheWritePolicy(envelope, resolvePromptCacheWritePolicy(parsedPayload));
                    return parsedPayload;
                }
                llmOutputInvalid = true;
                llmGateway.applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
                QueryAnswerPayload legacyMarkdownPayload = parseLegacyMarkdownAnswerPayload(
                        llmAnswer,
                        question,
                        queryArticleHits
                );
                if (legacyMarkdownPayload != null) {
                    return legacyMarkdownPayload;
                }
                if (canReuseLegacyMarkdownAnswer(llmAnswer)) {
                    return QueryAnswerPayload.fallback(
                            SensitiveTextMasker.mask(llmAnswer.trim()),
                            FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
                    );
                }
            }
            catch (RuntimeException ex) {
                // 查询主链允许在模型失败时降级到可预测 Markdown，避免直接中断用户查询。
                llmExecutionFailed = true;
            }
        }
        if (llmGateway == null) {
            return buildDeterministicFallbackPayload(
                    question,
                    queryArticleHits,
                    null,
                    GenerationMode.RULE_BASED,
                    ModelExecutionStatus.SKIPPED,
                    ""
            );
        }
        return buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                llmExecutionFailed ? ModelExecutionStatus.FAILED : ModelExecutionStatus.DEGRADED,
                llmExecutionFailed ? FALLBACK_REASON_LLM_CALL_FAILED : llmOutputInvalid
                        ? FALLBACK_REASON_LLM_OUTPUT_INVALID
                        : FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
        );
    }

    /**
     * 基于纠正信息重生成修订答案。
     *
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 修订后的 Markdown 答案
     */
    public String revise(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        return revise(
                null,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REWRITE,
                question,
                currentAnswer,
                correction,
                queryArticleHits
        );
    }

    /**
     * 基于纠正信息重生成修订答案。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 修订后的 Markdown 答案
     */
    public String revise(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (llmGateway != null) {
            try {
                String revisePrompt = buildRevisePrompt(question, currentAnswer, correction, queryArticleHits);
                String llmAnswer = llmGateway.generateTextWithScope(
                        scopeId,
                        scene,
                        agentRole,
                        "query-revise",
                        SYSTEM_QUERY_REVISE,
                        revisePrompt
                );
                if (llmAnswer != null && !llmAnswer.isBlank()) {
                    return llmAnswer.trim();
                }
            }
            catch (RuntimeException ex) {
                // 修订阶段沿用确定性 Markdown 兜底，避免用户反馈闭环被外部模型阻塞。
            }
        }
        return buildFallbackRevisionMarkdown(question, currentAnswer, correction, queryArticleHits);
    }

    /**
     * 基于审查问题重写最终答案。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param reviewFindings 审查问题
     * @param queryArticleHits 修订证据
     * @return 面向最终用户的 Markdown 答案
     */
    public String rewriteFromReviewFeedback(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        return rewriteFromReviewPayload(
                scopeId,
                scene,
                agentRole,
                question,
                currentAnswer,
                reviewFindings,
                queryArticleHits
        ).getAnswerMarkdown();
    }

    /**
     * 基于审查问题重写最终答案，并返回结构化载荷。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param reviewFindings 审查问题
     * @param queryArticleHits 修订证据
     * @return 结构化答案载荷
     */
    public QueryAnswerPayload rewriteFromReviewPayload(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        boolean llmExecutionFailed = false;
        boolean llmOutputInvalid = false;
        if (llmGateway != null) {
            try {
                LlmInvocationEnvelope envelope = llmGateway.invokeRawWithScope(
                        scopeId,
                        scene,
                        agentRole,
                        "query-rewrite-from-review-structured",
                        SYSTEM_QUERY_REWRITE_FROM_REVIEW,
                        buildReviewRewritePrompt(question, currentAnswer, reviewFindings, queryArticleHits)
                );
                String llmAnswer = envelope.getContent();
                QueryAnswerPayload parsedPayload = parseStructuredAnswerPayload(llmAnswer, question, queryArticleHits);
                if (parsedPayload != null) {
                    llmGateway.applyPromptCacheWritePolicy(envelope, resolvePromptCacheWritePolicy(parsedPayload));
                    return parsedPayload;
                }
                llmOutputInvalid = true;
                llmGateway.applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
                QueryAnswerPayload legacyMarkdownPayload = parseLegacyMarkdownAnswerPayload(
                        llmAnswer,
                        question,
                        queryArticleHits
                );
                if (legacyMarkdownPayload != null) {
                    return legacyMarkdownPayload;
                }
                if (canReuseLegacyMarkdownAnswer(llmAnswer)) {
                    return QueryAnswerPayload.fallback(
                            llmAnswer.trim(),
                            FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
                    );
                }
            }
            catch (RuntimeException ex) {
                // 审查驱动的重写失败时，降级回基于证据的结构化答案，避免把问题单直接返回给用户。
                llmExecutionFailed = true;
            }
        }
        if (llmGateway == null) {
            return QueryAnswerPayload.ruleBased(buildFallbackMarkdown(question, queryArticleHits), AnswerOutcome.PARTIAL_ANSWER);
        }
        return buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                llmExecutionFailed ? ModelExecutionStatus.FAILED : ModelExecutionStatus.DEGRADED,
                llmExecutionFailed ? FALLBACK_REASON_REWRITE_FAILED : llmOutputInvalid
                        ? FALLBACK_REASON_LLM_OUTPUT_INVALID
                        : FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
        );
    }

    /**
     * 解析结构化问答输出，并收敛为最小答案载荷。
     *
     * @param rawPayload 原始输出
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 结构化答案载荷；若无法解析则返回 null
     */
    private QueryAnswerPayload parseStructuredAnswerPayload(
            String rawPayload,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        JsonNode payloadNode = tryReadStructuredPayload(rawPayload);
        if (payloadNode == null) {
            return null;
        }
        String answerMarkdown = readText(payloadNode, "answerMarkdown");
        AnswerOutcome answerOutcome = readAnswerOutcome(payloadNode, "answerOutcome");
        if (answerMarkdown == null || answerMarkdown.isBlank() || answerOutcome == null) {
            return null;
        }
        answerMarkdown = normalizeStructuredAnswerMarkdown(answerMarkdown, question, queryArticleHits);
        answerMarkdown = compressStructuredExactLookupAnswer(answerMarkdown, question);
        answerOutcome = normalizeStructuredAnswerOutcome(answerOutcome, answerMarkdown, question, queryArticleHits);
        if (!containsCitationLiteral(answerMarkdown)) {
            return null;
        }
        boolean answerCacheable = readBoolean(payloadNode, "answerCacheable");
        if (answerOutcome != AnswerOutcome.SUCCESS) {
            answerCacheable = false;
        }
        QueryAnswerPayload answerPayload = QueryAnswerPayload.llm(
                SensitiveTextMasker.mask(answerMarkdown.trim()),
                answerOutcome,
                answerCacheable
        );
        return preferDeterministicExactLookupPayload(question, queryArticleHits, answerPayload);
    }

    /**
     * 规范化结构化模型答案，消除过度保守提示，并把缺失或错位 citation 归位到最贴近当前行的证据。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 规范化后的答案
     */
    private String normalizeStructuredAnswerMarkdown(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        String normalizedAnswer = removeOvercautiousEvidenceCaveats(answerMarkdown, question);
        normalizedAnswer = removeUnrequestedPathExamples(normalizedAnswer, question);
        normalizedAnswer = ensureRequestedPathHttpMethods(normalizedAnswer, question, queryArticleHits);
        return attachDefaultCitationWhenMissing(normalizedAnswer, question, queryArticleHits);
    }

    /**
     * 当模型已经给出可支撑的操作/枚举清单时，把过度保守的 PARTIAL 口径收敛为 SUCCESS。
     *
     * @param answerOutcome 模型声明的答案语义
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 规范化后的答案语义
     */
    private AnswerOutcome normalizeStructuredAnswerOutcome(
            AnswerOutcome answerOutcome,
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (answerOutcome != AnswerOutcome.PARTIAL_ANSWER) {
            return answerOutcome;
        }
        String normalizedAnswer = lowerCase(answerMarkdown);
        if (normalizedAnswer.contains("当前证据不足") || normalizedAnswer.contains("暂无法确认")) {
            return answerOutcome;
        }
        if (looksLikeExactLookupQuestion(question)) {
            List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
            if (!fallbackHits.isEmpty()
                    && isDirectFallbackAnswerable(question, fallbackHits.get(0))
                    && coversExactLookupAnswerText(answerMarkdown, question)
                    && isExactLookupAnswerGroundedByFocusedEvidence(question, fallbackHits, answerMarkdown)) {
                return AnswerOutcome.SUCCESS;
            }
        }
        if (!looksLikeEnumerationQuestion(question) && !looksLikeFlowQuestion(question)) {
            return answerOutcome;
        }
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        if (fallbackHits.isEmpty()) {
            return answerOutcome;
        }
        return isDirectFallbackAnswerable(question, fallbackHits.get(0))
                ? AnswerOutcome.SUCCESS
                : answerOutcome;
    }

    /**
     * 移除“没有某个 Runbook/材料名称”这类不影响事实回答的过度保守开场。
     *
     * @param answerMarkdown 模型答案
     * @return 收敛后的答案
     */
    private String removeOvercautiousEvidenceCaveats(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        String normalizedAnswer = answerMarkdown
                .replaceAll("现有证据没有给出一份明确的“[^”]+Runbook”，因此只能基于已知的 ([^；]+) 整理维护动作；以下为证据可支持的部分答案。", "基于已知的 $1，维护动作如下。")
                .replaceAll("基于现有证据，\\*\\*没有看到一份明确命名为“[^”]+”的材料\\*\\*；因此只能按", "根据")
                .replaceAll("基于现有证据，没有看到一份明确命名为“[^”]+”的材料；因此只能按", "根据")
                .replaceAll("根据现有资料，未看到一份明确命名为“[^”]+”的运维 SOP；只能从\\s*([^。]+?)\\s*推导出需要重点维护/关注的动作。因此以下为\\*\\*基于证据的部分答案\\*\\*。", "根据 $1，维护动作如下。")
                .replaceAll("根据现有资料，未看到一份明确命名为“[^”]+”的材料；只能从\\s*([^。]+?)\\s*推导出答案。因此以下为\\*\\*基于证据的部分答案\\*\\*。", "根据 $1，答案如下。")
                .replaceAll("证据中没有一份明确命名为“[^”]+”的材料；以下是基于", "以下基于")
                .replaceAll("证据里没有直接给出“[^”]+”；因此只能按现有", "根据现有")
                .replace("以下为证据可支持的部分答案。", "维护动作如下。")
                .replace("以下为**基于证据的部分答案**。", "答案如下。")
                .replace("，因此属于部分回答。", "。")
                .replace("；因此属于部分回答。", "。");
        normalizedAnswer = removeOvercautiousIntroParagraph(normalizedAnswer, question);
        normalizedAnswer = removeOvercautiousGapSection(normalizedAnswer, question);
        return removeUnsupportedDetailCaveatParagraphs(normalizedAnswer, question);
    }

    /**
     * 对显式 path 题补齐证据里给出的 HTTP 方法。
     *
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 补齐后的答案正文
     */
    private String ensureRequestedPathHttpMethods(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        List<String> requestedPaths = extractEvidencePaths(List.of(question));
        if (requestedPaths.isEmpty()) {
            return answerMarkdown;
        }
        String patchedAnswer = answerMarkdown;
        for (String requestedPath : requestedPaths) {
            String httpMethod = findHttpMethodForPath(requestedPath, queryArticleHits);
            if (httpMethod.isBlank() || containsHttpMethodPath(patchedAnswer, httpMethod, requestedPath)) {
                continue;
            }
            patchedAnswer = patchFirstRequestedPathWithMethod(patchedAnswer, requestedPath, httpMethod);
        }
        return patchedAnswer;
    }

    /**
     * 在证据中查找指定 path 对应的 HTTP 方法。
     *
     * @param requestedPath 被询问的 path
     * @param queryArticleHits 查询命中
     * @return HTTP 方法；无命中返回空串
     */
    private String findHttpMethodForPath(String requestedPath, List<QueryArticleHit> queryArticleHits) {
        if (requestedPath == null || requestedPath.isBlank() || queryArticleHits == null || queryArticleHits.isEmpty()) {
            return "";
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            String content = queryArticleHit == null ? "" : queryArticleHit.getContent();
            String httpMethod = findHttpMethodForPathInText(requestedPath, content);
            if (!httpMethod.isBlank()) {
                return httpMethod;
            }
        }
        return "";
    }

    /**
     * 在文本中查找 path 附近的 HTTP 方法。
     *
     * @param requestedPath 被询问的 path
     * @param text 证据文本
     * @return HTTP 方法；无命中返回空串
     */
    private String findHttpMethodForPathInText(String requestedPath, String text) {
        if (requestedPath == null || requestedPath.isBlank() || text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (!lowerCase(line).contains(lowerCase(requestedPath))) {
                continue;
            }
            String httpMethod = extractHttpMethod(line);
            if (!httpMethod.isBlank()) {
                return httpMethod;
            }
        }
        int pathIndex = lowerCase(text).indexOf(lowerCase(requestedPath));
        if (pathIndex < 0) {
            return "";
        }
        int startIndex = Math.max(0, pathIndex - 80);
        int endIndex = Math.min(text.length(), pathIndex + requestedPath.length() + 80);
        return extractHttpMethod(text.substring(startIndex, endIndex));
    }

    /**
     * 从片段中提取 HTTP 方法。
     *
     * @param text 文本片段
     * @return HTTP 方法；无命中返回空串
     */
    private String extractHttpMethod(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern.compile("(?i)(?<![A-Za-z])(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)(?![A-Za-z])")
                .matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    /**
     * 判断答案是否已经包含 method + path。
     *
     * @param answerMarkdown 答案正文
     * @param httpMethod HTTP 方法
     * @param requestedPath 被询问的 path
     * @return 已包含返回 true
     */
    private boolean containsHttpMethodPath(String answerMarkdown, String httpMethod, String requestedPath) {
        String normalizedAnswer = lowerCase(answerMarkdown);
        String normalizedMethodPath = lowerCase(httpMethod + " " + requestedPath);
        return normalizedAnswer.contains(normalizedMethodPath);
    }

    /**
     * 把答案中第一次出现的 path 补成 method + path。
     *
     * @param answerMarkdown 答案正文
     * @param requestedPath 被询问的 path
     * @param httpMethod HTTP 方法
     * @return 补齐后的答案正文
     */
    private String patchFirstRequestedPathWithMethod(
            String answerMarkdown,
            String requestedPath,
            String httpMethod
    ) {
        String normalizedAnswer = lowerCase(answerMarkdown);
        String normalizedPath = lowerCase(requestedPath);
        int pathIndex = normalizedAnswer.indexOf(normalizedPath);
        if (pathIndex < 0) {
            return answerMarkdown;
        }
        int methodStartIndex = Math.max(0, pathIndex - httpMethod.length() - 8);
        String nearbyPrefix = lowerCase(answerMarkdown.substring(methodStartIndex, pathIndex));
        if (nearbyPrefix.contains(lowerCase(httpMethod))) {
            return answerMarkdown;
        }
        return answerMarkdown.substring(0, pathIndex)
                + httpMethod
                + " "
                + answerMarkdown.substring(pathIndex);
    }

    /**
     * 对精确查值题收敛结构化答案，只保留直接答案段与最必要的补充段。
     *
     * <p>该规则只依赖通用题型特征，不绑定任何业务词、文档名或资料域。</p>
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @return 收敛后的答案
     */
    private String compressStructuredExactLookupAnswer(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank() || !looksLikeExactLookupQuestion(question)) {
            return answerMarkdown;
        }
        if (looksLikeComparisonQuestion(question) || looksLikeFlowQuestion(question)) {
            return answerMarkdown;
        }
        if (questionExplicitlyRequestsExpandedDetail(question)) {
            return answerMarkdown;
        }

        String[] rawParagraphs = answerMarkdown.split("\\n\\s*\\n", -1);
        if (rawParagraphs.length <= 1) {
            return answerMarkdown;
        }
        List<String> keptParagraphs = new ArrayList<String>();
        for (String rawParagraph : rawParagraphs) {
            String normalizedParagraph = rawParagraph == null ? "" : rawParagraph.trim();
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            if (isSummaryHeadingParagraph(normalizedParagraph)) {
                keptParagraphs.add(rawParagraph);
                continue;
            }
            if (keptParagraphs.isEmpty()) {
                keptParagraphs.add(rawParagraph);
                continue;
            }
            if (keptParagraphs.size() == 1 && looksLikeDirectAnswerParagraph(normalizedParagraph, question)) {
                keptParagraphs.add(rawParagraph);
                continue;
            }
            break;
        }
        if (keptParagraphs.size() >= 2) {
            String lastParagraph = keptParagraphs.get(keptParagraphs.size() - 1);
            if (looksLikeDanglingLeadInParagraph(lastParagraph)) {
                keptParagraphs.remove(keptParagraphs.size() - 1);
            }
        }
        String compactAnswer = String.join("\n\n", keptParagraphs).trim();
        if (compactAnswer.isBlank() || !containsCitationLiteral(compactAnswer)) {
            return answerMarkdown;
        }
        return compactAnswer;
    }

    /**
     * 判断问题是否明确要求展开细节。
     *
     * @param question 用户问题
     * @return 明确要求展开时返回 true
     */
    private boolean questionExplicitlyRequestsExpandedDetail(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("详细")
                || normalizedQuestion.contains("展开")
                || normalizedQuestion.contains("说明原因")
                || normalizedQuestion.contains("为什么")
                || normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("完整")
                || normalizedQuestion.contains("列出")
                || normalizedQuestion.contains("明细");
    }

    /**
     * 判断段落是否是概览性标题段。
     *
     * @param paragraph 段落
     * @return 概览段返回 true
     */
    private boolean isSummaryHeadingParagraph(String paragraph) {
        return paragraph.startsWith("## ")
                && (paragraph.contains("答案")
                || paragraph.contains("结论")
                || paragraph.contains("承接方")
                || paragraph.contains("链路分析"));
    }

    /**
     * 判断段落是否更像直接回答段。
     *
     * @param paragraph 段落
     * @param question 用户问题
     * @return 直接回答段返回 true
     */
    private boolean looksLikeDirectAnswerParagraph(String paragraph, String question) {
        String normalizedParagraph = lowerCase(stripEmbeddedCitationLiterals(paragraph));
        if (normalizedParagraph.startsWith("## ")) {
            return true;
        }
        if (normalizedParagraph.startsWith("- ")
                || normalizedParagraph.startsWith("* ")
                || normalizedParagraph.matches("^\\d+\\..*")) {
            return false;
        }
        if (normalizedParagraph.contains("|---|")) {
            return false;
        }
        if (looksLikeEnumerationQuestion(question) && countMarkdownListItems(paragraph) >= 2) {
            return false;
        }
        return normalizedParagraph.length() <= 220;
    }

    /**
     * 判断段落是否只是一个没有后续明细的引导句。
     *
     * @param paragraph 段落
     * @return 悬空引导句返回 true
     */
    private boolean looksLikeDanglingLeadInParagraph(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        String normalizedParagraph = lowerCase(stripEmbeddedCitationLiterals(paragraph)).trim();
        return normalizedParagraph.endsWith("如下：")
                || normalizedParagraph.endsWith("如下:")
                || normalizedParagraph.endsWith("如下。")
                || normalizedParagraph.contains("具体如下")
                || normalizedParagraph.contains("明细如下")
                || normalizedParagraph.contains("列表如下")
                || normalizedParagraph.contains("包括如下");
    }

    private int countMarkdownListItems(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String rawLine : markdown.split("\\R")) {
            String normalizedLine = rawLine.trim();
            if (normalizedLine.startsWith("- ")
                    || normalizedLine.startsWith("* ")
                    || normalizedLine.matches("^\\d+\\..*")) {
                count++;
            }
        }
        return count;
    }

    /**
     * 已有后续答案时，移除开头“没有 SOP / 证据不足”的保守引言段。
     *
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @return 清理后的答案正文
     */
    private String removeOvercautiousIntroParagraph(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        if (!looksLikeAnsweredScopeQuestion(question) || asksForLowLevelInterfaceDetails(question)) {
            return answerMarkdown;
        }
        String[] rawParagraphs = answerMarkdown.split("\\n\\s*\\n", -1);
        if (rawParagraphs.length <= 1 || !isUnsupportedIntroCaveatParagraph(rawParagraphs[0])) {
            return answerMarkdown;
        }
        List<String> retainedParagraphs = new ArrayList<String>();
        for (int index = 1; index < rawParagraphs.length; index++) {
            retainedParagraphs.add(rawParagraphs[index]);
        }
        String candidate = String.join("\n\n", retainedParagraphs).trim();
        if (candidate.isBlank() || !containsCitationLiteral(candidate) || candidate.length() < 40) {
            return answerMarkdown;
        }
        return candidate;
    }

    /**
     * 判断段落是否只是过度保守的开场。
     *
     * @param paragraph 候选段落
     * @return 是保守开场返回 true
     */
    private boolean isUnsupportedIntroCaveatParagraph(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        String normalizedParagraph = lowerCase(stripEmbeddedCitationLiterals(paragraph));
        boolean hasInsufficientSignal = normalizedParagraph.contains("证据中没有")
                || normalizedParagraph.contains("没有直接给出")
                || normalizedParagraph.contains("证据不足")
                || normalizedParagraph.contains("未提供");
        if (!hasInsufficientSignal) {
            return false;
        }
        return normalizedParagraph.contains("以下")
                || normalizedParagraph.contains("只能基于")
                || normalizedParagraph.contains("维护动作清单")
                || normalizedParagraph.contains("动作清单");
    }

    /**
     * 已经给出可支撑清单时，移除末尾“证据缺口”保守段。
     *
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @return 清理后的答案正文
     */
    private String removeOvercautiousGapSection(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        if (!looksLikeAnsweredScopeQuestion(question) || asksForLowLevelInterfaceDetails(question)) {
            return answerMarkdown;
        }
        String[] rawLines = answerMarkdown.split("\\R", -1);
        List<String> retainedLines = new ArrayList<String>();
        boolean skippingGapSection = false;
        boolean removed = false;
        for (String rawLine : rawLines) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.startsWith("## ")
                    && (normalizedLine.contains("证据缺口") || normalizedLine.contains("证据不足"))) {
                skippingGapSection = true;
                removed = true;
                continue;
            }
            if (skippingGapSection && normalizedLine.startsWith("## ")) {
                skippingGapSection = false;
            }
            if (!skippingGapSection) {
                retainedLines.add(rawLine);
            }
        }
        if (!removed) {
            return answerMarkdown;
        }
        String candidate = String.join("\n", retainedLines).trim();
        if (candidate.isBlank() || !containsCitationLiteral(candidate) || candidate.length() < 40) {
            return answerMarkdown;
        }
        return candidate;
    }

    private String removeUnsupportedDetailCaveatParagraphs(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        if (!looksLikeAnsweredScopeQuestion(question) || asksForLowLevelInterfaceDetails(question)) {
            return answerMarkdown;
        }
        String[] rawParagraphs = answerMarkdown.split("\\n\\s*\\n", -1);
        if (rawParagraphs.length <= 1) {
            return answerMarkdown;
        }
        List<String> keptParagraphs = new ArrayList<String>();
        boolean removed = false;
        for (String rawParagraph : rawParagraphs) {
            if (isUnsupportedDetailCaveatParagraph(rawParagraph)
                    || isUnrequestedAnchorCaveatParagraph(rawParagraph, question, answerMarkdown)) {
                removed = true;
                continue;
            }
            keptParagraphs.add(rawParagraph);
        }
        if (!removed) {
            return answerMarkdown;
        }
        String candidate = String.join("\n\n", keptParagraphs).trim();
        if (candidate.isBlank() || !containsCitationLiteral(candidate) || candidate.length() < 40) {
            return answerMarkdown;
        }
        return candidate;
    }

    /**
     * 判断段落是否是未被问题询问的额外锚点缺口说明。
     *
     * @param paragraph 候选段落
     * @param question 用户问题
     * @param answerMarkdown 完整答案
     * @return 是额外缺口说明返回 true
     */
    private boolean isUnrequestedAnchorCaveatParagraph(String paragraph, String question, String answerMarkdown) {
        if (!hasUnsupportedEvidenceSignal(paragraph)) {
            return false;
        }
        List<String> questionAnchors = extractReusableQuestionAnchors(question);
        if (questionAnchors.isEmpty()) {
            return false;
        }
        List<String> paragraphAnchors = extractReusableQuestionAnchors(paragraph);
        if (!containsAnchorOutsideQuestion(paragraphAnchors, questionAnchors)) {
            return false;
        }
        String candidateAnswer = answerMarkdown.replace(paragraph, "").trim();
        return coversRequestedQuestionAnchors(candidateAnswer, question)
                || countEnumerationFactLines(candidateAnswer) >= 2;
    }

    /**
     * 判断候选锚点中是否存在问题未点名的额外锚点。
     *
     * @param candidateAnchors 候选段落锚点
     * @param questionAnchors 问题锚点
     * @return 存在返回 true
     */
    private boolean containsAnchorOutsideQuestion(List<String> candidateAnchors, List<String> questionAnchors) {
        for (String candidateAnchor : candidateAnchors) {
            if (!questionAnchors.contains(candidateAnchor)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeAnsweredScopeQuestion(String question) {
        return looksLikeEnumerationQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeComparisonQuestion(question);
    }

    private boolean asksForLowLevelInterfaceDetails(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("报文")
                || normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("参数")
                || normalizedQuestion.contains("请求")
                || normalizedQuestion.contains("响应")
                || normalizedQuestion.contains("url")
                || normalizedQuestion.contains("endpoint");
    }

    private boolean isUnsupportedDetailCaveatParagraph(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        String normalizedParagraph = lowerCase(stripEmbeddedCitationLiterals(paragraph));
        if (!hasUnsupportedEvidenceSignal(normalizedParagraph)) {
            return false;
        }
        return normalizedParagraph.contains("以上证据")
                || normalizedParagraph.contains("证据中只明确")
                || normalizedParagraph.contains("资料只说明")
                || normalizedParagraph.contains("这些细节")
                || normalizedParagraph.contains("具体报文")
                || normalizedParagraph.contains("接口字段级差异")
                || normalizedParagraph.contains("接口协议")
                || normalizedParagraph.contains("接口 url")
                || normalizedParagraph.contains("接口地址")
                || normalizedParagraph.contains("请求字段")
                || normalizedParagraph.contains("响应字段")
                || normalizedParagraph.contains("http/api 参数")
                || normalizedParagraph.contains("返回码")
                || normalizedParagraph.contains("错误码")
                || normalizedParagraph.contains("签名方式")
                || normalizedParagraph.contains("幂等键")
                || normalizedParagraph.contains("请求/响应参数")
                || normalizedParagraph.contains("请求参数")
                || normalizedParagraph.contains("响应参数");
    }

    /**
     * 判断段落是否包含证据不足类信号。
     *
     * @param paragraph 候选段落
     * @return 包含返回 true
     */
    private boolean hasUnsupportedEvidenceSignal(String paragraph) {
        String normalizedParagraph = lowerCase(stripEmbeddedCitationLiterals(paragraph));
        return normalizedParagraph.contains("未提供")
                || normalizedParagraph.contains("没有提供")
                || normalizedParagraph.contains("未给出")
                || normalizedParagraph.contains("未覆盖")
                || normalizedParagraph.contains("证据不足")
                || normalizedParagraph.contains("当前证据不足")
                || normalizedParagraph.contains("暂无法确认")
                || normalizedParagraph.contains("无法从当前证据确认")
                || normalizedParagraph.contains("无法基于当前材料确认");
    }

    /**
     * 当模型返回了结构化 JSON 但部分正文行缺少 citation 时，使用当前最相关证据补上默认引用。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 至少尝试补过引用的答案
     */
    private String attachDefaultCitationWhenMissing(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        String defaultCitation = defaultCitationLiteral(question, queryArticleHits);
        if (defaultCitation.isBlank() && (queryArticleHits == null || queryArticleHits.isEmpty())) {
            return answerMarkdown;
        }
        String[] rawLines = answerMarkdown.split("\\R", -1);
        List<String> citedLines = new ArrayList<String>();
        boolean citationAttached = false;
        for (int index = 0; index < rawLines.length; index++) {
            String rawLine = rawLines[index];
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (looksLikeMarkdownTableHeaderLine(rawLines, index)) {
                citedLines.add(rawLine);
                continue;
            }
            if (!shouldAutoAttachCitation(normalizedLine)) {
                citedLines.add(rawLine);
                continue;
            }
            String lineCitation = bestCitationLiteralForLine(question, normalizedLine, queryArticleHits, defaultCitation);
            String supportedLine = removeEvidenceInsufficientMarkerIfSupported(
                    rawLine,
                    question,
                    normalizedLine,
                    queryArticleHits
            );
            if (lineCitation.isBlank()) {
                citedLines.add(supportedLine);
                continue;
            }
            if (containsCitationLiteral(normalizedLine) && looksLikeGenericCitationCarrierLine(normalizedLine)) {
                citedLines.add(stripEmbeddedCitationLiterals(supportedLine));
                citationAttached = true;
                continue;
            }
            if (!containsCitationLiteral(normalizedLine)) {
                citedLines.add(appendCitationToLine(supportedLine, normalizedLine, lineCitation));
                citationAttached = true;
                continue;
            }
            if (!hasRelevantCitationForLine(question, normalizedLine, queryArticleHits)) {
                String lineWithoutCitation = replaceLineCitations(supportedLine, lineCitation);
                citedLines.add(lineWithoutCitation);
                citationAttached = true;
            }
            else {
                citedLines.add(supportedLine);
            }
        }
        String citedAnswer = String.join("\n", citedLines).trim();
        if (!citationAttached && !containsCitationLiteral(answerMarkdown)) {
            return answerMarkdown.trim() + " " + defaultCitation;
        }
        return citedAnswer;
    }

    private boolean looksLikeGenericCitationCarrierLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lineWithoutCitations = lowerCase(stripEmbeddedCitationLiterals(normalizedLine));
        return lineWithoutCitations.matches("^(简表|表格|对比表|总结表)(如下|如下所示)?[:：。]?$")
                || lineWithoutCitations.matches("^下面(用|以)?(简表|表格|对比表)(说明|展示)?[:：。]?$");
    }

    /**
     * 如果“当前证据不足”所在行实际能由检索证据支撑，则去掉该标记。
     *
     * @param rawLine 原始行
     * @param question 用户问题
     * @param normalizedLine 归一化行
     * @param queryArticleHits 查询命中
     * @return 去标记后的行
     */
    private String removeEvidenceInsufficientMarkerIfSupported(
            String rawLine,
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (rawLine == null || !rawLine.contains("当前证据不足")) {
            return rawLine;
        }
        int bestScore = bestCitationScoreForLine(question, normalizedLine, queryArticleHits);
        if (bestScore < 8) {
            return rawLine;
        }
        return rawLine
                .replace("（当前证据不足）", "")
                .replace("(当前证据不足)", "")
                .replace("当前证据不足", "")
                .replaceAll("\\s+([，。；：])", "$1");
    }

    /**
     * 判断当前行现有 citation 是否已经指向能支撑该行的证据。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化行
     * @param queryArticleHits 查询命中
     * @return 已有 citation 可用返回 true
     */
    private boolean hasRelevantCitationForLine(
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return false;
        }
        List<String> citationLiterals = extractCitationLiterals(normalizedLine);
        if (citationLiterals.isEmpty()) {
            return false;
        }
        int bestAvailableScore = bestCitationScoreForLine(question, normalizedLine, queryArticleHits);
        for (String citationLiteral : citationLiterals) {
            int citationScore = scoreExistingCitationForLine(question, normalizedLine, citationLiteral, queryArticleHits);
            if (citationScore >= Math.max(4, bestAvailableScore - 8)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前行最适合使用的 citation。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化行
     * @param queryArticleHits 查询命中
     * @param defaultCitation 默认 citation
     * @return 最佳 citation
     */
    private String bestCitationLiteralForLine(
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits,
            String defaultCitation
    ) {
        QueryArticleHit bestHit = bestCitationHitForLine(question, normalizedLine, queryArticleHits);
        if (bestHit == null) {
            return defaultCitation == null ? "" : defaultCitation;
        }
        String citationLiteral = resolveConclusionCitationLiteral(bestHit, queryArticleHits);
        return citationLiteral.isBlank() ? (defaultCitation == null ? "" : defaultCitation) : citationLiteral;
    }

    private int bestCitationScoreForLine(String question, String normalizedLine, List<QueryArticleHit> queryArticleHits) {
        QueryArticleHit bestHit = bestCitationHitForLine(question, normalizedLine, queryArticleHits);
        return bestHit == null ? Integer.MIN_VALUE : scoreCitationLineAgainstHit(question, normalizedLine, bestHit);
    }

    private QueryArticleHit bestCitationHitForLine(
            String question,
            String normalizedLine,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return null;
        }
        QueryArticleHit bestHit = null;
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            int score = scoreCitationLineAgainstHit(question, normalizedLine, queryArticleHit);
            if (score > bestScore) {
                bestScore = score;
                bestHit = queryArticleHit;
            }
        }
        if (bestHit == null || bestScore <= 0) {
            List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
            return fallbackHits.isEmpty() ? null : fallbackHits.get(0);
        }
        return bestHit;
    }

    private int scoreExistingCitationForLine(
            String question,
            String normalizedLine,
            String citationLiteral,
            List<QueryArticleHit> queryArticleHits
    ) {
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (!citationLiteralMatchesHit(citationLiteral, queryArticleHit)) {
                continue;
            }
            bestScore = Math.max(bestScore, scoreCitationLineAgainstHit(question, normalizedLine, queryArticleHit));
        }
        return bestScore;
    }

    private int scoreCitationLineAgainstHit(String question, String normalizedLine, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || normalizedLine == null || normalizedLine.isBlank()) {
            return Integer.MIN_VALUE;
        }
        String claimLine = stripEmbeddedCitationLiterals(normalizedLine)
                .replace("当前证据不足", "")
                .replaceAll("[（(）)]", " ")
                .trim();
        List<String> claimTokens = extractCitationLineTokens(claimLine);
        int score = 0;
        if (claimTokens.isEmpty()) {
            score += QueryEvidenceRelevanceSupport.score(question, queryArticleHit);
        }
        for (String claimToken : claimTokens) {
            int tokenScore = citationTokenWeight(claimToken);
            int matchedScore = 0;
            if (matchesCitationStructuredField(queryArticleHit, claimToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 8);
            }
            if (containsNormalizedToken(queryArticleHit.getContent(), claimToken)) {
                matchedScore = Math.max(matchedScore, tokenScore + 2);
            }
            score += matchedScore;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            score += 2;
        }
        score += Math.min(QueryEvidenceRelevanceSupport.score(question, queryArticleHit), 6);
        return score;
    }

    private List<String> extractCitationLineTokens(String value) {
        List<String> tokens = new ArrayList<String>();
        for (String token : QueryTokenExtractor.extract(value)) {
            String normalizedToken = lowerCase(token);
            if (isUsefulCitationLineToken(normalizedToken) && !tokens.contains(normalizedToken)) {
                tokens.add(normalizedToken);
            }
        }
        return tokens;
    }

    private boolean isUsefulCitationLineToken(String token) {
        if (token == null || token.isBlank() || token.length() <= 1) {
            return false;
        }
        if (List.of(
                "当前",
                "证据",
                "不足",
                "包括",
                "主要",
                "完成",
                "工作",
                "需要",
                "关注",
                "确认",
                "字段",
                "渠道",
                "支持",
                "相关",
                "用户"
        ).contains(token)) {
            return false;
        }
        return true;
    }

    private int citationTokenWeight(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        if (token.matches(".*[0-9=_./-].*")) {
            return 6;
        }
        if (token.matches("[a-z0-9_-]+")) {
            return token.length() >= 6 ? 5 : 4;
        }
        return token.length() >= 3 ? 4 : 2;
    }

    private boolean matchesCitationStructuredField(QueryArticleHit queryArticleHit, String token) {
        if (queryArticleHit == null || token == null || token.isBlank()) {
            return false;
        }
        if (containsNormalizedToken(queryArticleHit.getArticleKey(), token)
                || containsNormalizedToken(queryArticleHit.getConceptId(), token)
                || containsNormalizedToken(queryArticleHit.getTitle(), token)
                || containsNormalizedToken(extractDescription(queryArticleHit.getMetadataJson()), token)) {
            return true;
        }
        if (queryArticleHit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : queryArticleHit.getSourcePaths()) {
            if (containsNormalizedToken(sourcePath, token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNormalizedToken(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return false;
        }
        return lowerCase(value).contains(lowerCase(token));
    }

    private List<String> extractCitationLiterals(String normalizedLine) {
        List<String> citationLiterals = new ArrayList<String>();
        java.util.regex.Matcher articleMatcher = ARTICLE_CITATION_PATTERN.matcher(normalizedLine);
        while (articleMatcher.find()) {
            citationLiterals.add(articleMatcher.group());
        }
        java.util.regex.Matcher sourceMatcher = SOURCE_CITATION_PATTERN.matcher(normalizedLine);
        while (sourceMatcher.find()) {
            citationLiterals.add(sourceMatcher.group());
        }
        return citationLiterals;
    }

    private boolean citationLiteralMatchesHit(String citationLiteral, QueryArticleHit queryArticleHit) {
        if (citationLiteral == null || citationLiteral.isBlank() || queryArticleHit == null) {
            return false;
        }
        String articleTarget = extractArticleCitationTarget(citationLiteral);
        if (!articleTarget.isBlank()
                && (articleTarget.equals(queryArticleHit.getArticleKey())
                || articleTarget.equals(queryArticleHit.getConceptId()))) {
            return true;
        }
        String sourceTarget = extractSourceCitationTarget(citationLiteral);
        if (sourceTarget.isBlank() || queryArticleHit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : queryArticleHit.getSourcePaths()) {
            if (normalizeSourceCitationTarget(sourcePath).equals(sourceTarget)) {
                return true;
            }
        }
        return false;
    }

    private String extractArticleCitationTarget(String citationLiteral) {
        if (citationLiteral == null || !citationLiteral.startsWith("[[") || citationLiteral.startsWith("[[→")) {
            return "";
        }
        int endIndex = citationLiteral.indexOf("]]");
        if (endIndex < 0) {
            return "";
        }
        String target = citationLiteral.substring(2, endIndex);
        int labelIndex = target.indexOf('|');
        if (labelIndex >= 0) {
            target = target.substring(0, labelIndex);
        }
        return target.trim();
    }

    private String extractSourceCitationTarget(String citationLiteral) {
        if (citationLiteral == null || citationLiteral.isBlank()) {
            return "";
        }
        String target = "";
        if (citationLiteral.startsWith("[→")) {
            target = citationLiteral.substring(2, citationLiteral.length() - 1);
        }
        else if (citationLiteral.startsWith("[[→")) {
            target = citationLiteral.substring(3, citationLiteral.length() - 2);
        }
        return normalizeSourceCitationTarget(target);
    }

    private String normalizeSourceCitationTarget(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalizedSourcePath = sourcePath.trim();
        if (normalizedSourcePath.startsWith("[") && normalizedSourcePath.endsWith("]")) {
            normalizedSourcePath = normalizedSourcePath.substring(1, normalizedSourcePath.length() - 1).trim();
        }
        int commaIndex = normalizedSourcePath.indexOf(',');
        if (commaIndex > 0) {
            normalizedSourcePath = normalizedSourcePath.substring(0, commaIndex).trim();
        }
        return normalizedSourcePath;
    }

    private String replaceLineCitations(String rawLine, String citationLiteral) {
        String lineWithoutCitations = stripEmbeddedCitationLiterals(rawLine).trim();
        if (lineWithoutCitations.isBlank()) {
            return rawLine;
        }
        String leadingWhitespace = rawLine == null ? "" : rawLine.replaceFirst("^(\\s*).*$", "$1");
        return leadingWhitespace + lineWithoutCitations + " " + citationLiteral;
    }

    /**
     * 为无引用模型答案选择一个默认引用。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 默认引用文本
     */
    private String defaultCitationLiteral(String question, List<QueryArticleHit> queryArticleHits) {
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String citationLiteral = resolveConclusionCitationLiteral(fallbackHit, queryArticleHits);
            if (!citationLiteral.isBlank()) {
                return citationLiteral;
            }
        }
        if (queryArticleHits != null) {
            for (QueryArticleHit queryArticleHit : queryArticleHits) {
                String citationLiteral = resolveConclusionCitationLiteral(queryArticleHit, queryArticleHits);
                if (!citationLiteral.isBlank()) {
                    return citationLiteral;
                }
            }
        }
        return "";
    }

    /**
     * 判断当前行是否适合自动补默认引用。
     *
     * @param normalizedLine 归一化行
     * @return 适合补引用返回 true
     */
    private boolean shouldAutoAttachCitation(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        if (normalizedLine.startsWith("|")) {
            return looksLikeMarkdownTableDataRow(normalizedLine);
        }
        return !normalizedLine.startsWith("#")
                && !lowerCaseLine.startsWith("```")
                && !lowerCaseLine.startsWith("~~~");
    }

    /**
     * 把 citation 加到普通行或表格数据行上。
     *
     * @param rawLine 原始行
     * @param normalizedLine 归一化行
     * @param citationLiteral 引用字面量
     * @return 补引用后的行
     */
    private String appendCitationToLine(String rawLine, String normalizedLine, String citationLiteral) {
        if (rawLine == null || rawLine.isBlank() || citationLiteral == null || citationLiteral.isBlank()) {
            return rawLine;
        }
        if (!looksLikeMarkdownTableDataRow(normalizedLine)) {
            return rawLine + " " + citationLiteral;
        }
        int lastPipeIndex = rawLine.lastIndexOf('|');
        if (lastPipeIndex <= 0) {
            return rawLine + " " + citationLiteral;
        }
        String beforeLastPipe = rawLine.substring(0, lastPipeIndex).stripTrailing();
        String afterLastPipe = rawLine.substring(lastPipeIndex);
        return beforeLastPipe + " " + citationLiteral + " " + afterLastPipe;
    }

    /**
     * 判断 Markdown 表格行是否是数据行，而不是表头或分隔行。
     *
     * @param normalizedLine 归一化行
     * @return 数据行返回 true
     */
    private boolean looksLikeMarkdownTableDataRow(String normalizedLine) {
        if (normalizedLine == null || !normalizedLine.startsWith("|")) {
            return false;
        }
        String compactLine = normalizedLine.replace("|", "")
                .replace(":", "")
                .replace("-", "")
                .trim();
        if (compactLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return !lowerCaseLine.contains("|---")
                && !lowerCaseLine.contains("| ---")
                && !lowerCaseLine.contains("|:---")
                && !lowerCaseLine.contains("| :---");
    }

    /**
     * 判断当前行是否是 Markdown 表头。
     *
     * @param rawLines 所有行
     * @param index 当前行下标
     * @return 表头返回 true
     */
    private boolean looksLikeMarkdownTableHeaderLine(String[] rawLines, int index) {
        if (rawLines == null || index < 0 || index + 1 >= rawLines.length) {
            return false;
        }
        String currentLine = rawLines[index] == null ? "" : rawLines[index].trim();
        String nextLine = rawLines[index + 1] == null ? "" : rawLines[index + 1].trim();
        return currentLine.startsWith("|") && looksLikeMarkdownTableSeparatorLine(nextLine);
    }

    /**
     * 判断当前行是否是 Markdown 表格分隔行。
     *
     * @param normalizedLine 归一化行
     * @return 分隔行返回 true
     */
    private boolean looksLikeMarkdownTableSeparatorLine(String normalizedLine) {
        if (normalizedLine == null || !normalizedLine.startsWith("|")) {
            return false;
        }
        String compactLine = normalizedLine.replace("|", "")
                .replace(":", "")
                .replace("-", "")
                .trim();
        return compactLine.isBlank();
    }

    /**
     * 尝试把原始输出解析成 JSON 节点。
     *
     * @param rawPayload 原始输出
     * @return JSON 节点
     */
    private JsonNode tryReadStructuredPayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return null;
        }
        JsonNode payloadNode = readJsonNode(rawPayload.trim());
        if (payloadNode != null) {
            return payloadNode;
        }
        String normalizedPayload = stripMarkdownCodeFence(rawPayload.trim());
        if (!normalizedPayload.equals(rawPayload.trim())) {
            payloadNode = readJsonNode(normalizedPayload);
            if (payloadNode != null) {
                return payloadNode;
            }
        }
        String jsonSlice = extractJsonObject(rawPayload);
        if (jsonSlice == null || jsonSlice.isBlank()) {
            return null;
        }
        return readJsonNode(jsonSlice);
    }

    /**
     * 把文本解析为 JSON 节点。
     *
     * @param content 文本内容
     * @return JSON 节点
     */
    private JsonNode readJsonNode(String content) {
        try {
            return OBJECT_MAPPER.readTree(content);
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * 去掉 Markdown 代码块包裹。
     *
     * @param content 文本内容
     * @return 归一化后的文本
     */
    private String stripMarkdownCodeFence(String content) {
        String normalizedContent = content;
        if (normalizedContent.startsWith("```json")) {
            normalizedContent = normalizedContent.substring("```json".length()).trim();
        }
        else if (normalizedContent.startsWith("```")) {
            normalizedContent = normalizedContent.substring("```".length()).trim();
        }
        if (normalizedContent.endsWith("```")) {
            normalizedContent = normalizedContent.substring(0, normalizedContent.length() - 3).trim();
        }
        return normalizedContent;
    }

    /**
     * 从混合文本中提取最外层 JSON 对象。
     *
     * @param content 原始内容
     * @return JSON 文本
     */
    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }

    /**
     * 判断当前输出是否看起来像结构化 JSON。
     *
     * @param rawPayload 原始输出
     * @return 是否像结构化 JSON
     */
    private boolean looksLikeStructuredJson(String rawPayload) {
        String normalizedPayload = rawPayload == null ? "" : rawPayload.trim();
        return normalizedPayload.startsWith("{")
                || normalizedPayload.startsWith("```json")
                || normalizedPayload.contains("\"answerMarkdown\"")
                || normalizedPayload.contains("\"answerOutcome\"");
    }

    /**
     * 判断旧式自由文本答案是否仍可作为 fallback 直接复用。
     *
     * @param rawPayload 原始输出
     * @return 可直接复用返回 true
     */
    private boolean canReuseLegacyMarkdownAnswer(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return false;
        }
        if (looksLikeStructuredJson(rawPayload)) {
            return false;
        }
        return containsCitationLiteral(rawPayload);
    }

    /**
     * 对非 JSON 但已经像完整答案的 Markdown 做温和复用，减少模型偶发未包 JSON 时的主链退化。
     *
     * @param rawPayload 原始模型输出
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 可复用答案；不可复用返回 null
     */
    private QueryAnswerPayload parseLegacyMarkdownAnswerPayload(
            String rawPayload,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (!canReuseUnstructuredMarkdownAsLlmAnswer(rawPayload, question)) {
            return null;
        }
        String normalizedMarkdown = normalizeStructuredAnswerMarkdown(
                stripMarkdownCodeFence(rawPayload.trim()),
                question,
                queryArticleHits
        );
        if (!containsCitationLiteral(normalizedMarkdown)) {
            return null;
        }
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        AnswerOutcome answerOutcome = inferUnstructuredMarkdownAnswerOutcome(question, normalizedMarkdown, fallbackHits);
        return QueryAnswerPayload.llm(SensitiveTextMasker.mask(normalizedMarkdown.trim()), answerOutcome, false);
    }

    /**
     * 根据自由文本答案自身覆盖度和 fallback 证据推导答案语义。
     *
     * @param question 用户问题
     * @param normalizedMarkdown 归一后的 Markdown
     * @param fallbackHits fallback 证据
     * @return 答案语义
     */
    private AnswerOutcome inferUnstructuredMarkdownAnswerOutcome(
            String question,
            String normalizedMarkdown,
            List<QueryArticleHit> fallbackHits
    ) {
        if (coversExactLookupUnstructuredAnswer(normalizedMarkdown, question)) {
            return AnswerOutcome.SUCCESS;
        }
        return inferFallbackEvidenceOutcome(question, fallbackHits);
    }

    /**
     * 判断非结构化 Markdown 是否足够像当前题目的完整答案。
     *
     * @param rawPayload 原始模型输出
     * @param question 用户问题
     * @return 可复用返回 true
     */
    private boolean canReuseUnstructuredMarkdownAsLlmAnswer(String rawPayload, String question) {
        if (rawPayload == null || rawPayload.isBlank() || looksLikeStructuredJson(rawPayload)) {
            return false;
        }
        String normalizedPayload = stripMarkdownCodeFence(rawPayload.trim());
        if (looksLikeModelRefusalOrError(normalizedPayload)) {
            return false;
        }
        if (looksLikeFocusedReferentialDefinitionQuestion(question)) {
            return coversRequestedReferentialIdentifiers(normalizedPayload, question)
                    && countEnumerationFactLines(normalizedPayload) >= 1;
        }
        if (looksLikeExactLookupQuestion(question)
                && (!looksLikeEnumerationQuestion(question) || containsRequestedExactPathIdentifier(question))) {
            return coversExactLookupUnstructuredAnswer(normalizedPayload, question);
        }
        if (looksLikeEnumerationQuestion(question)) {
            return countEnumerationFactLines(normalizedPayload) >= 2
                    || coversRequestedQuestionAnchors(normalizedPayload, question);
        }
        if (looksLikeFlowQuestion(question)) {
            return containsFlowSignal(normalizedPayload);
        }
        if (looksLikeStatusQuestion(question)) {
            return containsStatusSignal(lowerCase(normalizedPayload));
        }
        return false;
    }

    /**
     * 判断自由文本是否已经覆盖精确查值题的关键标识和问题维度。
     *
     * @param markdown 模型 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    private boolean coversExactLookupUnstructuredAnswer(String markdown, String question) {
        if (!containsSourceCitationLiteral(markdown)) {
            return false;
        }
        return coversExactLookupAnswerText(markdown, question);
    }

    /**
     * 判断答案正文是否覆盖精确查值题的关键标识和问题维度。
     *
     * @param markdown 答案 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    private boolean coversExactLookupAnswerText(String markdown, String question) {
        if (!coversRequestedReferentialIdentifiers(markdown, question)
                && !coversRequestedQuestionAnchors(markdown, question)) {
            return false;
        }
        String normalizedMarkdown = lowerCase(stripEmbeddedCitationLiterals(markdown));
        if (looksLikePathQuestion(question)
                && !containsPathSignal(normalizedMarkdown)) {
            return false;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && !containsRuleConstraintSignal(normalizedMarkdown)
                && !containsStrongConstraintSignal(normalizedMarkdown)) {
            return false;
        }
        if (looksLikeChangeTrackingQuestion(question)
                && !containsChangeTrackingSignal(normalizedMarkdown)
                && !containsStrongConstraintSignal(normalizedMarkdown)) {
            return false;
        }
        return true;
    }

    /**
     * 判断 Markdown 是否包含源文件式 citation，避免把普通内部文章引用误当作完整模型答案。
     *
     * @param markdown Markdown 文本
     * @return 包含源文件引用返回 true
     */
    private boolean containsSourceCitationLiteral(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        return SOURCE_CITATION_PATTERN.matcher(markdown).find();
    }

    /**
     * 判断非结构化答案是否覆盖了问题中明确点名的可复用锚点。
     *
     * @param markdown 模型 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    private boolean coversRequestedQuestionAnchors(String markdown, String question) {
        List<String> reusableAnchors = extractReusableQuestionAnchors(question);
        if (reusableAnchors.size() < 2) {
            return false;
        }
        String normalizedMarkdown = lowerCase(stripEmbeddedCitationLiterals(markdown));
        int matchedAnchorCount = countMatchedReusableAnchors(normalizedMarkdown, reusableAnchors);
        if (matchedAnchorCount < reusableAnchors.size()) {
            return false;
        }
        int anchoredFactUnitCount = countAnchoredFactUnits(normalizedMarkdown, reusableAnchors);
        if (anchoredFactUnitCount >= Math.min(2, reusableAnchors.size())) {
            return true;
        }
        return anchoredFactUnitCount == 1 && normalizedMarkdown.length() >= 40;
    }

    /**
     * 从问题中提取适合做答案覆盖校验的通用锚点。
     *
     * @param question 用户问题
     * @return 去重后的锚点
     */
    private List<String> extractReusableQuestionAnchors(String question) {
        Set<String> reusableAnchors = new LinkedHashSet<String>();
        for (String rawToken : QueryTokenExtractor.extract(question)) {
            String normalizedToken = lowerCase(rawToken);
            if (isReusableQuestionAnchor(normalizedToken)) {
                reusableAnchors.add(normalizedToken);
            }
        }
        return new ArrayList<String>(reusableAnchors);
    }

    /**
     * 判断 token 是否适合作为自由文本答案的覆盖锚点。
     *
     * @param token 待判断 token
     * @return 适合返回 true
     */
    private boolean isReusableQuestionAnchor(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.matches("\\d{2,}(?:\\.\\d+)?%?")) {
            return true;
        }
        if (!containsHanText(token) && token.matches("[a-z][a-z0-9_.=-]{3,}")) {
            return true;
        }
        return token.matches("[a-z0-9_.=-]*[_.=-][a-z0-9_.=-]*");
    }

    /**
     * 统计答案中命中的问题锚点数量。
     *
     * @param normalizedMarkdown 已归一化答案
     * @param reusableAnchors 问题锚点
     * @return 命中数量
     */
    private int countMatchedReusableAnchors(String normalizedMarkdown, List<String> reusableAnchors) {
        int matchedAnchorCount = 0;
        for (String reusableAnchor : reusableAnchors) {
            if (normalizedMarkdown.contains(reusableAnchor)) {
                matchedAnchorCount++;
            }
        }
        return matchedAnchorCount;
    }

    /**
     * 统计包含问题锚点的事实单元数量。
     *
     * @param normalizedMarkdown 已归一化答案
     * @param reusableAnchors 问题锚点
     * @return 事实单元数量
     */
    private int countAnchoredFactUnits(String normalizedMarkdown, List<String> reusableAnchors) {
        if (normalizedMarkdown == null || normalizedMarkdown.isBlank()) {
            return 0;
        }
        int factUnitCount = 0;
        String[] segments = normalizedMarkdown.split("\\R|[。；;]+");
        for (String rawSegment : segments) {
            String normalizedSegment = normalizeFallbackLineCandidate(rawSegment);
            if (normalizedSegment.length() < 8) {
                continue;
            }
            if (containsAnyReusableAnchor(lowerCase(normalizedSegment), reusableAnchors)) {
                factUnitCount++;
            }
        }
        return factUnitCount;
    }

    /**
     * 判断文本片段是否包含任一问题锚点。
     *
     * @param normalizedSegment 已归一化片段
     * @param reusableAnchors 问题锚点
     * @return 包含返回 true
     */
    private boolean containsAnyReusableAnchor(String normalizedSegment, List<String> reusableAnchors) {
        for (String reusableAnchor : reusableAnchors) {
            if (normalizedSegment.contains(reusableAnchor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断模型自由文本是否覆盖了问题中点名的精确标识。
     *
     * @param markdown 模型 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    private boolean coversRequestedReferentialIdentifiers(String markdown, String question) {
        List<String> identifiers = extractRequestedReferentialIdentifiers(question);
        if (identifiers.isEmpty()) {
            return false;
        }
        String normalizedMarkdown = lowerCase(markdown);
        for (String identifier : identifiers) {
            if (!normalizedMarkdown.contains(lowerCase(identifier))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断模型输出是否只是拒答或错误说明。
     *
     * @param markdown 模型 Markdown
     * @return 拒答/错误返回 true
     */
    private boolean looksLikeModelRefusalOrError(String markdown) {
        String normalizedMarkdown = lowerCase(markdown);
        return normalizedMarkdown.contains("无法回答")
                || normalizedMarkdown.contains("不能回答")
                || normalizedMarkdown.contains("没有足够信息")
                || normalizedMarkdown.contains("error")
                || normalizedMarkdown.contains("exception");
    }

    /**
     * 统计 Markdown 中像枚举事实的行数。
     *
     * @param markdown 模型 Markdown
     * @return 枚举事实行数
     */
    private int countEnumerationFactLines(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int factLineCount = 0;
        for (String rawLine : markdown.split("\\R")) {
            String normalizedLine = normalizeFallbackLineCandidate(rawLine);
            if (looksLikeEnumerationFactLine(rawLine, normalizedLine)) {
                factLineCount++;
            }
        }
        return factLineCount;
    }

    /**
     * 基于答案载荷推导 prompt cache 写策略。
     *
     * @param answerPayload 结构化答案载荷
     * @return prompt cache 写策略
     */
    private PromptCacheWritePolicy resolvePromptCacheWritePolicy(QueryAnswerPayload answerPayload) {
        if (answerPayload == null) {
            return PromptCacheWritePolicy.EVICT_AFTER_READ;
        }
        if (answerPayload.getAnswerOutcome() == AnswerOutcome.SUCCESS && answerPayload.isAnswerCacheable()) {
            return PromptCacheWritePolicy.WRITE;
        }
        return PromptCacheWritePolicy.SKIP_WRITE;
    }

    /**
     * 读取文本字段。
     *
     * @param payloadNode JSON 节点
     * @param fieldName 字段名
     * @return 字段值
     */
    private String readText(JsonNode payloadNode, String fieldName) {
        JsonNode fieldNode = payloadNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        String fieldValue = fieldNode.asText();
        if (fieldValue == null || fieldValue.isBlank()) {
            return null;
        }
        return fieldValue;
    }

    /**
     * 读取布尔字段。
     *
     * @param payloadNode JSON 节点
     * @param fieldName 字段名
     * @return 布尔值
     */
    private boolean readBoolean(JsonNode payloadNode, String fieldName) {
        JsonNode fieldNode = payloadNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return false;
        }
        return fieldNode.asBoolean(false);
    }

    /**
     * 读取答案语义字段。
     *
     * @param payloadNode JSON 节点
     * @param fieldName 字段名
     * @return 答案语义
     */
    private AnswerOutcome readAnswerOutcome(JsonNode payloadNode, String fieldName) {
        String fieldValue = readText(payloadNode, fieldName);
        if (fieldValue == null) {
            return null;
        }
        try {
            return AnswerOutcome.valueOf(fieldValue.trim());
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 返回当前作用域下的路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    public String currentRoute(String scopeId, String scene, String agentRole) {
        if (llmGateway == null) {
            return "fallback";
        }
        try {
            return llmGateway.routeFor(scopeId, scene, agentRole);
        }
        catch (RuntimeException ex) {
            return "fallback";
        }
    }

    /**
     * 提取查询 token。
     *
     * @param question 查询问题
     * @return token 列表
     */
    private List<String> extractQueryTokens(String question) {
        List<String> focusedTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (!focusedTokens.isEmpty()) {
            return new ArrayList<String>(focusedTokens);
        }
        return new ArrayList<String>(QueryTokenExtractor.extract(question));
    }

    /**
     * 选出与问题最相关的内容行。
     *
     * @param content 文章内容
     * @param queryTokens 查询 token
     * @return 匹配内容行
     */
    private List<String> selectMatchedLines(String content, List<String> queryTokens) {
        List<String> matchedLines = new ArrayList<String>();
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        String[] lines = bodyContent.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String normalizedLine = line.trim();
            if (normalizedLine.isEmpty()
                    || normalizedLine.startsWith("#")
                    || normalizedLine.startsWith(">")
                    || looksLikeTableOfContentsLine(normalizedLine)
                    || isMarkdownTableHeaderWithDivider(normalizedLine, index + 1 < lines.length ? lines[index + 1] : null)
                    || isNonTextMediaLine(normalizedLine)) {
                continue;
            }

            String plainLine = normalizedLine.startsWith("- ") ? normalizedLine.substring(2) : normalizedLine;
            String lowercaseLine = plainLine.toLowerCase(Locale.ROOT);
            for (String queryToken : queryTokens) {
                if (lowercaseLine.contains(queryToken)) {
                    matchedLines.add(plainLine);
                    break;
                }
            }
            if (matchedLines.size() >= 6) {
                break;
            }
        }
        return matchedLines;
    }

    /**
     * 从 metadata_json 中提取 description。
     *
     * @param metadataJson 元数据 JSON
     * @return 描述
     */
    private String extractDescription(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        String marker = "\"description\":";
        int markerIndex = metadataJson.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int quoteStart = metadataJson.indexOf('"', markerIndex + marker.length());
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = metadataJson.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return "";
        }
        return metadataJson.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 判断当前是否只包含文章层证据。
     *
     * @param queryArticleHits 查询命中
     * @return 是否只包含文章层证据
     */
    private boolean containsOnlyArticleEvidence(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits.size() != 1) {
            return false;
        }
        return queryArticleHits.get(0).getEvidenceType() == QueryEvidenceType.ARTICLE;
    }

    /**
     * 判断单 article 规则答案是否已经足够直接，可视为成功回答。
     *
     * @param question 查询问题
     * @param queryArticleHit 命中文章
     * @return 答案语义
     */
    private AnswerOutcome resolveSingleArticleAnswerOutcome(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return AnswerOutcome.PARTIAL_ANSWER;
        }
        return isDirectFallbackAnswerable(question, queryArticleHit)
                ? AnswerOutcome.SUCCESS
                : AnswerOutcome.PARTIAL_ANSWER;
    }

    /**
     * 构造确定性 fallback 载荷，并根据证据本身推导更准确的答案语义。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @param preferredOutcome 期望保留的答案语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param fallbackReason fallback 原因
     * @return fallback 载荷
     */
    private QueryAnswerPayload buildDeterministicFallbackPayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            AnswerOutcome preferredOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            String fallbackReason
    ) {
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        return new QueryAnswerPayload(
                SensitiveTextMasker.mask(buildFallbackMarkdown(question, queryArticleHits)),
                resolveFallbackAnswerOutcome(question, fallbackHits, preferredOutcome),
                generationMode,
                modelExecutionStatus,
                false,
                fallbackReason
        );
    }

    /**
     * 根据 fallback 证据推导最终答案语义。
     *
     * @param question 查询问题
     * @param fallbackHits fallback 证据
     * @param preferredOutcome 调用方期望保留的语义
     * @return 推导后的答案语义
     */
    private AnswerOutcome resolveFallbackAnswerOutcome(
            String question,
            List<QueryArticleHit> fallbackHits,
            AnswerOutcome preferredOutcome
    ) {
        if (preferredOutcome == AnswerOutcome.INSUFFICIENT_EVIDENCE
                || preferredOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE
                || preferredOutcome == AnswerOutcome.MODEL_FAILURE) {
            return preferredOutcome;
        }
        AnswerOutcome evidenceOutcome = inferFallbackEvidenceOutcome(question, fallbackHits);
        if (preferredOutcome == null || preferredOutcome == AnswerOutcome.SUCCESS) {
            return evidenceOutcome;
        }
        if (preferredOutcome == AnswerOutcome.PARTIAL_ANSWER && evidenceOutcome == AnswerOutcome.SUCCESS) {
            return AnswerOutcome.SUCCESS;
        }
        if (preferredOutcome == AnswerOutcome.PARTIAL_ANSWER
                && evidenceOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE
                && (looksLikeStrictExactIdentifierQuestion(question) || looksLikeRequiredFacetQuestion(question))) {
            return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
        }
        return preferredOutcome;
    }

    /**
     * 仅基于 fallback 证据本身判断答案是否可视为成功、部分回答或无相关知识。
     *
     * @param question 查询问题
     * @param fallbackHits fallback 证据
     * @return 证据侧答案语义
     */
    private AnswerOutcome inferFallbackEvidenceOutcome(String question, List<QueryArticleHit> fallbackHits) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
        }
        if (looksLikeRequiredFacetQuestion(question) && !coversRequiredQuestionFacets(question, fallbackHits)) {
            return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
        }
        List<String> comparisonOptions = extractComparisonOptions(question);
        if (comparisonOptions.size() >= 2
                && hasComparisonConflict(comparisonOptions.get(0), comparisonOptions.get(1), fallbackHits)) {
            return AnswerOutcome.PARTIAL_ANSWER;
        }
        return isDirectFallbackAnswerable(question, fallbackHits.get(0))
                ? AnswerOutcome.SUCCESS
                : AnswerOutcome.PARTIAL_ANSWER;
    }

    /**
     * 判断当前 fallback 证据是否已形成冲突口径。
     *
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @param fallbackHits fallback 证据
     * @return 存在双向口径返回 true
     */
    private boolean hasComparisonConflict(
            String leftOption,
            String rightOption,
            List<QueryArticleHit> fallbackHits
    ) {
        boolean hasLeftSupport = false;
        boolean hasRightSupport = false;
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String matchedOption = matchComparisonOption(fallbackHit, leftOption, rightOption);
            if (leftOption.equals(matchedOption)) {
                hasLeftSupport = true;
            }
            if (rightOption.equals(matchedOption)) {
                hasRightSupport = true;
            }
            if (hasLeftSupport && hasRightSupport) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单条证据是否已足够直接，可视为成功回答。
     *
     * @param question 查询问题
     * @param queryArticleHit 查询命中
     * @return 可直接回答返回 true
     */
    private boolean isDirectFallbackAnswerable(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        if (looksLikeRequiredFacetQuestion(question)
                && !coversRequiredQuestionFacets(question, List.of(queryArticleHit))) {
            return false;
        }
        List<String> queryTokens = extractQueryTokens(question);
        if (looksLikeStatusQuestion(question)) {
            String statusSnippet = selectQuestionFocusedFallbackSnippet(
                    question,
                    queryArticleHit,
                    queryTokens
            );
            return containsStatusSignal(lowerCase(statusSnippet));
        }
        if (looksLikeFlowQuestion(question)) {
            String flowSnippet = selectQuestionFocusedFallbackSnippet(question, queryArticleHit, queryTokens);
            if (containsFlowSignal(flowSnippet)) {
                return true;
            }
            if (containsFlowSignalInFallbackLines(queryArticleHit)) {
                return true;
            }
            return containsFlowSignal(extractDescription(queryArticleHit.getMetadataJson()));
        }
        if (!selectMatchedLines(queryArticleHit.getContent(), queryTokens).isEmpty()) {
            return true;
        }
        return !extractDescription(queryArticleHit.getMetadataJson()).isEmpty();
    }

    /**
     * 判断命中正文里是否存在通用流程/链路事实句。
     *
     * @param queryArticleHit 查询命中
     * @return 存在返回 true
     */
    private boolean containsFlowSignalInFallbackLines(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        for (String contentLine : selectFallbackContentLines(queryArticleHit.getContent())) {
            String normalizedLine = normalizeFallbackLineCandidate(contentLine);
            if (!normalizedLine.isBlank() && containsFlowSignal(normalizedLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否含有必须同时覆盖的通用技术焦点。
     *
     * @param question 用户问题
     * @return 需要覆盖返回 true
     */
    private boolean looksLikeRequiredFacetQuestion(String question) {
        return extractRequiredQuestionFacets(question).size() >= 2;
    }

    /**
     * 判断 fallback 证据是否覆盖问题中的必要技术焦点。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 覆盖返回 true
     */
    private boolean coversRequiredQuestionFacets(String question, List<QueryArticleHit> fallbackHits) {
        List<String> requiredFacets = extractRequiredQuestionFacets(question);
        if (requiredFacets.isEmpty()) {
            return true;
        }
        String evidenceText = lowerCase(joinHitTexts(fallbackHits));
        for (String requiredFacet : requiredFacets) {
            if (!evidenceText.contains(requiredFacet)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提取问题中需要被证据共同覆盖的通用技术焦点。
     *
     * @param question 用户问题
     * @return 技术焦点
     */
    private List<String> extractRequiredQuestionFacets(String question) {
        List<String> facets = new ArrayList<String>();
        String normalizedQuestion = lowerCase(question);
        if (!containsMultiFacetQuestionSignal(normalizedQuestion)) {
            return facets;
        }
        for (String token : QueryTokenExtractor.extract(question)) {
            String normalizedToken = lowerCase(token);
            if (!looksLikeRequiredTechnicalFacet(normalizedToken) || facets.contains(normalizedToken)) {
                continue;
            }
            facets.add(normalizedToken);
            if (facets.size() >= 4) {
                break;
            }
        }
        return facets;
    }

    /**
     * 判断问题是否包含多焦点提问信号。
     *
     * @param normalizedQuestion 归一化问题
     * @return 包含返回 true
     */
    private boolean containsMultiFacetQuestionSignal(String normalizedQuestion) {
        return normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("各自")
                || normalizedQuestion.contains("和")
                || normalizedQuestion.contains("以及")
                || normalizedQuestion.contains("、")
                || normalizedQuestion.contains("/");
    }

    /**
     * 判断 token 是否像必须被证据覆盖的技术焦点。
     *
     * @param token token
     * @return 是技术焦点返回 true
     */
    private boolean looksLikeRequiredTechnicalFacet(String token) {
        if (token == null || token.isBlank() || token.length() < 2) {
            return false;
        }
        if (token.matches("\\d+")) {
            return false;
        }
        if (!token.matches("[a-z0-9._/-]+")) {
            return false;
        }
        return !isGenericTechnicalFacet(token);
    }

    /**
     * 判断 token 是否只是泛化技术类型词，不应作为强制焦点。
     *
     * @param token token
     * @return 泛化词返回 true
     */
    private boolean isGenericTechnicalFacet(String token) {
        return "api".equals(token)
                || "http".equals(token)
                || "https".equals(token)
                || "path".equals(token)
                || "url".equals(token)
                || "json".equals(token)
                || "xml".equals(token)
                || "yaml".equals(token)
                || "yml".equals(token)
                || "sql".equals(token);
    }

    /**
     * 对精确查值题，若模型仍给出“证据不足/答非所问”，优先回退到确定性证据答案。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @param answerPayload 模型答案
     * @return 更稳妥的答案载荷
     */
    private QueryAnswerPayload preferDeterministicExactLookupPayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            QueryAnswerPayload answerPayload
    ) {
        if (answerPayload == null || !looksLikeExactLookupQuestion(question)) {
            return answerPayload;
        }
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        if (fallbackHits.isEmpty() || !isDirectFallbackAnswerable(question, fallbackHits.get(0))) {
            return answerPayload;
        }
        String normalizedAnswer = lowerCase(answerPayload.getAnswerMarkdown());
        ExactLookupPreferenceReason preferenceReason = ExactLookupPreferenceReason.NONE;
        ExactLookupGroundingStatus groundingStatus = ExactLookupGroundingStatus.GROUNDED;
        if (answerPayload.getAnswerOutcome() != AnswerOutcome.SUCCESS) {
            preferenceReason = ExactLookupPreferenceReason.OUTCOME_NOT_SUCCESS;
        }
        else if (containsOvercautiousExactLookupPhrase(normalizedAnswer)) {
            preferenceReason = ExactLookupPreferenceReason.OVERCAUTIOUS_PHRASE;
        }
        else {
            groundingStatus = evaluateExactLookupAnswerGrounding(
                    question,
                    fallbackHits,
                    answerPayload.getAnswerMarkdown()
            );
            if (groundingStatus != ExactLookupGroundingStatus.GROUNDED) {
                preferenceReason = ExactLookupPreferenceReason.GROUNDING_MISMATCH;
            }
        }
        if (preferenceReason != ExactLookupPreferenceReason.NONE) {
            logExactLookupPreference(preferenceReason, groundingStatus);
            return buildDeterministicFallbackPayload(
                    question,
                    queryArticleHits,
                    AnswerOutcome.SUCCESS,
                    GenerationMode.FALLBACK,
                    ModelExecutionStatus.DEGRADED,
                    FALLBACK_REASON_DETERMINISTIC_EXACT_LOOKUP_PREFERRED
            );
        }
        return answerPayload;
    }

    /**
     * 判断模型答案是否带有精确题常见的过度保守表达。
     *
     * @param normalizedAnswer 归一化答案
     * @return 命中返回 true
     */
    private boolean containsOvercautiousExactLookupPhrase(String normalizedAnswer) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return false;
        }
        return normalizedAnswer.contains("当前证据不足")
                || normalizedAnswer.contains("暂无法确认")
                || normalizedAnswer.contains("无法根据当前证据确定")
                || normalizedAnswer.contains("没有直接给出")
                || normalizedAnswer.contains("未直接给出")
                || normalizedAnswer.contains("未提供");
    }

    /**
     * 记录精确查值题偏向 deterministic fallback 的通用原因。
     *
     * @param preferenceReason 偏向 fallback 的原因
     * @param groundingStatus grounding 判定状态
     */
    private void logExactLookupPreference(
            ExactLookupPreferenceReason preferenceReason,
            ExactLookupGroundingStatus groundingStatus
    ) {
        log.info(
                "query_exact_lookup_deterministic_preferred reason: {}, groundingStatus: {}",
                preferenceReason,
                groundingStatus
        );
    }

    /**
     * 判断精确查值题的模型答案是否至少覆盖了证据中的关键形态。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中
     * @param answerMarkdown 模型答案
     * @return 基本贴合证据返回 true
     */
    private boolean isExactLookupAnswerGroundedByFocusedEvidence(
            String question,
            List<QueryArticleHit> fallbackHits,
            String answerMarkdown
    ) {
        return evaluateExactLookupAnswerGrounding(question, fallbackHits, answerMarkdown)
                == ExactLookupGroundingStatus.GROUNDED;
    }

    /**
     * 判断精确查值题的模型答案是否覆盖了证据里的关键形态，并返回通用失败原因。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中
     * @param answerMarkdown 模型答案
     * @return grounding 判定状态
     */
    private ExactLookupGroundingStatus evaluateExactLookupAnswerGrounding(
            String question,
            List<QueryArticleHit> fallbackHits,
            String answerMarkdown
    ) {
        if (answerMarkdown == null || answerMarkdown.isBlank() || fallbackHits == null || fallbackHits.isEmpty()) {
            return ExactLookupGroundingStatus.GROUNDED;
        }
        List<String> queryTokens = extractQueryTokens(question);
        List<String> focusSnippets = new ArrayList<String>();
        int evidenceLimit = Math.min(8, fallbackHits.size());
        int snippetLimit = shouldAggregateEvidenceConclusion(question) || looksLikeCompoundExactLookupQuestion(question)
                ? Math.max(4, desiredFallbackConclusionSnippetCount(question))
                : Math.max(2, desiredStructuredFactCount(question));
        for (int index = 0; index < evidenceLimit; index++) {
            if (shouldAggregateEvidenceConclusion(question) || looksLikeCompoundExactLookupQuestion(question)) {
                focusSnippets.addAll(selectAggregationCandidateLines(
                        question,
                        fallbackHits.get(index),
                        queryTokens,
                        snippetLimit
                ));
            }
            else {
                focusSnippets.addAll(selectQuestionFocusedFallbackSnippets(
                        question,
                        fallbackHits.get(index),
                        queryTokens,
                        snippetLimit
                ));
            }
        }
        if (focusSnippets.isEmpty()) {
            return ExactLookupGroundingStatus.GROUNDED;
        }
        String normalizedQuestion = lowerCase(question);
        String normalizedAnswer = lowerCase(answerMarkdown);
        if ((normalizedQuestion.contains("路径") || normalizedQuestion.contains("接口"))
                && containsAnySnippetToken(focusSnippets, "/")
                && !coversRequiredPathShape(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_PATH_SHAPE;
        }
        if ((normalizedQuestion.contains("命中数") || looksLikeNumericQuestion(question))
                && containsAnySnippetDigit(focusSnippets)
                && !normalizedAnswer.matches("(?s).*\\d.*")) {
            return ExactLookupGroundingStatus.MISSING_DIGIT;
        }
        if (looksLikeNumericQuestion(question)
                && !coversRequiredNumericShape(normalizedQuestion, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_NUMERIC_SHAPE;
        }
        if (expectsBatchOrOrdinalAnswer(normalizedQuestion)
                && containsAnyBatchOrOrdinalSignal(focusSnippets)
                && !containsBatchOrOrdinalSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_BATCH_OR_ORDINAL;
        }
        if (looksLikeStatusQuestion(question)
                && containsAnyStatusSignal(focusSnippets)
                && !containsStatusSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_STATUS;
        }
        if (looksLikeFlowQuestion(question)
                && containsAnyFlowTransitionSignal(focusSnippets)
                && !containsFlowTransitionSignal(answerMarkdown)) {
            return ExactLookupGroundingStatus.MISSING_FLOW;
        }
        if (normalizedQuestion.contains("结论")
                && containsAnyCorrectionOrStatusSignal(focusSnippets)
                && !containsCorrectionOrStatusSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_CORRECTION_OR_STATUS;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && containsAnyStrongConstraintSignal(focusSnippets)
                && !containsStrongConstraintSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_STRONG_CONSTRAINT;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && containsAnyRuleConstraintSignal(focusSnippets)
                && !containsRuleConstraintSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_RULE_CONSTRAINT;
        }
        if (looksLikeChangeTrackingQuestion(question)
                && !coversChangeTrackingAnswer(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_CHANGE_TRACKING;
        }
        if (coversRequestedPathContractAnswer(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.GROUNDED;
        }
        if (looksLikeCompoundExactLookupQuestion(question)
                && !coversMultipleEvidenceDimensions(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_COMPOUND_DIMENSIONS;
        }
        return ExactLookupGroundingStatus.GROUNDED;
    }

    /**
     * 判断数值题答案是否覆盖证据中的关键数值形态。
     *
     * @param normalizedQuestion 归一化问题
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖足够返回 true
     */
    private boolean coversRequiredNumericShape(
            String normalizedQuestion,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        List<String> evidenceNumbers = extractRequiredEvidenceNumbers(normalizedQuestion, focusSnippets);
        if (evidenceNumbers.isEmpty()) {
            return true;
        }
        int coveredNumberCount = countCoveredNumbers(normalizedAnswer, evidenceNumbers);
        if (coveredNumberCount > 0) {
            return true;
        }
        if (normalizedQuestion.contains("命中数") || normalizedQuestion.contains("多少")) {
            return false;
        }
        if (normalizedQuestion.contains("分别") && evidenceNumbers.size() >= 2) {
            return false;
        }
        return normalizedAnswer.matches("(?s).*\\d.*");
    }

    /**
     * 判断路径题答案是否覆盖了证据里的具体路径形态。
     *
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖足够返回 true
     */
    private boolean coversRequiredPathShape(String question, String normalizedAnswer, List<String> focusSnippets) {
        List<String> requestedPaths = extractRequestedPathIdentifiers(question);
        if (!requestedPaths.isEmpty()) {
            return coversRequestedPaths(normalizedAnswer, requestedPaths);
        }
        List<String> evidencePaths = extractEvidencePaths(focusSnippets);
        if (evidencePaths.isEmpty()) {
            return normalizedAnswer.contains("/");
        }
        int requiredPathCount = requiredPathCoverageCount(question, focusSnippets, evidencePaths);
        int coveredPathCount = 0;
        for (String evidencePath : evidencePaths) {
            if (normalizedAnswer.contains(lowerCase(evidencePath))) {
                coveredPathCount++;
                if (coveredPathCount >= requiredPathCount) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断答案是否覆盖用户问题中显式点名的路径。
     *
     * @param normalizedAnswer 归一化答案
     * @param requestedPaths 用户点名路径
     * @return 覆盖返回 true
     */
    private boolean coversRequestedPaths(String normalizedAnswer, List<String> requestedPaths) {
        if (requestedPaths == null || requestedPaths.isEmpty()) {
            return true;
        }
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return false;
        }
        for (String requestedPath : requestedPaths) {
            if (!normalizedAnswer.contains(lowerCase(requestedPath))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断多维查值题答案是否至少覆盖了两种证据维度。
     *
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖足够返回 true
     */
    private boolean coversMultipleEvidenceDimensions(
            String question,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        int evidenceDimensionCount = countEvidenceDimensions(focusSnippets);
        if (evidenceDimensionCount < 2) {
            return true;
        }
        int answerDimensionCount = countCoveredAnswerDimensions(question, normalizedAnswer, focusSnippets);
        return answerDimensionCount >= evidenceDimensionCount;
    }

    /**
     * 统计贴题证据里包含了多少种结构化维度。
     *
     * @param focusSnippets 贴题证据句
     * @return 维度数
     */
    private int countEvidenceDimensions(List<String> focusSnippets) {
        int dimensionCount = 0;
        if (containsAnyPathSignal(focusSnippets)) {
            dimensionCount++;
        }
        if (extractStructuredLabels(focusSnippets).size() >= 2) {
            dimensionCount++;
        }
        if (containsAnyBatchOrOrdinalSignal(focusSnippets)) {
            dimensionCount++;
        }
        if (containsAnyChangeTrackingSignal(focusSnippets)) {
            dimensionCount++;
        }
        return dimensionCount;
    }

    /**
     * 统计答案覆盖了多少种证据维度。
     *
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖维度数
     */
    private int countCoveredAnswerDimensions(
            String question,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        int coveredCount = 0;
        if (containsAnyPathSignal(focusSnippets)
                && coversRequiredPathShape(question, normalizedAnswer, focusSnippets)) {
            coveredCount++;
        }
        List<String> structuredLabels = extractStructuredLabels(focusSnippets);
        if (structuredLabels.size() >= 2
                && countCoveredStructuredLabels(normalizedAnswer, structuredLabels)
                >= Math.min(2, structuredLabels.size())) {
            coveredCount++;
        }
        if (containsAnyBatchOrOrdinalSignal(focusSnippets) && containsBatchOrOrdinalSignal(normalizedAnswer)) {
            coveredCount++;
        }
        if (containsAnyChangeTrackingSignal(focusSnippets) && containsChangeTrackingSignal(normalizedAnswer)) {
            coveredCount++;
        }
        return coveredCount;
    }

    /**
     * 从证据中提取接口或 URL path。
     *
     * @param snippets 证据句
     * @return 路径列表
     */
    private List<String> extractEvidencePaths(List<String> snippets) {
        List<String> paths = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return paths;
        }
        Pattern pathPattern = Pattern.compile("/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._*{}-]+)+");
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            Matcher pathMatcher = pathPattern.matcher(snippet);
            while (pathMatcher.find()) {
                String path = pathMatcher.group();
                if (!paths.contains(path)) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    /**
     * 删除显式路径契约题答案中未被用户点名的反例路径，避免把证据里的旁路示例扩写进最终结论。
     *
     * @param answerMarkdown 答案 Markdown
     * @param question 用户问题
     * @return 清理后的答案 Markdown
     */
    private String removeUnrequestedPathExamples(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank() || !requiresPathContractCompanion(question)) {
            return answerMarkdown;
        }
        List<String> requestedPaths = extractRequestedPathIdentifiers(question);
        if (requestedPaths.isEmpty()) {
            return answerMarkdown;
        }
        List<String> answerPaths = extractEvidencePaths(List.of(stripEmbeddedCitationLiterals(answerMarkdown)));
        if (answerPaths.isEmpty()) {
            return answerMarkdown;
        }
        String cleanedAnswer = answerMarkdown;
        for (String answerPath : answerPaths) {
            if (!containsIdentifierIgnoreCase(requestedPaths, answerPath)) {
                cleanedAnswer = removeUnrequestedPathClause(cleanedAnswer, answerPath);
            }
        }
        String normalizedCleanedAnswer = normalizeAfterUnrequestedPathRemoval(cleanedAnswer);
        if (normalizedCleanedAnswer.isBlank()) {
            return answerMarkdown;
        }
        String normalizedAnswer = lowerCase(stripEmbeddedCitationLiterals(normalizedCleanedAnswer));
        if (!coversRequestedPaths(normalizedAnswer, requestedPaths)) {
            return answerMarkdown;
        }
        if (!coversRequestedPathContractAnswer(question, normalizedAnswer, List.of(answerMarkdown))) {
            return answerMarkdown;
        }
        return normalizedCleanedAnswer;
    }

    /**
     * 删除包含未点名 path 的否定或示例从句。
     *
     * @param answerMarkdown 答案 Markdown
     * @param unrequestedPath 未点名 path
     * @return 删除后的答案 Markdown
     */
    private String removeUnrequestedPathClause(String answerMarkdown, String unrequestedPath) {
        if (answerMarkdown == null || answerMarkdown.isBlank()
                || unrequestedPath == null || unrequestedPath.isBlank()) {
            return answerMarkdown;
        }
        String quotedPath = "`?" + Pattern.quote(unrequestedPath) + "`?";
        String cleanedAnswer = answerMarkdown;
        cleanedAnswer = cleanedAnswer.replaceAll(
                "，?(?:不得|不要|不能|不应|不宜|不建议)[^。；\\n]*" + quotedPath + "[^。；\\n]*(?=[。；\\n])",
                ""
        );
        cleanedAnswer = cleanedAnswer.replaceAll(
                "，?(?:废弃|作废|反例|示例)[^。；\\n]*" + quotedPath + "[^。；\\n]*(?=[。；\\n])",
                ""
        );
        return cleanedAnswer;
    }

    /**
     * 清理删除未点名 path 从句后留下的重复标点和空白。
     *
     * @param answerMarkdown 答案 Markdown
     * @return 归一化答案
     */
    private String normalizeAfterUnrequestedPathRemoval(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return "";
        }
        return answerMarkdown
                .replaceAll("；\\s*；+", "；")
                .replaceAll("，\\s*，+", "，")
                .replaceAll("；\\s*。", "。")
                .replaceAll("，\\s*。", "。")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * 计算路径题至少需要覆盖多少个接口路径。
     *
     * @param question 用户问题
     * @param focusSnippets 贴题证据句
     * @param evidencePaths 证据路径
     * @return 需要覆盖的路径数
     */
    private int requiredPathCoverageCount(
            String question,
            List<String> focusSnippets,
            List<String> evidencePaths
    ) {
        if (evidencePaths == null || evidencePaths.isEmpty()) {
            return 0;
        }
        int labelCount = extractStructuredLabels(focusSnippets).size();
        if (looksLikeCompoundExactLookupQuestion(question) && labelCount >= 2) {
            return Math.min(labelCount, evidencePaths.size());
        }
        return 1;
    }

    /**
     * 根据问题语义抽取真正需要答案覆盖的证据数值。
     *
     * @param normalizedQuestion 归一化问题
     * @param focusSnippets 贴题证据句
     * @return 数值列表
     */
    private List<String> extractRequiredEvidenceNumbers(String normalizedQuestion, List<String> focusSnippets) {
        if (normalizedQuestion != null && normalizedQuestion.contains("命中数")) {
            List<String> countNumbers = extractNumbersFromSignalSnippets(focusSnippets, List.of("命中", "条", "count"));
            if (!countNumbers.isEmpty()) {
                return countNumbers;
            }
        }
        return extractRepresentativeNumbers(focusSnippets);
    }

    /**
     * 从包含指定语义信号的证据句中抽取数字。
     *
     * @param snippets 证据句
     * @param signals 语义信号
     * @return 数值列表
     */
    private List<String> extractNumbersFromSignalSnippets(List<String> snippets, List<String> signals) {
        List<String> numbers = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return numbers;
        }
        for (String snippet : snippets) {
            String normalizedSnippet = lowerCase(snippet);
            boolean matchedSignal = false;
            for (String signal : signals) {
                if (normalizedSnippet.contains(lowerCase(signal))) {
                    matchedSignal = true;
                    break;
                }
            }
            if (!matchedSignal) {
                continue;
            }
            for (String number : extractRepresentativeNumbers(List.of(snippet))) {
                if (!numbers.contains(number)) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    /**
     * 统计答案覆盖了多少个证据数值。
     *
     * @param normalizedAnswer 归一化答案
     * @param evidenceNumbers 证据数值
     * @return 覆盖数量
     */
    private int countCoveredNumbers(String normalizedAnswer, List<String> evidenceNumbers) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank() || evidenceNumbers == null || evidenceNumbers.isEmpty()) {
            return 0;
        }
        String compactAnswer = normalizedAnswer.replace(",", "");
        int coveredCount = 0;
        for (String evidenceNumber : evidenceNumbers) {
            String compactNumber = evidenceNumber.replace(",", "");
            if (!compactNumber.isBlank() && compactAnswer.contains(compactNumber)) {
                coveredCount++;
            }
        }
        return coveredCount;
    }

    /**
     * 从贴题证据里提取结构化标签，如 8A / 5G。
     *
     * @param snippets 证据句
     * @return 标签列表
     */
    private List<String> extractStructuredLabels(List<String> snippets) {
        List<String> labels = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return labels;
        }
        Pattern labelPattern = Pattern.compile("\\b\\d+[A-Za-z]\\b");
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            Matcher labelMatcher = labelPattern.matcher(snippet);
            while (labelMatcher.find()) {
                String label = lowerCase(labelMatcher.group());
                if (!labels.contains(label)) {
                    labels.add(label);
                }
            }
        }
        return labels;
    }

    /**
     * 从问题中提取显式点名的结构化标签，如 8A/8B/8C。
     *
     * @param question 用户问题
     * @return 标签列表
     */
    private List<String> extractRequestedStructuredLabels(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return extractStructuredLabels(List.of(question));
    }

    /**
     * 判断候选行是否更像“标签 + 路径”的结构化事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean looksLikeStructuredPathFactLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return containsStructuredLabelSignal(normalizedLine) && containsPathSignal(normalizedLine);
    }

    /**
     * 判断候选标签里是否包含问题显式点名的标签。
     *
     * @param lineLabels 候选标签
     * @param expectedLabels 期望标签
     * @return 命中返回 true
     */
    private boolean containsAnyExpectedLabel(List<String> lineLabels, List<String> expectedLabels) {
        if (lineLabels == null || lineLabels.isEmpty() || expectedLabels == null || expectedLabels.isEmpty()) {
            return false;
        }
        for (String expectedLabel : expectedLabels) {
            if (lineLabels.contains(lowerCase(expectedLabel))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统计答案覆盖了多少个结构化标签。
     *
     * @param normalizedAnswer 归一化答案
     * @param labels 标签列表
     * @return 覆盖数量
     */
    private int countCoveredStructuredLabels(String normalizedAnswer, List<String> labels) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank() || labels == null || labels.isEmpty()) {
            return 0;
        }
        int coveredCount = 0;
        for (String label : labels) {
            if (!label.isBlank() && normalizedAnswer.contains(lowerCase(label))) {
                coveredCount++;
            }
        }
        return coveredCount;
    }

    /**
     * 从贴题证据里抽取代表性数字，避免把日期年份当作唯一覆盖目标。
     *
     * @param snippets 证据句
     * @return 数值列表
     */
    private List<String> extractRepresentativeNumbers(List<String> snippets) {
        List<String> numbers = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return numbers;
        }
        Pattern numberPattern = Pattern.compile("(?<![A-Za-z0-9])\\d{1,3}(?:,\\d{3})+|(?<![A-Za-z0-9])\\d+(?![A-Za-z0-9])");
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            Matcher numberMatcher = numberPattern.matcher(snippet);
            while (numberMatcher.find()) {
                String number = numberMatcher.group();
                if (looksLikeLowInformationNumber(number)) {
                    continue;
                }
                if (!numbers.contains(number)) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    /**
     * 判断数值是否更像日期年份或单字符噪声。
     *
     * @param number 数值文本
     * @return 低信息数值返回 true
     */
    private boolean looksLikeLowInformationNumber(String number) {
        if (number == null || number.isBlank()) {
            return true;
        }
        String compactNumber = number.replace(",", "");
        if (compactNumber.length() <= 1) {
            return true;
        }
        if (compactNumber.matches("20\\d{2}") || compactNumber.matches("19\\d{2}")) {
            return true;
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现强限制语义。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyStrongConstraintSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsStrongConstraintSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现接口/URL path。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyPathSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsPathSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现变更语义。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyChangeTrackingSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsChangeTrackingSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现规则/约束语义。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyRuleConstraintSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsRuleConstraintSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断变更类问题的答案是否覆盖了关键变更语义和问题锚点。
     *
     * @param question 用户问题
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖返回 true
     */
    private boolean coversChangeTrackingAnswer(String question, String normalizedAnswer, List<String> focusSnippets) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return false;
        }
        if (coversRequestedPathContractAnswer(question, normalizedAnswer, focusSnippets)) {
            return true;
        }
        if (!containsChangeTrackingSignal(normalizedAnswer)) {
            return false;
        }
        if (containsAnyAssignmentLikeMappingSignal(focusSnippets)
                && !containsAssignmentLikeMappingSignal(normalizedAnswer)) {
            return false;
        }
        List<String> reusableAnchors = extractReusableQuestionAnchors(question);
        if (reusableAnchors.isEmpty()) {
            return true;
        }
        String normalizedAnswerWithoutCitation = lowerCase(stripEmbeddedCitationLiterals(normalizedAnswer));
        return countMatchedReusableAnchors(normalizedAnswerWithoutCitation, reusableAnchors) >= 1;
    }

    /**
     * 判断显式 path 契约题是否已覆盖用户真正询问的 path 与可变更性。
     *
     * @param question 用户问题
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖返回 true
     */
    private boolean coversRequestedPathContractAnswer(
            String question,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        return requiresPathContractCompanion(question)
                && coversRequiredPathShape(question, normalizedAnswer, focusSnippets)
                && (containsPathContractSignal(normalizedAnswer) || containsStrongConstraintSignal(normalizedAnswer));
    }

    /**
     * 判断若干贴题证据句里是否出现映射/重排信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyAssignmentLikeMappingSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsAssignmentLikeMappingSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 LLM 查询答案 Prompt。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return 用户提示词
     */
    private String buildAnswerPrompt(String question, List<QueryArticleHit> queryArticleHits) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = groupHitsByEvidenceType(queryArticleHits);
        List<String> queryTokens = extractQueryTokens(question);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        appendReferentialFocusSection(promptBuilder, question);
        appendQuestionFocusedEvidenceSection(promptBuilder, question, queryArticleHits, queryTokens);
        appendEvidenceSection(
                promptBuilder,
                "CONTRIBUTION EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "STRUCTURED FACT CARD EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.FACT_CARD), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "SOURCE EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.SOURCE), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "GRAPH EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.GRAPH), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "ARTICLE EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.ARTICLE), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
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
    private String buildRevisePrompt(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = groupHitsByEvidenceType(queryArticleHits);
        List<String> queryTokens = extractQueryTokens(question);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        promptBuilder.append("CURRENT ANSWER").append("\n");
        promptBuilder.append(currentAnswer == null ? "" : currentAnswer.trim()).append("\n\n");
        promptBuilder.append("CORRECTION").append("\n");
        promptBuilder.append(correction == null ? "" : correction.trim()).append("\n\n");
        appendQuestionFocusedEvidenceSection(promptBuilder, question, queryArticleHits, queryTokens);
        appendEvidenceSection(
                promptBuilder,
                "CONTRIBUTION EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "STRUCTURED FACT CARD EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.FACT_CARD), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "SOURCE EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.SOURCE), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "GRAPH EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.GRAPH), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "ARTICLE EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.ARTICLE), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
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
    private String buildReviewRewritePrompt(
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = groupHitsByEvidenceType(queryArticleHits);
        List<String> queryTokens = extractQueryTokens(question);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        promptBuilder.append("CURRENT ANSWER").append("\n");
        promptBuilder.append(currentAnswer == null ? "" : currentAnswer.trim()).append("\n\n");
        promptBuilder.append("REVIEW FINDINGS").append("\n");
        promptBuilder.append(reviewFindings == null ? "" : reviewFindings.trim()).append("\n\n");
        appendQuestionFocusedEvidenceSection(promptBuilder, question, queryArticleHits, queryTokens);
        appendEvidenceSection(
                promptBuilder,
                "CONTRIBUTION EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "STRUCTURED FACT CARD EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.FACT_CARD), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "SOURCE EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.SOURCE), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "GRAPH EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.GRAPH), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        appendEvidenceSection(
                promptBuilder,
                "ARTICLE EVIDENCE",
                sortPromptEvidenceHits(question, groupedHits.get(QueryEvidenceType.ARTICLE), queryTokens),
                queryArticleHits,
                question,
                queryTokens
        );
        return promptBuilder.toString().trim();
    }

    /**
     * 按证据类型分组命中列表。
     *
     * @param queryArticleHits 查询命中
     * @return 分组结果
     */
    private Map<QueryEvidenceType, List<QueryArticleHit>> groupHitsByEvidenceType(List<QueryArticleHit> queryArticleHits) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits =
                new EnumMap<QueryEvidenceType, List<QueryArticleHit>>(QueryEvidenceType.class);
        for (QueryEvidenceType queryEvidenceType : QueryEvidenceType.values()) {
            groupedHits.put(queryEvidenceType, new ArrayList<QueryArticleHit>());
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            QueryEvidenceType evidenceType = resolvePromptEvidenceType(queryArticleHit);
            groupedHits.get(evidenceType).add(queryArticleHit);
        }
        return groupedHits;
    }

    /**
     * 解析 Prompt 中使用的证据分组类型。
     *
     * @param queryArticleHit 查询命中
     * @return Prompt 证据类型
     */
    private QueryEvidenceType resolvePromptEvidenceType(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return QueryEvidenceType.ARTICLE;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD
                && FactCardReviewUsagePolicy.isBackgroundOnly(queryArticleHit.getReviewStatus())) {
            return QueryEvidenceType.ARTICLE;
        }
        return queryArticleHit.getEvidenceType();
    }

    /**
     * 追加单个证据分组。
     *
     * @param promptBuilder Prompt 构建器
     * @param sectionTitle 段落标题
     * @param queryArticleHits 证据列表
     */
    private void appendEvidenceSection(
            StringBuilder promptBuilder,
            String sectionTitle,
            List<QueryArticleHit> queryArticleHits,
            List<QueryArticleHit> citationCandidateHits,
            String question,
            List<String> queryTokens
    ) {
        promptBuilder.append(sectionTitle).append("\n");
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            promptBuilder.append("- NONE").append("\n\n");
            return;
        }
        int appendedHitCount = 0;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (appendedHitCount >= PROMPT_EVIDENCE_SECTION_HIT_LIMIT) {
                promptBuilder.append("- OMITTED: evidence section hit limit reached").append("\n");
                break;
            }
            if (isPromptEvidenceBudgetExhausted(promptBuilder)) {
                promptBuilder.append("- OMITTED: prompt evidence budget exhausted").append("\n");
                break;
            }
            List<String> focusSnippets = buildPromptFocusSnippets(question, queryTokens, queryArticleHit);
            boolean fullyAppended = appendPromptLineWithinBudget(
                    promptBuilder,
                    "- title: " + normalizePromptInlineText(queryArticleHit.getTitle())
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(
                    promptBuilder,
                    "  id: " + normalizePromptInlineText(queryArticleHit.getConceptId())
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(
                    promptBuilder,
                    "  sources: " + normalizePromptInlineText(String.join(", ", queryArticleHit.getSourcePaths()))
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(
                    promptBuilder,
                    "  citation: " + resolveCitationLiteral(queryArticleHit, citationCandidateHits)
            );
            if (fullyAppended && !focusSnippets.isEmpty()) {
                fullyAppended = appendPromptLineWithinBudget(promptBuilder, "  focus_snippets:");
                for (String focusSnippet : focusSnippets) {
                    if (!fullyAppended) {
                        break;
                    }
                    fullyAppended = appendPromptLineWithinBudget(promptBuilder, "    - " + focusSnippet);
                }
            }
            String evidenceContent = buildBoundedPromptEvidenceContent(queryTokens, queryArticleHit, focusSnippets);
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(promptBuilder, "  content: " + evidenceContent);
            String metadata = truncatePromptText(
                    normalizePromptInlineText(SensitiveTextMasker.mask(queryArticleHit.getMetadataJson())),
                    PROMPT_EVIDENCE_METADATA_CHAR_LIMIT
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(promptBuilder, "  metadata: " + metadata);
            appendedHitCount++;
            if (!fullyAppended) {
                promptBuilder.append("- OMITTED: prompt evidence budget exhausted").append("\n");
                break;
            }
        }
        promptBuilder.append("\n");
    }

    /**
     * 判断回答 Prompt 的证据预算是否已经耗尽。
     *
     * @param promptBuilder Prompt 构建器
     * @return 耗尽返回 true
     */
    private boolean isPromptEvidenceBudgetExhausted(StringBuilder promptBuilder) {
        return promptBuilder.length() >= PROMPT_USER_PROMPT_CHAR_LIMIT;
    }

    /**
     * 在 Prompt 剩余预算内追加一行。
     *
     * @param promptBuilder Prompt 构建器
     * @param line 待追加行
     * @return 完整追加返回 true，被截断或跳过返回 false
     */
    private boolean appendPromptLineWithinBudget(StringBuilder promptBuilder, String line) {
        int remainingBudget = PROMPT_USER_PROMPT_CHAR_LIMIT - promptBuilder.length();
        if (remainingBudget <= 0) {
            return false;
        }
        String safeLine = line == null ? "" : line;
        int requiredLength = safeLine.length() + 1;
        if (requiredLength <= remainingBudget) {
            promptBuilder.append(safeLine).append("\n");
            return true;
        }
        if (remainingBudget <= PROMPT_TRUNCATED_SUFFIX.length() + 8) {
            return false;
        }
        promptBuilder.append(truncatePromptText(safeLine, remainingBudget - 1)).append("\n");
        return false;
    }

    /**
     * 构建单条命中的有界证据正文。
     *
     * @param queryTokens 查询 token
     * @param queryArticleHit 查询命中
     * @param focusSnippets 贴题证据句
     * @return 有界证据正文
     */
    private String buildBoundedPromptEvidenceContent(
            List<String> queryTokens,
            QueryArticleHit queryArticleHit,
            List<String> focusSnippets
    ) {
        List<String> candidateParts = new ArrayList<String>();
        if (focusSnippets != null) {
            for (String focusSnippet : focusSnippets) {
                appendDistinctPromptEvidencePart(candidateParts, focusSnippet);
            }
        }
        String fallbackSnippet = selectFallbackEvidenceSnippet(queryArticleHit, queryTokens);
        appendDistinctPromptEvidencePart(candidateParts, fallbackSnippet);
        if (candidateParts.isEmpty()) {
            String boundedContent = sanitizeEvidenceContentForPrompt(
                    queryArticleHit.getContent(),
                    PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT
            );
            return normalizePromptInlineText(boundedContent);
        }
        String focusedContent = String.join(" | ", candidateParts);
        if (focusedContent.length() >= PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT) {
            return truncatePromptText(focusedContent, PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT);
        }
        int contextBudget = PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT - focusedContent.length() - " | context: ".length();
        if (contextBudget > 120) {
            String boundedContent = normalizePromptInlineText(sanitizeEvidenceContentForPrompt(queryArticleHit.getContent(), contextBudget));
            if (!boundedContent.isBlank()) {
                focusedContent = focusedContent + " | context: " + boundedContent;
            }
        }
        return truncatePromptText(focusedContent, PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT);
    }

    /**
     * 追加去重后的 Prompt 证据片段。
     *
     * @param candidateParts 候选片段
     * @param rawPart 原始片段
     */
    private void appendDistinctPromptEvidencePart(List<String> candidateParts, String rawPart) {
        String normalizedPart = normalizePromptInlineText(rawPart);
        if (normalizedPart.isBlank()) {
            return;
        }
        for (String candidatePart : candidateParts) {
            if (candidatePart.equals(normalizedPart)) {
                return;
            }
        }
        candidateParts.add(normalizedPart);
    }

    /**
     * 把 Prompt 证据文本归一化为单行。
     *
     * @param text 原始文本
     * @return 单行文本
     */
    private String normalizePromptInlineText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * 按字符上限截断 Prompt 文本。
     *
     * @param text 原始文本
     * @param limit 字符上限
     * @return 截断后的文本
     */
    private String truncatePromptText(String text, int limit) {
        if (text == null || text.isBlank() || limit <= 0) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        if (limit <= PROMPT_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, limit);
        }
        return text.substring(0, limit - PROMPT_TRUNCATED_SUFFIX.length()) + PROMPT_TRUNCATED_SUFFIX;
    }

    /**
     * 追加与当前问题最贴近的证据句，降低模型在长文章里抓错焦点的概率。
     *
     * @param promptBuilder Prompt 构建器
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @param queryTokens 查询 token
     */
    private void appendQuestionFocusedEvidenceSection(
            StringBuilder promptBuilder,
            String question,
            List<QueryArticleHit> queryArticleHits,
            List<String> queryTokens
    ) {
        promptBuilder.append("QUESTION-FOCUSED EVIDENCE").append("\n");
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            promptBuilder.append("- NONE").append("\n\n");
            return;
        }
        List<QueryArticleHit> sortedHits = sortPromptEvidenceHits(question, queryArticleHits, queryTokens);
        int evidenceCount = 0;
        for (QueryArticleHit queryArticleHit : sortedHits) {
            List<String> focusSnippets = buildPromptFocusSnippets(question, queryTokens, queryArticleHit);
            if (focusSnippets.isEmpty()) {
                continue;
            }
            promptBuilder.append("- title: ").append(queryArticleHit.getTitle()).append("\n");
            promptBuilder.append("  citation: ").append(resolveCitationLiteral(queryArticleHit, sortedHits)).append("\n");
            promptBuilder.append("  snippets:").append("\n");
            for (String focusSnippet : focusSnippets) {
                promptBuilder.append("    - ").append(focusSnippet).append("\n");
            }
            evidenceCount++;
            if (evidenceCount >= 6) {
                break;
            }
        }
        if (evidenceCount == 0) {
            promptBuilder.append("- NONE").append("\n");
        }
        promptBuilder.append("\n");
    }

    /**
     * 为 Prompt 证据段挑选若干条更贴题的证据句。
     *
     * @param question 用户问题
     * @param queryTokens 查询 token
     * @param queryArticleHit 查询命中
     * @return 贴题证据句
     */
    private List<String> buildPromptFocusSnippets(
            String question,
            List<String> queryTokens,
            QueryArticleHit queryArticleHit
    ) {
        if (queryArticleHit == null) {
            return List.of();
        }
        int snippetCount = looksLikeExactLookupQuestion(question)
                || looksLikeEnumerationQuestion(question)
                || looksLikeFlowQuestion(question)
                ? 2
                : 1;
        List<String> snippets = selectQuestionFocusedFallbackSnippets(
                question,
                queryArticleHit,
                queryTokens == null ? extractQueryTokens(question) : queryTokens,
                snippetCount
        );
        List<String> sanitizedSnippets = new ArrayList<String>();
        for (String snippet : snippets) {
            String normalizedSnippet = sanitizePromptEvidenceSnippet(snippet);
            if (!normalizedSnippet.isBlank()) {
                sanitizedSnippets.add(normalizedSnippet);
            }
        }
        return sanitizedSnippets;
    }

    /**
     * 按当前问题重新排序 Prompt 里的证据，优先展示更可能直接回答问题的命中。
     *
     * @param question 用户问题
     * @param queryArticleHits 原始命中
     * @param queryTokens 查询 token
     * @return 排序后的命中
     */
    private List<QueryArticleHit> sortPromptEvidenceHits(
            String question,
            List<QueryArticleHit> queryArticleHits,
            List<String> queryTokens
    ) {
        if (queryArticleHits == null || queryArticleHits.size() <= 1) {
            return queryArticleHits;
        }
        List<QueryArticleHit> sortedHits = new ArrayList<QueryArticleHit>(queryArticleHits);
        sortedHits.sort((leftHit, rightHit) -> {
            int scoreCompare = Integer.compare(
                    scoreQuestionFocusedFallbackHit(question, rightHit, queryTokens),
                    scoreQuestionFocusedFallbackHit(question, leftHit, queryTokens)
            );
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Double.compare(rightHit.getScore(), leftHit.getScore());
        });
        return sortedHits;
    }

    /**
     * 清理 Prompt 中的贴题证据句，避免换行和多余空白干扰模型读取。
     *
     * @param snippet 原始证据句
     * @return 清理后的证据句
     */
    private String sanitizePromptEvidenceSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        return SensitiveTextMasker.mask(
                snippet.replace("\r", " ")
                        .replace("\n", " ")
                        .replaceAll("\\s{2,}", " ")
                        .trim()
        );
    }

    /**
     * 追加问题中显式点名的精确标识，提醒模型逐项覆盖。
     *
     * @param promptBuilder Prompt 构建器
     * @param question 用户问题
     */
    private void appendReferentialFocusSection(StringBuilder promptBuilder, String question) {
        List<String> identifiers = extractRequestedReferentialIdentifiers(question);
        if (identifiers.isEmpty()) {
            return;
        }
        promptBuilder.append("REFERENTIAL FOCUS").append("\n");
        promptBuilder.append("- exact identifiers to cover: ").append(String.join(", ", identifiers)).append("\n\n");
    }

    /**
     * 构建模型失败时的确定性 Markdown 兜底答案。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return Markdown 答案
     */
    private String buildFallbackMarkdown(String question, List<QueryArticleHit> queryArticleHits) {
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        List<String> queryTokens = extractQueryTokens(question);
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# 查询回答").append("\n\n");
        markdownBuilder.append("## 问题").append("\n");
        markdownBuilder.append(question.trim()).append("\n\n");
        appendFallbackConclusion(markdownBuilder, question, fallbackHits, queryTokens);
        appendFallbackReferenceSection(markdownBuilder, question, fallbackHits, queryTokens);
        return markdownBuilder.toString().trim();
    }

    /**
     * 构建模型失败时的确定性修订 Markdown。
     *
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 修订 Markdown
     */
    private String buildFallbackRevisionMarkdown(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> queryTokens = extractQueryTokens(question);
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = groupHitsByEvidenceType(queryArticleHits);
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# 修订答案").append("\n\n");
        markdownBuilder.append("## 问题").append("\n");
        markdownBuilder.append(question.trim()).append("\n\n");
        markdownBuilder.append("## 修订结论").append("\n");
        markdownBuilder.append("- ").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        markdownBuilder.append("## 修订说明").append("\n");
        markdownBuilder.append("- 原答案摘要：").append(extractEvidenceSnippet(currentAnswer)).append("\n");
        markdownBuilder.append("- 纠正输入：").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        appendFallbackSection(markdownBuilder, "用户反馈证据", groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "结构化证据卡", groupedHits.get(QueryEvidenceType.FACT_CARD), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "源文件证据", groupedHits.get(QueryEvidenceType.SOURCE), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "图谱证据", groupedHits.get(QueryEvidenceType.GRAPH), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "文章背景证据", groupedHits.get(QueryEvidenceType.ARTICLE), queryArticleHits, queryTokens);
        return markdownBuilder.toString().trim();
    }

    /**
     * 追加 Markdown 兜底答案中的证据分组。
     *
     * @param markdownBuilder Markdown 构建器
     * @param title 标题
     * @param queryArticleHits 证据列表
     */
    private void appendFallbackSection(
            StringBuilder markdownBuilder,
            String title,
            List<QueryArticleHit> queryArticleHits,
            List<QueryArticleHit> citationCandidateHits,
            List<String> queryTokens
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return;
        }
        markdownBuilder.append("## ").append(title).append("\n");
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            markdownBuilder.append("- **").append(queryArticleHit.getTitle()).append("**");
            if (!queryArticleHit.getSourcePaths().isEmpty()) {
                markdownBuilder.append(" (").append(String.join(", ", queryArticleHit.getSourcePaths())).append(")");
            }
            markdownBuilder.append("：")
                    .append(SensitiveTextMasker.mask(selectFallbackEvidenceSnippet(queryArticleHit, queryTokens)))
                    .append(" ")
                    .append(resolveCitationLiteral(queryArticleHit, citationCandidateHits))
                    .append("\n");
        }
        markdownBuilder.append("\n");
    }

    /**
     * 追加 deterministic fallback 的结论段，优先先回答问题，再附证据说明。
     *
     * @param markdownBuilder Markdown 构建器
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendFallbackConclusion(
            StringBuilder markdownBuilder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        markdownBuilder.append("## 结论").append("\n");
        List<String> conclusionLines = buildFallbackConclusionLines(question, fallbackHits, queryTokens);
        if (conclusionLines.isEmpty()) {
            markdownBuilder.append("- 当前未找到与该问题直接相关的知识。").append("\n\n");
            return;
        }
        for (String conclusionLine : conclusionLines) {
            markdownBuilder.append("- ").append(SensitiveTextMasker.mask(conclusionLine)).append("\n");
        }
        markdownBuilder.append("\n");
    }

    /**
     * 追加 deterministic fallback 的参考说明，避免正文只有证据罗列。
     *
     * @param markdownBuilder Markdown 构建器
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendFallbackReferenceSection(
            StringBuilder markdownBuilder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return;
        }
        List<String> comparisonOptions = extractComparisonOptions(question);
        QueryArticleHit primaryHit = fallbackHits.get(0);
        markdownBuilder.append("## 参考说明").append("\n");
        for (int index = 0; index < fallbackHits.size(); index++) {
            QueryArticleHit fallbackHit = fallbackHits.get(index);
            if (index > 0
                    && comparisonOptions.size() < 2
                    && !shouldIncludeSecondaryFallbackHit(question, primaryHit, fallbackHit, queryTokens)) {
                continue;
            }
            String snippet = selectReferenceFallbackSnippet(question, fallbackHit, comparisonOptions, queryTokens);
            markdownBuilder.append("- **").append(fallbackHit.getTitle()).append("**");
            if (!fallbackHit.getSourcePaths().isEmpty()) {
                markdownBuilder.append(" (").append(String.join(", ", fallbackHit.getSourcePaths())).append(")");
            }
            markdownBuilder.append("：")
                    .append(SensitiveTextMasker.mask(snippet))
                    .append(" ")
                    .append(resolveCitationLiteral(fallbackHit, fallbackHits))
                    .append("\n");
        }
        markdownBuilder.append("\n");
    }

    /**
     * 为 deterministic fallback 构造更像最终回答的结论行。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 结论行
     */
    private List<String> buildFallbackConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        List<String> comparisonOptions = extractComparisonOptions(question);
        if (comparisonOptions.size() >= 2) {
            List<String> comparisonLines = buildComparisonFallbackConclusionLines(
                    comparisonOptions.get(0),
                    comparisonOptions.get(1),
                    fallbackHits,
                    queryTokens
            );
            if (!comparisonLines.isEmpty()) {
                return comparisonLines;
            }
        }
        return buildGeneralFallbackConclusionLines(question, fallbackHits, queryTokens);
    }

    /**
     * 为二选一问题生成更直接的 deterministic fallback 结论。
     *
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 结论行
     */
    private List<String> buildComparisonFallbackConclusionLines(
            String leftOption,
            String rightOption,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (leftOption.isBlank() || rightOption.isBlank() || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<QueryArticleHit> leftHits = new ArrayList<QueryArticleHit>();
        List<QueryArticleHit> rightHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String matchedOption = matchComparisonOption(fallbackHit, leftOption, rightOption);
            if (leftOption.equals(matchedOption)) {
                leftHits.add(fallbackHit);
                continue;
            }
            if (rightOption.equals(matchedOption)) {
                rightHits.add(fallbackHit);
            }
        }
        if (leftHits.isEmpty() || rightHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit leftRepresentativeHit = leftHits.get(0);
        QueryArticleHit rightRepresentativeHit = rightHits.get(0);
        String leftSnippet = trimTrailingFallbackPunctuation(
                selectOptionSpecificFallbackSnippet(leftRepresentativeHit, leftOption, queryTokens)
        );
        String rightSnippet = trimTrailingFallbackPunctuation(
                selectOptionSpecificFallbackSnippet(rightRepresentativeHit, rightOption, queryTokens)
        );
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("支持“"
                + leftOption
                + "”的材料提到："
                + leftSnippet
                + "。 "
                + joinConclusionCitations(leftHits));
        conclusionLines.add("支持“"
                + rightOption
                + "”的材料提到："
                + rightSnippet
                + "。 "
                + joinConclusionCitations(rightHits));
        String conflictSummaryCitations = joinConflictConclusionCitations(fallbackHits, preferredComparisonSummaryHits(
                leftOption,
                rightOption,
                leftHits,
                rightHits
        ));
        if (leftHits.size() == rightHits.size()) {
            if (!conflictSummaryCitations.isBlank()) {
                conclusionLines.add("因此，现有资料同时存在两种口径，暂时不能直接判定，需要继续核对原始实现。 "
                        + conflictSummaryCitations);
            }
            return conclusionLines;
        }
        String preferredOption = leftHits.size() > rightHits.size() ? leftOption : rightOption;
        if (!conflictSummaryCitations.isBlank()) {
            conclusionLines.add("因此，现有资料更偏向“"
                    + preferredOption
                    + "”，但相关说明的口径还没有完全收敛，仍需继续核对原始实现。 "
                    + conflictSummaryCitations);
        }
        return conclusionLines;
    }

    /**
     * 为冲突总结句挑选更稳的 citation，只引用同时覆盖“当前口径偏向”与“资料存在冲突”的证据。
     *
     * @param fallbackHits fallback 证据
     * @param preferredHits 倾向侧代表证据
     * @return citation 串
     */
    private String joinConflictConclusionCitations(
            List<QueryArticleHit> fallbackHits,
            List<QueryArticleHit> preferredHits
    ) {
        List<QueryArticleHit> conflictSignalHits = new ArrayList<QueryArticleHit>();
        if (fallbackHits != null) {
            for (QueryArticleHit fallbackHit : fallbackHits) {
                if (containsConflictSignal(fallbackHit)) {
                    conflictSignalHits.add(fallbackHit);
                }
            }
        }
        if (!conflictSignalHits.isEmpty()) {
            return joinConclusionCitations(conflictSignalHits);
        }
        return joinConclusionCitations(preferredHits);
    }

    /**
     * 返回冲突总结句优先使用的代表证据。
     *
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @param leftHits 左侧命中
     * @param rightHits 右侧命中
     * @return 代表证据
     */
    private List<QueryArticleHit> preferredComparisonSummaryHits(
            String leftOption,
            String rightOption,
            List<QueryArticleHit> leftHits,
            List<QueryArticleHit> rightHits
    ) {
        if (leftHits == null || rightHits == null || leftHits.isEmpty() || rightHits.isEmpty()) {
            return List.of();
        }
        String preferredOption = leftHits.size() >= rightHits.size() ? leftOption : rightOption;
        List<QueryArticleHit> preferredHits = leftHits.size() >= rightHits.size() ? leftHits : rightHits;
        List<QueryArticleHit> optionAwareHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit preferredHit : preferredHits) {
            if (matchesComparisonOption(buildFallbackEvidenceHaystack(preferredHit), preferredOption)) {
                optionAwareHits.add(preferredHit);
            }
        }
        if (!optionAwareHits.isEmpty()) {
            return optionAwareHits;
        }
        return List.of(preferredHits.get(0));
    }

    /**
     * 为普通问题生成 deterministic fallback 结论。
     *
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 结论行
     */
    private List<String> buildGeneralFallbackConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        List<String> conclusionLines = new ArrayList<String>();
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return conclusionLines;
        }
        QueryArticleHit primaryHit = fallbackHits.get(0);
        List<String> focusedFieldDefinitionLines = buildFocusedSpreadsheetFieldDefinitionConclusionLines(question, fallbackHits);
        if (!focusedFieldDefinitionLines.isEmpty()) {
            return focusedFieldDefinitionLines;
        }
        List<String> fieldDefinitionLines = buildSpreadsheetFieldDefinitionConclusionLines(question, primaryHit);
        if (!fieldDefinitionLines.isEmpty()) {
            return fieldDefinitionLines;
        }
        List<String> comparisonDifferenceLines = buildComparisonDifferenceConclusionLines(question, fallbackHits, queryTokens);
        if (!comparisonDifferenceLines.isEmpty()) {
            return comparisonDifferenceLines;
        }
        if (containsRequestedExactPathIdentifier(question)) {
            List<String> exactPathLines = buildExactPathConclusionLines(question, fallbackHits, queryTokens);
            if (!exactPathLines.isEmpty() && coversRequiredExactPathConclusion(question, exactPathLines)) {
                return exactPathLines;
            }
        }
        List<String> exactStructuredListLines = buildExactStructuredListConclusionLines(question, fallbackHits);
        if (!exactStructuredListLines.isEmpty()) {
            return exactStructuredListLines;
        }
        List<String> aggregatedConclusionLines = buildAggregatedEvidenceConclusionLines(question, fallbackHits, queryTokens);
        if (!aggregatedConclusionLines.isEmpty()
                && (!containsRequestedExactPathIdentifier(question)
                || coversRequiredExactPathConclusion(question, aggregatedConclusionLines))) {
            return aggregatedConclusionLines;
        }
        List<String> exactPathLines = buildExactPathConclusionLines(question, fallbackHits, queryTokens);
        if (!exactPathLines.isEmpty() && coversRequiredExactPathConclusion(question, exactPathLines)) {
            return exactPathLines;
        }
        if (looksLikeSetupChecklistQuestion(question)) {
            List<String> setupSnippets = selectQuestionFocusedFallbackSnippets(question, primaryHit, queryTokens, 4);
            List<String> setupSteps = extractSetupChecklistSteps(setupSnippets);
            if (!setupSteps.isEmpty()) {
                conclusionLines.add("当前可确认的信息是：启动前优先处理这几件事："
                        + String.join("；", setupSteps)
                        + " "
                        + joinConclusionCitations(List.of(primaryHit)));
                return conclusionLines;
            }
        }
        int desiredSnippetCount = desiredFallbackConclusionSnippetCount(question);
        List<String> primarySnippets = selectQuestionFocusedFallbackSnippets(
                question,
                primaryHit,
                queryTokens,
                desiredSnippetCount
        );
        if (!primarySnippets.isEmpty()) {
            for (int index = 0; index < primarySnippets.size(); index++) {
                String prefix = index == 0 ? "当前可确认的信息是：" : "同一份资料还给出：";
                conclusionLines.add(prefix
                        + primarySnippets.get(index)
                        + " "
                        + joinConclusionCitations(List.of(primaryHit)));
            }
            if (primarySnippets.size() > 1) {
                return conclusionLines;
            }
        }
        else {
            conclusionLines.add("当前可确认的信息是："
                    + selectFallbackEvidenceSnippet(primaryHit, queryTokens)
                    + " "
                    + joinConclusionCitations(List.of(primaryHit)));
        }
        if (fallbackHits.size() > 1 && shouldIncludeSecondaryFallbackHit(question, primaryHit, fallbackHits.get(1), queryTokens)) {
            QueryArticleHit secondaryHit = fallbackHits.get(1);
            conclusionLines.add("补充证据还提到："
                    + selectQuestionFocusedFallbackSnippet(question, secondaryHit, queryTokens)
                    + " "
                    + joinConclusionCitations(List.of(secondaryHit)));
        }
        return conclusionLines;
    }

    /**
     * 判断精确路径结论是否覆盖问题需要的契约维度。
     *
     * @param question 用户问题
     * @param exactPathLines 精确路径结论行
     * @return 覆盖返回 true
     */
    private boolean coversRequiredExactPathConclusion(String question, List<String> exactPathLines) {
        if (!requiresPathContractCompanion(question)) {
            return true;
        }
        if (exactPathLines == null || exactPathLines.isEmpty()) {
            return false;
        }
        for (String exactPathLine : exactPathLines) {
            if (containsPathContractSignal(exactPathLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否显式点名了需要解释的路径标识。
     *
     * @param question 用户问题
     * @return 点名路径标识返回 true
     */
    private boolean containsRequestedExactPathIdentifier(String question) {
        return !extractRequestedPathIdentifiers(question).isEmpty();
    }

    /**
     * 提取问题中显式点名的路径标识。
     *
     * @param question 用户问题
     * @return 路径标识
     */
    private List<String> extractRequestedPathIdentifiers(String question) {
        List<String> requestedPaths = new ArrayList<String>();
        for (String requestedIdentifier : extractRequestedReferentialIdentifiers(question)) {
            if (requestedIdentifier.contains("/")
                    && containsExactIdentifierSignal(requestedIdentifier)
                    && !requestedPaths.contains(requestedIdentifier)) {
                requestedPaths.add(requestedIdentifier);
            }
        }
        return requestedPaths;
    }

    /**
     * 为接口路径题优先选择最像真实 path 的证据句。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 路径题结论
     */
    private List<String> buildExactPathConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (!looksLikePathQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit bestHit = null;
        String bestSnippet = "";
        int bestScore = Integer.MIN_VALUE;
        List<EvidenceLineMatch> requestedPathMatches = new ArrayList<EvidenceLineMatch>();
        List<EvidenceLineMatch> fallbackPathMatches = new ArrayList<EvidenceLineMatch>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            List<String> snippets = selectExactPathCandidateLines(question, fallbackHit, queryTokens);
            for (String snippet : snippets) {
                boolean pathContractSnippet = requiresPathContractCompanion(question) && containsPathContractSignal(snippet);
                if ((!containsPathSignal(snippet) && !pathContractSnippet) || looksLikePathHeaderLine(snippet)) {
                    continue;
                }
                if (containsRequestedExactPathIdentifier(question)
                        && introducesUnrequestedPathForExactPathQuestion(question, snippet)) {
                    continue;
                }
                int snippetScore = scoreQuestionFocusedFallbackLine(question, snippet, snippet, queryTokens);
                EvidenceLineMatch evidenceLineMatch = new EvidenceLineMatch(fallbackHit, snippet, snippetScore);
                if (containsRequestedExactIdentifier(snippet, question)) {
                    requestedPathMatches.add(evidenceLineMatch);
                }
                else {
                    fallbackPathMatches.add(evidenceLineMatch);
                }
            }
        }
        List<EvidenceLineMatch> primaryMatches = requestedPathMatches.isEmpty() ? fallbackPathMatches : requestedPathMatches;
        for (EvidenceLineMatch primaryMatch : primaryMatches) {
            if (primaryMatch.getScore() > bestScore) {
                bestScore = primaryMatch.getScore();
                bestHit = primaryMatch.getQueryArticleHit();
                bestSnippet = primaryMatch.getLine();
            }
        }
        if (bestHit == null || bestSnippet.isBlank()) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedSemanticKeys = new LinkedHashSet<String>();
        appendAggregatedConclusionLine(
                question,
                new EvidenceLineMatch(bestHit, bestSnippet, bestScore),
                conclusionLines,
                selectedSemanticKeys
        );
        for (EvidenceLineMatch companionMatch : selectExactPathCompanionMatches(
                question,
                fallbackHits,
                queryTokens,
                selectedSemanticKeys
        )) {
            appendAggregatedConclusionLine(question, companionMatch, conclusionLines, selectedSemanticKeys);
            if (conclusionLines.size() >= desiredFallbackConclusionSnippetCount(question)) {
                break;
            }
        }
        return conclusionLines;
    }

    /**
     * 为显式 path 题收集候选行；除贴题摘句外，全篇补扫点名 path 与 path 契约行。
     *
     * @param question 用户问题
     * @param fallbackHit fallback 命中
     * @param queryTokens 查询 token
     * @return 候选行
     */
    private List<String> selectExactPathCandidateLines(
            String question,
            QueryArticleHit fallbackHit,
            List<String> queryTokens
    ) {
        List<String> candidates = new ArrayList<String>();
        if (fallbackHit == null) {
            return candidates;
        }
        candidates.addAll(selectQuestionFocusedFallbackSnippets(question, fallbackHit, queryTokens, 3));
        if (!containsRequestedExactPathIdentifier(question)) {
            return candidates;
        }
        for (String rawLine : selectFallbackContentLines(fallbackHit.getContent())) {
            String normalizedLine = normalizeFallbackLineCandidate(rawLine);
            if (normalizedLine.isBlank() || candidates.contains(normalizedLine)) {
                continue;
            }
            if (containsRequestedExactIdentifier(normalizedLine, question)
                    || containsPathContractSignal(normalizedLine)) {
                candidates.add(normalizedLine);
            }
        }
        return candidates;
    }

    /**
     * 为路径精确题补足同问题里的规则、变更或状态维度，避免只返回路径值。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @param selectedSemanticKeys 已选语义键
     * @return 补充事实
     */
    private List<EvidenceLineMatch> selectExactPathCompanionMatches(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            Set<String> selectedSemanticKeys
    ) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<EvidenceLineMatch> companionMatches = new ArrayList<EvidenceLineMatch>();
        int hitLimit = Math.min(8, fallbackHits.size());
        for (int hitIndex = 0; hitIndex < hitLimit; hitIndex++) {
            QueryArticleHit fallbackHit = fallbackHits.get(hitIndex);
            List<String> rawLines = new ArrayList<String>();
            rawLines.addAll(selectFallbackContentLines(fallbackHit.getContent()));
            if (requiresPathContractCompanion(question)) {
                rawLines.addAll(selectPathContractCandidateLines(fallbackHit));
            }
            for (String rawLine : rawLines) {
                String normalizedLine = normalizeFallbackLineCandidate(rawLine);
                if (normalizedLine.isBlank() || !isExactPathCompanionLine(question, normalizedLine)) {
                    continue;
                }
                String semanticKey = aggregatedEvidenceSemanticKey(question, normalizedLine);
                if (!semanticKey.isBlank()
                        && selectedSemanticKeys != null
                        && selectedSemanticKeys.contains(semanticKey)) {
                    continue;
                }
                int score = scoreQuestionFocusedFallbackLine(question, rawLine, normalizedLine, queryTokens);
                companionMatches.add(new EvidenceLineMatch(fallbackHit, normalizedLine, score));
            }
        }
        companionMatches.sort((leftMatch, rightMatch) -> Integer.compare(rightMatch.getScore(), leftMatch.getScore()));
        return companionMatches;
    }

    /**
     * 判断候选行是否能补充路径题里的规则、变更或状态维度。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选行
     * @return 可作为补充返回 true
     */
    private boolean isExactPathCompanionLine(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        if (requiresPathContractCompanion(question)
                && !containsPathContractSignal(normalizedLine)
                && !containsRequestedExactIdentifier(normalizedLine, question)) {
            return false;
        }
        if (requiresPathContractCompanion(question) && containsPathContractSignal(normalizedLine)) {
            return !introducesUnrequestedPathForExactPathQuestion(question, normalizedLine);
        }
        boolean requiredByRule = looksLikeRuleConstraintQuestion(question)
                && (containsRuleConstraintSignal(normalizedLine) || containsStrongConstraintSignal(normalizedLine));
        boolean requiredByChange = looksLikeChangeTrackingQuestion(question)
                && (containsChangeTrackingSignal(normalizedLine) || containsStrongConstraintSignal(normalizedLine));
        boolean requiredByStatus = looksLikeStatusQuestion(question)
                && containsStatusSignal(lowerCase(normalizedLine));
        if (!requiredByRule && !requiredByChange && !requiredByStatus) {
            return false;
        }
        return !introducesUnrequestedPathForExactPathQuestion(question, normalizedLine);
    }

    /**
     * 判断显式路径题是否需要优先补充接口契约类证据。
     *
     * @param question 用户问题
     * @return 需要契约证据返回 true
     */
    private boolean requiresPathContractCompanion(String question) {
        if (!containsRequestedExactPathIdentifier(question) || !looksLikePathQuestion(question)) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("path")
                || normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("改")
                || normalizedQuestion.contains("变")
                || normalizedQuestion.contains("一致")
                || normalizedQuestion.contains("兼容")
                || normalizedQuestion.contains("契约");
    }

    /**
     * 判断候选句是否表达 path / URL / endpoint 契约或兼容性约束。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中契约信号返回 true
     */
    private boolean containsPathContractSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        boolean containsPathWord = lowerCaseLine.contains("path")
                || lowerCaseLine.contains("路径")
                || lowerCaseLine.contains("url")
                || lowerCaseLine.contains("endpoint")
                || lowerCaseLine.contains("接口契约")
                || lowerCaseLine.contains("接口路径");
        if (!containsPathWord) {
            return false;
        }
        return lowerCaseLine.contains("一致")
                || lowerCaseLine.contains("兼容")
                || lowerCaseLine.contains("保持")
                || lowerCaseLine.contains("不变")
                || lowerCaseLine.contains("原路径")
                || lowerCaseLine.contains("旧路径")
                || lowerCaseLine.contains("沿用")
                || lowerCaseLine.contains("对齐")
                || lowerCaseLine.contains("不得")
                || lowerCaseLine.contains("不能")
                || lowerCaseLine.contains("不允许")
                || lowerCaseLine.contains("不可")
                || lowerCaseLine.contains("必须")
                || lowerCaseLine.contains("契约")
                || lowerCaseLine.contains("字节级");
    }

    /**
     * 判断补充行是否引入了与用户点名路径无关的其他路径。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选行
     * @return 引入无关路径返回 true
     */
    private boolean introducesUnrequestedPathForExactPathQuestion(String question, String normalizedLine) {
        List<String> requestedPaths = extractRequestedPathIdentifiers(question);
        if (requestedPaths.isEmpty()) {
            return false;
        }
        List<String> evidencePaths = extractEvidencePaths(List.of(normalizedLine));
        if (evidencePaths.isEmpty()) {
            return false;
        }
        for (String evidencePath : evidencePaths) {
            if (!containsIdentifierIgnoreCase(requestedPaths, evidencePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为多事实精确题跨命中聚合互补证据，避免首条摘要漏掉数值、子项或流程目标。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 聚合后的结论行
     */
    private List<String> buildAggregatedEvidenceConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (!shouldAggregateEvidenceConclusion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        if (looksLikeEnumerationQuestion(question)
                && !looksLikeStructuredFactQuestion(question)
                && !shouldAggregateEnumerationConclusion(question)) {
            return List.of();
        }
        int desiredCount = Math.max(2, desiredFallbackConclusionSnippetCount(question));
        if (looksLikePathQuestion(question)
                && (looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question))) {
            desiredCount = Math.max(3, desiredCount);
        }
        List<EvidenceLineMatch> rankedMatches = collectRankedEvidenceLineMatches(
                question,
                fallbackHits,
                queryTokens,
                Math.min(8, desiredCount + 2)
        );
        if (rankedMatches.size() < 2) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedSemanticKeys = new LinkedHashSet<String>();
        for (int index = 0; index < rankedMatches.size(); index++) {
            EvidenceLineMatch match = rankedMatches.get(index);
            appendAggregatedConclusionLine(question, match, conclusionLines, selectedSemanticKeys);
            if (conclusionLines.size() >= desiredCount) {
                return conclusionLines;
            }
            for (String companionLine : selectCompanionStructuredLines(question, match.getQueryArticleHit(), match.getLine(), 2)) {
                EvidenceLineMatch companionMatch = new EvidenceLineMatch(
                        match.getQueryArticleHit(),
                        companionLine,
                        match.getScore() - 1
                );
                appendAggregatedConclusionLine(question, companionMatch, conclusionLines, selectedSemanticKeys);
                if (conclusionLines.size() >= desiredCount) {
                    return conclusionLines;
                }
            }
        }
        return conclusionLines;
    }

    /**
     * 判断是否需要跨命中聚合多条事实。
     *
     * @param question 用户问题
     * @return 需要返回 true
     */
    private boolean shouldAggregateEvidenceConclusion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("哪些")
                || normalizedQuestion.contains("哪三个")
                || normalizedQuestion.contains("三个")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("批")
                || requiresPathContractCompanion(question)
                || (looksLikePathQuestion(question) && looksLikeRuleConstraintQuestion(question));
    }

    /**
     * 判断枚举题是否仍应走精确证据聚合，而不是退回普通枚举兜底。
     *
     * @param question 用户问题
     * @return 应聚合返回 true
     */
    private boolean shouldAggregateEnumerationConclusion(String question) {
        return looksLikePathQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeNumericQuestion(question)
                || looksLikeRuleConstraintQuestion(question);
    }

    /**
     * 跨命中收集已排序且去重后的证据行。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @param limit 最大条数
     * @return 证据行匹配结果
     */
    private List<EvidenceLineMatch> collectRankedEvidenceLineMatches(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            int limit
    ) {
        List<EvidenceLineMatch> matches = new ArrayList<EvidenceLineMatch>();
        Set<String> semanticKeys = new LinkedHashSet<String>();
        int hitLimit = Math.min(10, fallbackHits.size());
        int perHitLimit = Math.max(6, desiredFallbackConclusionSnippetCount(question));
        for (int hitIndex = 0; hitIndex < hitLimit; hitIndex++) {
            QueryArticleHit fallbackHit = fallbackHits.get(hitIndex);
            List<String> snippets = selectAggregationCandidateLines(question, fallbackHit, queryTokens, perHitLimit);
            for (String snippet : snippets) {
                if (looksLikeCurrentFactQuestion(lowerCase(question))
                        && !containsCurrentFactSignal(lowerCase(snippet))) {
                    continue;
                }
                if (!looksLikeUsefulAggregatedEvidenceLine(question, snippet)) {
                    continue;
                }
                String semanticKey = aggregatedEvidenceSemanticKey(question, snippet);
                if (semanticKey.isBlank() || semanticKeys.contains(semanticKey)) {
                    continue;
                }
                semanticKeys.add(semanticKey);
                int score = scoreQuestionFocusedFallbackLine(question, snippet, snippet, queryTokens);
                matches.add(new EvidenceLineMatch(fallbackHit, snippet, score));
            }
        }
        matches.sort((leftMatch, rightMatch) -> Integer.compare(rightMatch.getScore(), leftMatch.getScore()));
        if (matches.size() <= limit) {
            return matches;
        }
        return new ArrayList<EvidenceLineMatch>(matches.subList(0, limit));
    }

    /**
     * 为聚合回答收集候选事实行。
     *
     * @param question 用户问题
     * @param fallbackHit fallback 命中
     * @param queryTokens 查询 token
     * @param perHitLimit 每条命中内的基础候选数
     * @return 候选事实行
     */
    private List<String> selectAggregationCandidateLines(
            String question,
            QueryArticleHit fallbackHit,
            List<String> queryTokens,
            int perHitLimit
    ) {
        List<String> candidates = new ArrayList<String>();
        candidates.addAll(selectQuestionFocusedFallbackSnippets(question, fallbackHit, queryTokens, perHitLimit));
        if (fallbackHit == null) {
            return candidates;
        }
        for (String contentLine : selectFallbackContentLines(fallbackHit.getContent())) {
            String normalizedLine = normalizeFallbackLineCandidate(contentLine);
            if (normalizedLine.isBlank() || candidates.contains(normalizedLine)) {
                continue;
            }
            candidates.add(normalizedLine);
        }
        return candidates;
    }

    /**
     * 为聚合回答追加结论行，并按语义键去重。
     *
     * @param question 用户问题
     * @param match 候选事实
     * @param conclusionLines 输出结论
     * @param selectedSemanticKeys 已选语义键
     */
    private void appendAggregatedConclusionLine(
            String question,
            EvidenceLineMatch match,
            List<String> conclusionLines,
            Set<String> selectedSemanticKeys
    ) {
        if (match == null || conclusionLines == null || selectedSemanticKeys == null) {
            return;
        }
        String line = match.getLine();
        if (line == null || line.isBlank()) {
            return;
        }
        String semanticKey = aggregatedEvidenceSemanticKey(question, line);
        if (!semanticKey.isBlank() && selectedSemanticKeys.contains(semanticKey)) {
            return;
        }
        if (!semanticKey.isBlank()) {
            selectedSemanticKeys.add(semanticKey);
        }
        String prefix = conclusionLines.isEmpty() ? "当前可确认的信息是：" : "同一问题的补充事实是：";
        conclusionLines.add(prefix
                + line
                + " "
                + joinConclusionCitations(List.of(match.getQueryArticleHit())));
    }

    /**
     * 从同一命中里挑选与当前事实相邻的结构化补充行。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param primaryLine 已选主事实
     * @param limit 最多补充条数
     * @return 补充行
     */
    private List<String> selectCompanionStructuredLines(
            String question,
            QueryArticleHit queryArticleHit,
            String primaryLine,
            int limit
    ) {
        List<String> companionLines = new ArrayList<String>();
        if (queryArticleHit == null || primaryLine == null || primaryLine.isBlank() || limit <= 0) {
            return companionLines;
        }
        List<String> contentLines = selectFallbackContentLines(queryArticleHit.getContent());
        int primaryIndex = indexOfNormalizedFallbackLine(contentLines, primaryLine);
        if (primaryIndex < 0) {
            return companionLines;
        }
        int scanUpperBound = Math.min(contentLines.size(), primaryIndex + 8);
        for (int index = primaryIndex + 1; index < scanUpperBound; index++) {
            String normalizedLine = normalizeFallbackLineCandidate(contentLines.get(index));
            if (normalizedLine.isBlank()
                    || normalizedLine.equals(primaryLine)
                    || containsUncertainEvidenceSignal(lowerCase(normalizedLine))
                    || !looksLikeUsefulAggregatedEvidenceLine(question, normalizedLine)) {
                continue;
            }
            if (!looksLikeCompanionEvidenceLine(normalizedLine)) {
                continue;
            }
            if (!companionLines.contains(normalizedLine)) {
                companionLines.add(normalizedLine);
            }
            if (companionLines.size() >= limit) {
                break;
            }
        }
        return companionLines;
    }

    /**
     * 查找归一化候选行在原始内容行列表中的位置。
     *
     * @param contentLines 原始内容行
     * @param targetLine 目标候选行
     * @return 下标；未找到返回 -1
     */
    private int indexOfNormalizedFallbackLine(List<String> contentLines, String targetLine) {
        if (contentLines == null || contentLines.isEmpty() || targetLine == null || targetLine.isBlank()) {
            return -1;
        }
        for (int index = 0; index < contentLines.size(); index++) {
            String normalizedLine = normalizeFallbackLineCandidate(contentLines.get(index));
            if (targetLine.equals(normalizedLine)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 判断一条相邻候选是否更像主事实的结构化补充。
     *
     * @param normalizedLine 归一化候选句
     * @return 结构化补充返回 true
     */
    private boolean looksLikeCompanionEvidenceLine(String normalizedLine) {
        return containsPathSignal(normalizedLine)
                || containsStructuredLabelSignal(normalizedLine)
                || containsBatchOrOrdinalSignal(normalizedLine)
                || containsChangeTrackingSignal(normalizedLine)
                || containsCorrectionOrStatusSignal(lowerCase(normalizedLine))
                || containsFlowTransitionSignal(normalizedLine);
    }

    /**
     * 判断候选句是否适合作为聚合直答事实。
     *
     * @param question 用户问题
     * @param snippet 候选句
     * @return 适合返回 true
     */
    private boolean looksLikeUsefulAggregatedEvidenceLine(String question, String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return false;
        }
        if (looksLikePlantUmlDeclarationLine(snippet) || looksLikeLeadInSentence(snippet)) {
            return false;
        }
        String lowerCaseSnippet = lowerCase(snippet);
        if (lowerCaseSnippet.contains("应视为")
                || lowerCaseSnippet.contains("来源未展开")
                || lowerCaseSnippet.contains("不能进一步断言")
                || lowerCaseSnippet.contains("未提供校准依据")
                || containsUncertainEvidenceSignal(lowerCaseSnippet)) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
            if (looksLikePathQuestion(question)
                    && (looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question))) {
                if (containsRequestedExactPathIdentifier(question)
                        && introducesUnrequestedPathForExactPathQuestion(question, snippet)) {
                    return false;
                }
                return containsPathSignal(snippet)
                        || containsRequestedExactIdentifier(snippet, question)
                    || containsStrongConstraintSignal(snippet)
                    || containsRuleConstraintSignal(snippet)
                    || containsChangeTrackingSignal(snippet);
        }
        if (normalizedQuestion.contains("哪三个") || normalizedQuestion.contains("三个")) {
            return containsPathSignal(snippet)
                    || containsStructuredLabelSignal(snippet)
                    || containsBatchOrOrdinalSignal(snippet)
                    || containsCorrectionOrStatusSignal(lowerCase(snippet))
                    || containsChangeTrackingSignal(snippet)
                    || containsMachineIdentifierSignal(snippet);
        }
        if (looksLikeNumericQuestion(question) || expectsBatchOrOrdinalAnswer(normalizedQuestion)) {
            return snippet.matches("(?s).*\\d.*")
                    || containsBatchOrOrdinalSignal(snippet)
                    || containsCorrectionOrStatusSignal(lowerCase(snippet));
        }
        if (shouldCollectDistinctMachineIdentifiers(question) && containsMachineIdentifierSignal(snippet)) {
            return containsFlowTransitionSignal(snippet)
                    || containsPathSignal(snippet)
                    || containsMultipleHighSignalQuestionTokens(question, snippet);
        }
        if (looksLikePathQuestion(question)) {
            return containsPathSignal(snippet) || containsStrongConstraintSignal(snippet);
        }
        return containsMultipleHighSignalQuestionTokens(question, snippet);
    }

    /**
     * 判断候选证据是否带有不确定、推断或缺口语气。
     *
     * @param lowerCaseSnippet 小写候选句
     * @return 不确定候选返回 true
     */
    private boolean containsUncertainEvidenceSignal(String lowerCaseSnippet) {
        if (lowerCaseSnippet == null || lowerCaseSnippet.isBlank()) {
            return false;
        }
        return lowerCaseSnippet.contains("[推断]")
                || lowerCaseSnippet.contains("推断")
                || lowerCaseSnippet.contains("证据不足")
                || lowerCaseSnippet.contains("证据缺口")
                || lowerCaseSnippet.contains("未直接描述")
                || lowerCaseSnippet.contains("未直接提供")
                || lowerCaseSnippet.contains("有待补充")
                || lowerCaseSnippet.contains("无法形成");
    }

    /**
     * 生成聚合证据行的语义去重键。
     *
     * @param question 用户问题
     * @param snippet 候选句
     * @return 去重键
     */
    private String aggregatedEvidenceSemanticKey(String question, String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        List<String> paths = extractEvidencePaths(List.of(snippet));
        if (!paths.isEmpty()) {
            return String.join("|", paths);
        }
        List<String> identifiers = extractMachineIdentifiers(snippet);
        if (!identifiers.isEmpty()) {
            return String.join("|", identifiers);
        }
        if (looksLikePathQuestion(question)
                && (looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question))) {
            if (containsStrongConstraintSignal(snippet)) {
                return "strong-constraint";
            }
            if (containsRuleConstraintSignal(snippet)) {
                return "rule-constraint";
            }
            if (containsChangeTrackingSignal(snippet)) {
                return "change-tracking";
            }
        }
        Matcher labelMatcher = Pattern.compile("\\b\\d+[A-Za-z]\\b|\\b[A-Za-z]+\\d+[A-Za-z]?\\b").matcher(snippet);
        if (labelMatcher.find()) {
            return lowerCase(labelMatcher.group());
        }
        String compactSnippet = normalizeQuestionEchoText(snippet);
        return compactSnippet.length() <= 80 ? compactSnippet : compactSnippet.substring(0, 80);
    }

    /**
     * 从候选句中提取带分隔符的机器标识符。
     *
     * @param snippet 候选句
     * @return 标识列表
     */
    private List<String> extractMachineIdentifiers(String snippet) {
        List<String> identifiers = new ArrayList<String>();
        if (snippet == null || snippet.isBlank()) {
            return identifiers;
        }
        Matcher identifierMatcher = Pattern.compile("[A-Za-z0-9]+[-_][A-Za-z0-9][A-Za-z0-9_-]*").matcher(snippet);
        while (identifierMatcher.find()) {
            String identifier = lowerCase(identifierMatcher.group());
            if (!identifiers.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    /**
     * 为“差异/是否一致”类问题优先选择带对比结论的证据句。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     * @return 差异题结论
     */
    private List<String> buildComparisonDifferenceConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (!looksLikeComparisonQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit bestHit = null;
        String bestSnippet = "";
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit fallbackHit : fallbackHits) {
            List<String> snippets = selectQuestionFocusedFallbackSnippets(question, fallbackHit, queryTokens, 4);
            for (String snippet : snippets) {
                if (!containsComparisonSignal(snippet)) {
                    continue;
                }
                int snippetScore = scoreQuestionFocusedFallbackLine(question, snippet, snippet, queryTokens);
                if (snippetScore > bestScore) {
                    bestScore = snippetScore;
                    bestHit = fallbackHit;
                    bestSnippet = snippet;
                }
            }
        }
        if (bestHit == null || bestSnippet.isBlank()) {
            return List.of();
        }
        return List.of("当前可确认的信息是：" + bestSnippet + " " + joinConclusionCitations(List.of(bestHit)));
    }

    /**
     * 为“多个标签 + 多个路径”的结构化问题直接提取精确事实列表。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 精确列表结论
     */
    private List<String> buildExactStructuredListConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits
    ) {
        if (!looksLikeCompoundExactLookupQuestion(question)
                || !looksLikePathQuestion(question)
                || fallbackHits == null
                || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<String> expectedLabels = extractRequestedStructuredLabels(question);
        List<String> conclusionLines = new ArrayList<String>();
        Set<String> selectedPaths = new LinkedHashSet<String>();
        Set<String> selectedLabels = new LinkedHashSet<String>();
        int hitLimit = Math.min(6, fallbackHits.size());
        for (int hitIndex = 0; hitIndex < hitLimit; hitIndex++) {
            QueryArticleHit fallbackHit = fallbackHits.get(hitIndex);
            for (String rawLine : selectFallbackContentLines(fallbackHit.getContent())) {
                String normalizedLine = normalizeFallbackLineCandidate(rawLine);
                if (!looksLikeStructuredPathFactLine(normalizedLine)) {
                    continue;
                }
                List<String> linePaths = extractEvidencePaths(List.of(normalizedLine));
                if (linePaths.isEmpty()) {
                    continue;
                }
                List<String> lineLabels = extractStructuredLabels(List.of(normalizedLine));
                if (!expectedLabels.isEmpty() && lineLabels.isEmpty()) {
                    continue;
                }
                if (!expectedLabels.isEmpty() && !containsAnyExpectedLabel(lineLabels, expectedLabels)) {
                    continue;
                }
                String primaryPath = linePaths.get(0);
                if (selectedPaths.contains(primaryPath)) {
                    continue;
                }
                selectedPaths.add(primaryPath);
                selectedLabels.addAll(lineLabels);
                String prefix = conclusionLines.isEmpty() ? "当前可确认的信息是：" : "同一问题的补充事实是：";
                conclusionLines.add(prefix
                        + normalizedLine
                        + " "
                        + joinConclusionCitations(List.of(fallbackHit)));
                if (selectedPaths.size() >= 3
                        && (expectedLabels.isEmpty() || selectedLabels.containsAll(expectedLabels))) {
                    return conclusionLines;
                }
            }
        }
        if (conclusionLines.size() >= 2
                && (expectedLabels.isEmpty() || selectedLabels.containsAll(expectedLabels))) {
            return conclusionLines;
        }
        return List.of();
    }

    /**
     * 从候选证据中查找命中任一关键词的记录。
     *
     * @param fallbackHits 候选证据
     * @param keywords 关键词
     * @return 命中的证据；没有则返回 null
     */
    private QueryArticleHit findHitContainingAny(List<QueryArticleHit> fallbackHits, List<String> keywords) {
        if (fallbackHits == null || fallbackHits.isEmpty() || keywords == null || keywords.isEmpty()) {
            return null;
        }
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String haystack = lowerCase(fallbackHit.getTitle())
                    + " "
                    + lowerCase(extractDescription(fallbackHit.getMetadataJson()))
                    + " "
                    + lowerCase(fallbackHit.getContent());
            for (String keyword : keywords) {
                String normalizedKeyword = lowerCase(keyword);
                if (!normalizedKeyword.isBlank() && haystack.contains(normalizedKeyword)) {
                    return fallbackHit;
                }
            }
        }
        return null;
    }

    /**
     * 判断文本是否包含所有关键词。
     *
     * @param value 文本
     * @param keywords 关键词
     * @return 全部包含返回 true
     */
    private boolean containsAll(String value, List<String> keywords) {
        if (value == null || value.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String haystack = lowerCase(value);
        for (String keyword : keywords) {
            String normalizedKeyword = lowerCase(keyword);
            if (normalizedKeyword.isBlank() || !haystack.contains(normalizedKeyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 为 Excel/Markdown 表格编译出来的“报文字段定义”文章构造稳定 fallback。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 字段定义结论
     */
    private List<String> buildSpreadsheetFieldDefinitionConclusionLines(String question, QueryArticleHit primaryHit) {
        if (!looksLikeSpreadsheetFieldDefinitionQuestion(question, primaryHit)) {
            return List.of();
        }
        String citationLiteral = joinConclusionCitations(List.of(primaryHit));
        String content = primaryHit.getContent();
        List<FieldDefinitionTableSummary> tableSummaries = extractFieldDefinitionTableSummaries(content);
        if (tableSummaries.isEmpty()) {
            return List.of();
        }
        List<String> codeMappings = extractCodeMappings(content);
        List<String> conclusionLines = new ArrayList<String>();
        List<String> datasetSignals = new ArrayList<String>();
        for (FieldDefinitionTableSummary tableSummary : tableSummaries) {
            datasetSignals.add(tableSummary.getDisplayName());
        }
        if (!codeMappings.isEmpty()) {
            datasetSignals.add("编码对照");
        }
        if (!datasetSignals.isEmpty()) {
            conclusionLines.add("该资料给出了"
                    + String.join("、", datasetSignals)
                    + "等结构化字段定义。 "
                    + citationLiteral);
        }
        for (FieldDefinitionTableSummary tableSummary : tableSummaries) {
            conclusionLines.add(tableSummary.getDisplayName()
                    + "共 "
                    + tableSummary.getFieldDefinitions().size()
                    + " 个字段："
                    + String.join("；", tableSummary.getFieldDefinitions())
                    + "。 "
                    + citationLiteral);
        }
        if (!codeMappings.isEmpty()) {
            conclusionLines.add("编码对照包括："
                    + String.join("；", codeMappings)
                    + "。 "
                    + citationLiteral);
        }
        return conclusionLines;
    }

    /**
     * 为“指定字段/配置/枚举分别是什么”这类精确标识题构造通用 fallback。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 字段含义结论
     */
    private List<String> buildFocusedSpreadsheetFieldDefinitionConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits
    ) {
        if (!looksLikeFocusedReferentialDefinitionQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.isEmpty()) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        for (String requestedIdentifier : requestedIdentifiers) {
            FieldDefinitionMatch definitionMatch = findFieldDefinitionMatch(fallbackHits, requestedIdentifier);
            if (definitionMatch == null) {
                continue;
            }
            conclusionLines.add(definitionMatch.getDefinitionLine()
                    + " "
                    + joinConclusionCitations(List.of(definitionMatch.getQueryArticleHit())));
        }
        return conclusionLines;
    }

    /**
     * 判断问题是否属于字段名、状态码、枚举值、配置键等精确标识知识题。
     *
     * @param question 用户问题
     * @return 精确标识知识题返回 true
     */
    private boolean looksLikeReferentialKnowledgeQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.isEmpty()) {
            return false;
        }
        return normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("状态码")
                || normalizedQuestion.contains("枚举")
                || normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("参数")
                || normalizedQuestion.contains("报文")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("表示")
                || normalizedQuestion.contains("含义")
                || normalizedQuestion.contains("定义");
    }

    /**
     * 判断问题是否带有必须命中的精确标识。
     *
     * @param question 用户问题
     * @return 严格精确标识题返回 true
     */
    private boolean looksLikeStrictExactIdentifierQuestion(String question) {
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        for (String requestedIdentifier : requestedIdentifiers) {
            if (containsExactIdentifierSignal(requestedIdentifier)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为显式点名多个标识并询问定义/含义的题目。
     *
     * @param question 用户问题
     * @return 聚焦精确标识定义题返回 true
     */
    private boolean looksLikeFocusedReferentialDefinitionQuestion(String question) {
        if (!looksLikeReferentialKnowledgeQuestion(question)) {
            return false;
        }
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.size() < 2) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("表示")
                || normalizedQuestion.contains("含义")
                || normalizedQuestion.contains("定义")
                || normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("状态码")
                || normalizedQuestion.contains("枚举");
    }

    /**
     * 提取问题中显式点名、需要逐项覆盖的精确标识。
     *
     * @param question 用户问题
     * @return 标识列表，保持用户问题中的出现顺序
     */
    private List<String> extractRequestedReferentialIdentifiers(String question) {
        List<String> requestedIdentifiers = new ArrayList<String>();
        if (question == null || question.isBlank()) {
            return requestedIdentifiers;
        }
        for (String requestedPath : extractEvidencePaths(List.of(question))) {
            if (!containsIdentifierIgnoreCase(requestedIdentifiers, requestedPath)) {
                requestedIdentifiers.add(requestedPath);
            }
        }
        Matcher identifierMatcher = EXPLICIT_IDENTIFIER_PATTERN.matcher(question);
        while (identifierMatcher.find()) {
            String matchedIdentifier = identifierMatcher.group(1) == null
                    ? identifierMatcher.group(2)
                    : identifierMatcher.group(1);
            String rawIdentifier = matchedIdentifier == null
                    ? identifierMatcher.group()
                    : matchedIdentifier;
            String identifier = cleanupReferentialIdentifier(rawIdentifier);
            if (identifier.isBlank() || isGenericReferentialIdentifier(identifier)) {
                continue;
            }
            if (!containsIdentifierIgnoreCase(requestedIdentifiers, identifier)) {
                requestedIdentifiers.add(identifier);
            }
        }
        removeContextIdentifiersBeforeScopeMarker(question, requestedIdentifiers);
        removeContainerIdentifiersWhenSpecificFieldsExist(requestedIdentifiers);
        return requestedIdentifiers;
    }

    /**
     * 清理问题中提取出的标识文本。
     *
     * @param rawIdentifier 原始标识
     * @return 清理后的标识
     */
    private String cleanupReferentialIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return "";
        }
        return rawIdentifier
                .replaceAll("^[`'\"“”‘’]+", "")
                .replaceAll("[`'\"“”‘’？?。；;，,、:：]+$", "")
                .trim();
    }

    /**
     * 判断标识是否只是问题中的通用英文词，而不是需要回答的业务标识。
     *
     * @param identifier 标识
     * @return 通用词返回 true
     */
    private boolean isGenericReferentialIdentifier(String identifier) {
        String normalizedIdentifier = lowerCase(identifier);
        return List.of(
                "api",
                "http",
                "json",
                "xml",
                "excel",
                "xlsx",
                "docx",
                "pdf",
                "markdown",
                "md"
        ).contains(normalizedIdentifier);
    }

    /**
     * 判断标识是否包含路径、配置键、字段键等精确信号。
     *
     * @param identifier 标识
     * @return 包含精确信号返回 true
     */
    private boolean containsExactIdentifierSignal(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        return identifier.contains("_")
                || identifier.contains("-")
                || identifier.contains("=")
                || identifier.contains("/")
                || identifier.contains(".");
    }

    /**
     * 当问题里同时有 request/response 容器和更具体字段时，只保留具体字段。
     *
     * @param requestedIdentifiers 标识列表
     */
    private void removeContainerIdentifiersWhenSpecificFieldsExist(List<String> requestedIdentifiers) {
        if (requestedIdentifiers == null || requestedIdentifiers.size() <= 2) {
            return;
        }
        boolean hasSpecificIdentifier = false;
        for (String requestedIdentifier : requestedIdentifiers) {
            if (!isPayloadContainerIdentifier(requestedIdentifier)) {
                hasSpecificIdentifier = true;
                break;
            }
        }
        if (!hasSpecificIdentifier) {
            return;
        }
        requestedIdentifiers.removeIf(this::isPayloadContainerIdentifier);
    }

    /**
     * 移除“某系统/某 API 里 A、B 分别是什么”中范围标记前的上下文标识。
     *
     * @param question 用户问题
     * @param requestedIdentifiers 标识列表
     */
    private void removeContextIdentifiersBeforeScopeMarker(String question, List<String> requestedIdentifiers) {
        if (question == null || question.isBlank() || requestedIdentifiers == null || requestedIdentifiers.size() <= 1) {
            return;
        }
        int markerIndex = scopeMarkerIndex(question);
        if (markerIndex < 0) {
            return;
        }
        boolean hasIdentifierAfterMarker = false;
        for (String requestedIdentifier : requestedIdentifiers) {
            int identifierIndex = lowerCase(question).indexOf(lowerCase(requestedIdentifier));
            if (identifierIndex > markerIndex) {
                hasIdentifierAfterMarker = true;
                break;
            }
        }
        if (!hasIdentifierAfterMarker) {
            return;
        }
        requestedIdentifiers.removeIf(identifier -> {
            int identifierIndex = lowerCase(question).indexOf(lowerCase(identifier));
            return identifierIndex >= 0 && identifierIndex < markerIndex;
        });
    }

    /**
     * 查找“里/中”这类范围标记位置。
     *
     * @param question 用户问题
     * @return 标记下标；没有返回 -1
     */
    private int scopeMarkerIndex(String question) {
        int insideIndex = question.indexOf("里");
        if (insideIndex >= 0) {
            return insideIndex;
        }
        return question.indexOf("中");
    }

    /**
     * 判断标识是否更像请求/响应容器，而不是具体字段。
     *
     * @param identifier 标识
     * @return 容器标识返回 true
     */
    private boolean isPayloadContainerIdentifier(String identifier) {
        String normalizedIdentifier = lowerCase(identifier);
        return normalizedIdentifier.equals("request")
                || normalizedIdentifier.equals("response")
                || normalizedIdentifier.equals("requestdata")
                || normalizedIdentifier.equals("responsedata")
                || normalizedIdentifier.equals("requestbody")
                || normalizedIdentifier.equals("responsebody")
                || normalizedIdentifier.equals("payload")
                || normalizedIdentifier.equals("body")
                || normalizedIdentifier.equals("params")
                || normalizedIdentifier.equals("parameters")
                || normalizedIdentifier.equals("headers");
    }

    /**
     * 判断列表是否已包含大小写无关的同一标识。
     *
     * @param identifiers 已有标识
     * @param candidate 候选标识
     * @return 已存在返回 true
     */
    private boolean containsIdentifierIgnoreCase(List<String> identifiers, String candidate) {
        for (String identifier : identifiers) {
            if (lowerCase(identifier).equals(lowerCase(candidate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在候选证据中查找某个标识的最佳字段定义行。
     *
     * @param fallbackHits fallback 证据
     * @param identifier 标识
     * @return 字段定义匹配；没有则返回 null
     */
    private FieldDefinitionMatch findFieldDefinitionMatch(List<QueryArticleHit> fallbackHits, String identifier) {
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String definitionLine = buildFieldDefinitionLine(fallbackHit.getContent(), identifier);
            if (!definitionLine.isBlank()) {
                return new FieldDefinitionMatch(fallbackHit, definitionLine);
            }
        }
        return null;
    }

    /**
     * 从证据正文中构造某个标识的定义行。
     *
     * @param content 证据正文
     * @param identifier 标识
     * @return 定义行；证据不足时返回空串
     */
    private String buildFieldDefinitionLine(String content, String identifier) {
        if (content == null || content.isBlank() || identifier == null || identifier.isBlank()) {
            return "";
        }
        for (String rawLine : content.split("\\R")) {
            if (!lineContainsIdentifier(rawLine, identifier)) {
                continue;
            }
            String definitionLine = buildFieldDefinitionLineFromRawLine(rawLine, identifier);
            if (!definitionLine.isBlank()) {
                return definitionLine;
            }
        }
        return "";
    }

    /**
     * 判断一行文本是否包含指定精确标识。
     *
     * @param rawLine 原始行
     * @param identifier 标识
     * @return 包含返回 true
     */
    private boolean lineContainsIdentifier(String rawLine, String identifier) {
        if (rawLine == null || rawLine.isBlank() || identifier == null || identifier.isBlank()) {
            return false;
        }
        String normalizedLine = lowerCase(rawLine);
        String normalizedIdentifier = lowerCase(identifier);
        return normalizedLine.contains(normalizedIdentifier);
    }

    /**
     * 从 Markdown 表格行、CSV/TSV 行或普通文本行中抽取字段定义。
     *
     * @param rawLine 原始证据行
     * @param identifier 标识
     * @return 定义行；无法抽取时返回空串
     */
    private String buildFieldDefinitionLineFromRawLine(String rawLine, String identifier) {
        List<String> cells = splitStructuredDefinitionRow(rawLine);
        if (cells.size() >= 2) {
            String definitionLine = buildFieldDefinitionLineFromCells(cells, identifier);
            if (!definitionLine.isBlank()) {
                return definitionLine;
            }
        }
        String normalizedLine = normalizeFallbackLineCandidate(rawLine);
        if (normalizedLine.isBlank() || looksLikeHeadingOnlyFallbackLine(rawLine)) {
            return "";
        }
        return "`" + identifier + "`：" + trimTrailingFallbackPunctuation(normalizedLine);
    }

    /**
     * 切分结构化字段定义行，兼容 Markdown 表格、CSV 与 TSV。
     *
     * @param rawLine 原始行
     * @return 单元格
     */
    private List<String> splitStructuredDefinitionRow(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return List.of();
        }
        List<String> markdownCells = splitMarkdownTableRow(rawLine);
        if (!markdownCells.isEmpty()) {
            return markdownCells;
        }
        if (rawLine.contains("\t")) {
            return splitDelimitedDefinitionRow(rawLine, '\t');
        }
        if (rawLine.contains(",")) {
            return splitDelimitedDefinitionRow(rawLine, ',');
        }
        return List.of();
    }

    /**
     * 按分隔符切分单行字段定义，保留表格抽取后的空单元格位置。
     *
     * @param rawLine 原始行
     * @param delimiter 分隔符
     * @return 单元格
     */
    private List<String> splitDelimitedDefinitionRow(String rawLine, char delimiter) {
        List<String> cells = new ArrayList<String>();
        StringBuilder cellBuilder = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < rawLine.length(); index++) {
            char currentChar = rawLine.charAt(index);
            if (currentChar == '"') {
                quoted = !quoted;
                cellBuilder.append(currentChar);
                continue;
            }
            if (currentChar == delimiter && !quoted) {
                cells.add(cleanupMarkdownTableCell(cellBuilder.toString()));
                cellBuilder.setLength(0);
                continue;
            }
            cellBuilder.append(currentChar);
        }
        cells.add(cleanupMarkdownTableCell(cellBuilder.toString()));
        return cells;
    }

    /**
     * 基于结构化单元格构造字段定义。
     *
     * @param cells 单元格
     * @param identifier 标识
     * @return 定义行；无法抽取时返回空串
     */
    private String buildFieldDefinitionLineFromCells(List<String> cells, String identifier) {
        int identifierIndex = indexOfCellIdentifier(cells, identifier);
        if (identifierIndex < 0) {
            return "";
        }
        List<String> nonBlankTailCells = collectNonBlankTailCells(cells, identifierIndex + 1);
        if (nonBlankTailCells.isEmpty()) {
            return "";
        }
        String type = selectTypeCell(nonBlankTailCells);
        String length = selectLengthCell(nonBlankTailCells, type);
        String description = selectDescriptionCell(nonBlankTailCells, type, length);
        String enumValue = selectEnumCell(nonBlankTailCells, description);
        List<String> parts = new ArrayList<String>();
        if (!type.isBlank()) {
            parts.add("类型 `" + type + "`");
        }
        if (!length.isBlank()) {
            parts.add("长度 `" + length + "`");
        }
        if (!description.isBlank()) {
            parts.add(description);
        }
        if (!enumValue.isBlank()) {
            parts.add("枚举/取值：" + enumValue);
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "`" + identifier + "`：" + String.join("；", parts) + "。";
    }

    /**
     * 查找单元格中精确匹配标识的位置。
     *
     * @param cells 单元格
     * @param identifier 标识
     * @return 下标；未找到返回 -1
     */
    private int indexOfCellIdentifier(List<String> cells, String identifier) {
        String normalizedIdentifier = lowerCase(identifier);
        for (int index = 0; index < cells.size(); index++) {
            String normalizedCell = lowerCase(cleanupMarkdownTableCell(cells.get(index)));
            if (normalizedCell.equals(normalizedIdentifier)) {
                return index;
            }
        }
        for (int index = 0; index < cells.size(); index++) {
            String normalizedCell = lowerCase(cleanupMarkdownTableCell(cells.get(index)));
            if (normalizedCell.contains(normalizedIdentifier)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 收集字段名之后的有效单元格。
     *
     * @param cells 原始单元格
     * @param startIndex 起始下标
     * @return 非空尾部单元格
     */
    private List<String> collectNonBlankTailCells(List<String> cells, int startIndex) {
        List<String> tailCells = new ArrayList<String>();
        for (int index = Math.max(0, startIndex); index < cells.size(); index++) {
            String cell = cleanupMarkdownTableCell(cells.get(index));
            if (cell.isBlank() || looksLikeSpreadsheetUsageFlag(cell)) {
                continue;
            }
            tailCells.add(cell);
        }
        return tailCells;
    }

    /**
     * 选择类型单元格。
     *
     * @param tailCells 字段名后的单元格
     * @return 类型
     */
    private String selectTypeCell(List<String> tailCells) {
        if (tailCells.isEmpty()) {
            return "";
        }
        String firstCell = tailCells.get(0);
        if (!containsHanText(firstCell) && firstCell.length() <= 24) {
            return firstCell;
        }
        return "";
    }

    /**
     * 选择长度单元格。
     *
     * @param tailCells 字段名后的单元格
     * @param type 已选类型
     * @return 长度
     */
    private String selectLengthCell(List<String> tailCells, String type) {
        int startIndex = type.isBlank() ? 0 : 1;
        for (int index = startIndex; index < tailCells.size(); index++) {
            String cell = tailCells.get(index);
            if (looksLikeFieldLengthCell(cell)) {
                return cell;
            }
            if (containsHanText(cell)) {
                return "";
            }
        }
        return "";
    }

    /**
     * 选择说明单元格。
     *
     * @param tailCells 字段名后的单元格
     * @param type 已选类型
     * @param length 已选长度
     * @return 说明
     */
    private String selectDescriptionCell(List<String> tailCells, String type, String length) {
        for (String cell : tailCells) {
            if (cell.equals(type)
                    || cell.equals(length)
                    || looksLikeFieldLengthCell(cell)
                    || looksLikeSpreadsheetExampleValueCell(cell)) {
                continue;
            }
            if (containsHanText(cell) && !looksLikeEnumValueCell(cell)) {
                return trimLongDefinitionCell(cell);
            }
        }
        for (String cell : tailCells) {
            if (!cell.equals(type)
                    && !cell.equals(length)
                    && !looksLikeEnumValueCell(cell)
                    && !looksLikeSpreadsheetExampleValueCell(cell)) {
                return trimLongDefinitionCell(cell);
            }
        }
        return "";
    }

    /**
     * 选择枚举/取值单元格。
     *
     * @param tailCells 字段名后的单元格
     * @param description 已选说明
     * @return 枚举/取值
     */
    private String selectEnumCell(List<String> tailCells, String description) {
        for (String cell : tailCells) {
            if (cell.equals(description)) {
                continue;
            }
            if (looksLikeEnumValueCell(cell)) {
                return trimLongDefinitionCell(cell);
            }
        }
        return "";
    }

    /**
     * 判断单元格是否为字段长度。
     *
     * @param cell 单元格
     * @return 字段长度返回 true
     */
    private boolean looksLikeFieldLengthCell(String cell) {
        if (cell == null || cell.isBlank() || containsHanText(cell)) {
            return false;
        }
        return cell.length() <= 16 && cell.matches("[A-Za-z0-9_./ -]+");
    }

    /**
     * 判断单元格是否像枚举值。
     *
     * @param cell 单元格
     * @return 枚举值返回 true
     */
    private boolean looksLikeEnumValueCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return false;
        }
        return cell.matches(".*\\d{2,}.*[\\p{IsHan}A-Za-z].*")
                && (cell.contains(" ")
                || countOccurrences(cell, "；") > 0
                || countOccurrences(cell, "、") > 0
                || cell.matches(".*\\d{2}[^\\d].*\\d{2}.*"));
    }

    /**
     * 判断单元格是否只是“是否使用”等布尔标记。
     *
     * @param cell 单元格
     * @return 用法标记返回 true
     */
    private boolean looksLikeSpreadsheetUsageFlag(String cell) {
        String normalizedCell = lowerCase(cell);
        return normalizedCell.equals("是")
                || normalizedCell.equals("否")
                || normalizedCell.equals("y")
                || normalizedCell.equals("n")
                || normalizedCell.equals("yes")
                || normalizedCell.equals("no")
                || normalizedCell.equals("true")
                || normalizedCell.equals("false");
    }

    /**
     * 判断单元格是否只是示例值，而不是字段说明。
     *
     * @param cell 单元格
     * @return 示例值返回 true
     */
    private boolean looksLikeSpreadsheetExampleValueCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return false;
        }
        String normalizedCell = cell.replace("\"", "").trim();
        return normalizedCell.matches("\\d+(?:\\.\\d+)?")
                || (normalizedCell.length() <= 8
                && !containsHanText(normalizedCell)
                && normalizedCell.matches("[A-Za-z0-9_-]+"));
    }

    /**
     * 限制过长定义单元格，避免把整段 JSON 或表格余量塞进答案。
     *
     * @param cell 单元格
     * @return 裁剪后的单元格
     */
    private String trimLongDefinitionCell(String cell) {
        if (cell == null || cell.length() <= 180) {
            return cell == null ? "" : cell;
        }
        return cell.substring(0, 180).stripTrailing() + "...";
    }

    /**
     * 字段定义匹配结果。
     *
     * @author xiexu
     */
    private static final class FieldDefinitionMatch {

        private final QueryArticleHit queryArticleHit;

        private final String definitionLine;

        /**
         * 创建字段定义匹配结果。
         *
         * @param queryArticleHit 命中的证据
         * @param definitionLine 定义行
         */
        private FieldDefinitionMatch(QueryArticleHit queryArticleHit, String definitionLine) {
            this.queryArticleHit = queryArticleHit;
            this.definitionLine = definitionLine;
        }

        /**
         * 获取命中的证据。
         *
         * @return 命中的证据
         */
        private QueryArticleHit getQueryArticleHit() {
            return queryArticleHit;
        }

        /**
         * 获取定义行。
         *
         * @return 定义行
         */
        private String getDefinitionLine() {
            return definitionLine;
        }
    }

    /**
     * 字段定义表摘要。
     *
     * @author xiexu
     */
    private static final class FieldDefinitionTableSummary {

        private final String displayName;

        private final List<String> fieldDefinitions;

        /**
         * 创建字段定义表摘要。
         *
         * @param displayName 显示名称
         * @param fieldDefinitions 字段定义
         */
        private FieldDefinitionTableSummary(String displayName, List<String> fieldDefinitions) {
            this.displayName = displayName == null ? "" : displayName;
            this.fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
        }

        /**
         * 获取显示名称。
         *
         * @return 显示名称
         */
        private String getDisplayName() {
            return displayName;
        }

        /**
         * 获取字段定义。
         *
         * @return 字段定义
         */
        private List<String> getFieldDefinitions() {
            return fieldDefinitions;
        }
    }

    private boolean looksLikeSpreadsheetFieldDefinitionQuestion(String question, QueryArticleHit primaryHit) {
        if (question == null || question.isBlank() || primaryHit == null) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        if (!normalizedQuestion.contains("字段")) {
            return false;
        }
        if (!(normalizedQuestion.contains("定义")
                || normalizedQuestion.contains("报文")
                || normalizedQuestion.contains("有哪些")
                || normalizedQuestion.contains("哪些"))) {
            return false;
        }
        return !extractFieldDefinitionTableSummaries(primaryHit.getContent()).isEmpty();
    }

    /**
     * 从 Markdown 表格中抽取字段定义表摘要。
     *
     * @param content 证据正文
     * @return 字段定义表摘要
     */
    private List<FieldDefinitionTableSummary> extractFieldDefinitionTableSummaries(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<FieldDefinitionTableSummary> tableSummaries = new ArrayList<FieldDefinitionTableSummary>();
        String currentHeading = "";
        List<String> currentRows = new ArrayList<String>();
        for (String rawLine : content.split("\\R")) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.startsWith("#")) {
                addFieldDefinitionTableSummary(tableSummaries, currentHeading, currentRows);
                String headingCandidate = cleanupHeadingLine(normalizedLine);
                if (!isGenericFieldDefinitionSubheading(headingCandidate) || currentHeading.isBlank()) {
                    currentHeading = headingCandidate;
                }
                currentRows = new ArrayList<String>();
                continue;
            }
            if (normalizedLine.startsWith("|")) {
                currentRows.add(normalizedLine);
                continue;
            }
            if (!currentRows.isEmpty() && !normalizedLine.isBlank()) {
                addFieldDefinitionTableSummary(tableSummaries, currentHeading, currentRows);
                currentRows = new ArrayList<String>();
            }
        }
        addFieldDefinitionTableSummary(tableSummaries, currentHeading, currentRows);
        return tableSummaries;
    }

    /**
     * 添加字段定义表摘要。
     *
     * @param tableSummaries 表摘要列表
     * @param heading 表格附近标题
     * @param rawRows 原始表格行
     */
    private void addFieldDefinitionTableSummary(
            List<FieldDefinitionTableSummary> tableSummaries,
            String heading,
            List<String> rawRows
    ) {
        List<String> fieldDefinitions = extractFieldDefinitionRows(rawRows);
        if (fieldDefinitions.isEmpty()) {
            return;
        }
        String displayName = resolveFieldDefinitionTableName(heading, tableSummaries.size() + 1);
        tableSummaries.add(new FieldDefinitionTableSummary(displayName, fieldDefinitions));
    }

    /**
     * 从表格行抽取字段定义行。
     *
     * @param rawRows 原始表格行
     * @return 字段定义行
     */
    private List<String> extractFieldDefinitionRows(List<String> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }
        List<String> fieldDefinitions = new ArrayList<String>();
        for (String rawLine : rawRows) {
            List<String> cells = splitMarkdownTableRow(rawLine);
            if (!looksLikeNumberedFieldDefinitionRow(cells)) {
                continue;
            }
            String fieldName = cleanupMarkdownTableCell(cells.get(1));
            String type = cleanupMarkdownTableCell(cells.get(2));
            String length = cleanupMarkdownTableCell(cells.get(3));
            String description = cleanupMarkdownTableCell(cells.get(4));
            if (fieldName.isBlank()) {
                continue;
            }
            fieldDefinitions.add("`"
                    + fieldName
                    + "`（"
                    + type
                    + "/"
                    + length
                    + "，"
                    + description
                    + "）");
        }
        return fieldDefinitions;
    }

    /**
     * 判断是否为编号字段定义表格行。
     *
     * @param cells 单元格
     * @return 是字段定义行返回 true
     */
    private boolean looksLikeNumberedFieldDefinitionRow(List<String> cells) {
        return cells != null
                && cells.size() >= 5
                && cleanupMarkdownTableCell(cells.get(0)).matches("\\d+")
                && !cleanupMarkdownTableCell(cells.get(1)).isBlank();
    }

    /**
     * 解析字段定义表显示名称。
     *
     * @param heading 表格附近标题
     * @param tableIndex 表格序号
     * @return 显示名称
     */
    private String resolveFieldDefinitionTableName(String heading, int tableIndex) {
        String normalizedHeading = cleanupHeadingLine(heading);
        List<String> identifiers = extractBacktickIdentifiers(normalizedHeading);
        if (!identifiers.isEmpty()) {
            return "字段组 `" + identifiers.get(0) + "` ";
        }
        Matcher latinIdentifierMatcher = Pattern.compile("([A-Za-z][A-Za-z0-9_]{2,})").matcher(normalizedHeading);
        if (latinIdentifierMatcher.find()) {
            return "字段组 `" + latinIdentifierMatcher.group(1) + "` ";
        }
        if (!normalizedHeading.isBlank()) {
            return "字段组“" + normalizedHeading + "” ";
        }
        return "第 " + tableIndex + " 个字段组 ";
    }

    /**
     * 清理 Markdown 标题行。
     *
     * @param heading 标题行
     * @return 清理后的标题
     */
    private String cleanupHeadingLine(String heading) {
        if (heading == null || heading.isBlank()) {
            return "";
        }
        return heading.replaceFirst("^#+\\s*", "").trim();
    }

    /**
     * 提取反引号包裹的标识。
     *
     * @param value 原始文本
     * @return 标识列表
     */
    private List<String> extractBacktickIdentifiers(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("`([^`]+)`").matcher(value);
        List<String> identifiers = new ArrayList<String>();
        while (matcher.find()) {
            String identifier = matcher.group(1).trim();
            if (!identifier.isBlank()) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    /**
     * 判断是否为字段定义表的通用子标题。
     *
     * @param heading 标题
     * @return 通用子标题返回 true
     */
    private boolean isGenericFieldDefinitionSubheading(String heading) {
        String normalizedHeading = lowerCase(heading);
        return normalizedHeading.contains("字段通用属性")
                || normalizedHeading.contains("通用属性")
                || normalizedHeading.contains("字段属性");
    }

    /**
     * 从证据正文中抽取通用编码对照。
     *
     * @param content 证据正文
     * @return 编码对照
     */
    private List<String> extractCodeMappings(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> mappings = new ArrayList<String>();
        for (String rawLine : content.split("\\R")) {
            List<String> cells = splitMarkdownTableRow(rawLine);
            if (!looksLikeCodeMappingRow(cells)) {
                continue;
            }
            mappings.add("`" + cleanupMarkdownTableCell(cells.get(0)) + "`=" + cleanupMarkdownTableCell(cells.get(1)));
        }
        return mappings;
    }

    /**
     * 判断是否为编码对照行。
     *
     * @param cells 单元格
     * @return 是编码对照返回 true
     */
    private boolean looksLikeCodeMappingRow(List<String> cells) {
        if (cells == null || cells.size() != 2) {
            return false;
        }
        String code = cleanupMarkdownTableCell(cells.get(0));
        String meaning = cleanupMarkdownTableCell(cells.get(1));
        if (code.isBlank() || meaning.isBlank()) {
            return false;
        }
        return code.matches("[A-Za-z0-9_-]{1,12}") && containsHanText(meaning);
    }

    private List<String> splitMarkdownTableRow(String rawLine) {
        if (rawLine == null) {
            return List.of();
        }
        String line = rawLine.trim();
        if (!line.startsWith("|") || !line.endsWith("|") || line.matches("\\|\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|")) {
            return List.of();
        }
        String[] rawCells = line.substring(1, line.length() - 1).split("\\|", -1);
        List<String> cells = new ArrayList<String>();
        for (String rawCell : rawCells) {
            cells.add(rawCell.trim());
        }
        return cells;
    }

    private String cleanupMarkdownTableCell(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("**", "")
                .replace("`", "")
                .replace("<br>", " / ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 从 setup/checklist 类候选句里抽取真正适合展示的步骤项。
     *
     * @param snippets 候选句
     * @return 清洗后的步骤列表
     */
    private List<String> extractSetupChecklistSteps(List<String> snippets) {
        List<String> setupSteps = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return setupSteps;
        }
        for (String snippet : snippets) {
            String normalizedSnippet = stripOrderedListMarker(snippet);
            if (normalizedSnippet.isBlank()
                    || looksLikeLeadInSentence(normalizedSnippet)
                    || !containsSetupSignal(normalizedSnippet)) {
                continue;
            }
            setupSteps.add(trimTrailingFallbackPunctuation(normalizedSnippet));
            if (setupSteps.size() >= 4) {
                break;
            }
        }
        return setupSteps;
    }

    /**
     * 判断第二条 fallback 证据是否值得进入最终结论，避免无关旁证污染主回答。
     *
     * @param question 用户问题
     * @param primaryHit 首条证据
     * @param secondaryHit 第二条证据
     * @param queryTokens 查询 token
     * @return 值得展示返回 true
     */
    private boolean shouldIncludeSecondaryFallbackHit(
            String question,
            QueryArticleHit primaryHit,
            QueryArticleHit secondaryHit,
            List<String> queryTokens
    ) {
        if (secondaryHit == null) {
            return false;
        }
        int primaryScore = primaryHit == null ? Integer.MIN_VALUE : scoreQuestionFocusedFallbackHit(question, primaryHit, queryTokens);
        int secondaryScore = scoreQuestionFocusedFallbackHit(question, secondaryHit, queryTokens);
        String secondarySnippet = selectQuestionFocusedFallbackSnippet(question, secondaryHit, queryTokens);
        if (looksLikeCapabilityQuestion(question)) {
            List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
            return containsCapabilitySignal(secondarySnippet)
                    && matchesStructuredOrTitle(secondaryHit, highSignalTokens)
                    && secondaryScore >= primaryScore - 12;
        }
        if (looksLikeFlowQuestion(question)) {
            return containsFlowSignal(secondarySnippet) && secondaryScore >= primaryScore - 12;
        }
        if (looksLikeStatusQuestion(question)) {
            return containsStatusSignal(lowerCase(secondarySnippet)) && secondaryScore >= primaryScore - 12;
        }
        return secondaryScore >= Math.max(primaryScore - 10, 8);
    }

    /**
     * 从二选一问题中提取对比选项。
     *
     * @param question 用户问题
     * @return 选项列表
     */
    private List<String> extractComparisonOptions(String question) {
        List<String> comparisonOptions = new ArrayList<String>();
        if (question == null || question.isBlank() || !question.contains("还是")) {
            return comparisonOptions;
        }
        String[] rawOptions = question.split("还是", 2);
        if (rawOptions.length < 2) {
            return comparisonOptions;
        }
        String leftOption = cleanupComparisonOption(rawOptions[0], true);
        String rightOption = cleanupComparisonOption(rawOptions[1], false);
        if (leftOption.isBlank() || rightOption.isBlank()) {
            return comparisonOptions;
        }
        comparisonOptions.add(leftOption);
        comparisonOptions.add(rightOption);
        return comparisonOptions;
    }

    /**
     * 清理二选一问题里的选项文本。
     *
     * @param rawOption 原始选项
     * @param leftSide 是否为左侧选项
     * @return 清理后的选项
     */
    private String cleanupComparisonOption(String rawOption, boolean leftSide) {
        String normalizedOption = rawOption == null ? "" : rawOption.trim();
        if (leftSide) {
            normalizedOption = normalizedOption.replaceFirst("^.*(?:到底是|究竟是|是)", "");
        }
        normalizedOption = normalizedOption.replaceAll("[？?。！!]+$", "");
        return normalizedOption.trim();
    }

    /**
     * 判断命中更偏向哪个对比选项。
     *
     * @param fallbackHit fallback 证据
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @return 命中的选项；若都不匹配返回空字符串
     */
    private String matchComparisonOption(QueryArticleHit fallbackHit, String leftOption, String rightOption) {
        if (fallbackHit == null) {
            return "";
        }
        String haystack = buildFallbackEvidenceHaystack(fallbackHit);
        boolean matchLeft = matchesComparisonOption(haystack, leftOption);
        boolean matchRight = matchesComparisonOption(haystack, rightOption);
        if (matchLeft && !matchRight) {
            return leftOption;
        }
        if (matchRight && !matchLeft) {
            return rightOption;
        }
        return "";
    }

    /**
     * 拼出 fallback 命中的可检索文本，用于冲突判断与语义分析。
     *
     * @param fallbackHit fallback 命中
     * @return 小写 haystack
     */
    private String buildFallbackEvidenceHaystack(QueryArticleHit fallbackHit) {
        if (fallbackHit == null) {
            return "";
        }
        return lowerCase(selectFallbackEvidenceSnippet(fallbackHit, List.of()))
                + " "
                + lowerCase(fallbackHit.getTitle())
                + " "
                + lowerCase(extractDescription(fallbackHit.getMetadataJson()))
                + " "
                + lowerCase(fallbackHit.getContent());
    }

    /**
     * 为对比选项优先挑选更贴近该选项本身的证据句，而不是泛化摘要。
     *
     * @param queryArticleHit 查询命中
     * @param option 当前对比选项
     * @param fallbackTokens 问题级 token
     * @return 证据摘要
     */
    private String selectOptionSpecificFallbackSnippet(
            QueryArticleHit queryArticleHit,
            String option,
            List<String> fallbackTokens
    ) {
        List<String> optionTokens = extractQueryTokens(option);
        String optionSpecificLine = selectBestFallbackMatchedLine(
                selectMatchedLines(queryArticleHit.getContent(), optionTokens),
                optionTokens
        );
        if (!optionSpecificLine.isBlank()) {
            return optionSpecificLine;
        }
        return selectFallbackEvidenceSnippet(queryArticleHit, fallbackTokens);
    }

    /**
     * 判断证据文本是否命中某个对比选项，兼容中英混合表达。
     *
     * @param haystack 证据文本
     * @param option 对比选项
     * @return 命中返回 true
     */
    private boolean matchesComparisonOption(String haystack, String option) {
        String normalizedOption = lowerCase(option).replace(" ", "");
        String normalizedHaystack = lowerCase(haystack).replace(" ", "");
        if (normalizedOption.isBlank() || normalizedHaystack.isBlank()) {
            return false;
        }
        if (normalizedHaystack.contains(normalizedOption)) {
            return true;
        }
        List<String> optionTokens = QueryTokenExtractor.extract(option);
        for (String optionToken : optionTokens) {
            String normalizedToken = lowerCase(optionToken).replace(" ", "");
            if (!normalizedToken.isBlank() && normalizedHaystack.contains(normalizedToken)) {
                return true;
            }
        }
        if (normalizedOption.contains("乐观锁") && normalizedHaystack.contains("optimisticlocking")) {
            return true;
        }
        if (normalizedOption.contains("悲观锁") && normalizedHaystack.contains("pessimisticlocking")) {
            return true;
        }
        if (normalizedOption.contains("锁")) {
            String optionWithoutLock = normalizedOption.replace("锁", "");
            return !optionWithoutLock.isBlank()
                    && normalizedHaystack.contains(optionWithoutLock)
                    && normalizedHaystack.contains("lock");
        }
        return false;
    }

    /**
     * 判断命中中是否显式带有“冲突/不一致/需继续确认”这类总结信号。
     *
     * @param fallbackHit fallback 命中
     * @return 包含冲突信号返回 true
     */
    private boolean containsConflictSignal(QueryArticleHit fallbackHit) {
        String haystack = buildFallbackEvidenceHaystack(fallbackHit);
        return haystack.contains("冲突")
                || haystack.contains("不一致")
                || haystack.contains("conflict")
                || haystack.contains("需要继续确认")
                || haystack.contains("继续核对")
                || haystack.contains("无法确认")
                || haystack.contains("不能直接判定");
    }

    /**
     * 组装结论行要展示的 citation，优先使用更稳定的 article citation。
     *
     * @param fallbackHits fallback 证据
     * @return citation 串
     */
    private String joinConclusionCitations(List<QueryArticleHit> fallbackHits) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return "";
        }
        List<String> citationLiterals = new ArrayList<String>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String citationLiteral = resolveConclusionCitationLiteral(fallbackHit, fallbackHits);
            if (citationLiteral.isBlank() || citationLiterals.contains(citationLiteral)) {
                continue;
            }
            citationLiterals.add(citationLiteral);
        }
        return String.join("", citationLiterals);
    }

    /**
     * 为 deterministic fallback 选择更贴近问题、且尽量去重后的证据集合。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return fallback 证据集合
     */
    private List<QueryArticleHit> selectFallbackEvidenceHits(String question, List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return List.of();
        }
        List<QueryArticleHit> comparisonHits = selectComparisonFallbackEvidenceHits(question, queryArticleHits);
        if (!comparisonHits.isEmpty()) {
            return enrichPathContractCompanionHits(question, comparisonHits, queryArticleHits);
        }
        List<QueryArticleHit> complementaryHits = selectComplementaryEvidenceByQuestionTokens(question, queryArticleHits);
        if (!complementaryHits.isEmpty()) {
            return enrichPathContractCompanionHits(question, complementaryHits, queryArticleHits);
        }
        List<QueryArticleHit> sortedAllRelevantHits = deduplicateSortedFallbackEvidenceHits(
                question,
                sortFallbackEvidenceHits(question, filterFallbackEvidenceHits(queryArticleHits, question, false))
        );
        List<QueryArticleHit> allRelevantHits = shouldAggregateEvidenceConclusion(question)
                ? sortedAllRelevantHits
                : retainDirectStructuredEvidence(question, sortedAllRelevantHits);
        List<QueryArticleHit> preferredArticleHits = deduplicateSortedFallbackEvidenceHits(
                question,
                sortFallbackEvidenceHits(
                        question,
                        filterFallbackEvidenceHits(queryArticleHits, question, true)
                )
        );
        if (preferredArticleHits.isEmpty()) {
            return enrichPathContractCompanionHits(question, allRelevantHits, queryArticleHits);
        }
        List<QueryArticleHit> retainedArticleHits = shouldAggregateEvidenceConclusion(question)
                ? preferredArticleHits
                : retainDirectStructuredEvidence(question, preferredArticleHits);
        if (shouldPreferMixedEvidence(question, retainedArticleHits, allRelevantHits)) {
            return enrichPathContractCompanionHits(question, allRelevantHits, queryArticleHits);
        }
        return enrichPathContractCompanionHits(question, retainedArticleHits, queryArticleHits);
    }

    /**
     * 为二选一 / 对比题保留两侧选项各自命中的证据，避免全局问题 token 过滤掉其中一侧。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 对比证据
     */
    private List<QueryArticleHit> selectComparisonFallbackEvidenceHits(
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> comparisonOptions = extractComparisonOptions(question);
        if (comparisonOptions.size() < 2) {
            return List.of();
        }
        String leftOption = comparisonOptions.get(0);
        String rightOption = comparisonOptions.get(1);
        List<QueryArticleHit> comparisonHits = new ArrayList<QueryArticleHit>();
        boolean hasLeftHit = false;
        boolean hasRightHit = false;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            String matchedOption = matchComparisonOption(queryArticleHit, leftOption, rightOption);
            if (matchedOption.isBlank()) {
                continue;
            }
            addDistinctFallbackHit(comparisonHits, queryArticleHit);
            if (leftOption.equals(matchedOption)) {
                hasLeftHit = true;
            }
            if (rightOption.equals(matchedOption)) {
                hasRightHit = true;
            }
        }
        return hasLeftHit && hasRightHit ? comparisonHits : List.of();
    }

    /**
     * 为显式 path 契约题补充同源或异源的 path 契约证据。
     *
     * @param question 用户问题
     * @param selectedHits 已选证据
     * @param candidateHits 候选证据
     * @return 补充后的证据
     */
    private List<QueryArticleHit> enrichPathContractCompanionHits(
            String question,
            List<QueryArticleHit> selectedHits,
            List<QueryArticleHit> candidateHits
    ) {
        if (!requiresPathContractCompanion(question) || candidateHits == null || candidateHits.isEmpty()) {
            return selectedHits == null ? List.of() : selectedHits;
        }
        List<QueryArticleHit> enrichedHits = new ArrayList<QueryArticleHit>();
        if (selectedHits != null) {
            enrichedHits.addAll(selectedHits);
        }
        if (containsPathContractEvidence(enrichedHits)) {
            return enrichedHits;
        }
        List<QueryArticleHit> sortedCandidates = sortFallbackEvidenceHits(question, candidateHits);
        for (QueryArticleHit candidateHit : sortedCandidates) {
            if (!containsPathContractEvidence(List.of(candidateHit))) {
                continue;
            }
            addDistinctFallbackHit(question, enrichedHits, candidateHit);
            return enrichedHits;
        }
        return enrichedHits;
    }

    /**
     * 判断命中集合是否含有 path 契约证据。
     *
     * @param queryArticleHits 查询命中
     * @return 包含返回 true
     */
    private boolean containsPathContractEvidence(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return false;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            if (containsPathContractSignal(extractDescription(queryArticleHit.getMetadataJson()))) {
                return true;
            }
            for (String contentLine : selectFallbackContentLines(queryArticleHit.getContent())) {
                String normalizedLine = normalizeFallbackLineCandidate(contentLine);
                if (containsPathContractSignal(normalizedLine)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 为多主题问题保留互补证据，避免单篇结构化文档把另一组问题主体挤掉。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 能分别覆盖多个问题高信号词时返回互补候选，否则返回空集合
     */
    private List<QueryArticleHit> selectComplementaryEvidenceByQuestionTokens(
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (extractComparisonOptions(question).size() >= 2
                || shouldAggregateEvidenceConclusion(question)) {
            return List.of();
        }
        List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (highSignalTokens.size() < 2) {
            return List.of();
        }
        List<QueryArticleHit> sortedHits = deduplicateSortedFallbackEvidenceHits(
                question,
                sortFallbackEvidenceHits(question, filterFallbackEvidenceHits(queryArticleHits, question, false))
        );
        List<QueryArticleHit> candidates = sortedHits.isEmpty() ? queryArticleHits : sortedHits;
        List<QueryArticleHit> selectedHits = new ArrayList<QueryArticleHit>();
        QueryArticleHit firstSourceHit = firstSourceHit(candidates);
        if (firstSourceHit != null) {
            addDistinctFallbackHit(question, selectedHits, firstSourceHit);
        }
        for (String highSignalToken : highSignalTokens) {
            QueryArticleHit tokenHit = findHitContainingAny(candidates, List.of(highSignalToken));
            addDistinctFallbackHit(question, selectedHits, tokenHit);
        }
        return selectedHits.size() >= 2 ? selectedHits : List.of();
    }

    /**
     * 取第一条原文证据，避免 fallback 只保留摘要卡片而丢掉同源原文。
     *
     * @param queryArticleHits 查询命中
     * @return 原文命中
     */
    private QueryArticleHit firstSourceHit(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return null;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit != null && queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
                return queryArticleHit;
            }
        }
        return null;
    }

    /**
     * 按 fallback 规范去重追加证据。
     *
     * @param selectedHits 已选证据
     * @param fallbackHit 待追加证据
     */
    private void addDistinctFallbackHit(List<QueryArticleHit> selectedHits, QueryArticleHit fallbackHit) {
        if (fallbackHit == null) {
            return;
        }
        String canonicalKey = fallbackEvidenceCanonicalKey(fallbackHit);
        for (QueryArticleHit selectedHit : selectedHits) {
            if (fallbackEvidenceCanonicalKey(selectedHit).equals(canonicalKey)) {
                return;
            }
        }
        selectedHits.add(fallbackHit);
    }

    /**
     * 按问题感知去重追加 fallback 证据。
     *
     * @param question 用户问题
     * @param selectedHits 已选证据
     * @param fallbackHit 待追加证据
     */
    private void addDistinctFallbackHit(
            String question,
            List<QueryArticleHit> selectedHits,
            QueryArticleHit fallbackHit
    ) {
        if (fallbackHit == null) {
            return;
        }
        String canonicalKey = fallbackEvidenceCanonicalKey(question, fallbackHit);
        for (QueryArticleHit selectedHit : selectedHits) {
            if (fallbackEvidenceCanonicalKey(question, selectedHit).equals(canonicalKey)) {
                return;
            }
        }
        selectedHits.add(fallbackHit);
    }


    /**
     * 判断当前问题是否应优先采用更贴题的 source/graph 证据，而不是固定优先 article 摘要。
     *
     * @param question 用户问题
     * @param articlePreferredHits article/contribution 候选
     * @param allRelevantHits 全量候选
     * @return 应优先采用全量候选返回 true
     */
    private boolean shouldPreferMixedEvidence(
            String question,
            List<QueryArticleHit> articlePreferredHits,
            List<QueryArticleHit> allRelevantHits
    ) {
        if (allRelevantHits == null || allRelevantHits.isEmpty()) {
            return false;
        }
        if (articlePreferredHits == null || articlePreferredHits.isEmpty()) {
            return true;
        }
        if (shouldAggregateEvidenceConclusion(question)
                && containsEvidenceType(allRelevantHits, QueryEvidenceType.SOURCE)) {
            return true;
        }
        QueryArticleHit bestOverallHit = allRelevantHits.get(0);
        if (bestOverallHit.getEvidenceType() == QueryEvidenceType.ARTICLE
                || bestOverallHit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
            return false;
        }
        List<String> queryTokens = extractQueryTokens(question);
        int bestOverallScore = scoreQuestionFocusedFallbackHit(question, bestOverallHit, queryTokens);
        int bestArticleScore = scoreQuestionFocusedFallbackHit(question, articlePreferredHits.get(0), queryTokens);
        if (bestOverallScore > bestArticleScore) {
            return true;
        }
        return looksLikeStatusQuestion(question) && bestOverallScore >= bestArticleScore;
    }

    /**
     * 判断候选命中集合里是否包含指定证据类型。
     *
     * @param queryArticleHits 查询命中
     * @param evidenceType 证据类型
     * @return 包含返回 true
     */
    private boolean containsEvidenceType(List<QueryArticleHit> queryArticleHits, QueryEvidenceType evidenceType) {
        if (queryArticleHits == null || queryArticleHits.isEmpty() || evidenceType == null) {
            return false;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit != null && queryArticleHit.getEvidenceType() == evidenceType) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当首条命中已经能通过标题/结构化字段直接回答时，丢弃只在正文顺带提及实体的旁证，避免污染 fallback。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中
     * @return 收敛后的 fallback 命中
     */
    private List<QueryArticleHit> retainDirectStructuredEvidence(String question, List<QueryArticleHit> fallbackHits) {
        if (fallbackHits == null || fallbackHits.size() <= 1) {
            return fallbackHits == null ? List.of() : fallbackHits;
        }
        List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (highSignalTokens.isEmpty()) {
            return fallbackHits;
        }
        QueryArticleHit primaryHit = fallbackHits.get(0);
        if (!matchesStructuredOrTitle(primaryHit, highSignalTokens)) {
            return fallbackHits;
        }
        List<QueryArticleHit> retainedHits = new ArrayList<QueryArticleHit>();
        retainedHits.add(primaryHit);
        for (int index = 1; index < fallbackHits.size(); index++) {
            QueryArticleHit fallbackHit = fallbackHits.get(index);
            if (matchesStructuredOrTitle(fallbackHit, highSignalTokens)) {
                retainedHits.add(fallbackHit);
            }
        }
        return retainedHits;
    }

    /**
     * 按问题相关性与证据类型过滤 deterministic fallback 命中。
     *
     * @param queryArticleHits 查询命中
     * @param queryTokens 查询 token
     * @param preferArticleEvidence 是否仅保留 article / contribution 级证据
     * @return 过滤后的命中
     */
    private List<QueryArticleHit> filterFallbackEvidenceHits(
            List<QueryArticleHit> queryArticleHits,
            String question,
            boolean preferArticleEvidence
    ) {
        List<QueryArticleHit> filteredHits = new ArrayList<QueryArticleHit>();
        for (QueryArticleHit queryArticleHit : QueryEvidenceRelevanceSupport.filterRelevantHits(question, queryArticleHits)) {
            if (queryArticleHit == null) {
                continue;
            }
            if (preferArticleEvidence
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.CONTRIBUTION) {
                continue;
            }
            filteredHits.add(queryArticleHit);
        }
        if (filteredHits.isEmpty()) {
            filteredHits.addAll(selectQuestionScoredFallbackEvidenceHits(queryArticleHits, question, preferArticleEvidence));
        }
        return filteredHits;
    }

    /**
     * 当问题 token 与证据表述没有直接重叠时，使用通用候选句分值补选证据。
     *
     * @param queryArticleHits 查询命中
     * @param question 用户问题
     * @param preferArticleEvidence 是否仅保留 article / contribution 级证据
     * @return 补选证据
     */
    private List<QueryArticleHit> selectQuestionScoredFallbackEvidenceHits(
            List<QueryArticleHit> queryArticleHits,
            String question,
            boolean preferArticleEvidence
    ) {
        List<QueryArticleHit> scoredHits = new ArrayList<QueryArticleHit>();
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return scoredHits;
        }
        List<String> queryTokens = extractQueryTokens(question);
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            if (preferArticleEvidence
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE
                    && queryArticleHit.getEvidenceType() != QueryEvidenceType.CONTRIBUTION) {
                continue;
            }
            if (requiresRequestedIdentifierCoverage(question)
                    && !hitContainsRequestedIdentifier(question, queryArticleHit)) {
                continue;
            }
            int score = scoreQuestionFocusedFallbackHit(question, queryArticleHit, queryTokens);
            if (score >= 20) {
                addDistinctFallbackHit(scoredHits, queryArticleHit);
            }
        }
        return scoredHits;
    }

    /**
     * 判断问题是否点名了需要在证据里覆盖的英文或结构化标识。
     *
     * @param question 用户问题
     * @return 需要覆盖返回 true
     */
    private boolean requiresRequestedIdentifierCoverage(String question) {
        return !extractRequestedReferentialIdentifiers(question).isEmpty();
    }

    /**
     * 判断命中是否覆盖问题里点名的标识。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 覆盖返回 true
     */
    private boolean hitContainsRequestedIdentifier(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        String haystack = String.join(
                " ",
                lowerCase(queryArticleHit.getArticleKey()),
                lowerCase(queryArticleHit.getConceptId()),
                lowerCase(queryArticleHit.getTitle()),
                lowerCase(extractDescription(queryArticleHit.getMetadataJson())),
                lowerCase(queryArticleHit.getContent()),
                lowerCase(String.join(" ", queryArticleHit.getSourcePaths()))
        );
        for (String requestedIdentifier : extractRequestedReferentialIdentifiers(question)) {
            if (!requestedIdentifier.isBlank() && haystack.contains(lowerCase(requestedIdentifier))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对 fallback 证据按文档身份去重，并优先保留 article 级命中。
     *
     * @param queryArticleHits 查询命中
     * @return 去重后的命中
     */
    private List<QueryArticleHit> deduplicateFallbackEvidenceHits(List<QueryArticleHit> queryArticleHits) {
        Map<String, QueryArticleHit> hitsByCanonicalKey = new LinkedHashMap<String, QueryArticleHit>();
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            String canonicalKey = fallbackEvidenceCanonicalKey(queryArticleHit);
            QueryArticleHit existingHit = hitsByCanonicalKey.get(canonicalKey);
            if (existingHit == null || fallbackEvidencePriority(queryArticleHit) > fallbackEvidencePriority(existingHit)) {
                hitsByCanonicalKey.put(canonicalKey, queryArticleHit);
            }
        }
        return new ArrayList<QueryArticleHit>(hitsByCanonicalKey.values());
    }

    /**
     * 对已排序 fallback 命中按文档身份去重，保留排序更靠前的候选。
     *
     * @param sortedHits 已按问题相关性排序的命中
     * @return 去重后的命中
     */
    private List<QueryArticleHit> deduplicateSortedFallbackEvidenceHits(List<QueryArticleHit> sortedHits) {
        if (sortedHits == null || sortedHits.isEmpty()) {
            return List.of();
        }
        Map<String, QueryArticleHit> hitsByCanonicalKey = new LinkedHashMap<String, QueryArticleHit>();
        for (QueryArticleHit sortedHit : sortedHits) {
            if (sortedHit == null) {
                continue;
            }
            String canonicalKey = fallbackEvidenceCanonicalKey(sortedHit);
            hitsByCanonicalKey.putIfAbsent(canonicalKey, sortedHit);
        }
        return new ArrayList<QueryArticleHit>(hitsByCanonicalKey.values());
    }

    /**
     * 对已排序 fallback 命中按问题语义去重。
     *
     * @param question 用户问题
     * @param sortedHits 已排序命中
     * @return 去重后的命中
     */
    private List<QueryArticleHit> deduplicateSortedFallbackEvidenceHits(
            String question,
            List<QueryArticleHit> sortedHits
    ) {
        if (sortedHits == null || sortedHits.isEmpty()) {
            return List.of();
        }
        Map<String, QueryArticleHit> hitsByCanonicalKey = new LinkedHashMap<String, QueryArticleHit>();
        for (QueryArticleHit sortedHit : sortedHits) {
            if (sortedHit == null) {
                continue;
            }
            String canonicalKey = fallbackEvidenceCanonicalKey(question, sortedHit);
            hitsByCanonicalKey.putIfAbsent(canonicalKey, sortedHit);
        }
        return new ArrayList<QueryArticleHit>(hitsByCanonicalKey.values());
    }

    /**
     * 按问题相关性重排 fallback 证据，优先让更像“直接回答”的命中排前。
     *
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 重排后的命中
     */
    private List<QueryArticleHit> sortFallbackEvidenceHits(String question, List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.size() <= 1) {
            return queryArticleHits == null ? List.of() : queryArticleHits;
        }
        List<String> queryTokens = extractQueryTokens(question);
        List<QueryArticleHit> sortedHits = new ArrayList<QueryArticleHit>(queryArticleHits);
        sortedHits.sort((leftHit, rightHit) -> {
            int focusedSnippetCompare = Integer.compare(
                    scoreQuestionFocusedFallbackHit(question, rightHit, queryTokens),
                    scoreQuestionFocusedFallbackHit(question, leftHit, queryTokens)
            );
            if (focusedSnippetCompare != 0) {
                return focusedSnippetCompare;
            }
            int scoreCompare = Integer.compare(
                    QueryEvidenceRelevanceSupport.score(question, rightHit),
                    QueryEvidenceRelevanceSupport.score(question, leftHit)
            );
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int evidencePriorityCompare = Integer.compare(
                    fallbackEvidencePriority(rightHit),
                    fallbackEvidencePriority(leftHit)
            );
            if (evidencePriorityCompare != 0) {
                return evidencePriorityCompare;
            }
            return Double.compare(rightHit.getScore(), leftHit.getScore());
        });
        return sortedHits;
    }

    /**
     * 计算单条命中在当前问题下最优“事实句”的分值，用于 fallback 排序。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 最优事实句分值
     */
    private int scoreQuestionFocusedFallbackHit(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens
    ) {
        if (queryArticleHit == null) {
            return Integer.MIN_VALUE;
        }
        List<String> rawCandidates = new ArrayList<String>();
        rawCandidates.addAll(selectMatchedLines(queryArticleHit.getContent(), queryTokens));
        rawCandidates.addAll(selectStructuredJsonValueLines(queryArticleHit.getContent()));
        if (requiresPathContractCompanion(question)) {
            rawCandidates.addAll(selectPathContractCandidateLines(queryArticleHit));
        }
        if (looksLikeStructuredFactQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeCapabilityQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeEnumerationQuestion(question)) {
            rawCandidates.addAll(selectFallbackContentLines(queryArticleHit.getContent()));
        }
        int bestScore = Integer.MIN_VALUE;
        for (String rawCandidate : rawCandidates) {
            String normalizedCandidate = normalizeFallbackLineCandidate(rawCandidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            if (looksLikeQuestionEchoLine(question, normalizedCandidate)) {
                continue;
            }
            int candidateScore = scoreQuestionFocusedFallbackLine(question, rawCandidate, normalizedCandidate, queryTokens);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
            }
        }
        if (bestScore > Integer.MIN_VALUE && looksLikeStructuredFactQuestion(question)) {
            bestScore += scoreStructuredFactHitCoverage(question, rawCandidates);
        }
        if (looksLikeExactLookupQuestion(question)
                && queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE
                && bestScore > Integer.MIN_VALUE) {
            bestScore += 24;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE
                && lowerCase(queryArticleHit.getContent()).contains("review_status: needs_human_review")
                && bestScore > Integer.MIN_VALUE) {
            bestScore -= 18;
        }
        return bestScore;
    }

    /**
     * 计算结构化查值题在单条命中内的问题焦点覆盖和当前口径信号。
     *
     * @param question 用户问题
     * @param rawCandidates 候选行
     * @return 覆盖加分
     */
    private int scoreStructuredFactHitCoverage(String question, List<String> rawCandidates) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return 0;
        }
        List<String> focusTokens = extractStructuredFactFocusTokens(question);
        if (focusTokens.isEmpty()) {
            return 0;
        }
        int score = 0;
        boolean currentFactQuestion = looksLikeCurrentFactQuestion(lowerCase(question));
        boolean hasCurrentFactSignal = false;
        for (String focusToken : focusTokens) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (normalizedFocusToken.isBlank()) {
                continue;
            }
            boolean matchedFocus = false;
            boolean matchedCurrentFocus = false;
            for (String rawCandidate : rawCandidates) {
                String normalizedCandidate = normalizeFallbackLineCandidate(rawCandidate);
                if (normalizedCandidate.isBlank()) {
                    continue;
                }
                String lowerCaseCandidate = lowerCase(normalizedCandidate);
                if (containsCurrentFactSignal(lowerCaseCandidate)) {
                    hasCurrentFactSignal = true;
                }
                if (!lowerCaseCandidate.contains(normalizedFocusToken)) {
                    continue;
                }
                matchedFocus = true;
                if (currentFactQuestion && containsCurrentFactSignal(lowerCaseCandidate)) {
                    matchedCurrentFocus = true;
                }
            }
            if (matchedFocus) {
                score += 10;
            }
            if (matchedCurrentFocus) {
                score += 16;
            }
        }
        if (currentFactQuestion && hasCurrentFactSignal) {
            score += 16;
        }
        return score;
    }

    /**
     * 选择 deterministic fallback 中更适合展示给用户的证据摘要。
     *
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 证据摘要
     */
    private String selectFallbackEvidenceSnippet(QueryArticleHit queryArticleHit, List<String> queryTokens) {
        String matchedLine = selectBestFallbackMatchedLine(selectMatchedLines(queryArticleHit.getContent(), queryTokens), queryTokens);
        if (!matchedLine.isBlank()) {
            return matchedLine;
        }
        String description = extractDescription(queryArticleHit.getMetadataJson());
        if (!description.isEmpty()) {
            return stripEmbeddedCitationLiterals(description);
        }
        String contentLine = selectBestFallbackMatchedLine(selectFallbackContentLines(queryArticleHit.getContent()), queryTokens);
        if (!contentLine.isBlank()) {
            return contentLine;
        }
        return stripEmbeddedCitationLiterals(extractEvidenceSnippet(queryArticleHit.getContent()));
    }

    /**
     * 围绕当前问题挑选更像“最终回答”的证据句；配置/阈值题优先返回 key=value 类事实句。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @return 更贴题的证据句
     */
    private String selectQuestionFocusedFallbackSnippet(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens
    ) {
        List<String> snippets = selectQuestionFocusedFallbackSnippets(question, queryArticleHit, queryTokens, 1);
        if (!snippets.isEmpty()) {
            return snippets.get(0);
        }
        return selectFallbackEvidenceSnippet(queryArticleHit, queryTokens);
    }

    /**
     * 围绕当前问题挑选若干条最直接的证据句。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @param queryTokens 查询 token
     * @param limit 最大条数
     * @return 证据句列表
     */
    private List<String> selectQuestionFocusedFallbackSnippets(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> queryTokens,
            int limit
    ) {
        if (queryArticleHit == null || limit <= 0) {
            return List.of();
        }
        List<String> rawCandidates = new ArrayList<String>();
        rawCandidates.addAll(selectMatchedLines(queryArticleHit.getContent(), queryTokens));
        rawCandidates.addAll(selectStructuredJsonValueLines(queryArticleHit.getContent()));
        if (looksLikeStructuredFactQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeCapabilityQuestion(question)
                || looksLikeFlowQuestion(question)
                || looksLikeEnumerationQuestion(question)) {
            rawCandidates.addAll(selectFallbackContentLines(queryArticleHit.getContent()));
        }
        Map<String, Integer> scoredCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredFactCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredStatusCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredPolicyCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredOrdinalCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredFlowCandidates = new LinkedHashMap<String, Integer>();
        Map<String, Integer> scoredExactIdentifierCandidates = new LinkedHashMap<String, Integer>();
        for (String rawCandidate : rawCandidates) {
            String normalizedCandidate = normalizeFallbackLineCandidate(rawCandidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            if (looksLikeQuestionEchoLine(question, normalizedCandidate)) {
                continue;
            }
            int candidateScore = scoreQuestionFocusedFallbackLine(question, rawCandidate, normalizedCandidate, queryTokens);
            mergeCandidateScore(scoredCandidates, normalizedCandidate, candidateScore);
            if (looksLikeStructuredFactCandidate(question, normalizedCandidate)) {
                mergeCandidateScore(scoredFactCandidates, normalizedCandidate, candidateScore);
            }
            if (looksLikeStatusQuestion(question)
                    && containsStatusSignal(lowerCase(normalizedCandidate))) {
                mergeCandidateScore(scoredStatusCandidates, normalizedCandidate, candidateScore);
            }
            if ((looksLikeRuleConstraintQuestion(question)
                    && (containsRuleConstraintSignal(normalizedCandidate)
                    || containsStrongConstraintSignal(normalizedCandidate)
                    || containsChangeTrackingSignal(normalizedCandidate)
                    || containsComparisonSignal(normalizedCandidate)))
                    || (requiresPathContractCompanion(question) && containsPathContractSignal(normalizedCandidate))) {
                mergeCandidateScore(scoredPolicyCandidates, normalizedCandidate, candidateScore);
            }
            if (expectsBatchOrOrdinalAnswer(lowerCase(question))
                    && containsBatchOrOrdinalSignal(normalizedCandidate)) {
                mergeCandidateScore(scoredOrdinalCandidates, normalizedCandidate, candidateScore);
            }
            if (looksLikeFlowQuestion(question) && containsFlowSignal(normalizedCandidate)) {
                mergeCandidateScore(scoredFlowCandidates, normalizedCandidate, candidateScore);
            }
            if (containsRequestedExactIdentifier(normalizedCandidate, question)) {
                mergeCandidateScore(scoredExactIdentifierCandidates, normalizedCandidate, candidateScore);
            }
        }
        Map<String, Integer> preferredCandidates;
        if (!scoredExactIdentifierCandidates.isEmpty()) {
            preferredCandidates = mergePreferredCandidates(scoredExactIdentifierCandidates, scoredPolicyCandidates);
        }
        else if (expectsBatchOrOrdinalAnswer(lowerCase(question)) && !scoredOrdinalCandidates.isEmpty()) {
            preferredCandidates = scoredOrdinalCandidates;
        }
        else if (looksLikeRuleConstraintQuestion(question) && !scoredPolicyCandidates.isEmpty()) {
            preferredCandidates = scoredPolicyCandidates;
        }
        else if (looksLikeStatusQuestion(question) && !scoredStatusCandidates.isEmpty()) {
            preferredCandidates = scoredStatusCandidates;
        }
        else if (looksLikeFlowQuestion(question) && !scoredFlowCandidates.isEmpty()) {
            preferredCandidates = scoredFlowCandidates;
        }
        else if (looksLikeEnumerationQuestion(question) && !looksLikeStructuredFactQuestion(question)) {
            preferredCandidates = scoredCandidates;
        }
        else {
            preferredCandidates = scoredFactCandidates.isEmpty() ? scoredCandidates : scoredFactCandidates;
        }
        if (preferredCandidates.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<String, Integer>> rankedCandidates =
                new ArrayList<Map.Entry<String, Integer>>(preferredCandidates.entrySet());
        rankedCandidates.sort((leftEntry, rightEntry) -> {
            int scoreCompare = Integer.compare(rightEntry.getValue(), leftEntry.getValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Integer.compare(leftEntry.getKey().length(), rightEntry.getKey().length());
        });
        if (limit > 1 && shouldUseCoverageAwareFallbackSnippets(question)) {
            List<String> focusTokens = extractStructuredFactFocusTokens(question);
            return selectCoverageAwareStructuredFactSnippets(question, rankedCandidates, focusTokens, limit);
        }
        List<String> snippets = new ArrayList<String>();
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            snippets.add(stripEmbeddedCitationLiterals(rankedCandidate.getKey()));
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets;
    }

    /**
     * 为显式 path 契约题补充全篇契约候选，避免长文档前部的相邻接口列表截断后续约束行。
     *
     * @param queryArticleHit 查询命中
     * @return path 契约候选行
     */
    private List<String> selectPathContractCandidateLines(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<String>();
        String description = extractDescription(queryArticleHit.getMetadataJson());
        if (containsPathContractSignal(description)) {
            candidates.add(description);
        }
        for (String contentLine : selectFallbackContentLines(queryArticleHit.getContent())) {
            String normalizedLine = normalizeFallbackLineCandidate(contentLine);
            if (!normalizedLine.isBlank() && containsPathContractSignal(normalizedLine)) {
                candidates.add(normalizedLine);
            }
        }
        return candidates;
    }

    /**
     * 合并主候选和补充候选，保留主候选优先顺序。
     *
     * @param primaryCandidates 主候选
     * @param secondaryCandidates 补充候选
     * @return 合并后的候选
     */
    private Map<String, Integer> mergePreferredCandidates(
            Map<String, Integer> primaryCandidates,
            Map<String, Integer> secondaryCandidates
    ) {
        Map<String, Integer> mergedCandidates = new LinkedHashMap<String, Integer>();
        if (primaryCandidates != null) {
            mergedCandidates.putAll(primaryCandidates);
        }
        if (secondaryCandidates != null) {
            for (Map.Entry<String, Integer> secondaryCandidate : secondaryCandidates.entrySet()) {
                mergeCandidateScore(mergedCandidates, secondaryCandidate.getKey(), secondaryCandidate.getValue().intValue());
            }
        }
        return mergedCandidates;
    }

    /**
     * 针对“分别是多少”这类问题，优先让多条答案覆盖不同问题焦点，避免同一配置项重复占满结果位。
     *
     * @param rankedCandidates 已排序候选句
     * @param focusTokens 问题焦点
     * @param limit 最大条数
     * @return 更均衡的结构化事实句
     */
    private List<String> selectCoverageAwareStructuredFactSnippets(
            String question,
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> focusTokens,
            int limit
    ) {
        List<String> snippets = new ArrayList<String>();
        List<String> selectedCandidates = new ArrayList<String>();
        List<String> coveredFocusTokens = new ArrayList<String>();
        if (focusTokens != null) {
            for (String focusToken : focusTokens) {
                if (coveredFocusTokens.contains(focusToken)) {
                    continue;
                }
                String matchedCandidate = selectBestRankedCandidateMatchingFocusToken(
                        rankedCandidates,
                        focusToken,
                        selectedCandidates
                );
                if (matchedCandidate.isBlank()) {
                    continue;
                }
                selectedCandidates.add(matchedCandidate);
                coveredFocusTokens.add(focusToken);
                snippets.add(stripEmbeddedCitationLiterals(matchedCandidate));
                if (snippets.size() >= limit) {
                    return snippets;
                }
            }
        }
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "number");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "status");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "ordinal");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "path");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "rule");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "change");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "flow");
        addBestCandidateForRequiredShape(question, rankedCandidates, selectedCandidates, snippets, limit, "identifier");
        addDistinctMachineIdentifierCandidates(question, rankedCandidates, selectedCandidates, snippets, limit);
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            if (selectedCandidates.contains(rankedCandidate.getKey())) {
                continue;
            }
            if (matchesAnyFocusToken(rankedCandidate.getKey(), coveredFocusTokens)) {
                continue;
            }
            snippets.add(stripEmbeddedCitationLiterals(rankedCandidate.getKey()));
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets;
    }

    /**
     * 为多事实题补足不同机器标识符，避免同一标识的候选句占满答案位。
     *
     * @param question 用户问题
     * @param rankedCandidates 已排序候选句
     * @param selectedCandidates 已选候选
     * @param snippets 输出片段
     * @param limit 最大条数
     */
    private void addDistinctMachineIdentifierCandidates(
            String question,
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> selectedCandidates,
            List<String> snippets,
            int limit
    ) {
        if (!shouldCollectDistinctMachineIdentifiers(question) || snippets.size() >= limit) {
            return;
        }
        Set<String> coveredIdentifiers = new LinkedHashSet<String>();
        for (String selectedCandidate : selectedCandidates) {
            coveredIdentifiers.addAll(extractMachineIdentifiers(selectedCandidate));
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            String candidate = rankedCandidate.getKey();
            if (selectedCandidates.contains(candidate)
                    || !containsMachineIdentifierSignal(candidate)) {
                continue;
            }
            List<String> identifiers = extractMachineIdentifiers(candidate);
            if (identifiers.isEmpty() || coveredIdentifiers.containsAll(identifiers)) {
                continue;
            }
            selectedCandidates.add(candidate);
            coveredIdentifiers.addAll(identifiers);
            snippets.add(stripEmbeddedCitationLiterals(candidate));
            if (snippets.size() >= limit) {
                return;
            }
        }
    }

    /**
     * 判断当前问题是否适合补足多个机器标识符候选。
     *
     * @param question 用户问题
     * @return 适合返回 true
     */
    private boolean shouldCollectDistinctMachineIdentifiers(String question) {
        String normalizedQuestion = lowerCase(question);
        return containsMachineIdentifierSignal(question)
                || normalizedQuestion.contains("标识")
                || normalizedQuestion.contains("名称")
                || normalizedQuestion.contains("编号")
                || normalizedQuestion.contains("编码")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("队列")
                || normalizedQuestion.contains("主题")
                || normalizedQuestion.contains("key")
                || normalizedQuestion.contains("id")
                || normalizedQuestion.contains("url")
                || normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("topic")
                || normalizedQuestion.contains("queue");
    }

    /**
     * 为结构化查值题补足数值、状态、批次等不同证据形态。
     *
     * @param question 用户问题
     * @param rankedCandidates 已排序候选句
     * @param selectedCandidates 已选候选
     * @param snippets 输出片段
     * @param limit 最大条数
     * @param shape 需要补足的证据形态
     */
    private void addBestCandidateForRequiredShape(
            String question,
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> selectedCandidates,
            List<String> snippets,
            int limit,
            String shape
    ) {
        if (snippets.size() >= limit || !requiresStructuredEvidenceShape(question, shape)) {
            return;
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            String candidate = rankedCandidate.getKey();
            if (selectedCandidates.contains(candidate) || !matchesStructuredEvidenceShape(candidate, shape)) {
                continue;
            }
            selectedCandidates.add(candidate);
            snippets.add(stripEmbeddedCitationLiterals(candidate));
            return;
        }
    }

    /**
     * 判断 fallback 摘句是否需要覆盖多种问题维度。
     *
     * @param question 用户问题
     * @return 需要覆盖多维度返回 true
     */
    private boolean shouldUseCoverageAwareFallbackSnippets(String question) {
        return looksLikeStructuredFactQuestion(question)
                || looksLikeRuleConstraintQuestion(question)
                || requiresPathContractCompanion(question)
                || looksLikeChangeTrackingQuestion(question)
                || expectsBatchOrOrdinalAnswer(lowerCase(question));
    }

    /**
     * 判断问题是否要求指定证据形态。
     *
     * @param question 用户问题
     * @param shape 证据形态
     * @return 要求返回 true
     */
    private boolean requiresStructuredEvidenceShape(String question, String shape) {
        String normalizedQuestion = lowerCase(question);
        if ("number".equals(shape)) {
            return looksLikeNumericQuestion(question);
        }
        if ("status".equals(shape)) {
            return normalizedQuestion.contains("结论")
                    || normalizedQuestion.contains("流量")
                    || normalizedQuestion.contains("调整")
                    || normalizedQuestion.contains("修正")
                    || normalizedQuestion.contains("降级")
                    || looksLikeStatusQuestion(question);
        }
        if ("ordinal".equals(shape)) {
            return expectsBatchOrOrdinalAnswer(normalizedQuestion);
        }
        if ("path".equals(shape)) {
            return looksLikePathQuestion(question);
        }
        if ("rule".equals(shape)) {
            return looksLikeRuleConstraintQuestion(question) || requiresPathContractCompanion(question);
        }
        if ("change".equals(shape)) {
            return looksLikeChangeTrackingQuestion(question);
        }
        if ("flow".equals(shape)) {
            return looksLikeFlowQuestion(question);
        }
        if ("identifier".equals(shape)) {
            return shouldCollectDistinctMachineIdentifiers(question);
        }
        return false;
    }

    /**
     * 判断候选句是否匹配指定证据形态。
     *
     * @param candidate 候选句
     * @param shape 证据形态
     * @return 匹配返回 true
     */
    private boolean matchesStructuredEvidenceShape(String candidate, String shape) {
        if ("number".equals(shape)) {
            return candidate != null && candidate.matches("(?s).*\\d.*");
        }
        if ("status".equals(shape)) {
            return containsCorrectionOrStatusSignal(lowerCase(candidate)) || containsStatusSignal(lowerCase(candidate));
        }
        if ("ordinal".equals(shape)) {
            return containsBatchOrOrdinalSignal(candidate);
        }
        if ("path".equals(shape)) {
            return containsPathSignal(candidate);
        }
        if ("rule".equals(shape)) {
            return containsRuleConstraintSignal(candidate)
                    || containsStrongConstraintSignal(candidate)
                    || containsPathContractSignal(candidate);
        }
        if ("change".equals(shape)) {
            return containsChangeTrackingSignal(candidate);
        }
        if ("flow".equals(shape)) {
            return containsFlowTransitionSignal(candidate);
        }
        if ("identifier".equals(shape)) {
            return containsMachineIdentifierSignal(candidate);
        }
        return false;
    }

    /**
     * 判断候选句是否已命中过已覆盖的问题焦点。
     *
     * @param candidate 候选句
     * @param focusTokens 已覆盖焦点
     * @return 命中返回 true
     */
    private boolean matchesAnyFocusToken(String candidate, List<String> focusTokens) {
        if (candidate == null || candidate.isBlank() || focusTokens == null || focusTokens.isEmpty()) {
            return false;
        }
        String normalizedCandidate = lowerCase(candidate);
        for (String focusToken : focusTokens) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (!normalizedFocusToken.isBlank() && normalizedCandidate.contains(normalizedFocusToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按焦点 token 从已排序候选句里挑选最先出现的命中项。
     *
     * @param rankedCandidates 已排序候选句
     * @param focusToken 问题焦点
     * @param selectedCandidates 已选候选句
     * @return 命中的候选句；没有则返回空串
     */
    private String selectBestRankedCandidateMatchingFocusToken(
            List<Map.Entry<String, Integer>> rankedCandidates,
            String focusToken,
            List<String> selectedCandidates
    ) {
        if (rankedCandidates == null || rankedCandidates.isEmpty()) {
            return "";
        }
        String normalizedFocusToken = lowerCase(focusToken);
        if (normalizedFocusToken.isBlank()) {
            return "";
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            String candidate = rankedCandidate.getKey();
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (selectedCandidates != null && selectedCandidates.contains(candidate)) {
                continue;
            }
            if (lowerCase(candidate).contains(normalizedFocusToken)) {
                return candidate;
            }
        }
        return "";
    }

    /**
     * 合并候选句分值，保留更高分版本。
     *
     * @param candidateScores 候选分值映射
     * @param candidate 候选句
     * @param score 分值
     */
    private void mergeCandidateScore(Map<String, Integer> candidateScores, String candidate, int score) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        Integer existingScore = candidateScores.get(candidate);
        if (existingScore == null || score > existingScore.intValue()) {
            candidateScores.put(candidate, Integer.valueOf(score));
        }
    }

    /**
     * 为贴题证据句增加“配置键 = 值”“当前阈值”等问题导向加权。
     *
     * @param question 用户问题
     * @param rawLine 原始候选行
     * @param normalizedLine 归一化后的候选行
     * @param preferredTokens 查询 token
     * @return 候选分值
     */
    private int scoreQuestionFocusedFallbackLine(
            String question,
            String rawLine,
            String normalizedLine,
            List<String> preferredTokens
    ) {
        int score = scoreFallbackLineCandidate(rawLine, normalizedLine, preferredTokens);
        if (looksLikeStructuredFactCandidate(question, normalizedLine)) {
            score += 12;
        }
        if (looksLikeQuestionEchoLine(question, normalizedLine)) {
            score -= 60;
        }
        if (looksLikeTableOfContentsLine(normalizedLine)) {
            score -= 80;
        }
        if (looksLikeEnumerationQuestion(question) && looksLikeEnumerationFactLine(rawLine, normalizedLine)) {
            score += 32;
        }
        if (looksLikeNumericQuestion(question) && containsCountConclusionSignal(normalizedLine)) {
            score += 28;
        }
        if (looksLikeNumericQuestion(question) && containsNumericAssignmentSignal(normalizedLine)) {
            score += 22;
        }
        if (looksLikeRuleConstraintQuestion(question) && containsRuleConstraintSignal(normalizedLine)) {
            score += 42;
        }
        if (looksLikeRuleConstraintQuestion(question) && containsStrongConstraintSignal(normalizedLine)) {
            score += 28;
        }
        if (looksLikeChangeTrackingQuestion(question) && containsChangeTrackingSignal(normalizedLine)) {
            score += 40;
        }
        if (looksLikeChangeTrackingQuestion(question) && containsAssignmentLikeMappingSignal(normalizedLine)) {
            score += 26;
        }
        if (requiresPathContractCompanion(question) && containsPathContractSignal(normalizedLine)) {
            score += 56;
            if (containsStrongConstraintSignal(normalizedLine)) {
                score += 20;
            }
            if (containsRuleConstraintSignal(normalizedLine)) {
                score += 12;
            }
        }
        if (looksLikeNumericQuestion(question) && looksLikeAdjacentEnumerationNoise(normalizedLine, question)) {
            score -= 18;
        }
        if (looksLikePathQuestion(question) && containsPathSignal(normalizedLine)) {
            score += 40;
        }
        if (looksLikePathQuestion(question) && containsStructuredLabelSignal(normalizedLine)) {
            score += 18;
        }
        if (looksLikePathQuestion(question) && looksLikePathHeaderLine(normalizedLine)) {
            score -= 36;
        }
        if (looksLikeComparisonQuestion(question) && containsComparisonSignal(normalizedLine)) {
            score += 34;
        }
        if (looksLikeComparisonQuestion(question)
                && containsMultipleHighSignalQuestionTokens(question, normalizedLine)) {
            score += 18;
        }
        if (looksLikeCompoundExactLookupQuestion(question)
                && containsPathSignal(normalizedLine)
                && containsStructuredLabelSignal(normalizedLine)) {
            score += 24;
        }
        if (containsRequestedExactIdentifier(normalizedLine, question)) {
            score += 80;
        }
        if (startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score += 8;
        }
        if (looksLikeNumericQuestion(question) && normalizedLine.matches(".*\\d.*")) {
            score += 6;
        }
        String normalizedQuestion = lowerCase(question);
        String lowerCaseLine = lowerCase(normalizedLine);
        if (looksLikeCurrentFactQuestion(normalizedQuestion) && containsCurrentFactSignal(lowerCaseLine)) {
            score += 20;
        }
        if (looksLikeSetupChecklistQuestion(question) && containsSetupSignal(normalizedLine)) {
            score += 24;
        }
        if (looksLikeCapabilityQuestion(question) && containsCapabilitySignal(normalizedLine)) {
            score += 50;
        }
        if (looksLikeFlowQuestion(question) && containsFlowSignal(normalizedLine)) {
            score += 24;
        }
        if (looksLikeFlowQuestion(question) && containsFlowTransitionSignal(normalizedLine)) {
            score += 28;
        }
        if (shouldCollectDistinctMachineIdentifiers(question) && containsMachineIdentifierSignal(normalizedLine)) {
            score += 18;
        }
        if (looksLikeFlowQuestion(question) && containsQuestionTokenInFlowTransition(question, normalizedLine)) {
            score += 24;
        }
        if (looksLikeFlowQuestion(question) && looksLikePlantUmlDeclarationLine(normalizedLine)) {
            score -= 28;
        }
        if (looksLikeStatusQuestion(question) && containsStatusSignal(lowerCaseLine)) {
            score += 24;
        }
        if (looksLikeStatusQuestion(question) && containsPathSignal(normalizedLine)) {
            score += 12;
        }
        if (looksLikeStatusQuestion(question) && looksLikeHeadingOnlyFallbackLine(rawLine)) {
            score -= 8;
        }
        if (looksLikeFlowQuestion(question) && looksLikeHeadingOnlyFallbackLine(rawLine)) {
            score -= 8;
        }
        if (looksLikeEnumerationQuestion(question) && looksLikeHeadingOnlyFallbackLine(rawLine)) {
            score -= 18;
        }
        if (looksLikeFlowQuestion(question)
                && looksLikeLeadInSentence(normalizedLine)
                && !containsFlowSignal(normalizedLine)) {
            score -= 10;
        }
        if (looksLikeEnumerationQuestion(question)
                && looksLikeLeadInSentence(normalizedLine)
                && !looksLikeEnumerationFactLine(rawLine, normalizedLine)) {
            score -= 14;
        }
        if (looksLikeCapabilityQuestion(question) && looksLikeGenericSummarySentence(normalizedLine)) {
            score -= 18;
        }
        if (looksLikeCapabilityQuestion(question) && startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score -= 50;
        }
        if (looksLikeSetupChecklistQuestion(question) && startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score -= 28;
        }
        if (normalizedQuestion.contains("配置")
                && (lowerCaseLine.startsWith("代码中的") || lowerCaseLine.contains("数值一致"))) {
            score -= 8;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && lowerCaseLine.contains("一般遵循")
                && !containsStrongConstraintSignal(normalizedLine)) {
            score -= 16;
        }
        return score;
    }

    /**
     * 判断候选行是否覆盖了用户问题里显式点名的精确标识。
     *
     * @param normalizedLine 候选行
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    private boolean containsRequestedExactIdentifier(String normalizedLine, String question) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        for (String requestedIdentifier : extractRequestedReferentialIdentifiers(question)) {
            if (containsExactIdentifierSignal(requestedIdentifier)
                    && lowerCaseLine.contains(lowerCase(requestedIdentifier))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前问题是否更像配置/阈值/参数类精确值问题。
     *
     * @param question 用户问题
     * @return 精确值问题返回 true
     */
    private boolean looksLikeStructuredFactQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("参数")
                || normalizedQuestion.contains("规范")
                || normalizedQuestion.contains("规则")
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("结论")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("归属")
                || normalizedQuestion.contains("对应")
                || normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("几")
                || normalizedQuestion.contains("数值")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("分别");
    }

    /**
     * 判断当前问题是否属于精确查值/精确结论类问题。
     *
     * @param question 用户问题
     * @return 精确查值题返回 true
     */
    private boolean looksLikeExactLookupQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return looksLikeStructuredFactQuestion(question)
                || looksLikeRuleConstraintQuestion(question)
                || looksLikeChangeTrackingQuestion(question)
                || normalizedQuestion.contains("是否一致")
                || normalizedQuestion.contains("是否生效")
                || normalizedQuestion.contains("是否启用");
    }

    /**
     * 判断问题是否同时在问多种结构化维度，而不是单一查值。
     *
     * @param question 用户问题
     * @return 多维查值题返回 true
     */
    private boolean looksLikeCompoundExactLookupQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        int dimensionCount = 0;
        if (containsPathSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (containsBatchOrOrdinalSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (containsChangeTrackingSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (containsCorrectionOrStatusSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (looksLikeNumericQuestion(question)) {
            dimensionCount++;
        }
        return dimensionCount >= 2;
    }

    /**
     * 判断当前问题是否主要在问具体数值。
     *
     * @param question 用户问题
     * @return 数值题返回 true
     */
    private boolean looksLikeNumericQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("几")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("数值")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("窗口")
                || normalizedQuestion.contains("分别");
    }

    /**
     * 判断候选句是否带有总数/修正结论信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsCountConclusionSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("只有")
                || lowerCaseLine.contains("一共")
                || lowerCaseLine.contains("总计")
                || lowerCaseLine.contains("合计")
                || lowerCaseLine.contains("共 ")
                || lowerCaseLine.contains("共")
                || lowerCaseLine.contains("不是")
                || lowerCaseLine.contains("修正");
    }

    /**
     * 判断候选句是否包含规则、命名或约束类原始事实信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsRuleConstraintSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("命名规范")
                || lowerCaseLine.contains("格式")
                || lowerCaseLine.contains("统一")
                || lowerCaseLine.contains("采用")
                || lowerCaseLine.contains("规则")
                || lowerCaseLine.contains("约束");
    }

    /**
     * 判断问题是否强调当前值或当前口径。
     *
     * @param normalizedQuestion 归一化问题
     * @return 当前事实题返回 true
     */
    private boolean looksLikeCurrentFactQuestion(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        return normalizedQuestion.contains("当前")
                || normalizedQuestion.contains("现在")
                || normalizedQuestion.contains("目前")
                || normalizedQuestion.contains("最新");
    }

    /**
     * 判断候选句是否带有当前值、建议值或生效口径信号。
     *
     * @param lowerCaseLine 归一化候选句
     * @return 当前事实信号返回 true
     */
    private boolean containsCurrentFactSignal(String lowerCaseLine) {
        if (lowerCaseLine == null || lowerCaseLine.isBlank()) {
            return false;
        }
        return lowerCaseLine.contains("当前")
                || lowerCaseLine.contains("现在")
                || lowerCaseLine.contains("目前")
                || lowerCaseLine.contains("最新")
                || lowerCaseLine.contains("建议值")
                || lowerCaseLine.contains("生效")
                || lowerCaseLine.contains("现行");
    }

    /**
     * 判断候选句是否包含“必须 / 禁止 / 强约束”这类强限制语义。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsStrongConstraintSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("强约束")
                || lowerCaseLine.contains("禁止")
                || lowerCaseLine.contains("必须")
                || lowerCaseLine.contains("不得");
    }

    /**
     * 判断候选句是否带有修正 / 合并 / 删除 / 改为 / 承接变化等变更语义。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsChangeTrackingSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("修正")
                || lowerCaseLine.contains("改为")
                || lowerCaseLine.contains("删除")
                || lowerCaseLine.contains("合并")
                || lowerCaseLine.contains("并入")
                || lowerCaseLine.contains("调整")
                || lowerCaseLine.contains("承接")
                || lowerCaseLine.contains("保持不变");
    }

    /**
     * 判断候选句是否带有“X = Y / A -> B / A→B”这类映射或重排信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsAssignmentLikeMappingSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.contains(" = ")
                || normalizedLine.contains("→")
                || normalizedLine.contains("->");
    }

    /**
     * 判断候选句是否更像与主问题无关的相邻枚举项。
     *
     * @param normalizedLine 归一化候选句
     * @param question 用户问题
     * @return 相邻枚举噪音返回 true
     */
    private boolean looksLikeAdjacentEnumerationNoise(String normalizedLine, String question) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        if (!lowerCaseLine.matches("^\\d+\\..*")) {
            return false;
        }
        List<String> questionTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        int matchedTokenCount = 0;
        for (String questionToken : questionTokens) {
            if (questionToken != null
                    && !questionToken.isBlank()
                    && lowerCaseLine.contains(lowerCase(questionToken))) {
                matchedTokenCount++;
            }
        }
        return matchedTokenCount == 0;
    }

    /**
     * 判断当前问题是否主要在问接口/文件/HTTP 路径。
     *
     * @param question 用户问题
     * @return 路径题返回 true
     */
    private boolean looksLikePathQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("url");
    }

    /**
     * 判断当前问题是否更像“启动前需要先做哪些准备/步骤”的 setup checklist 题。
     *
     * @param question 用户问题
     * @return setup 题返回 true
     */
    private boolean looksLikeSetupChecklistQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return (normalizedQuestion.contains("启动前")
                || (normalizedQuestion.contains("启动") && normalizedQuestion.contains("之前"))
                || (normalizedQuestion.contains("启动") && normalizedQuestion.contains("先")))
                && (normalizedQuestion.contains("需要")
                || normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("准备")
                || normalizedQuestion.contains("顺序"));
    }

    /**
     * 判断当前问题是否更像“规则 / 命名 / 约束 / 格式”这类题。
     *
     * @param question 用户问题
     * @return 规则题返回 true
     */
    private boolean looksLikeRuleConstraintQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("规范")
                || normalizedQuestion.contains("规则")
                || normalizedQuestion.contains("命名")
                || normalizedQuestion.contains("格式")
                || normalizedQuestion.contains("约束")
                || normalizedQuestion.contains("原则")
                || normalizedQuestion.contains("契约")
                || normalizedQuestion.contains("怎么处理")
                || normalizedQuestion.contains("必须")
                || normalizedQuestion.contains("禁止");
    }

    /**
     * 判断当前问题是否更像“修正 / 变更 / 合并 / 重排 / 承接变化”这类题。
     *
     * @param question 用户问题
     * @return 变更题返回 true
     */
    private boolean looksLikeChangeTrackingQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("修正后")
                || normalizedQuestion.contains("调整后")
                || normalizedQuestion.contains("原来的")
                || normalizedQuestion.contains("怎么处理")
                || normalizedQuestion.contains("合并")
                || normalizedQuestion.contains("并入")
                || normalizedQuestion.contains("改为")
                || normalizedQuestion.contains("变成")
                || normalizedQuestion.contains("删除")
                || normalizedQuestion.contains("承接");
    }

    /**
     * 判断当前问题是否更像“支持哪些方式 / 入口 / 能力”这类能力枚举题。
     *
     * @param question 用户问题
     * @return 能力题返回 true
     */
    private boolean looksLikeCapabilityQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("支持")
                || normalizedQuestion.contains("接入")
                || normalizedQuestion.contains("入口")
                || normalizedQuestion.contains("方式")
                || normalizedQuestion.contains("能力")
                || normalizedQuestion.contains("有哪些");
    }

    /**
     * 判断当前问题是否在要求枚举多个事实项。
     *
     * @param question 用户问题
     * @return 枚举题返回 true
     */
    private boolean looksLikeEnumerationQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("有哪些")
                || normalizedQuestion.contains("哪些")
                || normalizedQuestion.contains("几种")
                || normalizedQuestion.contains("几个")
                || normalizedQuestion.contains("列出")
                || normalizedQuestion.contains("包括")
                || normalizedQuestion.contains("包含")
                || normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("技巧")
                || normalizedQuestion.contains("形态")
                || normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("渠道")
                || normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("主要完成");
    }

    /**
     * 判断当前问题是否在要求对比差异。
     *
     * @param question 用户问题
     * @return 对比题返回 true
     */
    private boolean looksLikeComparisonQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("差异")
                || normalizedQuestion.contains("区别")
                || normalizedQuestion.contains("不同")
                || normalizedQuestion.contains("对比")
                || normalizedQuestion.contains("比较");
    }

    /**
     * 判断当前问题是否更像“当前状态/是否可用/是否已就绪”这类状态题。
     *
     * @param question 用户问题
     * @return 状态题返回 true
     */
    private boolean looksLikeStatusQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        boolean explicitStatusQuestion = normalizedQuestion.contains("状态")
                || normalizedQuestion.contains("可用")
                || normalizedQuestion.contains("是否可用")
                || normalizedQuestion.contains("是否已经")
                || normalizedQuestion.contains("是否已")
                || normalizedQuestion.contains("是否启用")
                || normalizedQuestion.contains("就绪")
                || normalizedQuestion.contains("实现状态")
                || normalizedQuestion.contains("当前是否")
                || normalizedQuestion.contains("实际已有")
                || normalizedQuestion.contains("待配置")
                || normalizedQuestion.contains("是否正常");
        if (explicitStatusQuestion) {
            return true;
        }
        if (looksLikeEnumerationQuestion(question) || looksLikeStructuredFactQuestion(question)) {
            return false;
        }
        return normalizedQuestion.contains("现在") || normalizedQuestion.contains("当前");
    }

    /**
     * 判断当前问题是否更像“运行流程 / 主链路 / 步骤”这类链路题。
     *
     * @param question 用户问题
     * @return 链路题返回 true
     */
    private boolean looksLikeFlowQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("流程")
                || normalizedQuestion.contains("链路")
                || normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("发送")
                || normalizedQuestion.contains("投递")
                || normalizedQuestion.contains("转发")
                || normalizedQuestion.contains("队列")
                || normalizedQuestion.contains("topic")
                || normalizedQuestion.contains("queue")
                || normalizedQuestion.contains("怎么跑")
                || normalizedQuestion.contains("怎么走")
                || normalizedQuestion.contains("运行路径");
    }

    /**
     * 判断候选句是否带有更像“状态说明”的信号词。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中状态信号返回 true
     */
    private boolean containsStatusSignal(String normalizedLine) {
        return normalizedLine.contains("当前可用")
                || normalizedLine.contains("已可用")
                || normalizedLine.contains("不可用")
                || normalizedLine.contains("已实现")
                || normalizedLine.contains("未实现")
                || normalizedLine.contains("尚未实现")
                || normalizedLine.contains("已有")
                || normalizedLine.contains("实际")
                || normalizedLine.contains("待配置")
                || normalizedLine.contains("未配置")
                || normalizedLine.contains("不存在")
                || normalizedLine.contains("未提供")
                || normalizedLine.contains("启用")
                || normalizedLine.contains("禁用")
                || normalizedLine.contains("就绪")
                || normalizedLine.contains("正常")
                || normalizedLine.contains("还没有")
                || normalizedLine.contains("未发现");
    }

    /**
     * 判断候选句是否带有更像“主链路 / 流程说明”的信号词。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中流程信号返回 true
     */
    private boolean containsFlowSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return containsFlowTransitionSignal(normalizedLine)
                || (normalizedLine.contains("`") && (lowerCaseLine.contains("主链") || lowerCaseLine.contains("正式")))
                || lowerCaseLine.contains("主链路")
                || lowerCaseLine.contains("链路")
                || lowerCaseLine.contains("流程")
                || lowerCaseLine.contains("步骤")
                || lowerCaseLine.contains("阶段")
                || lowerCaseLine.contains("进入")
                || lowerCaseLine.contains("提交")
                || lowerCaseLine.contains("启动");
    }

    /**
     * 判断候选句是否像“发送方 -> 接收方 : 载荷”的流程转移事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsFlowTransitionSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.matches("(?s).*([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9_ .-]*|\\[[^\\]]+])\\s*(?:->|→)\\s*([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9_ .-]*|\\[[^\\]]+])(?:\\s*[:：].*)?.*");
    }

    /**
     * 判断候选句是否包含带分隔符的机器标识符。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsMachineIdentifierSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.matches("(?s).*[A-Za-z0-9]+[-_][A-Za-z0-9][A-Za-z0-9_-]*.*");
    }

    /**
     * 判断流程转移句是否覆盖了问题中的高信号词。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsQuestionTokenInFlowTransition(String question, String normalizedLine) {
        if (!containsFlowTransitionSignal(normalizedLine)) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        for (String token : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            String normalizedToken = lowerCase(token);
            if (!normalizedToken.isBlank() && lowerCaseLine.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断候选句是否包含类似 8A / 5G / C.10 的结构化标签。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsStructuredLabelSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.matches("(?s).*\\b\\d+[A-Za-z]\\b.*");
    }

    /**
     * 判断候选句是否只是时序图声明，而不是一次实际转移动作。
     *
     * @param normalizedLine 归一化候选句
     * @return 声明行返回 true
     */
    private boolean looksLikePlantUmlDeclarationLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine).trim();
        return lowerCaseLine.startsWith("title ")
                || lowerCaseLine.startsWith("actor ")
                || lowerCaseLine.startsWith("participant ")
                || lowerCaseLine.startsWith("queue ")
                || lowerCaseLine.startsWith("note over ")
                || lowerCaseLine.startsWith("activate ")
                || lowerCaseLine.startsWith("deactivate ");
    }

    /**
     * 判断候选句是否包含更像路径题直接答案的信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中路径信号返回 true
     */
    private boolean containsPathSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return !extractEvidencePaths(List.of(normalizedLine)).isEmpty()
                || lowerCaseLine.contains("post ")
                || lowerCaseLine.contains("get ")
                || lowerCaseLine.contains("put ")
                || lowerCaseLine.contains("delete ")
                || lowerCaseLine.contains("http://")
                || lowerCaseLine.contains("https://");
    }

    /**
     * 判断候选句是否更像“接口路径 | 功能 | …”这类表头，而不是具体路径值。
     *
     * @param normalizedLine 归一化候选句
     * @return 表头行返回 true
     */
    private boolean looksLikePathHeaderLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("接口路径")
                && lowerCaseLine.contains("功能")
                && !containsPathSignal(lowerCaseLine.replace("接口路径", "").trim());
    }

    /**
     * 判断候选句是否更像“能力枚举 / 入口列表”这类直接回答。
     *
     * @param normalizedLine 归一化候选句
     * @return 能力信号返回 true
     */
    private boolean containsCapabilitySignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        int listSeparatorCount = countOccurrences(normalizedLine, "、")
                + countOccurrences(normalizedLine, " / ")
                + countOccurrences(normalizedLine, "·");
        int backtickCount = countOccurrences(normalizedLine, "`");
        return lowerCaseLine.contains("api")
                || lowerCaseLine.contains("cli")
                || lowerCaseLine.contains("mcp")
                || lowerCaseLine.contains("http")
                || lowerCaseLine.contains("web")
                || lowerCaseLine.contains("sdk")
                || lowerCaseLine.contains("入口")
                || lowerCaseLine.contains("接入")
                || listSeparatorCount >= 2
                || backtickCount >= 4;
    }

    /**
     * 判断候选句是否包含“不同/差异/不一致/而非”这类对比结论信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 对比信号返回 true
     */
    private boolean containsComparisonSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("不同")
                || lowerCaseLine.contains("差异")
                || lowerCaseLine.contains("不一致")
                || lowerCaseLine.contains("而非")
                || lowerCaseLine.contains("不是")
                || lowerCaseLine.contains("仅")
                || lowerCaseLine.contains("但是")
                || lowerCaseLine.contains("区别");
    }

    /**
     * 判断候选句是否像“指标/字段 = 数值”的数值事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    private boolean containsNumericAssignmentSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int delimiterIndex = structuredAssignmentDelimiterIndex(normalizedLine);
        if (delimiterIndex <= 0) {
            return normalizedLine.matches("(?s).*[:：=]\\s*[`*\"“”']*\\d{1,3}(?:,\\d{3})+.*");
        }
        String assignmentValue = structuredAssignmentValue(normalizedLine, delimiterIndex);
        return assignmentValue.matches("(?s).*\\d.*");
    }

    /**
     * 判断候选句是否同时覆盖了问题里的多个高信号标识。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 同时覆盖多个标识返回 true
     */
    private boolean containsMultipleHighSignalQuestionTokens(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int matchedCount = 0;
        String lowerCaseLine = lowerCase(normalizedLine);
        for (String highSignalToken : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            if (highSignalToken == null || highSignalToken.isBlank()) {
                continue;
            }
            if (lowerCaseLine.contains(lowerCase(highSignalToken))) {
                matchedCount++;
                if (matchedCount >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现指定 token。
     *
     * @param snippets 证据句
     * @param token 待匹配 token
     * @return 任一证据句命中返回 true
     */
    private boolean containsAnySnippetToken(List<String> snippets, String token) {
        if (snippets == null || snippets.isEmpty() || token == null || token.isBlank()) {
            return false;
        }
        for (String snippet : snippets) {
            if (snippet != null && snippet.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否至少包含一个数字。
     *
     * @param snippets 证据句
     * @return 任一证据句含数字返回 true
     */
    private boolean containsAnySnippetDigit(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (snippet != null && snippet.matches("(?s).*\\d.*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否期待批次或序号类答案。
     *
     * @param normalizedQuestion 归一化问题
     * @return 期待返回 true
     */
    private boolean expectsBatchOrOrdinalAnswer(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        return normalizedQuestion.contains("批")
                || normalizedQuestion.contains("第几")
                || normalizedQuestion.contains("哪一")
                || normalizedQuestion.contains("顺序");
    }

    /**
     * 判断若干证据句是否包含批次或序号信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyBatchOrOrdinalSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsBatchOrOrdinalSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文本是否包含批次或序号信号。
     *
     * @param value 文本
     * @return 命中返回 true
     */
    private boolean containsBatchOrOrdinalSignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = lowerCase(value);
        return normalizedValue.matches("(?s).*(?:第[一二三四五六七八九十0-9]+[批阶段步项条个]?|[一二三四五六七八九十0-9]+[批阶段步项条个]?).*")
                || normalizedValue.contains("批次")
                || normalizedValue.contains("顺序");
    }

    /**
     * 判断若干证据句是否包含状态信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyStatusSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsStatusSignal(lowerCase(snippet))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干证据句是否包含流程转移信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    private boolean containsAnyFlowTransitionSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsFlowTransitionSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断贴题证据句里是否出现“修正/确认/生效状态”这类结论信号。
     *
     * @param snippets 证据句
     * @return 命中结论信号返回 true
     */
    private boolean containsAnyCorrectionOrStatusSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsCorrectionOrStatusSignal(lowerCase(snippet))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单段文本是否包含“修正/确认/生效状态”这类结论信号。
     *
     * @param normalizedValue 归一化文本
     * @return 命中结论信号返回 true
     */
    private boolean containsCorrectionOrStatusSignal(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return false;
        }
        return normalizedValue.contains("修正为")
                || normalizedValue.contains("改为")
                || normalizedValue.contains("确认")
                || normalizedValue.contains("生效")
                || normalizedValue.contains("启用")
                || normalizedValue.contains("禁用")
                || normalizedValue.contains("结论");
    }

    /**
     * 判断候选句是否更像 setup/checklist 类前置步骤说明。
     *
     * @param normalizedLine 归一化候选句
     * @return setup 信号返回 true
     */
    private boolean containsSetupSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return normalizedLine.matches("^\\d+\\.\\s+.*")
                || lowerCaseLine.contains("准备好")
                || lowerCaseLine.contains("确认")
                || lowerCaseLine.contains("创建")
                || lowerCaseLine.contains("schema")
                || lowerCaseLine.contains("profile")
                || lowerCaseLine.contains("环境变量")
                || lowerCaseLine.contains("容器")
                || lowerCaseLine.contains("启动")
                || lowerCaseLine.contains("顺序")
                || lowerCaseLine.contains("步骤")
                || lowerCaseLine.contains("前置")
                || lowerCaseLine.contains("依赖");
    }

    /**
     * 判断候选句是否更像“引导后续列表/说明”的 lead-in，而不是直接结论。
     *
     * @param normalizedLine 归一化候选句
     * @return 导入句返回 true
     */
    private boolean looksLikeLeadInSentence(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String trimmedLine = normalizedLine.trim();
        if (!(trimmedLine.endsWith("：") || trimmedLine.endsWith(":"))) {
            return false;
        }
        return !trimmedLine.contains("->")
                && !trimmedLine.matches(".*\\d.*")
                && !trimmedLine.contains("`");
    }

    /**
     * 判断候选句是否更像枚举项，而不是章节标题或导语。
     *
     * @param rawLine 原始行
     * @param normalizedLine 归一化候选句
     * @return 枚举事实项返回 true
     */
    private boolean looksLikeEnumerationFactLine(String rawLine, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String normalizedRawLine = rawLine == null ? "" : rawLine.trim();
        if (normalizedRawLine.startsWith("|")
                || normalizedRawLine.startsWith("- ")
                || normalizedRawLine.startsWith("* ")
                || normalizedRawLine.startsWith("• ")
                || normalizedRawLine.matches("^\\d+\\.\\s+.*")) {
            return true;
        }
        if (startsWithDirectStructuredFactAssignment(normalizedLine)) {
            return true;
        }
        if (countOccurrences(normalizedLine, "；") >= 2 || countOccurrences(normalizedLine, "、") >= 2) {
            return true;
        }
        return normalizedLine.contains("：") && !looksLikeLeadInSentence(normalizedLine);
    }

    /**
     * 判断候选句是否只是复述用户问题或章节目录标题。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 问题回声句返回 true
     */
    private boolean looksLikeQuestionEchoLine(String question, String normalizedLine) {
        String questionEcho = normalizeQuestionEchoText(question);
        String lineEcho = normalizeQuestionEchoText(normalizedLine);
        if (questionEcho.length() < 6 || lineEcho.length() < 6) {
            return false;
        }
        return questionEcho.contains(lineEcho) || lineEcho.contains(questionEcho);
    }

    /**
     * 归一化文本用于判断问题回声。
     *
     * @param value 原始文本
     * @return 归一后的紧凑文本
     */
    private String normalizeQuestionEchoText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return lowerCase(value)
                .replaceAll("[#*`|\\[\\]（）()“”\"'：:；;，,。！？?\\s\\t\\r\\n-]+", "")
                .replaceAll("\\d+$", "")
                .trim();
    }

    /**
     * 去掉有序列表前缀，便于把步骤项拼成自然语言。
     *
     * @param snippet 原始片段
     * @return 去前缀后的片段
     */
    private String stripOrderedListMarker(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        return snippet.replaceFirst("^\\d+\\.\\s*", "").trim();
    }

    /**
     * 判断候选句是否更像项目总述 / 价值判断，而不是直接答案。
     *
     * @param normalizedLine 归一化候选句
     * @return 总述句返回 true
     */
    private boolean looksLikeGenericSummarySentence(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.startsWith("换句话说")
                || normalizedLine.startsWith("本质上")
                || normalizedLine.contains("更像一个")
                || normalizedLine.contains("而不是一个");
    }

    /**
     * 统计子串出现次数。
     *
     * @param value 原始字符串
     * @param token 待统计子串
     * @return 出现次数
     */
    private int countOccurrences(String value, String token) {
        if (value == null || value.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int fromIndex = 0;
        while (fromIndex >= 0) {
            fromIndex = value.indexOf(token, fromIndex);
            if (fromIndex < 0) {
                break;
            }
            count++;
            fromIndex += token.length();
        }
        return count;
    }

    /**
     * 判断候选句是否更像章节标题，而不是可直接给用户的状态结论。
     *
     * @param rawLine 原始候选句
     * @return 标题类候选返回 true
     */
    private boolean looksLikeHeadingOnlyFallbackLine(String rawLine) {
        if (rawLine == null) {
            return false;
        }
        String trimmedLine = rawLine.trim().toLowerCase(Locale.ROOT);
        return trimmedLine.startsWith("#")
                || trimmedLine.startsWith("<h1")
                || trimmedLine.startsWith("<h2")
                || trimmedLine.startsWith("<h3")
                || trimmedLine.startsWith("<h4");
    }

    /**
     * 判断候选句是否明显来自目录、页码或页分隔符。
     *
     * @param normalizedLine 归一化候选句
     * @return 目录行返回 true
     */
    private boolean looksLikeTableOfContentsLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String trimmedLine = normalizedLine.trim();
        String lowerCaseLine = lowerCase(trimmedLine);
        if ("目录".equals(trimmedLine) || lowerCaseLine.startsWith("=== page:")) {
            return true;
        }
        return trimmedLine.length() <= 120
                && (trimmedLine.matches(".*[？?].*\\s+\\d+$") || trimmedLine.matches(".*\\t\\d+$"));
    }

    /**
     * 判断候选句是否长得像可直接回答问题的结构化事实。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化后的候选句
     * @return 结构化事实返回 true
     */
    private boolean looksLikeStructuredFactCandidate(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int assignmentDelimiterIndex = structuredAssignmentDelimiterIndex(normalizedLine);
        if (assignmentDelimiterIndex > 0) {
            String assignmentKey = normalizedLine.substring(0, assignmentDelimiterIndex).trim();
            String assignmentValue = structuredAssignmentValue(normalizedLine, assignmentDelimiterIndex);
            return looksLikeConfigFactKey(assignmentKey)
                    || (looksLikeStructuredFactQuestion(question)
                    && matchesStructuredFactFocusToken(question, assignmentKey)
                    && looksLikeScalarTableValue(assignmentValue));
        }
        if (!looksLikeStructuredFactQuestion(question)) {
            return false;
        }
        return looksLikeConfigFactKey(normalizedLine) && normalizedLine.matches(".*\\d.*");
    }

    /**
     * 判断结构化事实键是否命中用户问题里的焦点。
     *
     * @param question 用户问题
     * @param assignmentKey 结构化事实键
     * @return 命中焦点返回 true
     */
    private boolean matchesStructuredFactFocusToken(String question, String assignmentKey) {
        if (assignmentKey == null || assignmentKey.isBlank()) {
            return false;
        }
        String normalizedAssignmentKey = lowerCase(assignmentKey);
        for (String focusToken : extractStructuredFactFocusTokens(question)) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (!normalizedFocusToken.isBlank()
                    && (normalizedAssignmentKey.contains(normalizedFocusToken)
                    || normalizedFocusToken.contains(normalizedAssignmentKey))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 推导当前问题最希望直接回答几条结构化事实。
     *
     * @param question 用户问题
     * @return 期望条数
     */
    private int desiredStructuredFactCount(String question) {
        String normalizedQuestion = lowerCase(question);
        int requestedShapeCount = 1;
        if (normalizedQuestion.contains("命中数")) {
            requestedShapeCount++;
        }
        if (normalizedQuestion.contains("结论") || normalizedQuestion.contains("状态")) {
            requestedShapeCount++;
        }
        if (expectsBatchOrOrdinalAnswer(normalizedQuestion)) {
            requestedShapeCount++;
        }
        if (requestedShapeCount > 1) {
            return Math.min(4, requestedShapeCount);
        }
        if (normalizedQuestion.contains("分别")) {
            return 2;
        }
        if (looksLikeNumericQuestion(question) && normalizedQuestion.contains("和")) {
            return 2;
        }
        return 1;
    }

    /**
     * 推导 fallback 结论最多应保留几条事实句。
     *
     * @param question 用户问题
     * @return 期望事实句数量
     */
    private int desiredFallbackConclusionSnippetCount(String question) {
        String normalizedQuestion = lowerCase(question);
        if (looksLikeStructuredFactQuestion(question)
                && (normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("结论")
                || normalizedQuestion.contains("批")
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("窗口")
                || normalizedQuestion.contains("值"))) {
            return desiredStructuredFactCount(question);
        }
        if (looksLikeCapabilityQuestion(question)
                && !normalizedQuestion.contains("形态")
                && !normalizedQuestion.contains("技巧")
                && !normalizedQuestion.contains("字段")
                && !normalizedQuestion.contains("步骤")
                && !normalizedQuestion.contains("渠道")) {
            return 1;
        }
        if (normalizedQuestion.contains("步骤")) {
            return 8;
        }
        if (normalizedQuestion.contains("技巧") || normalizedQuestion.contains("形态")) {
            return 8;
        }
        if (normalizedQuestion.contains("字段") || normalizedQuestion.contains("渠道")) {
            return 6;
        }
        if (looksLikeEnumerationQuestion(question)) {
            return 6;
        }
        return desiredStructuredFactCount(question);
    }

    /**
     * 从“X 和 Y 分别是多少”这类题目里提取需要覆盖的结构化焦点。
     *
     * @param question 用户问题
     * @return 焦点列表
     */
    private List<String> extractStructuredFactFocusTokens(String question) {
        List<String> focusTokens = new ArrayList<String>();
        if (question == null || question.isBlank() || !looksLikeStructuredFactQuestion(question)) {
            return focusTokens;
        }
        if (!shouldExtractStructuredFactFocusTokens(question)) {
            return focusTokens;
        }
        String[] rawSegments = question.split("和|以及|及|、|/|，|,");
        for (String rawSegment : rawSegments) {
            String focusToken = cleanupStructuredFactQuestionSegment(rawSegment);
            if (focusToken.isBlank() || focusTokens.contains(focusToken)) {
                continue;
            }
            focusTokens.add(focusToken);
        }
        if (!focusTokens.isEmpty()) {
            return focusTokens;
        }
        for (String queryToken : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            String normalizedToken = cleanupStructuredFactQuestionSegment(queryToken);
            if (normalizedToken.isBlank() || focusTokens.contains(normalizedToken)) {
                continue;
            }
            focusTokens.add(normalizedToken);
            if (focusTokens.size() >= 2) {
                break;
            }
        }
        return focusTokens;
    }

    /**
     * 判断当前问题是否真的在要求按多个结构化焦点分别取值。
     *
     * @param question 用户问题
     * @return 需要拆焦点返回 true
     */
    private boolean shouldExtractStructuredFactFocusTokens(String question) {
        String normalizedQuestion = lowerCase(question);
        if (normalizedQuestion.contains("分别")) {
            return true;
        }
        return normalizedQuestion.matches("(?s).*[A-Za-z0-9._-]+\\s*(?:和|以及|、|/)\\s*[A-Za-z0-9._-]+.*")
                && (normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("参数"));
    }

    /**
     * 清理结构化问题片段里的疑问词与语气词，保留真正需要回答的配置项/指标名。
     *
     * @param rawSegment 原始问题片段
     * @return 清理后的焦点
     */
    private String cleanupStructuredFactQuestionSegment(String rawSegment) {
        String normalizedSegment = lowerCase(rawSegment);
        if (normalizedSegment.isBlank()) {
            return "";
        }
        normalizedSegment = normalizedSegment.replace("当前", " ");
        normalizedSegment = normalizedSegment.replace("现在", " ");
        normalizedSegment = normalizedSegment.replace("目前", " ");
        normalizedSegment = normalizedSegment.replace("分别", " ");
        normalizedSegment = normalizedSegment.replace("多少", " ");
        normalizedSegment = normalizedSegment.replace("几", " ");
        normalizedSegment = normalizedSegment.replace("数值", " ");
        normalizedSegment = normalizedSegment.replace("参数", " ");
        normalizedSegment = normalizedSegment.replace("配置", " ");
        normalizedSegment = normalizedSegment.replace("是什么", " ");
        normalizedSegment = normalizedSegment.replace("是多少", " ");
        normalizedSegment = normalizedSegment.replace("值", " ");
        normalizedSegment = normalizedSegment.replace("是", " ");
        normalizedSegment = normalizedSegment.replaceAll("[？?。！!：:（）()“”\"'`]", " ");
        normalizedSegment = normalizedSegment.replaceAll("\\s+", " ").trim();
        if (normalizedSegment.isBlank()) {
            return "";
        }
        if (looksLikeConfigFactKey(normalizedSegment) || normalizedSegment.length() >= 2) {
            return normalizedSegment;
        }
        return "";
    }

    /**
     * 读取结构化赋值右侧文本。
     *
     * @param normalizedLine 归一化候选句
     * @param delimiterIndex 分隔符位置
     * @return 右侧文本
     */
    private String structuredAssignmentValue(String normalizedLine, int delimiterIndex) {
        if (normalizedLine == null || normalizedLine.isBlank() || delimiterIndex < 0) {
            return "";
        }
        if (normalizedLine.startsWith(" = ", delimiterIndex)) {
            return normalizedLine.substring(delimiterIndex + 3).trim();
        }
        if (normalizedLine.startsWith(": ", delimiterIndex)) {
            return normalizedLine.substring(delimiterIndex + 2).trim();
        }
        return normalizedLine.substring(delimiterIndex + 1).trim();
    }

    /**
     * 在多条候选命中行里挑选最像“可直接回答用户”的那一句。
     *
     * @param candidateLines 候选行
     * @param preferredTokens 当前优先 token
     * @return 最佳候选；不存在时返回空串
     */
    private String selectBestFallbackMatchedLine(List<String> candidateLines, List<String> preferredTokens) {
        if (candidateLines == null || candidateLines.isEmpty()) {
            return "";
        }
        String bestLine = "";
        int bestScore = Integer.MIN_VALUE;
        for (String candidateLine : candidateLines) {
            String normalizedLine = normalizeFallbackLineCandidate(candidateLine);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            int candidateScore = scoreFallbackLineCandidate(candidateLine, normalizedLine, preferredTokens);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                bestLine = normalizedLine;
            }
        }
        return stripEmbeddedCitationLiterals(bestLine);
    }

    /**
     * 为参考说明挑选更贴近当前 comparison 选项的证据句。
     *
     * @param queryArticleHit 查询命中
     * @param comparisonOptions comparison 选项
     * @param queryTokens 问题 token
     * @return 参考说明片段
     */
    private String selectReferenceFallbackSnippet(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> comparisonOptions,
            List<String> queryTokens
    ) {
        if (comparisonOptions != null && comparisonOptions.size() >= 2) {
            String matchedOption = matchComparisonOption(
                    queryArticleHit,
                    comparisonOptions.get(0),
                    comparisonOptions.get(1)
            );
            if (!matchedOption.isBlank()) {
                return selectOptionSpecificFallbackSnippet(queryArticleHit, matchedOption, queryTokens);
            }
        }
        return selectQuestionFocusedFallbackSnippet(question, queryArticleHit, queryTokens);
    }

    /**
     * 去掉 snippet 尾部多余句号/分号，避免嵌入模板句后出现双标点。
     *
     * @param snippet 原始片段
     * @return 去尾标点后的片段
     */
    private String trimTrailingFallbackPunctuation(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        return snippet.replaceAll("[。；;，,：:]+$", "").trim();
    }

    /**
     * 计算 fallback 候选句的优先级，优先保留更直接、更事实化的句子。
     *
     * @param rawLine 原始候选行
     * @param normalizedLine 归一化后的候选行
     * @param preferredTokens 当前优先 token
     * @return 候选分值
     */
    private int scoreFallbackLineCandidate(String rawLine, String normalizedLine, List<String> preferredTokens) {
        String lowerCaseLine = lowerCase(normalizedLine);
        int score = 0;
        if (preferredTokens != null) {
            for (String preferredToken : preferredTokens) {
                if (preferredToken == null || preferredToken.isBlank()) {
                    continue;
                }
                if (lowerCaseLine.contains(lowerCase(preferredToken))) {
                    score += 10;
                }
            }
        }
        if (normalizedLine.contains("`")) {
            score += 6;
        }
        if (lowerCaseLine.contains("采用")
                || lowerCaseLine.contains("通过")
                || lowerCaseLine.contains("使用字段")
                || lowerCaseLine.contains("默认值")
                || lowerCaseLine.contains("配置项")) {
            score += 4;
        }
        if (normalizedLine.contains(" = ")) {
            score += 12;
            int equalsIndex = normalizedLine.indexOf(" = ");
            if (equalsIndex > 0 && looksLikeConfigFactKey(normalizedLine.substring(0, equalsIndex).trim())) {
                score += 10;
            }
        }
        if (normalizedLine.matches(".*\\d.*")) {
            score += 3;
        }
        if (lowerCaseLine.contains("本条目汇总")
                || lowerCaseLine.contains("主要记录了")
                || lowerCaseLine.contains("记录了若干")
                || lowerCaseLine.contains("回答时需要")
                || lowerCaseLine.contains("当前资料")
                || lowerCaseLine.contains("文档规则")
                || lowerCaseLine.contains("现有资料主要包含")
                || lowerCaseLine.contains("在当前资料中")
                || lowerCaseLine.contains("主要聚焦于")) {
            score -= 8;
        }
        if (lowerCaseLine.contains("汇总")
                || lowerCaseLine.contains("概述")
                || lowerCaseLine.contains("概要")) {
            score -= 3;
        }
        if (lowerCaseLine.contains("应视为")
                || lowerCaseLine.contains("而非")
                || lowerCaseLine.contains("来源未展开")
                || lowerCaseLine.contains("适用条件")
                || lowerCaseLine.contains("不能进一步断言")
                || lowerCaseLine.contains("未提供校准依据")) {
            score -= 8;
        }
        String lowerCaseRawLine = lowerCase(rawLine);
        if (lowerCaseRawLine.startsWith("summary:")
                || lowerCaseRawLine.startsWith("description:")
                || lowerCaseRawLine.startsWith("content:")) {
            score -= 2;
        }
        score -= Math.max(0, normalizedLine.length() - 120) / 24;
        return score;
    }

    /**
     * 从正文中选出首条可读内容，避开 frontmatter 与结构行。
     *
     * @param content 证据正文
     * @return 可读内容行
     */
    private List<String> selectFallbackContentLines(String content) {
        List<String> contentLines = new ArrayList<String>();
        if (content == null || content.isBlank()) {
            return contentLines;
        }
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        String[] rawLines = bodyContent.split("\\R");
        for (int index = 0; index < rawLines.length; index++) {
            String rawLine = rawLines[index];
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            String lowerCaseLine = normalizedLine.toLowerCase(Locale.ROOT);
            if (normalizedLine.isEmpty()
                    || normalizedLine.startsWith("#")
                    || normalizedLine.startsWith(">")
                    || looksLikeTableOfContentsLine(normalizedLine)
                    || isMarkdownTableHeaderWithDivider(normalizedLine, index + 1 < rawLines.length ? rawLines[index + 1] : null)
                    || isNonTextMediaLine(normalizedLine)
                    || lowerCaseLine.startsWith("<h1")
                    || lowerCaseLine.startsWith("<h2")
                    || lowerCaseLine.startsWith("<h3")
                    || lowerCaseLine.startsWith("<h4")) {
                continue;
            }
            String plainLine = normalizedLine.startsWith("- ") ? normalizedLine.substring(2) : normalizedLine;
            contentLines.add(plainLine);
        }
        return filterFallbackMatchedLines(contentLines);
    }

    /**
     * 计算 fallback 证据的去重键。
     *
     * @param queryArticleHit 查询命中
     * @return 去重键
     */
    private String fallbackEvidenceCanonicalKey(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            if (queryArticleHit.getArticleKey() != null
                    && queryArticleHit.getArticleKey().contains("#")) {
                return queryArticleHit.getArticleKey();
            }
            if (queryArticleHit.getConceptId() != null
                    && queryArticleHit.getConceptId().contains("#")) {
                return queryArticleHit.getConceptId();
            }
        }
        if (queryArticleHit.getSourcePaths() != null && !queryArticleHit.getSourcePaths().isEmpty()) {
            return queryArticleHit.getSourcePaths().get(0);
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit.getConceptId() != null && !queryArticleHit.getConceptId().isBlank()) {
            return queryArticleHit.getConceptId();
        }
        return queryArticleHit.getTitle() == null ? "" : queryArticleHit.getTitle();
    }

    /**
     * 针对精确查值题保留同源的 ARTICLE / SOURCE 互补证据，避免 source 里的精确值被 article 摘要吞掉。
     *
     * @param question 用户问题
     * @param queryArticleHit 查询命中
     * @return 问题感知后的去重键
     */
    private String fallbackEvidenceCanonicalKey(String question, QueryArticleHit queryArticleHit) {
        String canonicalKey = fallbackEvidenceCanonicalKey(queryArticleHit);
        if (!looksLikeExactLookupQuestion(question) || queryArticleHit == null) {
            return canonicalKey;
        }
        QueryEvidenceType evidenceType = queryArticleHit.getEvidenceType();
        if (evidenceType == QueryEvidenceType.ARTICLE
                || evidenceType == QueryEvidenceType.CONTRIBUTION
                || evidenceType == QueryEvidenceType.FACT_CARD
                || evidenceType == QueryEvidenceType.SOURCE) {
            String identityKey = fallbackEvidenceIdentityKey(queryArticleHit);
            if (!identityKey.isBlank()) {
                return canonicalKey + "#" + evidenceType.name() + "#" + identityKey;
            }
            return canonicalKey + "#" + evidenceType.name();
        }
        return canonicalKey;
    }

    /**
     * 计算同源文档内可区分章节、条目或卡片的细粒度身份。
     *
     * @param queryArticleHit 查询命中
     * @return 细粒度身份键
     */
    private String fallbackEvidenceIdentityKey(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getArticleKey() != null && !queryArticleHit.getArticleKey().isBlank()) {
            return queryArticleHit.getArticleKey();
        }
        if (queryArticleHit.getConceptId() != null && !queryArticleHit.getConceptId().isBlank()) {
            return queryArticleHit.getConceptId();
        }
        return queryArticleHit.getTitle() == null ? "" : queryArticleHit.getTitle();
    }

    /**
     * 计算 fallback 证据的展示优先级。
     *
     * @param queryArticleHit 查询命中
     * @return 优先级
     */
    private int fallbackEvidencePriority(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() == null) {
            return Integer.MIN_VALUE;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
            return 120;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            return 115;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            return 100;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            return 60;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.GRAPH) {
            return 40;
        }
        return 20;
    }

    /**
     * 判断命中是否在标题、标识符或描述层直接命中了问题高信号 token。
     *
     * @param queryArticleHit 查询命中
     * @param highSignalTokens 高信号 token
     * @return 直接命中返回 true
     */
    private boolean matchesStructuredOrTitle(QueryArticleHit queryArticleHit, List<String> highSignalTokens) {
        if (queryArticleHit == null || highSignalTokens == null || highSignalTokens.isEmpty()) {
            return false;
        }
        String structuredHaystack = String.join(
                " ",
                lowerCase(queryArticleHit.getArticleKey()),
                lowerCase(queryArticleHit.getConceptId()),
                lowerCase(queryArticleHit.getTitle()),
                lowerCase(extractDescription(queryArticleHit.getMetadataJson()))
        );
        for (String highSignalToken : highSignalTokens) {
            String normalizedToken = lowerCase(highSignalToken);
            if (!normalizedToken.isBlank() && structuredHaystack.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取证据摘要，避免兜底答案过长。
     *
     * @param content 证据正文
     * @return 摘要文本
     */
    private String extractEvidenceSnippet(String content) {
        String normalizedContent = sanitizeEvidenceContentForPrompt(content, 180 + PROMPT_TRUNCATED_SUFFIX.length());
        if (normalizedContent.length() <= 180) {
            return normalizedContent;
        }
        return normalizedContent.substring(0, 180) + "...";
    }

    /**
     * 清理证据正文中的纯媒体行，避免图片/HTML embed 被当成答案语义或直接塞进 prompt。
     *
     * @param content 原始正文
     * @return 清理后的正文
     */
    private String sanitizeEvidenceContentForPrompt(String content) {
        return sanitizeEvidenceContentForPrompt(content, 0);
    }

    /**
     * 清理证据正文中的纯媒体行，并按需限制累计字符数。
     *
     * @param content 原始正文
     * @param maxChars 最大字符数；小于等于 0 时不限制
     * @return 清理后的正文
     */
    private String sanitizeEvidenceContentForPrompt(String content, int maxChars) {
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        if (bodyContent == null || bodyContent.isBlank()) {
            return "";
        }
        String[] rawLines = bodyContent.split("\\R");
        List<String> keptLines = new ArrayList<String>();
        for (String rawLine : rawLines) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (isNonTextMediaLine(normalizedLine)) {
                continue;
            }
            if (maxChars <= 0) {
                keptLines.add(rawLine);
                continue;
            }
            String currentText = String.join("\n", keptLines);
            int separatorLength = currentText.isBlank() ? 0 : 1;
            int remainingChars = maxChars - currentText.length() - separatorLength;
            if (remainingChars <= 0) {
                break;
            }
            if (rawLine.length() <= remainingChars) {
                keptLines.add(rawLine);
            } else {
                keptLines.add(truncatePromptText(rawLine, remainingChars));
                break;
            }
        }
        return SensitiveTextMasker.mask(String.join("\n", keptLines).trim());
    }

    /**
     * 判断一行是否为纯媒体嵌入，而不是可供问答引用的自然语言内容。
     *
     * @param normalizedLine 归一化后的单行文本
     * @return 纯媒体行返回 true
     */
    private boolean isNonTextMediaLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String trimmedLine = normalizedLine.trim();
        String lowerCaseLine = trimmedLine.toLowerCase(Locale.ROOT);
        return trimmedLine.matches("^!\\[[^\\]]*]\\([^)]*\\)$")
                || lowerCaseLine.matches("^<img\\b[^>]*>$");
    }

    /**
     * 判断当前行是否为 Markdown 表头，且下一行为分隔线。
     *
     * @param currentLine 当前行
     * @param nextLine 下一行
     * @return 命中表头返回 true
     */
    private boolean isMarkdownTableHeaderWithDivider(String currentLine, String nextLine) {
        if (currentLine == null || nextLine == null) {
            return false;
        }
        String normalizedCurrentLine = currentLine.trim();
        String normalizedNextLine = nextLine.trim();
        if (!(normalizedCurrentLine.startsWith("|") && normalizedCurrentLine.endsWith("|"))) {
            return false;
        }
        if (!(normalizedNextLine.startsWith("|") && normalizedNextLine.endsWith("|"))) {
            return false;
        }
        return isMarkdownTableDividerRow(parseMarkdownTableCells(normalizedNextLine));
    }

    /**
     * 过滤 fallback 片段中的元数据行，优先保留真正的事实内容。
     *
     * @param matchedLines 原始命中行
     * @return 过滤后的命中行
     */
    private List<String> filterFallbackMatchedLines(List<String> matchedLines) {
        List<String> filteredLines = new ArrayList<String>();
        if (matchedLines == null) {
            return filteredLines;
        }
        for (String matchedLine : matchedLines) {
            String normalizedLine = normalizeFallbackLineCandidate(matchedLine);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            filteredLines.add(normalizedLine);
        }
        return filteredLines;
    }

    /**
     * 归一化 fallback 片段候选行，保留 summary/content 等真正有信息的字段值。
     *
     * @param candidateLine 原始候选行
     * @return 归一化后的可展示文本；无法展示时返回空串
     */
    private String normalizeFallbackLineCandidate(String candidateLine) {
        String normalizedLine = candidateLine == null ? "" : candidateLine.trim();
        normalizedLine = stripStructuredLinePrefix(normalizedLine);
        String lowerCaseLine = normalizedLine.toLowerCase(Locale.ROOT);
        if (normalizedLine.isEmpty() || "---".equals(normalizedLine)) {
            return "";
        }
        if (isNonTextMediaLine(normalizedLine)) {
            return "";
        }
        if (looksLikeTableOfContentsLine(normalizedLine)) {
            return "";
        }
        if (normalizedLine.startsWith("|") && normalizedLine.endsWith("|")) {
            return SensitiveTextMasker.mask(normalizeMarkdownTableRow(normalizedLine));
        }
        String structuredJsonLine = normalizeStructuredJsonLine(normalizedLine);
        if (!structuredJsonLine.isBlank()) {
            return SensitiveTextMasker.mask(structuredJsonLine);
        }
        if (lowerCaseLine.startsWith("summary:")
                || lowerCaseLine.startsWith("description:")
                || lowerCaseLine.startsWith("content:")) {
            return SensitiveTextMasker.mask(extractFallbackFieldValue(normalizedLine));
        }
        if (lowerCaseLine.startsWith("<h1")
                || lowerCaseLine.startsWith("<h2")
                || lowerCaseLine.startsWith("<h3")
                || lowerCaseLine.startsWith("<h4")) {
            return "";
        }
        if (lowerCaseLine.startsWith("title:")
                || lowerCaseLine.startsWith("referential_keywords:")
                || lowerCaseLine.startsWith("sources:")
                || lowerCaseLine.startsWith("source_paths:")
                || lowerCaseLine.startsWith("article_key:")
                || lowerCaseLine.startsWith("concept_id:")
                || lowerCaseLine.startsWith("file_path:")
                || lowerCaseLine.startsWith("metadata:")
                || lowerCaseLine.startsWith("depends_on:")
                || lowerCaseLine.startsWith("related:")
                || lowerCaseLine.startsWith("confidence:")
                || lowerCaseLine.startsWith("compiled_at:")
                || lowerCaseLine.startsWith("review_status:")
                || lowerCaseLine.startsWith("lifecycle:")) {
            return "";
        }
        return SensitiveTextMasker.mask(normalizedLine);
    }

    /**
     * 去掉结构化抽取残留前缀，避免把内部标记直接暴露给用户。
     *
     * @param candidateLine 候选行
     * @return 去前缀后的文本
     */
    private String stripStructuredLinePrefix(String candidateLine) {
        if (candidateLine == null || candidateLine.isBlank()) {
            return "";
        }
        return candidateLine
                .replaceFirst("^(?i)table_row:\\s*", "")
                .replaceFirst("^(?i)sheet=\\S+;\\s*", "")
                .replaceFirst("^(?i)row=\\d+;\\s*", "")
                .trim();
    }

    /**
     * 归一 Markdown 表格行，尽量还原为可直接展示的事实句。
     *
     * @param tableRow 表格行
     * @return 归一后的事实句
     */
    private String normalizeMarkdownTableRow(String tableRow) {
        List<String> cells = parseMarkdownTableCells(tableRow);
        if (cells.isEmpty() || isMarkdownTableDividerRow(cells)) {
            return "";
        }
        List<String> normalizedCells = new ArrayList<String>();
        for (String cell : cells) {
            String normalizedCell = stripTableCellMarkup(cell);
            if (normalizedCell.isBlank()) {
                continue;
            }
            normalizedCells.add(normalizedCell);
        }
        if (normalizedCells.isEmpty()) {
            return "";
        }
        if (normalizedCells.size() >= 3 && normalizedCells.get(0).matches("\\d+")) {
            normalizedCells.remove(0);
        }
        if (!normalizedCells.isEmpty() && normalizedCells.get(0).contains("=")) {
            String assignmentSentence = normalizeAssignmentCell(normalizedCells.get(0));
            if (normalizedCells.size() >= 2) {
                return assignmentSentence + "，" + normalizedCells.get(1);
            }
            return assignmentSentence;
        }
        if (normalizedCells.size() >= 2 && looksLikeConfigFactKey(normalizedCells.get(0))) {
            String baseSentence = normalizedCells.get(0) + " = " + normalizedCells.get(1);
            if (normalizedCells.size() >= 3) {
                return baseSentence + "，" + normalizedCells.get(2);
            }
            return baseSentence;
        }
        if (looksLikeLabelValueTableRow(normalizedCells)) {
            String baseSentence = normalizedCells.get(0) + " = " + normalizedCells.get(1);
            if (normalizedCells.size() >= 3) {
                return baseSentence + "，" + normalizedCells.get(2);
            }
            return baseSentence;
        }
        return String.join("；", normalizedCells);
    }

    /**
     * 将 JSON 行归一成可读事实句，避免 fallback 直接展示内部结构。
     *
     * @param normalizedLine 候选行
     * @return 可读事实句；非 JSON 返回空串
     */
    private String normalizeStructuredJsonLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return "";
        }
        String trimmedLine = normalizedLine.trim();
        if (!(trimmedLine.startsWith("{") || trimmedLine.startsWith("["))) {
            return "";
        }
        List<String> valueLines = selectStructuredJsonValueLines(trimmedLine);
        if (valueLines.isEmpty()) {
            return "";
        }
        return String.join("；", valueLines.subList(0, Math.min(4, valueLines.size())));
    }

    /**
     * 从结构化 JSON 中提取可读的字符串值，作为通用证据候选。
     *
     * @param content 原始内容
     * @return 可读值列表
     */
    private List<String> selectStructuredJsonValueLines(String content) {
        List<String> valueLines = new ArrayList<String>();
        if (content == null || content.isBlank()) {
            return valueLines;
        }
        String trimmedContent = content.trim();
        if (!(trimmedContent.startsWith("{") || trimmedContent.startsWith("["))) {
            return valueLines;
        }
        JsonNode jsonNode = readJsonNode(trimmedContent);
        if (jsonNode == null) {
            return valueLines;
        }
        collectStructuredJsonValueLines(jsonNode, valueLines);
        return valueLines;
    }

    /**
     * 递归收集 JSON 字符串叶子节点。
     *
     * @param jsonNode JSON 节点
     * @param valueLines 输出值
     */
    private void collectStructuredJsonValueLines(JsonNode jsonNode, List<String> valueLines) {
        if (jsonNode == null || valueLines.size() >= 24) {
            return;
        }
        if (jsonNode.isTextual()) {
            String textValue = normalizeFallbackLineCandidate(jsonNode.asText());
            if (!textValue.isBlank()) {
                addDistinctStructuredJsonValue(valueLines, textValue);
            }
            return;
        }
        if (jsonNode.isNumber() || jsonNode.isBoolean()) {
            addDistinctStructuredJsonValue(valueLines, jsonNode.asText());
            return;
        }
        if (jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(entry -> collectStructuredJsonValueLines(entry.getValue(), valueLines));
            return;
        }
        if (jsonNode.isArray()) {
            for (JsonNode childNode : jsonNode) {
                collectStructuredJsonValueLines(childNode, valueLines);
                if (valueLines.size() >= 24) {
                    break;
                }
            }
        }
    }

    /**
     * 去重追加 JSON 值候选。
     *
     * @param valueLines 已收集值
     * @param textValue 候选值
     */
    private void addDistinctStructuredJsonValue(List<String> valueLines, String textValue) {
        String normalizedValue = textValue == null ? "" : textValue.trim();
        if (normalizedValue.isBlank() || normalizedValue.length() > 260) {
            return;
        }
        if (!valueLines.contains(normalizedValue)) {
            valueLines.add(normalizedValue);
        }
    }

    /**
     * 判断表格数据行是否像“标签列 + 值列”的结构化事实。
     *
     * @param normalizedCells 归一化单元格
     * @return 标签值行返回 true
     */
    private boolean looksLikeLabelValueTableRow(List<String> normalizedCells) {
        if (normalizedCells == null || normalizedCells.size() < 2) {
            return false;
        }
        String labelCell = normalizedCells.get(0);
        String valueCell = normalizedCells.get(1);
        if (labelCell == null || labelCell.isBlank() || valueCell == null || valueCell.isBlank()) {
            return false;
        }
        if (labelCell.length() > 40 || valueCell.length() > 80) {
            return false;
        }
        if (isMarkdownTableHeaderCell(labelCell) && isMarkdownTableHeaderCell(valueCell)) {
            return false;
        }
        return looksLikeScalarTableValue(valueCell)
                || (normalizedCells.size() >= 3 && looksLikeScalarTableValue(normalizedCells.get(2)));
    }

    /**
     * 判断表格单元格是否像可直接回答的值。
     *
     * @param cell 单元格
     * @return 值单元格返回 true
     */
    private boolean looksLikeScalarTableValue(String cell) {
        if (cell == null || cell.isBlank()) {
            return false;
        }
        String normalizedCell = lowerCase(cell);
        return normalizedCell.matches(".*\\d.*")
                || normalizedCell.contains("/")
                || normalizedCell.contains("_")
                || normalizedCell.contains("-")
                || normalizedCell.contains("@")
                || normalizedCell.contains("=")
                || normalizedCell.contains("是")
                || normalizedCell.contains("否")
                || normalizedCell.length() <= 12;
    }

    /**
     * 解析 Markdown 表格单元格。
     *
     * @param tableRow 表格行
     * @return 单元格列表
     */
    private List<String> parseMarkdownTableCells(String tableRow) {
        List<String> cells = new ArrayList<String>();
        if (tableRow == null || tableRow.isBlank()) {
            return cells;
        }
        String[] rawCells = tableRow.split("\\|");
        for (String rawCell : rawCells) {
            String normalizedCell = rawCell == null ? "" : rawCell.trim();
            if (normalizedCell.isEmpty()) {
                continue;
            }
            cells.add(normalizedCell);
        }
        return cells;
    }

    /**
     * 判断表格行是否为分隔线。
     *
     * @param cells 表格单元格
     * @return 分隔线返回 true
     */
    private boolean isMarkdownTableDividerRow(List<String> cells) {
        for (String cell : cells) {
            if (!cell.matches(":?-{2,}:?")) {
                return false;
            }
        }
        return !cells.isEmpty();
    }

    /**
     * 判断表格行是否为表头。
     *
     * @param cells 表格单元格
     * @return 表头返回 true
     */
    private boolean isMarkdownTableHeaderRow(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            String normalizedCell = stripTableCellMarkup(cell);
            if (normalizedCell.isBlank()) {
                continue;
            }
            if (!isMarkdownTableHeaderCell(normalizedCell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断表格单元格是否更像表头标签。
     *
     * @param cell 单元格
     * @return 表头标签返回 true
     */
    private boolean isMarkdownTableHeaderCell(String cell) {
        return "序号".equals(cell)
                || "检查项".equals(cell)
                || "说明".equals(cell)
                || "配置键".equals(cell)
                || "精确值".equals(cell)
                || "项目".equals(cell)
                || "类型".equals(cell)
                || "标识符".equals(cell)
                || "建议值".equals(cell)
                || "是否自动".equals(cell)
                || "典型形态".equals(cell)
                || "示例".equals(cell)
                || "方法".equals(cell)
                || "返回值".equals(cell)
                || "类别".equals(cell)
                || "含义".equals(cell)
                || "来源说明".equals(cell)
                || "优先检查项".equals(cell)
                || "触发信号".equals(cell);
    }

    /**
     * 去掉表格单元格内的 Markdown 修饰。
     *
     * @param cell 原始单元格
     * @return 归一后的单元格
     */
    private String stripTableCellMarkup(String cell) {
        String normalizedCell = cell == null ? "" : cell.trim();
        normalizedCell = normalizedCell.replace("**", "");
        normalizedCell = normalizedCell.replace("`", "");
        return normalizedCell.trim();
    }

    /**
     * 归一化已写成 key=value 形式的配置单元格，提升展示一致性。
     *
     * @param assignmentCell 配置单元格
     * @return 归一化后的配置表达式
     */
    private String normalizeAssignmentCell(String assignmentCell) {
        String normalizedCell = stripTableCellMarkup(assignmentCell);
        return normalizedCell.replaceAll("\\s*=\\s*", " = ");
    }

    /**
     * 判断候选句是否以“配置键/指标名 = 值”的直接事实形式开头。
     *
     * @param normalizedLine 归一化后的候选句
     * @return 直接事实句返回 true
     */
    private boolean startsWithDirectStructuredFactAssignment(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int assignmentDelimiterIndex = structuredAssignmentDelimiterIndex(normalizedLine);
        if (assignmentDelimiterIndex <= 0) {
            return false;
        }
        String assignmentKey = normalizedLine.substring(0, assignmentDelimiterIndex).trim();
        if (assignmentKey.isBlank()) {
            return false;
        }
        return looksLikeConfigFactKey(assignmentKey);
    }

    /**
     * 查找结构化赋值分隔符位置。
     *
     * @param normalizedLine 归一化候选句
     * @return 分隔符位置；不存在返回 -1
     */
    private int structuredAssignmentDelimiterIndex(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return -1;
        }
        int equalsIndex = normalizedLine.indexOf(" = ");
        int colonIndex = normalizedLine.indexOf(": ");
        if (equalsIndex < 0) {
            return colonIndex;
        }
        if (colonIndex < 0) {
            return equalsIndex;
        }
        return Math.min(equalsIndex, colonIndex);
    }

    /**
     * 判断文本是否更像配置键或阈值字段名。
     *
     * @param cell 单元格
     * @return 配置键返回 true
     */
    private boolean looksLikeConfigFactKey(String cell) {
        String normalizedCell = lowerCase(cell);
        return normalizedCell.contains(".")
                || normalizedCell.contains("_")
                || normalizedCell.contains("threshold")
                || normalizedCell.contains("window")
                || normalizedCell.contains("timeout")
                || normalizedCell.contains("retry")
                || normalizedCell.matches(".*[A-Za-z][A-Za-z0-9._-]{2,}.*");
    }

    /**
     * 提取结构化元数据行中的字段值。
     *
     * @param line 原始行
     * @return 字段值
     */
    private String extractFallbackFieldValue(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        int colonIndex = line.indexOf(':');
        if (colonIndex < 0 || colonIndex >= line.length() - 1) {
            return line.trim();
        }
        String fieldValue = line.substring(colonIndex + 1).trim();
        fieldValue = fieldValue.replaceAll("^[\"']+", "");
        fieldValue = fieldValue.replaceAll("[\"']+$", "");
        return fieldValue.trim();
    }

    /**
     * 去掉片段里原本夹带的 citation，避免 fallback 再追加标准引用时重复。
     *
     * @param snippet 原始片段
     * @return 去除内嵌 citation 后的片段
     */
    private String stripEmbeddedCitationLiterals(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        String normalizedSnippet = ARTICLE_CITATION_PATTERN.matcher(snippet).replaceAll("");
        normalizedSnippet = SOURCE_CITATION_PATTERN.matcher(normalizedSnippet).replaceAll("");
        normalizedSnippet = normalizedSnippet.replaceAll("\\s{2,}", " ").trim();
        normalizedSnippet = normalizedSnippet.replaceAll("\\s+([，。；：])", "$1");
        return normalizedSnippet;
    }

    /**
     * 把文本转成小写字符串，便于 fallback 相关性判断。
     *
     * @param value 原始文本
     * @return 小写文本
     */
    private String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * 判断文本中是否包含中文字符。
     *
     * @param value 原始文本
     * @return 包含中文返回 true
     */
    private boolean containsHanText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(index));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析单条证据对应的标准引用文本。
     *
     * @param queryArticleHit 证据命中
     * @return 引用文本
     */
    private String resolveCitationLiteral(QueryArticleHit queryArticleHit) {
        String articleCitationLiteral = resolveArticleCitationLiteral(queryArticleHit);
        String sourceCitationLiteral = resolveSourceCitationLiteral(queryArticleHit);
        if (!articleCitationLiteral.isBlank() && !sourceCitationLiteral.isBlank()) {
            return articleCitationLiteral + sourceCitationLiteral;
        }
        if (!articleCitationLiteral.isBlank()) {
            return articleCitationLiteral;
        }
        return sourceCitationLiteral;
    }

    /**
     * 解析证据对应的引用文本，并让结构化卡片优先回落到同源 source chunk。
     *
     * @param queryArticleHit 证据命中
     * @param candidateHits 同批候选命中
     * @return 引用文本
     */
    private String resolveCitationLiteral(QueryArticleHit queryArticleHit, List<QueryArticleHit> candidateHits) {
        if (queryArticleHit != null && queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            String sourceCitationLiteral = resolveFactCardSourceCitationLiteral(queryArticleHit, candidateHits);
            if (!sourceCitationLiteral.isBlank()) {
                return sourceCitationLiteral;
            }
        }
        return resolveCitationLiteral(queryArticleHit);
    }

    /**
     * 为结论段挑选更稳定的 citation 形式。
     *
     * @param queryArticleHit 证据命中
     * @return citation 文本
     */
    private String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit) {
        return resolveConclusionCitationLiteral(queryArticleHit, List.of());
    }

    /**
     * 为结论段挑选更稳定的 citation 形式，fact card 优先引用同源 source chunk。
     *
     * @param queryArticleHit 证据命中
     * @param candidateHits 同批候选命中
     * @return citation 文本
     */
    private String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit, List<QueryArticleHit> candidateHits) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            String sourceCitationLiteral = resolveFactCardSourceCitationLiteral(queryArticleHit, candidateHits);
            if (!sourceCitationLiteral.isBlank()) {
                return sourceCitationLiteral;
            }
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            String articleCitationLiteral = resolveArticleCitationLiteral(queryArticleHit);
            if (!articleCitationLiteral.isBlank()) {
                return articleCitationLiteral;
            }
        }
        String sourceCitationLiteral = resolveSourceCitationLiteral(queryArticleHit);
        if (!sourceCitationLiteral.isBlank()) {
            return sourceCitationLiteral;
        }
        return resolveArticleCitationLiteral(queryArticleHit);
    }

    /**
     * 为 fact card 查找同批 source chunk 的引用。
     *
     * @param factCardHit fact card 命中
     * @param candidateHits 同批候选命中
     * @return source 引用文本
     */
    private String resolveFactCardSourceCitationLiteral(
            QueryArticleHit factCardHit,
            List<QueryArticleHit> candidateHits
    ) {
        QueryArticleHit sourceHit = findFactCardSourceHit(factCardHit, candidateHits);
        String sourceCitationLiteral = resolveSourceCitationLiteral(sourceHit);
        if (!sourceCitationLiteral.isBlank()) {
            return sourceCitationLiteral;
        }
        return resolveSourceCitationLiteral(factCardHit);
    }

    /**
     * 查找与 fact card 同源且内容相近的 source hit。
     *
     * @param factCardHit fact card 命中
     * @param candidateHits 同批候选命中
     * @return source 命中
     */
    private QueryArticleHit findFactCardSourceHit(QueryArticleHit factCardHit, List<QueryArticleHit> candidateHits) {
        if (factCardHit == null || candidateHits == null || candidateHits.isEmpty()) {
            return null;
        }
        QueryArticleHit bestHit = null;
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit candidateHit : candidateHits) {
            if (candidateHit == null || candidateHit.getEvidenceType() != QueryEvidenceType.SOURCE) {
                continue;
            }
            int score = scoreFactCardSourceHit(factCardHit, candidateHit);
            if (score > bestScore) {
                bestScore = score;
                bestHit = candidateHit;
            }
        }
        return bestScore > 0 ? bestHit : null;
    }

    /**
     * 计算 source hit 与 fact card 的同源贴合分。
     *
     * @param factCardHit fact card 命中
     * @param sourceHit source 命中
     * @return 贴合分
     */
    private int scoreFactCardSourceHit(QueryArticleHit factCardHit, QueryArticleHit sourceHit) {
        int score = 0;
        if (hasSharedSourcePath(factCardHit, sourceHit)) {
            score += 24;
        }
        List<String> factTokens = QueryTokenExtractor.extract(joinHitText(factCardHit));
        String sourceHaystack = lowerCase(joinHitText(sourceHit));
        for (String factToken : factTokens) {
            String normalizedToken = lowerCase(factToken);
            if (normalizedToken.length() >= 2 && sourceHaystack.contains(normalizedToken)) {
                score += normalizedToken.matches(".*[0-9=_./-].*") ? 8 : 3;
            }
        }
        return score;
    }

    /**
     * 拼接命中的标题与内容，兼容可空字段。
     *
     * @param queryArticleHit 查询命中
     * @return 可用于匹配的文本
     */
    private String joinHitText(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        return lowerCase(queryArticleHit.getTitle()) + " " + lowerCase(queryArticleHit.getContent());
    }

    /**
     * 拼接多条命中的标题、正文与来源路径。
     *
     * @param queryArticleHits 查询命中
     * @return 可用于匹配的文本
     */
    private String joinHitTexts(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return "";
        }
        StringBuilder textBuilder = new StringBuilder();
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            textBuilder.append(' ')
                    .append(queryArticleHit.getTitle())
                    .append(' ')
                    .append(queryArticleHit.getContent())
                    .append(' ')
                    .append(queryArticleHit.getMetadataJson());
            if (queryArticleHit.getSourcePaths() != null) {
                textBuilder.append(' ')
                        .append(String.join(" ", queryArticleHit.getSourcePaths()));
            }
        }
        return textBuilder.toString();
    }

    /**
     * 判断两条命中是否共享源文件路径。
     *
     * @param leftHit 左侧命中
     * @param rightHit 右侧命中
     * @return 共享源文件返回 true
     */
    private boolean hasSharedSourcePath(QueryArticleHit leftHit, QueryArticleHit rightHit) {
        if (leftHit == null
                || rightHit == null
                || leftHit.getSourcePaths() == null
                || rightHit.getSourcePaths() == null) {
            return false;
        }
        for (String leftPath : leftHit.getSourcePaths()) {
            String normalizedLeftPath = normalizeSourceCitationTarget(leftPath);
            if (normalizedLeftPath.isBlank()) {
                continue;
            }
            for (String rightPath : rightHit.getSourcePaths()) {
                if (normalizedLeftPath.equals(normalizeSourceCitationTarget(rightPath))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 解析文章级 citation。
     *
     * @param queryArticleHit 证据命中
     * @return article citation
     */
    private String resolveArticleCitationLiteral(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
            return "";
        }
        String articleKey = queryArticleHit.getArticleKey();
        if (articleKey == null || articleKey.isBlank()) {
            articleKey = queryArticleHit.getConceptId();
        }
        if (articleKey == null || articleKey.isBlank()) {
            return "";
        }
        return "[[" + articleKey + "]]";
    }

    /**
     * 解析源文件级 citation。
     *
     * @param queryArticleHit 证据命中
     * @return source citation
     */
    private String resolveSourceCitationLiteral(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null
                || queryArticleHit.getSourcePaths() == null
                || queryArticleHit.getSourcePaths().isEmpty()) {
            return "";
        }
        String sourcePath = queryArticleHit.getSourcePaths().get(0);
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalizedSourcePath = sourcePath.trim();
        if (normalizedSourcePath.startsWith("[") && normalizedSourcePath.endsWith("]")) {
            normalizedSourcePath = normalizedSourcePath.substring(1, normalizedSourcePath.length() - 1).trim();
        }
        if (normalizedSourcePath.isBlank()) {
            return "";
        }
        return "[→ " + normalizedSourcePath + "]";
    }

    /**
     * 判断答案中是否仍保留至少一个可解析 citation。
     *
     * @param answerMarkdown 答案正文
     * @return 含 citation 返回 true
     */
    private boolean containsCitationLiteral(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return false;
        }
        return ARTICLE_CITATION_PATTERN.matcher(answerMarkdown).find()
                || SOURCE_CITATION_PATTERN.matcher(answerMarkdown).find();
    }

    /**
     * 精确查值题偏向 deterministic fallback 的通用原因。
     *
     * 职责：标识模型答案为什么没有被直接保留为 LLM 成功结果
     *
     * @author xiexu
     */
    private enum ExactLookupPreferenceReason {

        /**
         * 不需要 deterministic fallback。
         */
        NONE,

        /**
         * 模型答案语义不是成功。
         */
        OUTCOME_NOT_SUCCESS,

        /**
         * 模型答案包含过度保守表达。
         */
        OVERCAUTIOUS_PHRASE,

        /**
         * 模型答案未覆盖贴题证据形态。
         */
        GROUNDING_MISMATCH
    }

    /**
     * 精确查值题答案 grounding 判定状态。
     *
     * 职责：给通用 grounding 保护提供可观测的失败分类
     *
     * @author xiexu
     */
    private enum ExactLookupGroundingStatus {

        /**
         * 答案覆盖了当前问题所需的贴题证据形态。
         */
        GROUNDED,

        /**
         * 缺少必要路径形态。
         */
        MISSING_PATH_SHAPE,

        /**
         * 缺少数字。
         */
        MISSING_DIGIT,

        /**
         * 缺少必要数值形态。
         */
        MISSING_NUMERIC_SHAPE,

        /**
         * 缺少批次或序号形态。
         */
        MISSING_BATCH_OR_ORDINAL,

        /**
         * 缺少状态形态。
         */
        MISSING_STATUS,

        /**
         * 缺少流程形态。
         */
        MISSING_FLOW,

        /**
         * 缺少修正或状态结论形态。
         */
        MISSING_CORRECTION_OR_STATUS,

        /**
         * 缺少强约束形态。
         */
        MISSING_STRONG_CONSTRAINT,

        /**
         * 缺少规则约束形态。
         */
        MISSING_RULE_CONSTRAINT,

        /**
         * 缺少变更跟踪形态。
         */
        MISSING_CHANGE_TRACKING,

        /**
         * 缺少复合精确题的多维证据覆盖。
         */
        MISSING_COMPOUND_DIMENSIONS
    }

    /**
     * 聚合候选证据行。
     *
     * 职责：承载 deterministic fallback 聚合阶段的一条候选事实及其来源
     *
     * @author xiexu
     */
    private static final class EvidenceLineMatch {

        private final QueryArticleHit queryArticleHit;

        private final String line;

        private final int score;

        /**
         * 创建候选证据行。
         *
         * @param queryArticleHit 来源命中
         * @param line 候选事实行
         * @param score 相关性分值
         */
        private EvidenceLineMatch(QueryArticleHit queryArticleHit, String line, int score) {
            this.queryArticleHit = queryArticleHit;
            this.line = line;
            this.score = score;
        }

        /**
         * 获取来源命中。
         *
         * @return 来源命中
         */
        private QueryArticleHit getQueryArticleHit() {
            return queryArticleHit;
        }

        /**
         * 获取候选事实行。
         *
         * @return 候选事实行
         */
        private String getLine() {
            return line;
        }

        /**
         * 获取相关性分值。
         *
         * @return 相关性分值
         */
        private int getScore() {
            return score;
        }
    }
}
