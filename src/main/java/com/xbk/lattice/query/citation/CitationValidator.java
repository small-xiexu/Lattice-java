package com.xbk.lattice.query.citation;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citation 校验器
 *
 * 职责：对单条引用执行规则校验，并输出重叠分与命中摘录
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CitationValidator {

    private static final Pattern NUMERIC_LITERAL_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b");

    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("\\b([a-z][a-z0-9]*(?:_[a-z0-9]+){1,})\\b");

    private static final Pattern FQN_PATTERN = Pattern.compile("\\b(com(?:\\.[A-Za-z_][\\w$]*){2,})\\b");

    private static final Pattern HTTP_PATH_PATTERN = Pattern.compile("(/[-A-Za-z0-9_./]+)");

    private static final Pattern JAVA_SYMBOL_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9]*(?:Mapper|Service|ServiceImpl|Impl|Controller|Dao))\\b"
    );

    private static final Pattern LATIN_TERM_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_./-])([A-Za-z][A-Za-z0-9-]{2,})(?![A-Za-z0-9_./-])"
    );

    private static final Pattern HAN_TERM_PATTERN = Pattern.compile("([\\p{IsHan}]{3,})");

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    /**
     * 创建 Citation 校验器。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public CitationValidator(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
    }

    /**
     * 校验单条引用。
     *
     * @param citation 引用
     * @return 校验结果
     */
    public CitationValidationResult validate(Citation citation) {
        if (citation == null) {
            return new CitationValidationResult(null, null, CitationValidationStatus.DEMOTED, 0.0D, "citation_missing", "", -1);
        }
        if (citation.getSourceType() == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    null,
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "unsupported_source_type",
                    "",
                    citation.getOrdinal()
            );
        }
        if (isBlank(citation.getTargetKey())) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "target_key_missing",
                    "",
                    citation.getOrdinal()
            );
        }
        List<String> hardFactTokens = extractHardFactTokens(citation.getClaimText());
        if (hardFactTokens.isEmpty()) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.SKIPPED,
                    0.0D,
                    "no_hard_fact_literals",
                    "",
                    citation.getOrdinal()
            );
        }
        if (citation.getSourceType() == CitationSourceType.SOURCE_FILE) {
            if (sourceFileJdbcRepository == null) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.DEMOTED,
                        0.0D,
                        "source_file_repository_unavailable",
                        "",
                        citation.getOrdinal()
                );
            }
            SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.findByPath(citation.getTargetKey()).orElse(null);
            if (sourceFileRecord == null) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.NOT_FOUND,
                        0.0D,
                        "source_file_not_found",
                        "",
                        citation.getOrdinal()
                );
            }
            double overlapScore = calculateOverlapScore(hardFactTokens, sourceFileRecord.getContentText());
            if (hasDirectEvidenceLineMatch(citation.getClaimText(), sourceFileRecord.getContentText())) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        Math.max(overlapScore, 1.0D),
                        "source_direct_line_match_verified",
                        extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                        citation.getOrdinal()
                );
            }
            if (overlapScore >= 1.0D) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        overlapScore,
                        "source_rule_overlap_verified",
                        extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                        citation.getOrdinal()
                );
            }
            if (isHighConfidencePartialOverlap(hardFactTokens, overlapScore)) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        overlapScore,
                        "source_near_complete_overlap_verified",
                        extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                        citation.getOrdinal()
                );
            }
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    overlapScore,
                    "source_insufficient_overlap",
                    extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        if (articleJdbcRepository == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "article_repository_unavailable",
                    "",
                    citation.getOrdinal()
            );
        }
        ArticleRecord articleRecord = articleJdbcRepository.findByArticleKey(citation.getTargetKey())
                .or(() -> articleJdbcRepository.findByConceptId(citation.getTargetKey()))
                .orElse(null);
        if (articleRecord == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.NOT_FOUND,
                    0.0D,
                    "article_not_found",
                    "",
                    citation.getOrdinal()
            );
        }
        double overlapScore = calculateOverlapScore(hardFactTokens, buildEvidenceText(articleRecord));
        if (hasDirectEvidenceLineMatch(citation.getClaimText(), articleRecord.getContent())) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    Math.max(overlapScore, 1.0D),
                    "direct_line_match_verified",
                    extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        if (overlapScore >= 1.0D) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    overlapScore,
                    "rule_overlap_verified",
                    extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        if (isHighConfidencePartialOverlap(hardFactTokens, overlapScore)) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    overlapScore,
                    "near_complete_overlap_verified",
                    extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        return new CitationValidationResult(
                citation.getTargetKey(),
                citation.getSourceType(),
                CitationValidationStatus.DEMOTED,
                overlapScore,
                "insufficient_overlap",
                extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                citation.getOrdinal()
        );
    }

    private boolean isHighConfidencePartialOverlap(List<String> hardFactTokens, double overlapScore) {
        return hardFactTokens != null
                && ((hardFactTokens.size() >= 4 && overlapScore >= 0.75D)
                || (hardFactTokens.size() >= 2 && overlapScore >= 0.66D));
    }

    private String buildEvidenceText(ArticleRecord articleRecord) {
        StringBuilder textBuilder = new StringBuilder();
        if (articleRecord.getTitle() != null) {
            textBuilder.append(articleRecord.getTitle()).append('\n');
        }
        if (articleRecord.getSummary() != null) {
            textBuilder.append(articleRecord.getSummary()).append('\n');
        }
        if (articleRecord.getContent() != null) {
            textBuilder.append(articleRecord.getContent());
        }
        return textBuilder.toString();
    }

    private double calculateOverlapScore(List<String> hardFactTokens, String evidenceText) {
        Set<String> claimTokens = new LinkedHashSet<String>(hardFactTokens);
        if (claimTokens.isEmpty()) {
            return 0.0D;
        }
        Set<String> evidenceTokens = tokenize(evidenceText);
        if (evidenceTokens.isEmpty()) {
            return 0.0D;
        }
        int matchedCount = 0;
        for (String claimToken : claimTokens) {
            if (evidenceTokens.contains(claimToken)) {
                matchedCount++;
            }
        }
        return matchedCount * 1.0D / claimTokens.size();
    }

    private List<String> extractHardFactTokens(String claimText) {
        List<String> hardFactTokens = new ArrayList<String>();
        if (claimText == null || claimText.isBlank()) {
            return hardFactTokens;
        }
        String normalizedClaimText = normalizeForHardFactExtraction(claimText);
        appendMatches(hardFactTokens, NUMERIC_LITERAL_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, SNAKE_CASE_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, FQN_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, HTTP_PATH_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, JAVA_SYMBOL_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, LATIN_TERM_PATTERN.matcher(normalizedClaimText));
        if (!hardFactTokens.isEmpty()) {
            appendHanTermMatches(hardFactTokens, HAN_TERM_PATTERN.matcher(normalizedClaimText));
        }
        return hardFactTokens;
    }

    private void appendMatches(List<String> hardFactTokens, Matcher matcher) {
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (literal == null || literal.isBlank()) {
                continue;
            }
            String normalizedLiteral = normalizeToken(literal);
            if (!normalizedLiteral.isBlank() && !hardFactTokens.contains(normalizedLiteral)) {
                hardFactTokens.add(normalizedLiteral);
            }
        }
    }

    private void appendHanTermMatches(List<String> hardFactTokens, Matcher matcher) {
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (literal == null || literal.isBlank() || !containsNamedHanSignal(literal) || isGenericHanLiteral(literal)) {
                continue;
            }
            String normalizedLiteral = normalizeToken(literal);
            if (!hardFactTokens.contains(normalizedLiteral)) {
                hardFactTokens.add(normalizedLiteral);
            }
            if (literal.length() >= 5) {
                for (int start = 0; start <= literal.length() - 3; start++) {
                    String slice = literal.substring(start, start + 3);
                    if (!containsNamedHanSignal(slice) || isGenericHanLiteral(slice)) {
                        continue;
                    }
                    String normalizedSlice = normalizeToken(slice);
                    if (!hardFactTokens.contains(normalizedSlice)) {
                        hardFactTokens.add(normalizedSlice);
                    }
                }
            }
        }
    }

    private boolean isGenericHanLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return true;
        }
        return literal.contains("当前证据不足")
                || literal.contains("主要")
                || literal.contains("包括")
                || literal.contains("需要")
                || literal.contains("可以")
                || literal.contains("通过")
                || literal.contains("相关")
                || literal.contains("不同")
                || literal.contains("如下")
                || literal.contains("例如")
                || literal.contains("使用")
                || literal.contains("暴露")
                || literal.contains("采用")
                || literal.contains("处理");
    }

    private boolean containsNamedHanSignal(String literal) {
        if (literal == null || literal.isBlank()) {
            return false;
        }
        return literal.contains("资和信")
                || literal.contains("易百")
                || literal.contains("杉德")
                || literal.contains("苏州")
                || literal.contains("得仕")
                || literal.contains("广发")
                || literal.contains("民生")
                || literal.contains("宁波")
                || literal.contains("上海")
                || literal.contains("星巴克")
                || literal.contains("账号数据库")
                || literal.contains("目录服务");
    }

    private Set<String> tokenize(String content) {
        Set<String> tokens = new LinkedHashSet<String>();
        if (content == null || content.isBlank()) {
            return tokens;
        }
        String[] parts = content.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_./-]+");
        for (String part : parts) {
            if (part != null && !part.isBlank() && (part.length() >= 2 || isNumericToken(part))) {
                tokens.add(part);
            }
        }
        appendEmbeddedNumericTokens(tokens, content);
        return tokens;
    }

    /**
     * 补充嵌在中文单位或连续文本里的数字事实。
     *
     * @param tokens token 集合
     * @param content 原始内容
     */
    private void appendEmbeddedNumericTokens(Set<String> tokens, String content) {
        Matcher matcher = NUMERIC_LITERAL_PATTERN.matcher(content);
        while (matcher.find()) {
            String numericToken = normalizeToken(matcher.group(1));
            if (!numericToken.isBlank()) {
                tokens.add(numericToken);
            }
        }
    }

    private String normalizeToken(String literal) {
        return literal == null ? "" : literal.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeForHardFactExtraction(String claimText) {
        return claimText == null
                ? ""
                : claimText
                .replace("**", "")
                .replace("`", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("(?<=\\s|^)\\d+\\.\\s*", "")
                .trim();
    }

    private boolean hasDirectEvidenceLineMatch(String claimText, String evidenceText) {
        String normalizedClaimText = normalizeForDirectLineMatch(claimText);
        if (normalizedClaimText.isBlank() || evidenceText == null || evidenceText.isBlank()) {
            return false;
        }
        for (String line : evidenceText.split("\\R")) {
            String normalizedEvidenceLine = normalizeForDirectLineMatch(line);
            if (normalizedEvidenceLine.isBlank()) {
                continue;
            }
            if (normalizedEvidenceLine.contains(normalizedClaimText) || normalizedClaimText.contains(normalizedEvidenceLine)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForDirectLineMatch(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace("**", "")
                .replace("`", "")
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceFirst("^\\s*[-*]\\s*", "")
                .replaceFirst("^当前可确认的信息是[:：]\\s*", "")
                .replaceFirst("^补充证据还提到[:：]\\s*", "")
                .replaceFirst("^同一份资料还给出[:：]\\s*", "")
                .replaceFirst("^支持“[^”]+”的材料提到[:：]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNumericToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private String extractMatchedExcerpt(String content, List<String> hardFactTokens) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (hardFactTokens == null || hardFactTokens.isEmpty()) {
            return content.length() <= 200 ? content : content.substring(0, 200);
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim().toLowerCase(Locale.ROOT);
            if (normalizedLine.isBlank()) {
                continue;
            }
            for (String hardFactToken : hardFactTokens) {
                String normalizedToken = hardFactToken == null ? "" : hardFactToken.trim().toLowerCase(Locale.ROOT);
                if (!normalizedToken.isBlank() && normalizedLine.contains(normalizedToken)) {
                    return line.length() <= 200 ? line : line.substring(0, 200);
                }
            }
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }
}
