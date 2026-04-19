package com.xbk.lattice.source.service;

import com.xbk.lattice.source.domain.BundleSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 资料包特征提取器。
 *
 * 职责：从 staging 工作目录提取 BundleSummary 与 manifest 指纹
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class BundleFeatureExtractor {

    private static final int MAX_SAMPLE_PATHS = 20;

    private static final int MAX_KEYWORDS = 12;

    private static final int MAX_TITLE_HINTS = 5;

    /**
     * 提取资料包特征。
     *
     * @param stagingDir staging 目录
     * @return 资料包特征快照
     * @throws IOException IO 异常
     */
    public UploadBundleSnapshot extract(Path stagingDir) throws IOException {
        List<Path> filePaths = new ArrayList<Path>();
        List<Path> directoryPaths = new ArrayList<Path>();
        try (Stream<Path> pathStream = Files.walk(stagingDir)) {
            pathStream.forEach(path -> {
                if (path.equals(stagingDir)) {
                    return;
                }
                if (Files.isDirectory(path)) {
                    directoryPaths.add(path);
                }
                else if (Files.isRegularFile(path)) {
                    filePaths.add(path);
                }
            });
        }
        filePaths.sort(Comparator.naturalOrder());
        directoryPaths.sort(Comparator.naturalOrder());

        List<String> relativePaths = new ArrayList<String>();
        Map<String, Integer> extensionDistribution = new LinkedHashMap<String, Integer>();
        Set<String> topLevelNames = new LinkedHashSet<String>();
        List<String> signatureFiles = new ArrayList<String>();
        Map<String, Integer> keywordCounter = new LinkedHashMap<String, Integer>();
        List<String> titleHints = new ArrayList<String>();
        List<String> manifestParts = new ArrayList<String>();
        List<String> contentParts = new ArrayList<String>();
        int binaryLikeCount = 0;

        for (Path filePath : filePaths) {
            String relativePath = normalizeRelativePath(stagingDir.relativize(filePath));
            relativePaths.add(relativePath);
            manifestParts.add(relativePath + ":" + Files.size(filePath) + ":" + hash(Files.readAllBytes(filePath)));
            contentParts.add(hash(Files.readAllBytes(filePath)));
            collectTopLevelName(relativePath, topLevelNames);
            collectExtension(relativePath, extensionDistribution);
            collectSignatureFile(relativePath, signatureFiles);
            collectKeywords(relativePath, keywordCounter);
            if (isBinaryLike(relativePath)) {
                binaryLikeCount++;
            }
            collectTitleHint(filePath, relativePath, titleHints);
        }

        List<String> relativePathsSample = sample(relativePaths, MAX_SAMPLE_PATHS);
        List<String> keywordList = topKeywords(keywordCounter, MAX_KEYWORDS);
        String contentProfile = resolveContentProfile(filePaths.size(), binaryLikeCount);
        String pathFingerprint = hash(String.join("\n", relativePaths));
        String contentFingerprint = hash(String.join("\n", contentParts));
        String manifestHash = hash(String.join("\n", manifestParts));
        String displayName = resolveDisplayName(stagingDir, topLevelNames, titleHints);
        String summaryText = buildSummaryText(displayName, topLevelNames, signatureFiles, keywordList, contentProfile);

        BundleSummary bundleSummary = new BundleSummary(
                displayName,
                filePaths.size(),
                directoryPaths.size(),
                new ArrayList<String>(topLevelNames),
                extensionDistribution,
                relativePathsSample,
                signatureFiles,
                contentProfile,
                keywordList,
                sample(titleHints, MAX_TITLE_HINTS),
                pathFingerprint,
                contentFingerprint,
                summaryText
        );
        return new UploadBundleSnapshot(stagingDir, manifestHash, relativePaths, bundleSummary);
    }

    /**
     * 资料包特征快照。
     *
     * 职责：组合 staging 路径、manifest 哈希、文件列表与摘要
     *
     * @author xiexu
     */
    public static class UploadBundleSnapshot {

        private final Path stagingDir;

        private final String manifestHash;

        private final List<String> sourceNames;

        private final BundleSummary bundleSummary;

        /**
         * 创建资料包特征快照。
         *
         * @param stagingDir staging 目录
         * @param manifestHash manifest 哈希
         * @param sourceNames 来源文件名
         * @param bundleSummary 资料包摘要
         */
        public UploadBundleSnapshot(
                Path stagingDir,
                String manifestHash,
                List<String> sourceNames,
                BundleSummary bundleSummary
        ) {
            this.stagingDir = stagingDir;
            this.manifestHash = manifestHash;
            this.sourceNames = sourceNames;
            this.bundleSummary = bundleSummary;
        }

        public Path getStagingDir() {
            return stagingDir;
        }

        public String getManifestHash() {
            return manifestHash;
        }

        public List<String> getSourceNames() {
            return sourceNames;
        }

        public BundleSummary getBundleSummary() {
            return bundleSummary;
        }
    }

    private String normalizeRelativePath(Path relativePath) {
        return relativePath.toString().replace("\\", "/");
    }

    private void collectTopLevelName(String relativePath, Set<String> topLevelNames) {
        int separatorIndex = relativePath.indexOf('/');
        if (separatorIndex < 0) {
            topLevelNames.add(relativePath);
            return;
        }
        topLevelNames.add(relativePath.substring(0, separatorIndex));
    }

    private void collectExtension(String relativePath, Map<String, Integer> extensionDistribution) {
        int dotIndex = relativePath.lastIndexOf('.');
        String extension = dotIndex < 0 ? "(none)" : relativePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        Integer currentCount = extensionDistribution.get(extension);
        extensionDistribution.put(extension, currentCount == null ? 1 : currentCount + 1);
    }

    private void collectSignatureFile(String relativePath, List<String> signatureFiles) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("readme.md")
                || normalized.endsWith("readme.txt")
                || normalized.endsWith("overview.md")
                || normalized.endsWith("index.md")
                || normalized.endsWith("toc.md")
                || normalized.endsWith("summary.md")) {
            signatureFiles.add(relativePath);
        }
    }

    private void collectKeywords(String relativePath, Map<String, Integer> keywordCounter) {
        String[] rawTokens = relativePath.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String rawToken : rawTokens) {
            if (rawToken.isBlank() || rawToken.length() < 3) {
                continue;
            }
            Integer currentCount = keywordCounter.get(rawToken);
            keywordCounter.put(rawToken, currentCount == null ? 1 : currentCount + 1);
        }
    }

    private boolean isBinaryLike(String relativePath) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".png")
                || normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg")
                || normalized.endsWith(".gif")
                || normalized.endsWith(".pdf")
                || normalized.endsWith(".zip")
                || normalized.endsWith(".mp4")
                || normalized.endsWith(".mov");
    }

    private void collectTitleHint(Path filePath, String relativePath, List<String> titleHints) throws IOException {
        if (titleHints.size() >= MAX_TITLE_HINTS) {
            return;
        }
        if (!isTitleHintCandidate(relativePath)) {
            return;
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isBlank()) {
                titleHints.add(trimmed.replaceFirst("^#+\\s*", ""));
                return;
            }
        }
    }

    private boolean isTitleHintCandidate(String relativePath) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".md")
                || normalized.endsWith(".txt")
                || normalized.endsWith(".json")
                || normalized.endsWith(".yaml")
                || normalized.endsWith(".yml");
    }

    private List<String> sample(List<String> values, int maxSize) {
        if (values.size() <= maxSize) {
            return new ArrayList<String>(values);
        }
        return new ArrayList<String>(values.subList(0, maxSize));
    }

    private List<String> topKeywords(Map<String, Integer> keywordCounter, int maxSize) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(keywordCounter.entrySet());
        entries.sort((left, right) -> {
            int compareCount = right.getValue().compareTo(left.getValue());
            if (compareCount != 0) {
                return compareCount;
            }
            return left.getKey().compareTo(right.getKey());
        });
        List<String> keywords = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : entries) {
            keywords.add(entry.getKey());
            if (keywords.size() >= maxSize) {
                break;
            }
        }
        return keywords;
    }

    private String resolveContentProfile(int fileCount, int binaryLikeCount) {
        if (fileCount == 0) {
            return "DOCUMENT";
        }
        if (binaryLikeCount == 0) {
            return "DOCUMENT";
        }
        if (binaryLikeCount * 2 >= fileCount) {
            return "ASSET_HEAVY";
        }
        return "MIXED";
    }

    private String resolveDisplayName(Path stagingDir, Set<String> topLevelNames, List<String> titleHints) {
        if (!titleHints.isEmpty()) {
            return titleHints.get(0);
        }
        if (!topLevelNames.isEmpty()) {
            return topLevelNames.iterator().next();
        }
        Path fileName = stagingDir.getFileName();
        return fileName == null ? "upload" : fileName.toString();
    }

    private String buildSummaryText(
            String displayName,
            Set<String> topLevelNames,
            List<String> signatureFiles,
            List<String> keywords,
            String contentProfile
    ) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("displayName=").append(displayName);
        if (!topLevelNames.isEmpty()) {
            stringBuilder.append("; topLevelNames=").append(String.join(",", topLevelNames));
        }
        if (!signatureFiles.isEmpty()) {
            stringBuilder.append("; signatureFiles=").append(String.join(",", signatureFiles));
        }
        if (!keywords.isEmpty()) {
            stringBuilder.append("; keywords=").append(String.join(",", keywords));
        }
        stringBuilder.append("; contentProfile=").append(contentProfile);
        return stringBuilder.toString();
    }

    private String hash(byte[] data) {
        return hash(new String(data, StandardCharsets.ISO_8859_1));
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
