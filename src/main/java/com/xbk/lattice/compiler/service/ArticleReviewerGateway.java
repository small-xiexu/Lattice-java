package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.query.service.ReviewResult;
import com.xbk.lattice.query.service.ReviewResultParser;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 文章审查网关
 *
 * 职责：封装编译侧文章审查调用，并在禁用时提供稳定的跳过策略
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ArticleReviewerGateway {

    private final LlmGateway llmGateway;

    private final ReviewResultParser reviewResultParser;

    private final LlmProperties llmProperties;

    /**
     * 创建文章审查网关。
     *
     * @param llmGateway LLM 网关
     * @param reviewResultParser 审查结果解析器
     * @param llmProperties LLM 配置
     */
    public ArticleReviewerGateway(
            LlmGateway llmGateway,
            ReviewResultParser reviewResultParser,
            LlmProperties llmProperties
    ) {
        this.llmGateway = llmGateway;
        this.reviewResultParser = reviewResultParser;
        this.llmProperties = llmProperties;
    }

    /**
     * 是否启用真实审查。
     *
     * @return 是否启用真实审查
     */
    public boolean isEnabled() {
        return llmProperties.isReviewEnabled();
    }

    /**
     * 执行文章审查。
     *
     * @param articleContent 文章内容
     * @param sourceContents 源文件正文
     * @return 审查结果
     */
    public ReviewResult review(String articleContent, String sourceContents) {
        if (!isEnabled()) {
            return ReviewResult.passed();
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
        String rawResult = llmGateway.review("review", LatticePrompts.SYSTEM_REVIEW, prompt);
        return reviewResultParser.parse(rawResult);
    }
}
