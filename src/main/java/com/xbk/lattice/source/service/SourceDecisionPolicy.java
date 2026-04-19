package com.xbk.lattice.source.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.source.domain.BundleSummary;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceDecisionResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 资料源自动识别收口策略。
 *
 * 职责：基于规则特征在 NEW / UPDATE / APPEND / AMBIGUOUS 之间做保守收口
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceDecisionPolicy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 基于现有资料源做自动识别决策。
     *
     * @param bundleSummary 资料包摘要
     * @param manifestHash manifest 哈希
     * @param existingSources 已有资料源
     * @return 收口结果
     */
    public SourceDecisionResult decide(
            BundleSummary bundleSummary,
            String manifestHash,
            List<KnowledgeSource> existingSources
    ) {
        List<KnowledgeSource> candidates = filterCandidates(existingSources);
        if (candidates.isEmpty()) {
            return new SourceDecisionResult(
                    "RULE_ONLY",
                    "NEW_SOURCE",
                    "CREATE",
                    null,
                    false,
                    false,
                    "未命中已有资料源，自动创建新资料源"
            );
        }

        for (KnowledgeSource candidate : candidates) {
            if (manifestHash.equals(candidate.getLatestManifestHash())) {
                return new SourceDecisionResult(
                        "RULE_ONLY",
                        "EXISTING_SOURCE_UPDATE",
                        "UPDATE",
                        candidate.getId(),
                        false,
                        true,
                        "资料包与最近一次成功快照一致，跳过本次同步"
                );
            }
        }

        MatchScore bestMatch = null;
        MatchScore secondMatch = null;
        for (KnowledgeSource candidate : candidates) {
            MatchScore current = scoreCandidate(bundleSummary, candidate);
            if (bestMatch == null || current.getScore() > bestMatch.getScore()) {
                secondMatch = bestMatch;
                bestMatch = current;
            }
            else if (secondMatch == null || current.getScore() > secondMatch.getScore()) {
                secondMatch = current;
            }
        }

        if (bestMatch == null || bestMatch.getScore() < 40) {
            return new SourceDecisionResult(
                    "RULE_ONLY",
                    "NEW_SOURCE",
                    "CREATE",
                    null,
                    false,
                    false,
                    "未发现强匹配候选，自动创建新资料源"
            );
        }

        int scoreGap = secondMatch == null ? bestMatch.getScore() : bestMatch.getScore() - secondMatch.getScore();
        if (bestMatch.getScore() >= 80 && scoreGap >= 20) {
            return new SourceDecisionResult(
                    "RULE_ONLY",
                    bestMatch.isUpdateLike() ? "EXISTING_SOURCE_UPDATE" : "EXISTING_SOURCE_APPEND",
                    bestMatch.isUpdateLike() ? "UPDATE" : "APPEND",
                    bestMatch.getSource().getId(),
                    false,
                    false,
                    "命中已有资料源，自动归并并继续编译"
            );
        }

        return new SourceDecisionResult(
                "RULE_ONLY",
                "AMBIGUOUS",
                null,
                bestMatch.getSource().getId(),
                true,
                false,
                "命中候选资料源但证据不足，进入待确认"
        );
    }

    private List<KnowledgeSource> filterCandidates(List<KnowledgeSource> existingSources) {
        List<KnowledgeSource> candidates = new ArrayList<KnowledgeSource>();
        for (KnowledgeSource source : existingSources) {
            if ("legacy-default".equals(source.getSourceCode())) {
                continue;
            }
            if ("DISABLED".equals(source.getStatus())) {
                continue;
            }
            if ("ARCHIVED".equals(source.getStatus())) {
                continue;
            }
            if (!"UPLOAD".equals(source.getSourceType())) {
                continue;
            }
            candidates.add(source);
        }
        return candidates;
    }

    private MatchScore scoreCandidate(BundleSummary bundleSummary, KnowledgeSource source) {
        int score = 0;
        boolean updateLike = false;
        String normalizedDisplayName = normalize(bundleSummary.getDisplayName());
        String normalizedSourceName = normalize(source.getName());
        if (!normalizedDisplayName.isBlank() && normalizedDisplayName.equals(normalizedSourceName)) {
            score += 50;
        }

        String storedPathFingerprint = readBundleField(source.getMetadataJson(), "pathFingerprint");
        if (!storedPathFingerprint.isBlank() && storedPathFingerprint.equals(bundleSummary.getPathFingerprint())) {
            score += 80;
            updateLike = true;
        }

        List<String> storedTopLevels = readBundleArray(source.getMetadataJson(), "topLevelNames");
        if (!storedTopLevels.isEmpty() && storedTopLevels.equals(bundleSummary.getTopLevelNames())) {
            score += 40;
            updateLike = true;
        }
        else {
            double overlap = overlapRatio(storedTopLevels, bundleSummary.getTopLevelNames());
            if (overlap >= 0.5D) {
                score += 20;
            }
        }

        String storedSummary = normalize(readBundleField(source.getMetadataJson(), "summaryText"));
        if (!storedSummary.isBlank() && storedSummary.contains(normalizedDisplayName)) {
            score += 15;
        }

        return new MatchScore(source, score, updateLike);
    }

    private String readBundleField(String metadataJson, String fieldName) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            JsonNode bundleNode = rootNode.path("bundleSummary");
            return bundleNode.path(fieldName).asText("");
        }
        catch (Exception ex) {
            return "";
        }
    }

    private List<String> readBundleArray(String metadataJson, String fieldName) {
        List<String> values = new ArrayList<String>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return values;
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(metadataJson);
            JsonNode bundleNode = rootNode.path("bundleSummary").path(fieldName);
            if (!bundleNode.isArray()) {
                return values;
            }
            for (JsonNode itemNode : bundleNode) {
                values.add(itemNode.asText());
            }
            return values;
        }
        catch (Exception ex) {
            return values;
        }
    }

    private double overlapRatio(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0D;
        }
        List<String> normalizedLeft = new ArrayList<String>();
        for (String value : left) {
            normalizedLeft.add(normalize(value));
        }
        List<String> normalizedRight = new ArrayList<String>();
        for (String value : right) {
            normalizedRight.add(normalize(value));
        }
        int overlapCount = 0;
        for (String value : normalizedLeft) {
            if (normalizedRight.contains(value)) {
                overlapCount++;
            }
        }
        int denominator = Math.max(normalizedLeft.size(), normalizedRight.size());
        return denominator == 0 ? 0.0D : (double) overlapCount / (double) denominator;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    /**
     * 候选资料源评分。
     *
     * 职责：承载规则匹配后的候选得分与偏向动作
     *
     * @author xiexu
     */
    private static class MatchScore {

        private final KnowledgeSource source;

        private final int score;

        private final boolean updateLike;

        private MatchScore(KnowledgeSource source, int score, boolean updateLike) {
            this.source = source;
            this.score = score;
            this.updateLike = updateLike;
        }

        private KnowledgeSource getSource() {
            return source;
        }

        private int getScore() {
            return score;
        }

        private boolean isUpdateLike() {
            return updateLike;
        }
    }
}
