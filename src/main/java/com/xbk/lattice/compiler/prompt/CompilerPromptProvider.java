package com.xbk.lattice.compiler.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Compiler Prompt 外置提供者
 *
 * 职责：从 classpath 加载 Writer / Reviewer / Fixer system prompt，
 *       替换 {{shared-grounding-rules}} 占位符后提供给调用方
 *
 * @author xiexu
 */
@Service
public class CompilerPromptProvider {

    private static final String INCLUDE_PLACEHOLDER = "{{shared-grounding-rules}}";

    private final String writerPrompt;

    private final String writerImagePrompt;

    private final String reviewerPrompt;

    private final String reviewerImagePrompt;

    private final String fixerPrompt;

    /**
     * 创建 Compiler Prompt 外置提供者。
     *
     * 在构造时加载全部 prompt 文件并完成占位符替换与校验。
     */
    public CompilerPromptProvider() {
        this(CompilerPromptProvider::loadClasspathResource);
    }

    /**
     * 使用指定资源加载器创建 Compiler Prompt 外置提供者。
     *
     * @param promptResourceLoader prompt 资源加载器
     */
    CompilerPromptProvider(PromptResourceLoader promptResourceLoader) {
        String groundingRules = loadRequiredResource(promptResourceLoader, "prompts/compiler/shared-grounding-rules.md");
        this.writerPrompt = resolveIncludes(
                loadRequiredResource(promptResourceLoader, "prompts/compiler/writer.md"),
                groundingRules,
                "writer.md"
        );
        this.writerImagePrompt = resolveIncludes(
                loadRequiredResource(promptResourceLoader, "prompts/compiler/writer-image.md"),
                groundingRules,
                "writer-image.md"
        );
        this.reviewerPrompt = resolveIncludes(
                loadRequiredResource(promptResourceLoader, "prompts/compiler/reviewer.md"),
                groundingRules,
                "reviewer.md"
        );
        this.reviewerImagePrompt = resolveIncludes(
                loadRequiredResource(promptResourceLoader, "prompts/compiler/reviewer-image.md"),
                groundingRules,
                "reviewer-image.md"
        );
        this.fixerPrompt = resolveIncludes(
                loadRequiredResource(promptResourceLoader, "prompts/compiler/fixer.md"),
                groundingRules,
                "fixer.md"
        );
    }

    /**
     * 返回 Writer system prompt。
     *
     * @return Writer system prompt
     */
    public String writerPrompt() {
        return writerPrompt;
    }

    /**
     * 返回 Writer（图片概念）system prompt。
     *
     * @return Writer 图片 system prompt
     */
    public String writerImagePrompt() {
        return writerImagePrompt;
    }

    /**
     * 返回 Reviewer system prompt。
     *
     * @return Reviewer system prompt
     */
    public String reviewerPrompt() {
        return reviewerPrompt;
    }

    /**
     * 返回 Reviewer（图片文章）system prompt。
     *
     * @return Reviewer 图片 system prompt
     */
    public String reviewerImagePrompt() {
        return reviewerImagePrompt;
    }

    /**
     * 返回 Fixer system prompt。
     *
     * @return Fixer system prompt
     */
    public String fixerPrompt() {
        return fixerPrompt;
    }

    /**
     * 从资源加载器加载必需文件并校验非空。
     *
     * @param promptResourceLoader prompt 资源加载器
     * @param path classpath 路径
     * @return 文件内容
     */
    private static String loadRequiredResource(PromptResourceLoader promptResourceLoader, String path) {
        String content = promptResourceLoader.load(path);
        if (content == null) {
            throw new IllegalStateException("Compiler prompt file missing: " + path);
        }
        if (content.isBlank()) {
            throw new IllegalStateException("Compiler prompt file is empty: " + path);
        }
        return content;
    }

    /**
     * 从 classpath 加载资源文件。
     *
     * @param path classpath 路径
     * @return 文件内容
     */
    private static String loadClasspathResource(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Compiler prompt file missing: " + path);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to load compiler prompt: " + path, exception);
        }
    }

    /**
     * 替换 {{shared-grounding-rules}} 占位符并校验。
     *
     * @param template 模板内容
     * @param groundingRules 共享基础规则
     * @param fileName 文件名（用于错误报告）
     * @return 替换后的 prompt
     */
    private String resolveIncludes(String template, String groundingRules, String fileName) {
        String resolved = template.replace(INCLUDE_PLACEHOLDER, groundingRules);
        if (resolved.contains("{{")) {
            throw new IllegalStateException(
                    "Unresolved placeholder in compiler prompt " + fileName
                            + ": " + resolved.substring(0, Math.min(200, resolved.length()))
            );
        }
        return resolved;
    }

    /**
     * Prompt 资源加载器。
     *
     * 职责：为生产 classpath 加载与测试可控加载提供统一接口
     *
     * @author xiexu
     */
    @FunctionalInterface
    interface PromptResourceLoader {

        /**
         * 加载指定路径的 prompt 内容。
         *
         * @param path classpath 路径
         * @return prompt 内容，缺失时返回 null
         */
        String load(String path);
    }
}
