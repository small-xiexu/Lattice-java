package com.xbk.lattice.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.vault.snapshot.VaultGitService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vault 导出服务
 *
 * 职责：将数据库中的文章、合成产物和贡献导出为可挂载的 Vault 文件树
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class VaultExportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    private final VaultManifestStore vaultManifestStore;

    private final VaultGitService vaultGitService;

    /**
     * 创建 Vault 导出服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param contributionJdbcRepository 贡献仓储
     * @param synthesisArtifactJdbcStore 合成产物仓储
     */
    public VaultExportService(
            ArticleJdbcRepository articleJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            SynthesisArtifactJdbcStore synthesisArtifactJdbcStore,
            VaultGitService vaultGitService
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.synthesisArtifactJdbcStore = synthesisArtifactJdbcStore;
        this.vaultGitService = vaultGitService;
        this.vaultManifestStore = new VaultManifestStore();
    }

    /**
     * 导出到指定 Vault 目录。
     *
     * @param vaultDir Vault 目录
     * @return 导出结果
     * @throws IOException IO 异常
     */
    public VaultExportResult export(Path vaultDir) throws IOException {
        vaultGitService.ensureRepository(vaultDir);
        Path conceptsDir = vaultDir.resolve("concepts");
        Path contributionsDir = vaultDir.resolve("_contributions");
        Path metaDir = vaultDir.resolve("_meta");
        Path manifestPath = metaDir.resolve("export-manifest.json");

        Files.createDirectories(conceptsDir);
        Files.createDirectories(contributionsDir);
        Files.createDirectories(metaDir);
        Files.writeString(
                metaDir.resolve("README.md"),
                "# Managed Vault\n\n该目录由 Lattice-java 导出与维护。\n",
                StandardCharsets.UTF_8
        );

        Map<String, Object> previousManifest = vaultManifestStore.read(manifestPath);
        Map<String, Object> nextManifest = new LinkedHashMap<String, Object>();
        nextManifest.put("exportedAt", OffsetDateTime.now().toString());

        int writtenFiles = 0;
        int skippedFiles = 0;
        Set<String> managedPaths = new LinkedHashSet<String>();

        Map<String, Map<String, String>> articleEntries = new LinkedHashMap<String, Map<String, String>>();
        for (ArticleRecord articleRecord : articleJdbcRepository.findAll()) {
            String relativePath = "concepts/" + articleRecord.getConceptId() + ".md";
            Path outputPath = vaultDir.resolve(relativePath);
            String content = articleRecord.getContent();
            String contentHash = hash(content);
            boolean written = writeIfChanged(outputPath, content, previousManifest, relativePath, contentHash);
            if (written) {
                writtenFiles++;
            }
            else {
                skippedFiles++;
            }
            managedPaths.add(relativePath);
            articleEntries.put(articleRecord.getConceptId(), manifestEntry(relativePath, contentHash));
        }
        nextManifest.put("articles", articleEntries);

        Map<String, Map<String, String>> artifactEntries = new LinkedHashMap<String, Map<String, String>>();
        for (SynthesisArtifactRecord synthesisArtifactRecord : synthesisArtifactJdbcStore.findAll()) {
            String relativePath = artifactFileName(synthesisArtifactRecord.getArtifactType());
            Path outputPath = vaultDir.resolve(relativePath);
            String content = synthesisArtifactRecord.getContent();
            String contentHash = hash(content);
            boolean written = writeIfChanged(outputPath, content, previousManifest, relativePath, contentHash);
            if (written) {
                writtenFiles++;
            }
            else {
                skippedFiles++;
            }
            managedPaths.add(relativePath);
            artifactEntries.put(synthesisArtifactRecord.getArtifactType(), manifestEntry(relativePath, contentHash));
        }
        nextManifest.put("artifacts", artifactEntries);

        Map<String, Map<String, String>> contributionEntries = new LinkedHashMap<String, Map<String, String>>();
        for (ContributionRecord contributionRecord : contributionJdbcRepository.findAll()) {
            String fileName = contributionFileName(contributionRecord);
            String relativePath = "_contributions/" + fileName;
            Path outputPath = vaultDir.resolve(relativePath);
            String content = renderContribution(contributionRecord);
            String contentHash = hash(content);
            boolean written = writeIfChanged(outputPath, content, previousManifest, relativePath, contentHash);
            if (written) {
                writtenFiles++;
            }
            else {
                skippedFiles++;
            }
            managedPaths.add(relativePath);
            contributionEntries.put(contributionRecord.getId().toString(), manifestEntry(relativePath, contentHash));
        }
        nextManifest.put("contributions", contributionEntries);

        int deletedFiles = deleteOrphanedManagedFiles(vaultDir, previousManifest, managedPaths);
        vaultManifestStore.write(manifestPath, nextManifest);

        return new VaultExportResult(vaultDir.toString(), writtenFiles, skippedFiles, deletedFiles);
    }

    private boolean writeIfChanged(
            Path outputPath,
            String content,
            Map<String, Object> previousManifest,
            String relativePath,
            String contentHash
    ) throws IOException {
        String previousHash = readPreviousHash(previousManifest, relativePath);
        if (contentHash.equals(previousHash) && Files.exists(outputPath)) {
            return false;
        }
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        return true;
    }

    private int deleteOrphanedManagedFiles(
            Path vaultDir,
            Map<String, Object> previousManifest,
            Set<String> managedPaths
    ) throws IOException {
        Set<String> previousPaths = collectManagedPaths(previousManifest);
        int deleted = 0;
        for (String previousPath : previousPaths) {
            if (managedPaths.contains(previousPath)) {
                continue;
            }
            Path targetPath = vaultDir.resolve(previousPath);
            if (Files.exists(targetPath)) {
                Files.delete(targetPath);
                deleted++;
            }
        }
        return deleted;
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectManagedPaths(Map<String, Object> manifest) {
        Set<String> paths = new LinkedHashSet<String>();
        for (String key : List.of("articles", "artifacts", "contributions")) {
            Object section = manifest.get(key);
            if (!(section instanceof Map<?, ?> sectionMap)) {
                continue;
            }
            for (Object value : sectionMap.values()) {
                if (!(value instanceof Map<?, ?> entryMap)) {
                    continue;
                }
                Object path = entryMap.get("path");
                if (path != null) {
                    paths.add(String.valueOf(path));
                }
            }
        }
        return paths;
    }

    @SuppressWarnings("unchecked")
    private String readPreviousHash(Map<String, Object> manifest, String relativePath) {
        for (String key : List.of("articles", "artifacts", "contributions")) {
            Object section = manifest.get(key);
            if (!(section instanceof Map<?, ?> sectionMap)) {
                continue;
            }
            for (Object value : sectionMap.values()) {
                if (!(value instanceof Map<?, ?> entryMap)) {
                    continue;
                }
                Object path = entryMap.get("path");
                if (path != null && relativePath.equals(String.valueOf(path))) {
                    Object contentHash = entryMap.get("contentHash");
                    return contentHash == null ? null : String.valueOf(contentHash);
                }
            }
        }
        return null;
    }

    private Map<String, String> manifestEntry(String path, String contentHash) {
        Map<String, String> entry = new LinkedHashMap<String, String>();
        entry.put("path", path);
        entry.put("contentHash", contentHash);
        return entry;
    }

    private String artifactFileName(String artifactType) {
        return switch (artifactType.toLowerCase(Locale.ROOT)) {
            case "index" -> "index.md";
            case "timeline" -> "timeline.md";
            case "tradeoffs" -> "tradeoffs.md";
            case "gaps" -> "gaps.md";
            default -> artifactType + ".md";
        };
    }

    private String contributionFileName(ContributionRecord contributionRecord) {
        OffsetDateTime confirmedAt = contributionRecord.getConfirmedAt();
        String timestamp = confirmedAt == null
                ? "unknown-time"
                : FILE_TIME_FORMATTER.format(confirmedAt);
        return "confirmed_query-" + timestamp + "-" + contributionRecord.getId() + ".md";
    }

    private String renderContribution(ContributionRecord contributionRecord) {
        Map<String, Object> corrections = new LinkedHashMap<String, Object>();
        corrections.put("raw", contributionRecord.getCorrectionsJson());
        try {
            return """
                    ---
                    id: "%s"
                    confirmed_by: "%s"
                    confirmed_at: "%s"
                    ---

                    # Confirmed Query

                    ## Question

                    %s

                    ## Answer

                    %s

                    ## Corrections

                    %s
                    """.formatted(
                    contributionRecord.getId(),
                    contributionRecord.getConfirmedBy(),
                    contributionRecord.getConfirmedAt(),
                    contributionRecord.getQuestion(),
                    contributionRecord.getAnswer(),
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(corrections)
            ).trim();
        }
        catch (Exception exception) {
            throw new IllegalStateException("渲染 contribution Markdown 失败", exception);
        }
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
            throw new IllegalStateException("计算内容哈希失败", exception);
        }
    }
}
