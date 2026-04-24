package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            return QueryAnswerPayload.ruleBased(generate(question, queryArticleHits.get(0)), AnswerOutcome.PARTIAL_ANSWER);
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
                if (llmAnswer != null && !llmAnswer.isBlank() && !looksLikeStructuredJson(llmAnswer)) {
                    return QueryAnswerPayload.fallback(llmAnswer.trim());
                }
            }
            catch (RuntimeException ex) {
                // 查询主链允许在模型失败时降级到可预测 Markdown，避免直接中断用户查询。
            }
        }
        if (llmGateway == null) {
            return QueryAnswerPayload.ruleBased(buildFallbackMarkdown(question, queryArticleHits), AnswerOutcome.PARTIAL_ANSWER);
        }
        return QueryAnswerPayload.fallback(buildFallbackMarkdown(question, queryArticleHits));
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
                if (llmAnswer != null && !llmAnswer.isBlank() && !looksLikeStructuredJson(llmAnswer)) {
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
            if (matchedLines.size() >= 2) {
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
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = groupHitsByEvidenceType(queryArticleHits);
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# 查询回答").append("\n\n");
        markdownBuilder.append("## 问题").append("\n");
        markdownBuilder.append(question.trim()).append("\n\n");
        appendFallbackSection(markdownBuilder, "用户反馈证据", groupedHits.get(QueryEvidenceType.CONTRIBUTION));
        appendFallbackSection(markdownBuilder, "文章证据", groupedHits.get(QueryEvidenceType.ARTICLE));
        appendFallbackSection(markdownBuilder, "图谱证据", groupedHits.get(QueryEvidenceType.GRAPH));
        appendFallbackSection(markdownBuilder, "源文件证据", groupedHits.get(QueryEvidenceType.SOURCE));
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
        appendFallbackSection(markdownBuilder, "用户反馈证据", groupedHits.get(QueryEvidenceType.CONTRIBUTION));
        appendFallbackSection(markdownBuilder, "文章证据", groupedHits.get(QueryEvidenceType.ARTICLE));
        appendFallbackSection(markdownBuilder, "图谱证据", groupedHits.get(QueryEvidenceType.GRAPH));
        appendFallbackSection(markdownBuilder, "源文件证据", groupedHits.get(QueryEvidenceType.SOURCE));
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
            List<QueryArticleHit> queryArticleHits
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
                    .append(extractEvidenceSnippet(queryArticleHit.getContent()))
                    .append(" ")
                    .append(resolveCitationLiteral(queryArticleHit))
                    .append("\n");
        }
        markdownBuilder.append("\n");
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
     * 解析单条证据对应的标准引用文本。
     *
     * @param queryArticleHit 证据命中
     * @return 引用文本
     */
    private String resolveCitationLiteral(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        if (!queryArticleHit.getSourcePaths().isEmpty()) {
            return "[→ " + queryArticleHit.getSourcePaths().get(0) + "]";
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
}
