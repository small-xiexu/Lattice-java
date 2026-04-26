package com.xbk.lattice.query.service;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        List<String> matchedLines = selectMatchedLines(articleHit.getContent(), queryTokens);

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append(articleHit.getTitle());
        if (!matchedLines.isEmpty()) {
            answerBuilder.append("：").append(String.join("；", matchedLines));
            answerBuilder.append(" ").append(resolveCitationLiteral(articleHit));
            return answerBuilder.toString();
        }

        String description = extractDescription(articleHit.getMetadataJson());
        if (!description.isEmpty()) {
            answerBuilder.append("：").append(description);
            answerBuilder.append(" ").append(resolveCitationLiteral(articleHit));
            return answerBuilder.toString();
        }

        answerBuilder.append("：").append(articleHit.getContent());
        answerBuilder.append(" ").append(resolveCitationLiteral(articleHit));
        return answerBuilder.toString();
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
                ModelExecutionStatus.FAILED
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
                ModelExecutionStatus.FAILED
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
                QueryAnswerPayload parsedPayload = parseStructuredAnswerPayload(llmAnswer);
                if (parsedPayload != null) {
                    llmGateway.applyPromptCacheWritePolicy(envelope, resolvePromptCacheWritePolicy(parsedPayload));
                    return parsedPayload;
                }
                llmGateway.applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
                if (canReuseLegacyMarkdownAnswer(llmAnswer)) {
                    return QueryAnswerPayload.fallback(llmAnswer.trim());
                }
            }
            catch (RuntimeException ex) {
                // 查询主链允许在模型失败时降级到可预测 Markdown，避免直接中断用户查询。
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
                null,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.FAILED
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
                QueryAnswerPayload parsedPayload = parseStructuredAnswerPayload(llmAnswer);
                if (parsedPayload != null) {
                    llmGateway.applyPromptCacheWritePolicy(envelope, resolvePromptCacheWritePolicy(parsedPayload));
                    return parsedPayload;
                }
                llmGateway.applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
                if (canReuseLegacyMarkdownAnswer(llmAnswer)) {
                    return QueryAnswerPayload.fallback(llmAnswer.trim());
                }
            }
            catch (RuntimeException ex) {
                // 审查驱动的重写失败时，降级回基于证据的结构化答案，避免把问题单直接返回给用户。
            }
        }
        if (llmGateway == null) {
            return QueryAnswerPayload.ruleBased(buildFallbackMarkdown(question, queryArticleHits), AnswerOutcome.PARTIAL_ANSWER);
        }
        return QueryAnswerPayload.fallback(buildFallbackMarkdown(question, queryArticleHits));
    }

    /**
     * 解析结构化问答输出，并收敛为最小答案载荷。
     *
     * @param rawPayload 原始输出
     * @return 结构化答案载荷；若无法解析则返回 null
     */
    private QueryAnswerPayload parseStructuredAnswerPayload(String rawPayload) {
        JsonNode payloadNode = tryReadStructuredPayload(rawPayload);
        if (payloadNode == null) {
            return null;
        }
        String answerMarkdown = readText(payloadNode, "answerMarkdown");
        AnswerOutcome answerOutcome = readAnswerOutcome(payloadNode, "answerOutcome");
        if (answerMarkdown == null || answerMarkdown.isBlank() || answerOutcome == null) {
            return null;
        }
        if (!containsCitationLiteral(answerMarkdown)) {
            return null;
        }
        boolean answerCacheable = readBoolean(payloadNode, "answerCacheable");
        if (answerOutcome != AnswerOutcome.SUCCESS) {
            answerCacheable = false;
        }
        return QueryAnswerPayload.llm(answerMarkdown.trim(), answerOutcome, answerCacheable);
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
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalizedLine = line.trim();
            if (normalizedLine.isEmpty() || normalizedLine.startsWith("#") || normalizedLine.startsWith(">")) {
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
                buildFallbackMarkdown(question, queryArticleHits),
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
            promptBuilder.append("  content: ").append(queryArticleHit.getContent()).append("\n");
            promptBuilder.append("  metadata: ").append(queryArticleHit.getMetadataJson()).append("\n");
        }
        promptBuilder.append("\n");
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
        markdownBuilder.append("- ").append(correction == null ? "" : correction.trim()).append("\n\n");
        markdownBuilder.append("## 修订说明").append("\n");
        markdownBuilder.append("- 原答案摘要：").append(extractEvidenceSnippet(currentAnswer)).append("\n");
        markdownBuilder.append("- 纠正输入：").append(correction == null ? "" : correction.trim()).append("\n\n");
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
                    .append(selectFallbackEvidenceSnippet(queryArticleHit, queryTokens))
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
            markdownBuilder.append("- ").append(conclusionLine).append("\n");
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
        markdownBuilder.append("## 参考说明").append("\n");
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String snippet = selectReferenceFallbackSnippet(fallbackHit, comparisonOptions, queryTokens);
            markdownBuilder.append("- **").append(fallbackHit.getTitle()).append("**");
            if (!fallbackHit.getSourcePaths().isEmpty()) {
                markdownBuilder.append(" (").append(String.join(", ", fallbackHit.getSourcePaths())).append(")");
            }
            markdownBuilder.append("：")
                    .append(snippet)
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
        return buildGeneralFallbackConclusionLines(fallbackHits, queryTokens);
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
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        List<String> conclusionLines = new ArrayList<String>();
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return conclusionLines;
        }
        QueryArticleHit primaryHit = fallbackHits.get(0);
        conclusionLines.add("当前可确认的信息是："
                + selectFallbackEvidenceSnippet(primaryHit, queryTokens)
                + " "
                + joinConclusionCitations(List.of(primaryHit)));
        if (fallbackHits.size() > 1) {
            QueryArticleHit secondaryHit = fallbackHits.get(1);
            conclusionLines.add("补充证据还提到："
                    + selectFallbackEvidenceSnippet(secondaryHit, queryTokens)
                    + " "
                    + joinConclusionCitations(List.of(secondaryHit)));
        }
        return conclusionLines;
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
        List<QueryArticleHit> deduplicatedHits = deduplicateFallbackEvidenceHits(queryArticleHits);
        List<QueryArticleHit> preferredArticleHits = filterFallbackEvidenceHits(deduplicatedHits, question, true);
        if (!preferredArticleHits.isEmpty()) {
            return preferredArticleHits;
        }
        return filterFallbackEvidenceHits(deduplicatedHits, question, false);
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
        return selectFallbackEvidenceSnippet(queryArticleHit, queryTokens);
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
        if (lowerCaseLine.contains("本条目汇总")
                || lowerCaseLine.contains("主要记录了")
                || lowerCaseLine.contains("记录了若干")
                || lowerCaseLine.contains("回答时需要")
                || lowerCaseLine.contains("当前资料")
                || lowerCaseLine.contains("文档规则")) {
            score -= 8;
        }
        if (lowerCaseLine.contains("汇总")
                || lowerCaseLine.contains("概述")
                || lowerCaseLine.contains("概要")) {
            score -= 3;
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
        String[] rawLines = content.split("\\R");
        for (String rawLine : rawLines) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.isEmpty() || normalizedLine.startsWith("#") || normalizedLine.startsWith(">")) {
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
     * 提取证据摘要，避免兜底答案过长。
     *
     * @param content 证据正文
     * @return 摘要文本
     */
    private String extractEvidenceSnippet(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.length() <= 180) {
            return normalizedContent;
        }
        return normalizedContent.substring(0, 180) + "...";
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
        if (lowerCaseLine.startsWith("summary:")
                || lowerCaseLine.startsWith("description:")
                || lowerCaseLine.startsWith("content:")) {
            return extractFallbackFieldValue(normalizedLine);
        }
        if (lowerCaseLine.startsWith("title:")
                || lowerCaseLine.startsWith("referential_keywords:")
                || lowerCaseLine.startsWith("sources:")
                || lowerCaseLine.startsWith("source_paths:")
                || lowerCaseLine.startsWith("article_key:")
                || lowerCaseLine.startsWith("concept_id:")
                || lowerCaseLine.startsWith("file_path:")
                || lowerCaseLine.startsWith("metadata:")
                || lowerCaseLine.startsWith("depends_on:")) {
            return "";
        }
        return normalizedLine;
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
        if (queryArticleHit == null) {
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
