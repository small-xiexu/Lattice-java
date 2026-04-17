package com.xbk.lattice.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vault 回写服务
 *
 * 职责：将 concepts/*.md 的人工修改受控回写到数据库
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class VaultSyncService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R(.*?)\\R---\\R?(.*)\\z", Pattern.DOTALL);

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    private final VaultManifestStore vaultManifestStore;

    public VaultSyncService(
            ArticleJdbcRepository articleJdbcRepository,
            SynthesisArtifactJdbcStore synthesisArtifactJdbcStore
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.synthesisArtifactJdbcStore = synthesisArtifactJdbcStore;
        this.vaultManifestStore = new VaultManifestStore();
    }

    /**
     * 执行 Vault inbound 受控回写。
     *
     * @param vaultDir Vault 目录
     * @param force 是否强制覆盖冲突
     * @return 回写结果
     * @throws IOException IO 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public VaultSyncResult sync(Path vaultDir, boolean force) throws IOException {
        Path manifestPath = vaultDir.resolve("_meta/export-manifest.json");
        Map<String, Object> manifest = vaultManifestStore.read(manifestPath);
        Map<String, Map<String, String>> articleEntries = readSection(manifest, "articles");
        Map<String, Map<String, String>> artifactEntries = readSection(manifest, "artifacts");
        List<VaultConflictReport> conflicts = new ArrayList<VaultConflictReport>();
        int syncedFiles = 0;
        int skippedFiles = 0;

        for (Map.Entry<String, Map<String, String>> entry : articleEntries.entrySet()) {
            String conceptId = entry.getKey();
            String relativePath = entry.getValue().get("path");
            if (relativePath == null || !relativePath.startsWith("concepts/")) {
                continue;
            }
            Path targetFile = vaultDir.resolve(relativePath);
            if (!Files.exists(targetFile)) {
                skippedFiles++;
                continue;
            }
            Optional<ArticleRecord> optionalArticleRecord = articleJdbcRepository.findByConceptId(conceptId);
            if (optionalArticleRecord.isEmpty()) {
                skippedFiles++;
                continue;
            }

            ArticleRecord articleRecord = optionalArticleRecord.orElseThrow();
            String fileContent = Files.readString(targetFile, StandardCharsets.UTF_8);
            String fileHash = hash(fileContent);
            String manifestHash = entry.getValue().get("contentHash");
            String dbHash = hash(articleRecord.getContent());

            if (manifestHash != null && manifestHash.equals(fileHash)) {
                skippedFiles++;
                continue;
            }
            if (!force && manifestHash != null && !manifestHash.equals(dbHash)) {
                conflicts.add(new VaultConflictReport(
                        relativePath,
                        "DB 在导出后已发生变化",
                        manifestHash,
                        dbHash,
                        fileHash
                ));
                continue;
            }

            ArticleRecord mergedRecord = mergeArticle(articleRecord, fileContent);
            articleJdbcRepository.upsert(mergedRecord);
            syncedFiles++;
        }

        Map<String, SynthesisArtifactRecord> currentArtifacts = new LinkedHashMap<String, SynthesisArtifactRecord>();
        for (SynthesisArtifactRecord synthesisArtifactRecord : synthesisArtifactJdbcStore.findAll()) {
            currentArtifacts.put(synthesisArtifactRecord.getArtifactType(), synthesisArtifactRecord);
        }
        for (Map.Entry<String, Map<String, String>> entry : artifactEntries.entrySet()) {
            String artifactType = entry.getKey();
            String relativePath = entry.getValue().get("path");
            if (!isEditableArtifactPath(relativePath)) {
                continue;
            }
            Path targetFile = vaultDir.resolve(relativePath);
            if (!Files.exists(targetFile)) {
                skippedFiles++;
                continue;
            }
            SynthesisArtifactRecord currentArtifact = currentArtifacts.get(artifactType);
            if (currentArtifact == null) {
                skippedFiles++;
                continue;
            }

            String fileContent = Files.readString(targetFile, StandardCharsets.UTF_8);
            String fileHash = hash(fileContent);
            String manifestHash = entry.getValue().get("contentHash");
            String dbHash = hash(currentArtifact.getContent());

            if (manifestHash != null && manifestHash.equals(fileHash)) {
                skippedFiles++;
                continue;
            }
            if (!force && manifestHash != null && !manifestHash.equals(dbHash)) {
                conflicts.add(new VaultConflictReport(
                        relativePath,
                        "DB 合成产物在导出后已发生变化",
                        manifestHash,
                        dbHash,
                        fileHash
                ));
                continue;
            }

            synthesisArtifactJdbcStore.save(new SynthesisArtifactRecord(
                    artifactType,
                    extractTitle(fileContent, currentArtifact.getTitle()),
                    fileContent,
                    OffsetDateTime.now()
            ));
            syncedFiles++;
        }

        return new VaultSyncResult(vaultDir.toString(), syncedFiles, skippedFiles, conflicts);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> readSection(Map<String, Object> manifest, String sectionName) {
        Object section = manifest.get(sectionName);
        if (!(section instanceof Map<?, ?> sectionMap)) {
            return Map.of();
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<?, ?> entry : sectionMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> entryMap)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (Map.Entry<?, ?> fieldEntry : entryMap.entrySet()) {
                values.put(String.valueOf(fieldEntry.getKey()), String.valueOf(fieldEntry.getValue()));
            }
            result.put(String.valueOf(entry.getKey()), values);
        }
        return result;
    }

    private ArticleRecord mergeArticle(ArticleRecord existingRecord, String fileContent) {
        FrontmatterDocument document = parseDocument(fileContent);
        String title = readEditableString(document.frontmatter, "title", existingRecord.getTitle());
        String summary = readEditableString(document.frontmatter, "summary", existingRecord.getSummary());
        String confidence = readEditableString(document.frontmatter, "confidence", existingRecord.getConfidence());
        List<String> referentialKeywords = readEditableList(
                document.frontmatter,
                "referential_keywords",
                existingRecord.getReferentialKeywords()
        );
        List<String> dependsOn = readEditableList(document.frontmatter, "depends_on", existingRecord.getDependsOn());
        List<String> related = readEditableList(document.frontmatter, "related", existingRecord.getRelated());
        String renderedContent = renderManagedDocument(
                title,
                summary,
                referentialKeywords,
                existingRecord.getSourcePaths(),
                dependsOn,
                related,
                confidence,
                existingRecord.getReviewStatus(),
                document.body
        );
        return new ArticleRecord(
                existingRecord.getConceptId(),
                title,
                renderedContent,
                existingRecord.getLifecycle(),
                existingRecord.getCompiledAt(),
                existingRecord.getSourcePaths(),
                existingRecord.getMetadataJson(),
                summary,
                referentialKeywords,
                dependsOn,
                related,
                confidence,
                existingRecord.getReviewStatus()
        );
    }

    private FrontmatterDocument parseDocument(String content) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return new FrontmatterDocument(Map.of(), content.strip());
        }
        Map<String, String> frontmatter = new LinkedHashMap<String, String>();
        String rawFrontmatter = matcher.group(1);
        for (String line : rawFrontmatter.split("\\R")) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex < 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separatorIndex + 1).trim();
            frontmatter.put(key, value);
        }
        return new FrontmatterDocument(frontmatter, matcher.group(2).strip());
    }

    private String readEditableString(Map<String, String> frontmatter, String key, String fallbackValue) {
        String currentValue = frontmatter.get(key);
        if (currentValue == null || currentValue.isBlank()) {
            return fallbackValue;
        }
        return stripQuotes(currentValue);
    }

    private List<String> readEditableList(Map<String, String> frontmatter, String key, List<String> fallbackValue) {
        String rawValue = frontmatter.get(key);
        if (rawValue == null || rawValue.isBlank()) {
            return fallbackValue;
        }
        try {
            if (rawValue.startsWith("[")) {
                return OBJECT_MAPPER.readValue(rawValue, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        }
        catch (Exception ignored) {
            // 退回到逗号分隔解析
        }
        List<String> values = new ArrayList<String>();
        for (String token : rawValue.split(",")) {
            String normalized = stripQuotes(token.trim());
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String renderManagedDocument(
            String title,
            String summary,
            List<String> referentialKeywords,
            List<String> sourcePaths,
            List<String> dependsOn,
            List<String> related,
            String confidence,
            String reviewStatus,
            String body
    ) {
        return """
                ---
                title: "%s"
                summary: "%s"
                referential_keywords: %s
                source_paths: %s
                depends_on: %s
                related: %s
                confidence: "%s"
                review_status: "%s"
                ---

                %s
                """.formatted(
                escape(title),
                escape(summary),
                toJsonArray(referentialKeywords),
                toJsonArray(sourcePaths),
                toJsonArray(dependsOn),
                toJsonArray(related),
                escape(confidence),
                escape(reviewStatus),
                body == null ? "" : body.strip()
        ).strip();
    }

    private boolean isEditableArtifactPath(String relativePath) {
        if (relativePath == null) {
            return false;
        }
        return switch (relativePath) {
            case "index.md", "timeline.md", "tradeoffs.md", "gaps.md" -> true;
            default -> false;
        };
    }

    private String extractTitle(String content, String fallbackTitle) {
        if (content == null) {
            return fallbackTitle;
        }
        for (String line : content.split("\\R")) {
            String normalized = line.trim();
            if (normalized.startsWith("# ")) {
                return normalized.substring(2).trim();
            }
        }
        return fallbackTitle;
    }

    private String toJsonArray(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化 frontmatter 列表失败", exception);
        }
    }

    private String stripQuotes(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"");
    }

    private String hash(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("计算 Vault sync hash 失败", exception);
        }
    }

    private static class FrontmatterDocument {

        private final Map<String, String> frontmatter;

        private final String body;

        private FrontmatterDocument(Map<String, String> frontmatter, String body) {
            this.frontmatter = frontmatter;
            this.body = body;
        }
    }
}
