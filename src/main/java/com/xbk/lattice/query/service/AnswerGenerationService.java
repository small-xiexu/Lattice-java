package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.LlmGateway;
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

    private static final String SYSTEM_QUERY_ANSWER = """
            你是 Lattice 查询助手。请基于给定证据回答用户问题。

            输出要求：
            1. 必须输出 Markdown
            2. 优先引用 ARTICLE / SOURCE / CONTRIBUTION 中直接可证实的信息
            3. 如果信息不足，要明确指出缺口，不要编造
            4. 回答语言使用简体中文，保留必要英文术语或原始配置项
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
            return answerBuilder.toString();
        }

        String description = extractDescription(articleHit.getMetadataJson());
        if (!description.isEmpty()) {
            answerBuilder.append("：").append(description);
            return answerBuilder.toString();
        }

        answerBuilder.append("：").append(articleHit.getContent());
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
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return "未找到相关知识";
        }
        if (containsOnlyArticleEvidence(queryArticleHits)) {
            return generate(question, queryArticleHits.get(0));
        }

        if (llmGateway != null) {
            try {
                String llmAnswer = llmGateway.compile(
                        "query-answer",
                        SYSTEM_QUERY_ANSWER,
                        buildAnswerPrompt(question, queryArticleHits)
                );
                if (llmAnswer != null && !llmAnswer.isBlank()) {
                    return llmAnswer.trim();
                }
            }
            catch (RuntimeException ex) {
                // 查询主链允许在模型失败时降级到可预测 Markdown，避免直接中断用户查询。
            }
        }
        return buildFallbackMarkdown(question, queryArticleHits);
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
        if (llmGateway != null) {
            try {
                String llmAnswer = llmGateway.compile(
                        "query-revise",
                        SYSTEM_QUERY_REVISE,
                        buildRevisePrompt(question, currentAnswer, correction, queryArticleHits)
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
            markdownBuilder.append("：").append(extractEvidenceSnippet(queryArticleHit.getContent())).append("\n");
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
}
