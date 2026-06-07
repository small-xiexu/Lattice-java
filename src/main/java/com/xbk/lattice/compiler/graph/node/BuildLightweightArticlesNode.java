package com.xbk.lattice.compiler.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.compiler.graph.CompileGraphState;
import com.xbk.lattice.compiler.graph.CompileGraphStateMapper;
import com.xbk.lattice.compiler.graph.CompileWorkingSetStore;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 构建轻量文章节点
 *
 * 职责：在 CODE_LIGHT 内容画像下，从 merged concepts 直接构建最小 ArticleRecord，
 * 以源码原文作为文章内容，绕过 writer/reviewer/fixer LLM 调用。
 *
 * @author xiexu
 */
@Component
public class BuildLightweightArticlesNode extends AbstractCompileGraphNode {

    /**
     * 创建构建轻量文章节点。
     *
     * @param compileGraphStateMapper 编译图状态映射器
     * @param compileWorkingSetStore 编译工作集存储
     */
    public BuildLightweightArticlesNode(
            CompileGraphStateMapper compileGraphStateMapper,
            CompileWorkingSetStore compileWorkingSetStore
    ) {
        super(compileGraphStateMapper, compileWorkingSetStore);
    }

    /**
     * 为每个 merged concept 构建轻量 ArticleRecord，直接设为通过审查。
     *
     * @param overAllState 图状态
     * @return 更新后的状态增量
     */
    public Map<String, Object> execute(OverAllState overAllState) {
        CompileGraphState state = state(overAllState);
        List<MergedConcept> conceptsToCompile = resolveConceptsToCompile(state);
        Path sourceDir = Path.of(state.getSourceDir());
        List<ArticleReviewEnvelope> acceptedArticles = new ArrayList<ArticleReviewEnvelope>();
        for (MergedConcept concept : conceptsToCompile) {
            String sourcePath = resolvePrimarySourcePath(concept);
            String content = readSourceContent(sourceDir, sourcePath);
            ArticleRecord article = buildArticleRecord(concept, sourcePath, content);
            ArticleReviewEnvelope envelope = new ArticleReviewEnvelope();
            envelope.setArticle(article);
            envelope.setReviewStatus("passed");
            acceptedArticles.add(envelope);
        }
        state.setAcceptedArticlesRef(saveAcceptedArticles(state.getJobId(), acceptedArticles));
        state.setAcceptedCount(acceptedArticles.size());
        state.setPendingReviewCount(0);
        return delta(state);
    }

    /**
     * 解析概念的主源文件路径。
     *
     * @param concept 合并概念
     * @return 主源文件路径
     */
    private String resolvePrimarySourcePath(MergedConcept concept) {
        List<String> sourcePaths = concept.getSourcePaths();
        if (sourcePaths != null && !sourcePaths.isEmpty()) {
            return sourcePaths.get(0);
        }
        return concept.getTitle();
    }

    /**
     * 从源码目录读取文件内容。
     *
     * @param sourceDir 源目录
     * @param relativePath 相对路径
     * @return 文件内容
     */
    private String readSourceContent(Path sourceDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        Path filePath = sourceDir.resolve(relativePath).normalize();
        if (!filePath.startsWith(sourceDir)) {
            return "";
        }
        try {
            return Files.readString(filePath);
        }
        catch (IOException e) {
            return "";
        }
    }

    /**
     * 构建轻量文章记录。
     *
     * @param concept 合并概念
     * @param sourcePath 源文件路径
     * @param content 源码内容
     * @return 文章记录
     */
    private ArticleRecord buildArticleRecord(MergedConcept concept, String sourcePath, String content) {
        String metadataJson = buildMetadataJson(sourcePath);
        OffsetDateTime now = OffsetDateTime.now();
        return new ArticleRecord(
                concept.getConceptId(),
                concept.getTitle(),
                content,
                "ACTIVE",
                now,
                concept.getSourcePaths() != null ? concept.getSourcePaths() : List.of(),
                metadataJson,
                "",
                List.of(),
                List.of(),
                List.of(),
                "high",
                "passed"
        );
    }

    /**
     * 构建 CODE_LIGHT 元数据 JSON。
     *
     * @param sourcePath 源文件路径
     * @return 元数据 JSON
     */
    private String buildMetadataJson(String sourcePath) {
        String fileType = resolveFileType(sourcePath);
        return "{\"contentProfile\":\"CODE_LIGHT\",\"sourcePath\":\""
                + escapeJson(sourcePath)
                + "\",\"fileType\":\""
                + escapeJson(fileType)
                + "\"}";
    }

    /**
     * 根据文件扩展名解析文件类型标签。
     *
     * @param sourcePath 源文件路径
     * @return 文件类型标签
     */
    private String resolveFileType(String sourcePath) {
        if (sourcePath == null) {
            return "unknown";
        }
        String lower = sourcePath.toLowerCase();
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".xml")) {
            return "xml";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".properties")) {
            return "properties";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        int lastDot = lower.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < lower.length() - 1) {
            return lower.substring(lastDot + 1);
        }
        return "unknown";
    }

    /**
     * JSON 字符串转义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
}
