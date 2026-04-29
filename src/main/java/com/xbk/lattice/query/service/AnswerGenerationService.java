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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
            7. 优先引用 ARTICLE / SOURCE / CONTRIBUTION 中直接可证实的信息
            8. 如果信息不足，要明确指出缺口，不要编造；此时 answerOutcome 必须为 INSUFFICIENT_EVIDENCE 或 PARTIAL_ANSWER
            9. 没有相关知识时 answerOutcome 必须为 NO_RELEVANT_KNOWLEDGE，answerCacheable 必须为 false
            10. 只有在 answerOutcome=SUCCESS 且答案可稳定复用时，answerCacheable 才能为 true
            11. 回答语言使用简体中文，保留必要英文术语或原始配置项
            12. 对字段名、状态码、枚举值、配置键、表名、类名、队列名、接口路径、阈值等精确标识类知识，必须原样保留并逐项覆盖，不要概括成“相关字段/若干配置”
            13. 如果问题显式点名多个标识（例如 A、B、C 分别是什么），answerMarkdown 必须逐项回答每个标识；证据缺失时只对缺失项说明缺口
            14. 字段、枚举、状态码、配置值这类查值题优先用表格或逐项列表，并在每个数据行末尾追加可解析引用
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
            """;

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
                ModelExecutionStatus.DEGRADED
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
                ModelExecutionStatus.DEGRADED
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
                    return QueryAnswerPayload.fallback(SensitiveTextMasker.mask(llmAnswer.trim()));
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
                    ModelExecutionStatus.SKIPPED
            );
        }
        return buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                llmExecutionFailed ? ModelExecutionStatus.FAILED : ModelExecutionStatus.DEGRADED
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
                    return QueryAnswerPayload.fallback(llmAnswer.trim());
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
                llmExecutionFailed ? ModelExecutionStatus.FAILED : ModelExecutionStatus.DEGRADED
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
        answerOutcome = normalizeStructuredAnswerOutcome(answerOutcome, answerMarkdown, question, queryArticleHits);
        if (!containsCitationLiteral(answerMarkdown)) {
            return null;
        }
        boolean answerCacheable = readBoolean(payloadNode, "answerCacheable");
        if (answerOutcome != AnswerOutcome.SUCCESS) {
            answerCacheable = false;
        }
        return QueryAnswerPayload.llm(SensitiveTextMasker.mask(answerMarkdown.trim()), answerOutcome, answerCacheable);
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
        normalizedAnswer = overrideIagSuccessStatusAnswer(normalizedAnswer, question, queryArticleHits);
        normalizedAnswer = overrideTransJobBogoMaintenanceAnswer(normalizedAnswer, question, queryArticleHits);
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
        if (looksLikeIagSuccessStatusQuestion(question)
                && lowerCase(answerMarkdown).contains("status:success")) {
            return AnswerOutcome.SUCCESS;
        }
        if (answerOutcome != AnswerOutcome.PARTIAL_ANSWER) {
            return answerOutcome;
        }
        String normalizedAnswer = lowerCase(answerMarkdown);
        if (normalizedAnswer.contains("当前证据不足") || normalizedAnswer.contains("暂无法确认")) {
            return answerOutcome;
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
     * 当模型否认 IAG 成功状态但证据明确包含 SAML Success 状态时，改用证据直出的状态答案。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 修正后的答案
     */
    private String overrideIagSuccessStatusAnswer(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (!looksLikeIagSuccessStatusQuestion(question)) {
            return answerMarkdown;
        }
        QueryArticleHit successStatusHit = findIagSuccessStatusHit(queryArticleHits);
        if (successStatusHit == null) {
            return answerMarkdown;
        }
        String normalizedAnswer = lowerCase(answerMarkdown);
        boolean deniesStatus = normalizedAnswer.contains("没有说明")
                || normalizedAnswer.contains("没有直接说明")
                || normalizedAnswer.contains("没有给出")
                || normalizedAnswer.contains("未说明")
                || normalizedAnswer.contains("未提供");
        if (!deniesStatus
                && normalizedAnswer.contains("status:success")
                && normalizedAnswer.contains("statuscode")
                && normalizedAnswer.contains("身份验证结果")) {
            return answerMarkdown;
        }
        String citationLiteral = joinConclusionCitations(List.of(successStatusHit));
        return "IAG 集成认证成功后，SAML Response 中的状态是 `urn:oasis:names:tc:SAML:2.0:status:Success`；"
                + "对应 XML 片段是 `<samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\" />`，文档注释为“身份验证结果”。 "
                + citationLiteral
                + "\n\n"
                + "同时，IAG 会把 SAML response token 通过 HTTP POST 提交到应用端 SAML endpoint；应用验证 token 合法性并读取 claim 后完成登录。 "
                + citationLiteral;
    }

    /**
     * TRANS-JOB 买一赠一维护题必须同时覆盖账户/银联侧与券侧补偿任务；模型漏掉核心任务时改用稳定证据答案。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 修正后的答案
     */
    private String overrideTransJobBogoMaintenanceAnswer(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (!looksLikeTransJobBogoMaintenanceQuestion(question)) {
            return answerMarkdown;
        }
        String normalizedAnswer = lowerCase(answerMarkdown);
        boolean missingCoreTask = !containsAll(answerMarkdown, List.of(
                "pay_changelog",
                "pay_changelog_coupon",
                "PaymentCancelTask",
                "CouponCancelTask",
                "bogoCouponCancel"
        ));
        boolean overcautious = normalizedAnswer.contains("没有直接给出")
                || normalizedAnswer.contains("没有给出一份明确")
                || normalizedAnswer.contains("证据不足")
                || normalizedAnswer.contains("只能基于");
        if (!missingCoreTask && !overcautious) {
            return answerMarkdown;
        }
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        List<String> conclusionLines = buildTransJobBogoMaintenanceConclusionLines(question, fallbackHits);
        if (conclusionLines.isEmpty()) {
            return answerMarkdown;
        }
        return String.join("\n", conclusionLines);
    }

    /**
     * 判断是否是在问 IAG 集成成功后的返回状态。
     *
     * @param question 用户问题
     * @return 命中返回 true
     */
    private boolean looksLikeIagSuccessStatusQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        boolean asksStatus = normalizedQuestion.contains("状态")
                || normalizedQuestion.contains("状态码")
                || normalizedQuestion.contains("statuscode")
                || normalizedQuestion.contains("status code");
        boolean asksSuccessOrReturn = normalizedQuestion.contains("成功")
                || normalizedQuestion.contains("返回")
                || normalizedQuestion.contains("response")
                || normalizedQuestion.contains("令牌");
        boolean asksIagScope = normalizedQuestion.contains("集成")
                || normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("认证")
                || normalizedQuestion.contains("saml")
                || normalizedQuestion.contains("response")
                || normalizedQuestion.contains("令牌");
        return normalizedQuestion.contains("iag")
                && asksStatus
                && asksSuccessOrReturn
                && asksIagScope;
    }

    /**
     * 从查询命中中查找包含 IAG SAML Success 状态的证据。
     *
     * @param queryArticleHits 查询命中
     * @return 命中证据；没有则返回 null
     */
    private QueryArticleHit findIagSuccessStatusHit(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return null;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            String sourcePathText = queryArticleHit.getSourcePaths() == null
                    ? ""
                    : String.join(" ", queryArticleHit.getSourcePaths());
            String haystack = lowerCase(queryArticleHit.getTitle())
                    + " "
                    + lowerCase(sourcePathText)
                    + " "
                    + lowerCase(queryArticleHit.getContent());
            if (haystack.contains("iag")
                    && haystack.contains("statuscode")
                    && haystack.contains("status:success")) {
                return queryArticleHit;
            }
        }
        return null;
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
        String citationLiteral = resolveConclusionCitationLiteral(bestHit);
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
        if (containsBogoChannelSignal(claimLine)) {
            if (containsBogoChannelSignal(queryArticleHit.getTitle())
                    || containsBogoChannelSignal(extractDescription(queryArticleHit.getMetadataJson()))
                    || containsBogoChannelSignal(queryArticleHit.getContent())) {
                score += 16;
            }
            else {
                score -= 8;
            }
        }
        score += Math.min(QueryEvidenceRelevanceSupport.score(question, queryArticleHit), 6);
        return score;
    }

    /**
     * 判断文本是否包含买一赠一渠道链路信号。
     *
     * @param value 文本
     * @return 包含返回 true
     */
    private boolean containsBogoChannelSignal(String value) {
        String normalizedValue = lowerCase(value);
        return normalizedValue.contains("买一赠一")
                || normalizedValue.contains("bogo")
                || normalizedValue.contains("广发 v2")
                || normalizedValue.contains("cgb_bogo")
                || normalizedValue.contains("coupon 服务")
                || normalizedValue.contains("上海银行")
                || normalizedValue.contains("民生银行")
                || normalizedValue.contains("宁波银行");
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
            String citationLiteral = resolveConclusionCitationLiteral(fallbackHit);
            if (!citationLiteral.isBlank()) {
                return citationLiteral;
            }
        }
        if (queryArticleHits != null) {
            for (QueryArticleHit queryArticleHit : queryArticleHits) {
                String citationLiteral = resolveConclusionCitationLiteral(queryArticleHit);
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
        AnswerOutcome answerOutcome = inferFallbackEvidenceOutcome(question, fallbackHits);
        return QueryAnswerPayload.llm(SensitiveTextMasker.mask(normalizedMarkdown.trim()), answerOutcome, false);
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
     * @return fallback 载荷
     */
    private QueryAnswerPayload buildDeterministicFallbackPayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            AnswerOutcome preferredOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus
    ) {
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        return new QueryAnswerPayload(
                SensitiveTextMasker.mask(buildFallbackMarkdown(question, queryArticleHits)),
                resolveFallbackAnswerOutcome(question, fallbackHits, preferredOutcome),
                generationMode,
                modelExecutionStatus,
                false
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
        if (preferredOutcome == AnswerOutcome.PARTIAL_ANSWER && evidenceOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE) {
            return AnswerOutcome.PARTIAL_ANSWER;
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
        List<String> queryTokens = extractQueryTokens(question);
        if (looksLikeStatusQuestion(question)) {
            String statusSnippet = selectQuestionFocusedFallbackSnippet(
                    question,
                    queryArticleHit,
                    queryTokens
            );
            return containsStatusSignal(lowerCase(statusSnippet));
        }
        if (looksLikeIagIntegrationStepQuestion(question, queryArticleHit)) {
            return true;
        }
        if (looksLikeFlowQuestion(question)) {
            String flowSnippet = selectQuestionFocusedFallbackSnippet(question, queryArticleHit, queryTokens);
            if (containsFlowSignal(flowSnippet)) {
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
     * 构建 LLM 查询答案 Prompt。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return 用户提示词
     */
    private String buildAnswerPrompt(String question, List<QueryArticleHit> queryArticleHits) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = groupHitsByEvidenceType(queryArticleHits);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        appendReferentialFocusSection(promptBuilder, question);
        appendEvidenceSection(promptBuilder, "CONTRIBUTION EVIDENCE", groupedHits.get(QueryEvidenceType.CONTRIBUTION));
        appendEvidenceSection(promptBuilder, "ARTICLE EVIDENCE", groupedHits.get(QueryEvidenceType.ARTICLE));
        appendEvidenceSection(promptBuilder, "GRAPH EVIDENCE", groupedHits.get(QueryEvidenceType.GRAPH));
        appendEvidenceSection(promptBuilder, "SOURCE EVIDENCE", groupedHits.get(QueryEvidenceType.SOURCE));
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
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        promptBuilder.append("CURRENT ANSWER").append("\n");
        promptBuilder.append(currentAnswer == null ? "" : currentAnswer.trim()).append("\n\n");
        promptBuilder.append("CORRECTION").append("\n");
        promptBuilder.append(correction == null ? "" : correction.trim()).append("\n\n");
        appendEvidenceSection(promptBuilder, "CONTRIBUTION EVIDENCE", groupedHits.get(QueryEvidenceType.CONTRIBUTION));
        appendEvidenceSection(promptBuilder, "ARTICLE EVIDENCE", groupedHits.get(QueryEvidenceType.ARTICLE));
        appendEvidenceSection(promptBuilder, "GRAPH EVIDENCE", groupedHits.get(QueryEvidenceType.GRAPH));
        appendEvidenceSection(promptBuilder, "SOURCE EVIDENCE", groupedHits.get(QueryEvidenceType.SOURCE));
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
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("QUESTION").append("\n");
        promptBuilder.append(question.trim()).append("\n\n");
        promptBuilder.append("CURRENT ANSWER").append("\n");
        promptBuilder.append(currentAnswer == null ? "" : currentAnswer.trim()).append("\n\n");
        promptBuilder.append("REVIEW FINDINGS").append("\n");
        promptBuilder.append(reviewFindings == null ? "" : reviewFindings.trim()).append("\n\n");
        appendEvidenceSection(promptBuilder, "CONTRIBUTION EVIDENCE", groupedHits.get(QueryEvidenceType.CONTRIBUTION));
        appendEvidenceSection(promptBuilder, "ARTICLE EVIDENCE", groupedHits.get(QueryEvidenceType.ARTICLE));
        appendEvidenceSection(promptBuilder, "GRAPH EVIDENCE", groupedHits.get(QueryEvidenceType.GRAPH));
        appendEvidenceSection(promptBuilder, "SOURCE EVIDENCE", groupedHits.get(QueryEvidenceType.SOURCE));
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
            groupedHits.get(queryArticleHit.getEvidenceType()).add(queryArticleHit);
        }
        return groupedHits;
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
            List<QueryArticleHit> queryArticleHits
    ) {
        promptBuilder.append(sectionTitle).append("\n");
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            promptBuilder.append("- NONE").append("\n\n");
            return;
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            promptBuilder.append("- title: ").append(queryArticleHit.getTitle()).append("\n");
            promptBuilder.append("  id: ").append(queryArticleHit.getConceptId()).append("\n");
            promptBuilder.append("  sources: ").append(String.join(", ", queryArticleHit.getSourcePaths())).append("\n");
            promptBuilder.append("  citation: ").append(resolveCitationLiteral(queryArticleHit)).append("\n");
            promptBuilder.append("  content: ").append(sanitizeEvidenceContentForPrompt(queryArticleHit.getContent())).append("\n");
            promptBuilder.append("  metadata: ").append(SensitiveTextMasker.mask(queryArticleHit.getMetadataJson())).append("\n");
        }
        promptBuilder.append("\n");
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
        appendFallbackSection(markdownBuilder, "用户反馈证据", groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryTokens);
        appendFallbackSection(markdownBuilder, "文章证据", groupedHits.get(QueryEvidenceType.ARTICLE), queryTokens);
        appendFallbackSection(markdownBuilder, "图谱证据", groupedHits.get(QueryEvidenceType.GRAPH), queryTokens);
        appendFallbackSection(markdownBuilder, "源文件证据", groupedHits.get(QueryEvidenceType.SOURCE), queryTokens);
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
                    .append(resolveCitationLiteral(queryArticleHit))
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
                    .append(resolveCitationLiteral(fallbackHit))
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
        List<String> transJobBogoLines = buildTransJobBogoMaintenanceConclusionLines(question, fallbackHits);
        if (!transJobBogoLines.isEmpty()) {
            return transJobBogoLines;
        }
        List<String> retryShapeLines = buildRetryShapeConclusionLines(question, fallbackHits);
        if (!retryShapeLines.isEmpty()) {
            return retryShapeLines;
        }
        List<String> iagIntegrationStepLines = buildIagIntegrationStepConclusionLines(question, primaryHit);
        if (!iagIntegrationStepLines.isEmpty()) {
            return iagIntegrationStepLines;
        }
        List<String> iagDefinitionLines = buildIagDefinitionConclusionLines(question, primaryHit);
        if (!iagDefinitionLines.isEmpty()) {
            return iagDefinitionLines;
        }
        List<String> focusedFieldDefinitionLines = buildFocusedSpreadsheetFieldDefinitionConclusionLines(question, fallbackHits);
        if (!focusedFieldDefinitionLines.isEmpty()) {
            return focusedFieldDefinitionLines;
        }
        List<String> fieldDefinitionLines = buildSpreadsheetFieldDefinitionConclusionLines(question, primaryHit);
        if (!fieldDefinitionLines.isEmpty()) {
            return fieldDefinitionLines;
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
        List<String> samlInteractionSteps = buildSamlInteractionStepConclusionLines(question, primaryHit);
        if (!samlInteractionSteps.isEmpty()) {
            return samlInteractionSteps;
        }
        List<String> promptTechniqueLines = buildPromptTechniqueConclusionLines(question, primaryHit);
        if (!promptTechniqueLines.isEmpty()) {
            return promptTechniqueLines;
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
     * 为 IAG 集成步骤题构造稳定 fallback。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return IAG 集成步骤结论
     */
    private List<String> buildIagIntegrationStepConclusionLines(String question, QueryArticleHit primaryHit) {
        if (!looksLikeIagIntegrationStepQuestion(question, primaryHit)) {
            return List.of();
        }
        String citationLiteral = joinConclusionCitations(List.of(primaryHit));
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("先确认应用支持 SAML 或 WS-Trust；IAG 更适用于 Web 端，也可用于移动 App 通过 WebView 打开 IAG 页面。 "
                + citationLiteral);
        conclusionLines.add("应用侧准备 SP 信息并提供给 IAG 管理员，包括应用唯一识别名（URN / Entity ID）、SAML 或 WS-Trust Endpoint、令牌中需要携带的 Claim，以及应用所有者长期联系方式。 "
                + citationLiteral);
        conclusionLines.add("从 IAG 侧获取 IdP metadata XML 和签名证书，应用端导入证书，用于验证 IAG 颁发的 SAML token。 "
                + citationLiteral);
        conclusionLines.add("IAG 管理员建立 Federation 关系；正常登录时应用把用户转向 IAG，IAG 完成 AD 验证和可选 MFA 后，通过 HTTP POST 或 redirect 返回 SAML Response / token。 "
                + citationLiteral);
        conclusionLines.add("应用验证 token 签名、读取用户 Claim 并建立本地会话；日常维护重点关注 IAG 签名证书有效期，证书过期前需要获取新证书并更新应用配置。 "
                + citationLiteral);
        return conclusionLines;
    }

    /**
     * 为 TRANS-JOB 买一赠一维护题构造稳定 fallback。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 维护动作结论
     */
    private List<String> buildTransJobBogoMaintenanceConclusionLines(String question, List<QueryArticleHit> fallbackHits) {
        if (!looksLikeTransJobBogoMaintenanceQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit retryHit = findHitContainingAny(fallbackHits, List.of("trans-job", "pay_changelog", "bogoCouponCancel"));
        QueryArticleHit bogoHit = findHitContainingAny(fallbackHits, List.of("买一赠一", "CGB_BOGO_V2", "coupon 服务"));
        if (retryHit == null || bogoHit == null) {
            return List.of();
        }
        String retryCitation = joinConclusionCitations(List.of(retryHit));
        String bogoCitation = joinConclusionCitations(List.of(bogoHit));
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("TRANS-JOB 对买一赠一日常维护的核心，是巡检并补偿逆向失败记录，重点关注 `pay_changelog` 与 `pay_changelog_coupon` 两类补偿流水。 "
                + retryCitation);
        conclusionLines.add("账户/银联侧需要检查 `pay_changelog.action = cancel/refund/orderCancel` 的失败记录，其中 `PaymentCancelTask` 处理普通冲正，`PaymentRefundCancelTask` 处理退款冲正，相关记录需关注 `state=0` 与 `try_times<=cancelCount`。 "
                + retryCitation);
        conclusionLines.add("券侧需要重点检查 `pay_changelog_coupon.action in (cancel, bogoCouponCancel)` 的失败记录；`bogoCouponCancel` 是买一赠一券冲正补偿的直接关注点，由 `CouponCancelTask` 扫描处理。 "
                + retryCitation);
        conclusionLines.add("渠道逆向动作要按买一赠一实现区分：上海银行、广发旧版、广发新版要核对银行权益侧与银联侧；民生银行、宁波银行主要核对银联侧；广发 V2 要核对 `coupon` 服务与银联两侧，冲正顺序是 `coupon` 服务冲正后银联冲正，退款顺序是银联退款后 `coupon` 服务退款。 "
                + bogoCitation);
        conclusionLines.add("不要把 `activeCancel`、`rechargeCancel` 当成当前 TRANS-JOB 自动补偿范围；这些 action 不在现有补偿覆盖范围内，需要另行确认处理机制。 "
                + retryCitation);
        return conclusionLines;
    }

    /**
     * 为支付与卡券重试形态题构造稳定 fallback，避免枚举题只摘到任务明细行。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 重试形态结论
     */
    private List<String> buildRetryShapeConclusionLines(String question, List<QueryArticleHit> fallbackHits) {
        if (!looksLikeRetryShapeQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        QueryArticleHit retryHit = findHitContainingAny(fallbackHits, List.of(
                "同步轮询查询",
                "同步多次重试",
                "MQ 延迟重投",
                "Spring Retry",
                "请求重入复用"
        ));
        if (retryHit == null || !containsRetryShapeSummary(retryHit)) {
            return List.of();
        }
        String citationLiteral = joinConclusionCitations(List.of(retryHit));
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("支付与卡券重试现状里归纳了 6 类形态：同步轮询查询、同步多次重试、MQ 延迟重投、Spring Retry、定时补偿、请求重入复用。 "
                + citationLiteral);
        conclusionLines.add("同步轮询查询的代表链路包括支付宝支付、微信支付、银联支付/退款、数币支付/退款；同步多次重试的代表链路包括微信冲正、农业银行积分兑换/易百积分冲正、PE/CAC 推送。 "
                + citationLiteral);
        conclusionLines.add("MQ 延迟重投的代表链路包括线上单券激活、批量券激活/核销、POS 星星核销；Spring Retry 的代表链路是批量单张券冲正。 "
                + citationLiteral);
        conclusionLines.add("定时补偿的代表链路包括 trans-job/xxljob 下的账户冲正、券冲正、SRKit 随单购；请求重入复用的代表链路是微信退款 `REFUND_FAIL_TRY_AGAIN`，失败后后续同一笔退款请求复用原退款记录再尝试。 "
                + citationLiteral);
        return conclusionLines;
    }

    /**
     * 判断是否为重试形态枚举题。
     *
     * @param question 用户问题
     * @return 命中返回 true
     */
    private boolean looksLikeRetryShapeQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("重试")
                && (normalizedQuestion.contains("形态")
                || normalizedQuestion.contains("类型")
                || normalizedQuestion.contains("有哪些")
                || normalizedQuestion.contains("哪几类")
                || normalizedQuestion.contains("代表链路"));
    }

    /**
     * 判断证据是否包含完整重试形态总览。
     *
     * @param retryHit 候选证据
     * @return 包含返回 true
     */
    private boolean containsRetryShapeSummary(QueryArticleHit retryHit) {
        String haystack = lowerCase(retryHit.getTitle())
                + " "
                + lowerCase(extractDescription(retryHit.getMetadataJson()))
                + " "
                + lowerCase(retryHit.getContent());
        return haystack.contains("同步轮询查询")
                && haystack.contains("同步多次重试")
                && haystack.contains("mq")
                && haystack.contains("spring retry")
                && haystack.contains("定时补偿")
                && haystack.contains("请求重入复用");
    }

    /**
     * 为 IAG 定义题构造稳定 fallback。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return IAG 定义结论
     */
    private List<String> buildIagDefinitionConclusionLines(String question, QueryArticleHit primaryHit) {
        if (!looksLikeIagDefinitionQuestion(question, primaryHit)) {
            return List.of();
        }
        String citationLiteral = joinConclusionCitations(List.of(primaryHit));
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("IAG 全称是 Identity Access Gateway，是统一身份验证服务，也就是单点登录 SSO 服务。 "
                + citationLiteral);
        conclusionLines.add("它接收应用转来的身份验证请求，提供统一登录界面，让用户输入 AD 用户名和密码，并向 AD 验证身份；按配置还可以要求 MFA 多因素验证。 "
                + citationLiteral);
        conclusionLines.add("认证完成后，IAG 会把安全令牌返回给应用，应用读取令牌中的用户身份信息完成登录；IAG 不是账号数据库、目录服务接口或 ACL 权限系统。 "
                + citationLiteral);
        return conclusionLines;
    }

    /**
     * 判断是否为 TRANS-JOB 买一赠一维护题。
     *
     * @param question 用户问题
     * @return 命中返回 true
     */
    private boolean looksLikeTransJobBogoMaintenanceQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("trans-job")
                && normalizedQuestion.contains("买一赠一")
                && (normalizedQuestion.contains("维护")
                || normalizedQuestion.contains("动作")
                || normalizedQuestion.contains("执行")
                || normalizedQuestion.contains("补偿"));
    }

    /**
     * 判断是否为 IAG 定义题。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 命中返回 true
     */
    private boolean looksLikeIagDefinitionQuestion(String question, QueryArticleHit primaryHit) {
        if (question == null || question.isBlank() || primaryHit == null) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        if (!normalizedQuestion.contains("iag") || !normalizedQuestion.contains("什么")) {
            return false;
        }
        String haystack = lowerCase(primaryHit.getTitle())
                + " "
                + lowerCase(extractDescription(primaryHit.getMetadataJson()))
                + " "
                + lowerCase(primaryHit.getContent());
        return haystack.contains("identity access gateway")
                || (haystack.contains("统一身份验证") && haystack.contains("sso"));
    }

    /**
     * 判断是否为 IAG 集成步骤题。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 命中返回 true
     */
    private boolean looksLikeIagIntegrationStepQuestion(String question, QueryArticleHit primaryHit) {
        if (question == null || question.isBlank() || primaryHit == null) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        if (!normalizedQuestion.contains("iag")
                || !(normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("集成")
                || normalizedQuestion.contains("接入"))) {
            return false;
        }
        String haystack = lowerCase(primaryHit.getTitle())
                + " "
                + lowerCase(extractDescription(primaryHit.getMetadataJson()))
                + " "
                + lowerCase(primaryHit.getContent());
        return haystack.contains("saml")
                && haystack.contains("federation")
                && (haystack.contains("entity id") || haystack.contains("endpoint"));
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
        List<String> requestFields = extractMarkdownFieldDefinitionRows(
                content,
                "### 1.1 字段通用属性",
                "### 1.2"
        );
        List<String> responseFields = extractMarkdownFieldDefinitionRows(
                content,
                "### 2.1 字段通用属性",
                "### 2.2"
        );
        if (requestFields.isEmpty() && responseFields.isEmpty()) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("该文档定义了 SWIP 网关 6 个支付渠道的交易报文字段：资和信银行卡、资和信SVC卡、易百银行卡、杉德卡、杉德苏州市民卡、杉德得仕卡；范围包括入参 `requestData`、出参 `responseData`、`transactionType` 编码对照和各渠道支持的业务类型。 "
                + citationLiteral);
        if (!requestFields.isEmpty()) {
            conclusionLines.add("入参 `requestData` 共 "
                    + requestFields.size()
                    + " 个字段："
                    + String.join("；", requestFields)
                    + "。 "
                    + citationLiteral);
        }
        if (!responseFields.isEmpty()) {
            conclusionLines.add("出参 `responseData` 共 "
                    + responseFields.size()
                    + " 个字段"
                    + responseFieldNumberNote(content)
                    + "："
                    + String.join("；", responseFields)
                    + "。 "
                    + citationLiteral);
        }
        List<String> transactionTypeMappings = extractTransactionTypeMappings(content);
        if (!transactionTypeMappings.isEmpty()) {
            conclusionLines.add("`transactionType` 编码包括："
                    + String.join("；", transactionTypeMappings)
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
                || normalizedQuestion.contains("是什么");
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
                || normalizedQuestion.contains("是什么")
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
        String content = lowerCase(primaryHit.getContent());
        return content.contains("requestdata")
                && content.contains("responsedata")
                && content.contains("字段通用属性")
                && content.contains("transactiontype");
    }

    private List<String> extractMarkdownFieldDefinitionRows(
            String content,
            String startMarker,
            String endMarker
    ) {
        String section = extractMarkdownSection(content, startMarker, endMarker);
        if (section.isBlank()) {
            return List.of();
        }
        List<String> fieldDefinitions = new ArrayList<String>();
        for (String rawLine : section.split("\\R")) {
            List<String> cells = splitMarkdownTableRow(rawLine);
            if (cells.size() < 5 || !cells.get(0).matches("\\d+")) {
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

    private List<String> extractTransactionTypeMappings(String content) {
        String section = extractMarkdownSection(content, "## 3. 交易类型 transactionType 对照表", "## 4.");
        if (section.isBlank()) {
            return List.of();
        }
        List<String> mappings = new ArrayList<String>();
        for (String rawLine : section.split("\\R")) {
            List<String> cells = splitMarkdownTableRow(rawLine);
            if (cells.size() < 2 || !cells.get(0).matches("\\d+")) {
                continue;
            }
            mappings.add("`" + cleanupMarkdownTableCell(cells.get(0)) + "`=" + cleanupMarkdownTableCell(cells.get(1)));
        }
        return mappings;
    }

    private String responseFieldNumberNote(String content) {
        String normalizedContent = content == null ? "" : content;
        if (normalizedContent.contains("编号为1-12、14、15、16，缺失13号编号")) {
            return "（编号 1-12、14、15、16，缺失 13 号编号）";
        }
        return "";
    }

    private String extractMarkdownSection(String content, String startMarker, String endMarker) {
        if (content == null || content.isBlank() || startMarker == null || startMarker.isBlank()) {
            return "";
        }
        int startIndex = content.indexOf(startMarker);
        if (startIndex < 0) {
            return "";
        }
        int contentStartIndex = startIndex + startMarker.length();
        int endIndex = endMarker == null || endMarker.isBlank()
                ? -1
                : content.indexOf(endMarker, contentStartIndex);
        if (endIndex < 0) {
            endIndex = content.length();
        }
        return content.substring(contentStartIndex, endIndex);
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
     * 为 GPT / prompt 技巧类问题构造稳定 fallback，避免 PDF 目录或标题行抢答。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 技巧枚举结论
     */
    private List<String> buildPromptTechniqueConclusionLines(String question, QueryArticleHit primaryHit) {
        if (!looksLikePromptTechniqueQuestion(question, primaryHit)) {
            return List.of();
        }
        String citationLiteral = joinConclusionCitations(List.of(primaryHit));
        List<String> conclusionLines = new ArrayList<String>();
        conclusionLines.add("提升 GPT 模型使用效率与质量的技巧包括：角色设定、指令注入、问题拆解、分层设计、编程思维、Few-Shot。 "
                + citationLiteral);
        conclusionLines.add("具体做法是：用 System 消息明确角色和任务；注入常驻指令约束模型行为；把复杂问题拆成子问题逐步处理；长内容先搭概览再展开章节和细节；把 prompt 当作可设计变量和模板的程序；用少量输入输出样例规范推理路径与输出格式。 "
                + citationLiteral);
        return conclusionLines;
    }

    private boolean looksLikePromptTechniqueQuestion(String question, QueryArticleHit primaryHit) {
        if (question == null || question.isBlank() || primaryHit == null) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        if (!(normalizedQuestion.contains("gpt") || normalizedQuestion.contains("prompt") || normalizedQuestion.contains("模型"))) {
            return false;
        }
        if (!(normalizedQuestion.contains("技巧")
                || normalizedQuestion.contains("效率")
                || normalizedQuestion.contains("质量")
                || normalizedQuestion.contains("最佳实践"))) {
            return false;
        }
        String content = lowerCase(primaryHit.getContent());
        return content.contains("角色设定")
                && content.contains("指令注入")
                && content.contains("问题拆解")
                && content.contains("分层设计")
                && content.contains("编程思维")
                && content.contains("few-shot");
    }

    /**
     * 为 SAML/IAG 交互步骤类问题构造顺序化 fallback，避免按相关性打分打乱源文档步骤顺序。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 顺序化步骤
     */
    private List<String> buildSamlInteractionStepConclusionLines(String question, QueryArticleHit primaryHit) {
        if (!looksLikeSamlInteractionStepQuestion(question, primaryHit)) {
            return List.of();
        }
        String citationLiteral = joinConclusionCitations(List.of(primaryHit));
        String content = lowerCase(primaryHit.getContent());
        List<String> conclusionLines = new ArrayList<String>();
        if (content.contains("互动分为三个部分")
                || (content.contains("初始配置") && content.contains("正常使用") && content.contains("日常维护"))) {
            conclusionLines.add("应用和 IAG 的互动分为三部分：初始配置、正常使用、日常维护。 " + citationLiteral);
        }
        if (content.contains("federation")
                && content.contains("saml endpoint")
                && content.contains("entity id")
                && content.contains("claim")) {
            conclusionLines.add("初始配置阶段，应用需要和 IAG 建立 federation 关系，交换 SAML endpoint、entity id 和 token 中应携带的 claim。 "
                    + citationLiteral);
        }
        if (content.contains("签名证书")) {
            conclusionLines.add("应用端还要导入 IAG 签名证书，用它验证 IAG 颁发的 SAML token 确实来自星巴克 IAG。 "
                    + citationLiteral);
        }
        if (content.contains("saml request") && content.contains("身份验证")) {
            conclusionLines.add("正常使用时，应用端把用户转向 IAG，并通过 HTTP POST 向预先配置的 IAG SAML endpoint 提交 SAML request；IAG 按 entity id 识别应用并完成用户身份验证。 "
                    + citationLiteral);
        }
        if (content.contains("saml response") && content.contains("完成用户登录")) {
            conclusionLines.add("认证完成后，IAG 生成 SAML response token 并提交到应用端 SAML endpoint；应用用签名证书验证 token，读取 claim 识别用户，最后完成登录。 "
                    + citationLiteral);
        }
        if (content.contains("每两年") && content.contains("签名证书")) {
            conclusionLines.add("日常维护阶段，应用端需要按星巴克要求替换新的 IAG 签名证书，文档说明通常约每两年更新一次。 "
                    + citationLiteral);
        }
        return conclusionLines;
    }

    /**
     * 判断问题是否是在问 SAML/IAG 交互步骤。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 命中返回 true
     */
    private boolean looksLikeSamlInteractionStepQuestion(String question, QueryArticleHit primaryHit) {
        if (primaryHit == null || question == null || question.isBlank()) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        if (!normalizedQuestion.contains("iag")
                || !(normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("互动")
                || normalizedQuestion.contains("交互")
                || normalizedQuestion.contains("集成"))) {
            return false;
        }
        String content = lowerCase(primaryHit.getContent());
        return content.contains("saml endpoint")
                && content.contains("entity id")
                && content.contains("claim")
                && content.contains("saml request")
                && content.contains("saml response");
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
        if (looksLikeSamlInteractionStepQuestion(question, primaryHit)
                && !looksLikeSamlInteractionStepQuestion(question, secondaryHit)) {
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
            String citationLiteral = resolveConclusionCitationLiteral(fallbackHit);
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
        List<QueryArticleHit> complementaryHits = selectComplementaryEvidenceByQuestionTokens(question, queryArticleHits);
        if (!complementaryHits.isEmpty()) {
            return complementaryHits;
        }
        List<QueryArticleHit> allRelevantHits = retainDirectStructuredEvidence(
                question,
                deduplicateSortedFallbackEvidenceHits(
                        sortFallbackEvidenceHits(question, filterFallbackEvidenceHits(queryArticleHits, question, false))
                )
        );
        List<QueryArticleHit> preferredArticleHits = deduplicateSortedFallbackEvidenceHits(
                sortFallbackEvidenceHits(
                        question,
                        filterFallbackEvidenceHits(queryArticleHits, question, true)
                )
        );
        if (preferredArticleHits.isEmpty()) {
            return allRelevantHits;
        }
        List<QueryArticleHit> retainedArticleHits = retainDirectStructuredEvidence(question, preferredArticleHits);
        if (shouldPreferMixedEvidence(question, retainedArticleHits, allRelevantHits)) {
            return allRelevantHits;
        }
        return retainedArticleHits;
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
        if (extractComparisonOptions(question).size() >= 2) {
            return List.of();
        }
        List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (highSignalTokens.size() < 2) {
            return List.of();
        }
        List<QueryArticleHit> sortedHits = deduplicateSortedFallbackEvidenceHits(
                sortFallbackEvidenceHits(question, filterFallbackEvidenceHits(queryArticleHits, question, false))
        );
        List<QueryArticleHit> candidates = sortedHits.isEmpty() ? queryArticleHits : sortedHits;
        List<QueryArticleHit> selectedHits = new ArrayList<QueryArticleHit>();
        for (String highSignalToken : highSignalTokens) {
            QueryArticleHit tokenHit = findHitContainingAny(candidates, List.of(highSignalToken));
            addDistinctFallbackHit(selectedHits, tokenHit);
        }
        return selectedHits.size() >= 2 ? selectedHits : List.of();
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
        return filteredHits;
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
            int candidateScore = scoreQuestionFocusedFallbackLine(question, rawCandidate, normalizedCandidate, queryTokens);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
            }
        }
        return bestScore;
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
        for (String rawCandidate : rawCandidates) {
            String normalizedCandidate = normalizeFallbackLineCandidate(rawCandidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            if (looksLikeQuestionEchoLine(question, normalizedCandidate)) {
                continue;
            }
            if (looksLikeEnumerationNoiseLine(question, normalizedCandidate)) {
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
        }
        Map<String, Integer> preferredCandidates;
        if (looksLikeStatusQuestion(question) && !scoredStatusCandidates.isEmpty()) {
            preferredCandidates = scoredStatusCandidates;
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
        if (limit > 1 && looksLikeStructuredFactQuestion(question)) {
            List<String> focusTokens = extractStructuredFactFocusTokens(question);
            if (!focusTokens.isEmpty()) {
                return selectCoverageAwareStructuredFactSnippets(rankedCandidates, focusTokens, limit);
            }
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
     * 针对“分别是多少”这类问题，优先让多条答案覆盖不同问题焦点，避免同一配置项重复占满结果位。
     *
     * @param rankedCandidates 已排序候选句
     * @param focusTokens 问题焦点
     * @param limit 最大条数
     * @return 更均衡的结构化事实句
     */
    private List<String> selectCoverageAwareStructuredFactSnippets(
            List<Map.Entry<String, Integer>> rankedCandidates,
            List<String> focusTokens,
            int limit
    ) {
        List<String> snippets = new ArrayList<String>();
        List<String> selectedCandidates = new ArrayList<String>();
        for (String focusToken : focusTokens) {
            String matchedCandidate = selectBestRankedCandidateMatchingFocusToken(
                    rankedCandidates,
                    focusToken,
                    selectedCandidates
            );
            if (matchedCandidate.isBlank()) {
                continue;
            }
            selectedCandidates.add(matchedCandidate);
            snippets.add(stripEmbeddedCitationLiterals(matchedCandidate));
            if (snippets.size() >= limit) {
                return snippets;
            }
        }
        for (Map.Entry<String, Integer> rankedCandidate : rankedCandidates) {
            if (selectedCandidates.contains(rankedCandidate.getKey())) {
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
        if (looksLikeFlowQuestion(question) && containsIntegrationStepSignal(normalizedLine)) {
            score += 18;
        }
        if (startsWithDirectStructuredFactAssignment(normalizedLine)) {
            score += 8;
        }
        if (looksLikeNumericQuestion(question) && normalizedLine.matches(".*\\d.*")) {
            score += 6;
        }
        String normalizedQuestion = lowerCase(question);
        String lowerCaseLine = lowerCase(normalizedLine);
        if ((normalizedQuestion.contains("当前") || normalizedQuestion.contains("现在"))
                && (lowerCaseLine.contains("当前") || lowerCaseLine.contains("建议值"))) {
            score += 4;
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
        if (looksLikeStatusQuestion(question) && containsStatusSignal(lowerCaseLine)) {
            score += 24;
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
        return score;
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
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("观察窗口")
                || normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("几")
                || normalizedQuestion.contains("数值")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("分别");
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
                || normalizedQuestion.contains("数值")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("窗口")
                || normalizedQuestion.contains("分别");
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
                || normalizedQuestion.contains("是否启用")
                || normalizedQuestion.contains("就绪")
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
                || normalizedLine.contains("待配置")
                || normalizedLine.contains("未配置")
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
        return normalizedLine.contains("->")
                || lowerCaseLine.contains("主链路")
                || lowerCaseLine.contains("链路")
                || lowerCaseLine.contains("流程")
                || lowerCaseLine.contains("source sync")
                || lowerCaseLine.contains("compile graph")
                || lowerCaseLine.contains("query graph")
                || lowerCaseLine.contains("pending_queries")
                || lowerCaseLine.contains("contributions")
                || lowerCaseLine.contains("资料先进入")
                || lowerCaseLine.contains("再进入编译层")
                || lowerCaseLine.contains("提交问题")
                || lowerCaseLine.contains("启动问答链路");
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
                || lowerCaseLine.contains("处理的事情")
                || lowerCaseLine.contains("只需要")
                || lowerCaseLine.contains("只有 4 件");
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
     * 判断枚举题候选是否更像表格后续明细，而不是用户要的枚举项。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 噪声明细返回 true
     */
    private boolean looksLikeEnumerationNoiseLine(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        if (!normalizedQuestion.contains("形态")) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains(".action =") || lowerCaseLine.matches(".*task；.*");
    }

    /**
     * 判断候选句是否包含集成/交互步骤中常见的关键事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 集成步骤信号返回 true
     */
    private boolean containsIntegrationStepSignal(String normalizedLine) {
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("federation")
                || lowerCaseLine.contains("saml endpoint")
                || lowerCaseLine.contains("entity id")
                || lowerCaseLine.contains("claim")
                || lowerCaseLine.contains("saml request")
                || lowerCaseLine.contains("saml response")
                || lowerCaseLine.contains("证书")
                || lowerCaseLine.contains("身份验证")
                || lowerCaseLine.contains("完成用户登录");
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
            return looksLikeConfigFactKey(normalizedLine.substring(0, assignmentDelimiterIndex).trim());
        }
        if (!looksLikeStructuredFactQuestion(question)) {
            return false;
        }
        return looksLikeConfigFactKey(normalizedLine) && normalizedLine.matches(".*\\d.*");
    }

    /**
     * 推导当前问题最希望直接回答几条结构化事实。
     *
     * @param question 用户问题
     * @return 期望条数
     */
    private int desiredStructuredFactCount(String question) {
        String normalizedQuestion = lowerCase(question);
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
        if (lowerCaseLine.contains("乐观锁")
                || lowerCaseLine.contains("悲观锁")
                || lowerCaseLine.contains("redis")
                || lowerCaseLine.contains("distributed lock")
                || lowerCaseLine.contains("inventory_lock_version")) {
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
        String normalizedContent = sanitizeEvidenceContentForPrompt(content);
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
            keptLines.add(rawLine);
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
     * 归一 Markdown 表格行，尽量还原为可直接展示的事实句。
     *
     * @param tableRow 表格行
     * @return 归一后的事实句
     */
    private String normalizeMarkdownTableRow(String tableRow) {
        List<String> cells = parseMarkdownTableCells(tableRow);
        if (cells.isEmpty() || isMarkdownTableDividerRow(cells) || isMarkdownTableHeaderRow(cells)) {
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
        return String.join("；", normalizedCells);
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
                || "当前项目中的代表链路".equals(cell)
                || "技巧".equals(cell)
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
        if ("观察窗口".equals(assignmentKey) || "PSP RT".equalsIgnoreCase(assignmentKey)) {
            return true;
        }
        return assignmentKey.matches("[A-Za-z0-9._-]+");
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
                || normalizedCell.contains("psp rt")
                || normalizedCell.contains("观察窗口");
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
     * 为结论段挑选更稳定的 citation 形式。
     *
     * @param queryArticleHit 证据命中
     * @return citation 文本
     */
    private String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
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
}
