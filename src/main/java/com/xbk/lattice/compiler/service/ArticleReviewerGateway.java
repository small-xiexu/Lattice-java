package com.xbk.lattice.compiler.service;

import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.prompt.CompilerPromptProvider;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.service.ReviewResultParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 文章审查网关
 *
 * 职责：封装编译侧文章审查调用，并在禁用时提供稳定的跳过策略
 *
 * @author xiexu
 */
@Service
public class ArticleReviewerGateway {

    private final LlmGateway llmGateway;

    private final ReviewResultParser reviewResultParser;

    private final LlmProperties llmProperties;

    private final RuleBasedArticleReviewer ruleBasedArticleReviewer;

    private final CompileJobJdbcRepository compileJobJdbcRepository;

    private final CompilerPromptProvider compilerPromptProvider;

    /**
     * 创建文章审查网关。
     *
     * @param llmGateway LLM 网关
     * @param reviewResultParser 审查结果解析器
     * @param llmProperties LLM 配置
     * @param ruleBasedArticleReviewer 规则审查器
     */
    public ArticleReviewerGateway(
            LlmGateway llmGateway,
            ReviewResultParser reviewResultParser,
            LlmProperties llmProperties,
            RuleBasedArticleReviewer ruleBasedArticleReviewer
    ) {
        this(llmGateway, reviewResultParser, llmProperties, ruleBasedArticleReviewer, null, null);
    }

    /**
     * 创建文章审查网关。
     *
     * @param llmGateway LLM 网关
     * @param reviewResultParser 审查结果解析器
     * @param llmProperties LLM 配置
     * @param ruleBasedArticleReviewer 规则审查器
     * @param compileJobJdbcRepository 编译作业仓储
     */
    public ArticleReviewerGateway(
            LlmGateway llmGateway,
            ReviewResultParser reviewResultParser,
            LlmProperties llmProperties,
            RuleBasedArticleReviewer ruleBasedArticleReviewer,
            CompileJobJdbcRepository compileJobJdbcRepository
    ) {
        this(llmGateway, reviewResultParser, llmProperties, ruleBasedArticleReviewer, compileJobJdbcRepository, null);
    }

    /**
     * 创建文章审查网关。
     *
     * @param llmGateway LLM 网关
     * @param reviewResultParser 审查结果解析器
     * @param llmProperties LLM 配置
     * @param ruleBasedArticleReviewer 规则审查器
     * @param compileJobJdbcRepository 编译作业仓储
     * @param compilerPromptProvider Compiler Prompt 外置提供者
     */
    @Autowired
    public ArticleReviewerGateway(
            LlmGateway llmGateway,
            ReviewResultParser reviewResultParser,
            LlmProperties llmProperties,
            RuleBasedArticleReviewer ruleBasedArticleReviewer,
            CompileJobJdbcRepository compileJobJdbcRepository,
            CompilerPromptProvider compilerPromptProvider
    ) {
        this.llmGateway = llmGateway;
        this.reviewResultParser = reviewResultParser;
        this.llmProperties = llmProperties;
        this.ruleBasedArticleReviewer = ruleBasedArticleReviewer;
        this.compileJobJdbcRepository = compileJobJdbcRepository;
        this.compilerPromptProvider = compilerPromptProvider;
    }

    /**
     * 是否启用真实审查。
     *
     * @return 是否启用真实审查
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * 执行文章审查。
     *
     * @param articleContent 文章内容
     * @param sourceContents 源文件正文
     * @return 审查结果
     */
    public ReviewResult review(String articleContent, String sourceContents) {
        return review(articleContent, sourceContents, null, null, "reviewer");
    }

    /**
     * 执行文章审查。
     *
     * @param articleContent 文章内容
     * @param sourceContents 源文件正文
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 审查结果
     */
    public ReviewResult review(
            String articleContent,
            String sourceContents,
            String scopeId,
            String scene,
            String agentRole
    ) {
        return review(articleContent, sourceContents, scopeId, scene, agentRole, null);
    }

    /**
     * 执行文章审查。
     *
     * @param articleContent 文章内容
     * @param sourceContents 源文件正文
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param requestedReviewMode 请求审查模式
     * @return 审查结果
     */
    public ReviewResult review(
            String articleContent,
            String sourceContents,
            String scopeId,
            String scene,
            String agentRole,
            String requestedReviewMode
    ) {
        String reviewMode = resolveReviewMode(scopeId, requestedReviewMode);
        if (!CompileExecutionRequest.isLlmReviewMode(reviewMode)) {
            return ruleBasedArticleReviewer.review(articleContent, sourceContents);
        }
        if (llmGateway == null) {
            return ReviewResult.timeoutFallback();
        }
        String truncatedSources = sourceContents.length() > 12000
                ? sourceContents.substring(0, 12000)
                : sourceContents;
        String prompt = """
                === COMPILED ARTICLE ===
                %s
                === END ARTICLE ===

                === ORIGINAL SOURCE MATERIALS (sample) ===
                %s
                === END SOURCES ===
                """.formatted(articleContent, truncatedSources);
        String effectiveScene = scene == null || scene.isBlank()
                ? ExecutionLlmSnapshotService.COMPILE_SCENE
                : scene;
        String effectiveAgentRole = agentRole == null || agentRole.isBlank()
                ? ExecutionLlmSnapshotService.ROLE_REVIEWER
                : agentRole;
        String systemPrompt = resolveReviewSystemPrompt(articleContent);
        try {
            LlmInvocationEnvelope envelope = scopeId == null || scopeId.isBlank()
                    ? llmGateway.invokeRaw(
                            effectiveScene,
                            effectiveAgentRole,
                            "compile-review",
                            systemPrompt,
                            prompt
                    )
                    : llmGateway.invokeRawWithScope(
                            scopeId,
                            effectiveScene,
                            effectiveAgentRole,
                            "compile-review",
                            systemPrompt,
                            prompt
                    );
            llmGateway.applyPromptCacheWritePolicy(
                    envelope,
                    reviewResultParser.resolvePromptCacheWritePolicy(envelope.getContent())
            );
            return reviewResultParser.parse(envelope.getContent());
        }
        catch (RuntimeException exception) {
            return ReviewResult.timeoutFallback();
        }
    }

    /**
     * 解析当前审查路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 审查路由
     */
    public String resolveRoute(String scopeId, String scene, String agentRole) {
        return resolveRoute(scopeId, scene, agentRole, null);
    }

    /**
     * 解析当前审查路由。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param requestedReviewMode 请求审查模式
     * @return 审查路由
     */
    public String resolveRoute(String scopeId, String scene, String agentRole, String requestedReviewMode) {
        if (!CompileExecutionRequest.isLlmReviewMode(resolveReviewMode(scopeId, requestedReviewMode))) {
            return "rule-based";
        }
        if (llmGateway == null) {
            return "llm-unavailable";
        }
        String effectiveScene = scene == null || scene.isBlank()
                ? ExecutionLlmSnapshotService.COMPILE_SCENE
                : scene;
        String effectiveAgentRole = agentRole == null || agentRole.isBlank()
                ? ExecutionLlmSnapshotService.ROLE_REVIEWER
                : agentRole;
        if (scopeId == null || scopeId.isBlank()) {
            return llmGateway.routeResolution(effectiveScene, effectiveAgentRole).getRouteLabel();
        }
        return llmGateway.routeFor(scopeId, effectiveScene, effectiveAgentRole);
    }

    /**
     * 解析当前作业的审查模式。
     *
     * @param scopeId 作用域标识
     * @param requestedReviewMode 请求审查模式
     * @return 审查模式
     */
    public String resolveReviewMode(String scopeId, String requestedReviewMode) {
        if (requestedReviewMode != null && !requestedReviewMode.isBlank()) {
            return CompileExecutionRequest.normalizeReviewMode(requestedReviewMode);
        }
        if (scopeId == null || scopeId.isBlank() || compileJobJdbcRepository == null) {
            return llmProperties.isReviewEnabled()
                    ? CompileExecutionRequest.REVIEW_MODE_LLM
                    : CompileExecutionRequest.REVIEW_MODE_RULE_BASED;
        }
        return compileJobJdbcRepository.findReviewModeByJobId(scopeId)
                .map(CompileExecutionRequest::normalizeReviewMode)
                .orElse(CompileExecutionRequest.REVIEW_MODE_RULE_BASED);
    }

    /**
     * 根据文章来源类型选择更合适的审查提示词。
     *
     * @param articleContent 文章内容
     * @return 审查提示词
     */
    private String resolveReviewSystemPrompt(String articleContent) {
        if (isImageDominantArticle(articleContent)) {
            return compilerPromptProvider != null
                    ? compilerPromptProvider.reviewerImagePrompt()
                    : LatticePrompts.SYSTEM_REVIEW_IMAGE_ARTICLE;
        }
        return compilerPromptProvider != null
                ? compilerPromptProvider.reviewerPrompt()
                : LatticePrompts.SYSTEM_REVIEW;
    }

    /**
     * 判断文章是否主要来自图片 / OCR 资产。
     *
     * @param articleContent 文章内容
     * @return 图片主导返回 true
     */
    private boolean isImageDominantArticle(String articleContent) {
        ArticleMarkdownSupport.ParsedFrontmatter parsedFrontmatter = ArticleMarkdownSupport.parse(articleContent);
        if (!parsedFrontmatter.isPresent() || parsedFrontmatter.getSourcePaths().isEmpty()) {
            return false;
        }
        List<String> sourcePaths = parsedFrontmatter.getSourcePaths();
        int imageSourceCount = 0;
        for (String sourcePath : sourcePaths) {
            if (isImageLikePath(sourcePath)) {
                imageSourceCount++;
            }
        }
        return imageSourceCount == sourcePaths.size();
    }

    /**
     * 判断来源路径是否为图片 / 视觉资产。
     *
     * @param sourcePath 来源路径
     * @return 图片类资源返回 true
     */
    private boolean isImageLikePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }
        String normalizedPath = sourcePath.trim().toLowerCase(Locale.ROOT);
        return normalizedPath.endsWith(".png")
                || normalizedPath.endsWith(".jpg")
                || normalizedPath.endsWith(".jpeg")
                || normalizedPath.endsWith(".gif")
                || normalizedPath.endsWith(".bmp")
                || normalizedPath.endsWith(".webp")
                || normalizedPath.endsWith(".svg")
                || normalizedPath.endsWith(".drawio");
    }
}
