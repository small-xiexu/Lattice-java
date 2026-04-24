package com.xbk.lattice.query.citation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citation 提取器
 *
 * 职责：把最终答案切成 claim 片段，并从片段中提取文章/源文件引用
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CitationExtractor {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]+)?]]");

    private static final Pattern SOURCE_PATTERN = Pattern.compile("\\[→\\s*([^,\\]]+)(?:,[^\\]]+)?]");

    private static final List<String> NON_CLAIM_SECTIONS = List.of("问题", "分层摘要", "冲突提示");

    /**
     * 提取答案中的 claim 片段。
     *
     * @param answerMarkdown 答案 Markdown
     * @return claim 片段
     */
    public List<ClaimSegment> extractClaims(String answerMarkdown) {
        List<ClaimSegment> claimSegments = new ArrayList<ClaimSegment>();
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return claimSegments;
        }
        String[] paragraphs = answerMarkdown.split("\\n\\s*\\n");
        int claimIndex = 0;
        int citationOrdinal = 0;
        String currentSection = "";
        for (String paragraph : paragraphs) {
            String normalizedParagraph = paragraph == null ? "" : paragraph.trim();
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            String claimCandidate = normalizedParagraph;
            if (normalizedParagraph.startsWith("#")) {
                String[] headingAndBody = normalizedParagraph.split("\\R", 2);
                currentSection = headingAndBody[0].replaceFirst("^#+\\s*", "").trim();
                if (headingAndBody.length < 2) {
                    continue;
                }
                claimCandidate = headingAndBody[1].trim();
                if (claimCandidate.isBlank()) {
                    continue;
                }
            }
            if (shouldSkipSection(currentSection)) {
                continue;
            }
            String[] lines = claimCandidate.split("\\R");
            if (hasLineScopedClaims(lines)) {
                for (String line : lines) {
                    ClaimSegment claimSegment = buildClaimSegment(claimIndex, line, citationOrdinal);
                    if (claimSegment == null) {
                        continue;
                    }
                    claimSegments.add(claimSegment);
                    claimIndex++;
                    if (!claimSegment.getCitations().isEmpty()) {
                        citationOrdinal = claimSegment.getCitations()
                                .get(claimSegment.getCitations().size() - 1)
                                .getOrdinal() + 1;
                    }
                }
                continue;
            }
            ClaimSegment claimSegment = buildClaimSegment(claimIndex, claimCandidate, citationOrdinal);
            if (claimSegment == null) {
                continue;
            }
            claimSegments.add(claimSegment);
            claimIndex++;
            if (!claimSegment.getCitations().isEmpty()) {
                citationOrdinal = claimSegment.getCitations().get(claimSegment.getCitations().size() - 1).getOrdinal() + 1;
            }
        }
        return claimSegments;
    }

    /**
     * 去掉段落中的引用文本。
     *
     * @param paragraph 原始段落
     * @return 去掉引用后的文本
     */
    public String stripCitationLiteral(String paragraph) {
        String stripped = ARTICLE_PATTERN.matcher(paragraph).replaceAll("");
        stripped = SOURCE_PATTERN.matcher(stripped).replaceAll("");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private int extractArticleCitations(
            List<Citation> citations,
            String paragraph,
            String claimText,
            int startOrdinal
    ) {
        int ordinal = startOrdinal;
        Matcher matcher = ARTICLE_PATTERN.matcher(paragraph);
        while (matcher.find()) {
            citations.add(new Citation(
                    ordinal,
                    matcher.group(),
                    CitationSourceType.ARTICLE,
                    matcher.group(1).trim(),
                    claimText,
                    paragraph
            ));
            ordinal++;
        }
        return ordinal;
    }

    private int extractSourceCitations(
            List<Citation> citations,
            String paragraph,
            String claimText,
            int startOrdinal
    ) {
        int ordinal = startOrdinal;
        Matcher matcher = SOURCE_PATTERN.matcher(paragraph);
        while (matcher.find()) {
            citations.add(new Citation(
                    ordinal,
                    matcher.group(),
                    CitationSourceType.SOURCE_FILE,
                    matcher.group(1).trim(),
                    claimText,
                    paragraph
            ));
            ordinal++;
        }
        return ordinal;
    }

    private boolean hasLineScopedClaims(String[] lines) {
        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim();
            if (normalizedLine.startsWith("- ")
                    || normalizedLine.startsWith("* ")
                    || normalizedLine.matches("\\d+\\.\\s+.+")) {
                return true;
            }
        }
        return false;
    }

    private ClaimSegment buildClaimSegment(int claimIndex, String rawParagraph, int startOrdinal) {
        String normalizedParagraph = rawParagraph == null ? "" : rawParagraph.trim();
        if (normalizedParagraph.isBlank() || normalizedParagraph.startsWith("#")) {
            return null;
        }
        String claimText = stripCitationLiteral(normalizedParagraph).trim();
        if (claimText.isBlank()) {
            return null;
        }
        List<Citation> citations = new ArrayList<Citation>();
        int nextOrdinal = extractArticleCitations(citations, normalizedParagraph, claimText, startOrdinal);
        extractSourceCitations(citations, normalizedParagraph, claimText, nextOrdinal);
        return new ClaimSegment(claimIndex, claimText, normalizedParagraph, citations);
    }

    private boolean shouldSkipSection(String currentSection) {
        if (currentSection == null || currentSection.isBlank()) {
            return false;
        }
        for (String nonClaimSection : NON_CLAIM_SECTIONS) {
            if (currentSection.contains(nonClaimSection)) {
                return true;
            }
        }
        return false;
    }
}
