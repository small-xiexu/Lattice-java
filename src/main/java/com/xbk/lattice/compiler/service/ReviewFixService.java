package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.prompt.CompilerPromptProvider;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.query.domain.ReviewIssue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 审查修复服务
 *
 * 职责：基于审查问题生成修复提示词，并调用编译模型输出修复后的完整文章
 *
 * @author xiexu
 */
@Service
public class ReviewFixService {

    private static final String COMPILE_SCENE = "compile";

    private static final String FIXER_ROLE = "fixer";

    private static final int FIXER_ARTICLE_MAX_CHARS = 6000;

    private static final int FIXER_SOURCE_MAX_CHARS = 7000;

    private final LlmGateway llmGateway;

    private final CompilerPromptProvider compilerPromptProvider;

    /**
     * 创建审查修复服务。
     *
     * @param llmGateway LLM 网关
     */
    public ReviewFixService(LlmGateway llmGateway) {
        this(llmGateway, null);
    }

    /**
     * 创建审查修复服务。
     *
     * @param llmGateway LLM 网关
     * @param compilerPromptProvider Compiler Prompt 外置提供者
     */
    @Autowired
    public ReviewFixService(LlmGateway llmGateway, CompilerPromptProvider compilerPromptProvider) {
        this.llmGateway = llmGateway;
        this.compilerPromptProvider = compilerPromptProvider;
    }

    /**
     * 应用审查修复。
     *
     * @param articleContent 原始文章
     * @param reviewIssues 审查问题
     * @param sourceContents 源文件正文
     * @return 修复后的文章；失败时返回 null
     */
    public String applyFix(String articleContent, List<ReviewIssue> reviewIssues, String sourceContents) {
        return applyFix(articleContent, reviewIssues, sourceContents, null, null, "fixer");
    }

    /**
     * 应用审查修复。
     *
     * @param articleContent 原始文章
     * @param reviewIssues 审查问题
     * @param sourceContents 源文件正文
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 修复后的文章；失败时返回 null
     */
    public String applyFix(
            String articleContent,
            List<ReviewIssue> reviewIssues,
            String sourceContents,
            String scopeId,
            String scene,
            String agentRole
    ) {
        String issueList = buildIssueList(reviewIssues);
        String boundedArticleContent = boundText(articleContent, FIXER_ARTICLE_MAX_CHARS);
        String boundedSources = boundText(sourceContents, FIXER_SOURCE_MAX_CHARS);
        try {
            String userPrompt = """
                    审查员发现的问题:
                    %s

                    原始文章:
                    %s

                    源文件参考:
                    %s
                    """.formatted(issueList, boundedArticleContent, boundedSources);
            String systemPrompt = compilerPromptProvider != null
                    ? compilerPromptProvider.fixerPrompt()
                    : LatticePrompts.SYSTEM_REVIEW_FIX;
            return scopeId == null || scopeId.isBlank()
                    ? llmGateway.generateText(
                            COMPILE_SCENE,
                            FIXER_ROLE,
                            "review-fix",
                            systemPrompt,
                            userPrompt
                    )
                    : llmGateway.generateTextWithScope(
                            scopeId,
                            scene,
                            agentRole,
                            "review-fix",
                            systemPrompt,
                            userPrompt
                    );
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 截断文本到指定长度。
     *
     * @param text 原始文本
     * @param maxChars 最大字符数
     * @return 有界文本
     */
    private String boundText(String text, int maxChars) {
        if (text == null || text.isBlank() || maxChars <= 0) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars).trim();
    }

    /**
     * 构建问题列表文本，最多保留前 5 条。
     *
     * @param reviewIssues 审查问题
     * @return 问题列表文本
     */
    private String buildIssueList(List<ReviewIssue> reviewIssues) {
        List<ReviewIssue> sortedIssues = new ArrayList<ReviewIssue>(reviewIssues);
        sortedIssues.sort(Comparator.comparing(this::severityRank));
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, sortedIssues.size());
        for (int index = 0; index < limit; index++) {
            ReviewIssue reviewIssue = sortedIssues.get(index);
            builder.append(index + 1)
                    .append(". [")
                    .append(reviewIssue.getSeverity())
                    .append("] ")
                    .append(reviewIssue.getCategory())
                    .append(": ")
                    .append(reviewIssue.getDescription())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 严重度排序值。
     *
     * @param reviewIssue 审查问题
     * @return 排序值
     */
    private Integer severityRank(ReviewIssue reviewIssue) {
        String severity = reviewIssue.getSeverity();
        if ("HIGH".equalsIgnoreCase(severity)) {
            return Integer.valueOf(0);
        }
        if ("MEDIUM".equalsIgnoreCase(severity)) {
            return Integer.valueOf(1);
        }
        return Integer.valueOf(2);
    }
}
