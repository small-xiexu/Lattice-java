package com.xbk.lattice.compiler.prompt;

import com.xbk.lattice.compiler.config.CompilerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCHEMA 感知 Prompt 服务
 *
 * 职责：在静态基础 prompt 上叠加输入目录中的 SCHEMA.md 规则
 *
 * @author xiexu
 */
@Service
public class SchemaAwarePrompts {

    private static final int MAX_SCHEMA_CHARS = 8000;

    private final CompilerProperties compilerProperties;

    private final CompilerPromptProvider compilerPromptProvider;

    private final Map<Path, String> schemaCache = new ConcurrentHashMap<Path, String>();

    /**
     * 创建 SCHEMA 感知 Prompt 服务。
     *
     * @param compilerProperties 编译配置
     */
    public SchemaAwarePrompts(CompilerProperties compilerProperties) {
        this(compilerProperties, null);
    }

    /**
     * 创建 SCHEMA 感知 Prompt 服务。
     *
     * @param compilerProperties 编译配置
     * @param compilerPromptProvider Compiler Prompt 外置提供者
     */
    @Autowired
    public SchemaAwarePrompts(CompilerProperties compilerProperties, CompilerPromptProvider compilerPromptProvider) {
        this.compilerProperties = compilerProperties;
        this.compilerPromptProvider = compilerPromptProvider;
    }

    /**
     * 获取分析 prompt。
     *
     * @param outputDir 输入目录
     * @return 叠加后的分析 prompt
     */
    public String getAnalyzePrompt(Path outputDir) {
        return appendSchemaRules(LatticePrompts.SYSTEM_ANALYZE, outputDir);
    }

    /**
     * 获取文章编译 prompt。
     *
     * @param outputDir 输入目录
     * @return 叠加后的编译 prompt
     */
    public String getCompileArticlePrompt(Path outputDir) {
        String basePrompt = compilerPromptProvider != null
                ? compilerPromptProvider.writerPrompt()
                : LatticePrompts.SYSTEM_COMPILE_ARTICLE;
        return appendSchemaRules(basePrompt, outputDir);
    }

    /**
     * 获取图片文章编译 prompt。
     *
     * @return 图片文章编译 prompt
     */
    public String getCompileImageArticlePrompt() {
        return compilerPromptProvider != null
                ? compilerPromptProvider.writerImagePrompt()
                : LatticePrompts.SYSTEM_COMPILE_IMAGE_ARTICLE;
    }

    private String appendSchemaRules(String basePrompt, Path outputDir) {
        String schema = tryLoadSchema(outputDir);
        if (schema == null || schema.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n# User-Defined Schema Rules\n" + schema;
    }

    private String tryLoadSchema(Path outputDir) {
        if (outputDir == null) {
            return null;
        }
        Path normalizedPath = outputDir.toAbsolutePath().normalize();
        return schemaCache.computeIfAbsent(normalizedPath, this::readSchemaFile);
    }

    private String readSchemaFile(Path outputDir) {
        Path schemaPath = outputDir.resolve("SCHEMA.md");
        if (!Files.exists(schemaPath)) {
            return "";
        }
        try {
            String schema = Files.readString(schemaPath, StandardCharsets.UTF_8);
            if (schema.length() <= MAX_SCHEMA_CHARS) {
                return schema;
            }
            return schema.substring(0, MAX_SCHEMA_CHARS);
        }
        catch (IOException ex) {
            return "";
        }
    }
}
