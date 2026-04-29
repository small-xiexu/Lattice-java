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

    private static final Pattern ARTICLE_PATTERN = Pattern.compile("\\[\\[(?!→\\s*)([^\\]|]+)(?:\\|[^\\]]+)?]]");

    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "(?:\\[\\[→\\s*([^,\\]]+)(?:,[^\\]]+)?]]|\\[→\\s*([^,\\]]+)(?:,[^\\]]+)?])"
    );

    private static final Pattern SOURCE_LINE_RANGE_PATTERN = Pattern.compile("^(.+?):(?:L)?\\d+(?:-(?:L)?\\d+)?$");

    private static final List<String> NON_CLAIM_SECTIONS = List.of("问题", "分层摘要", "冲突提示", "参考说明");

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
        String[] blocks = answerMarkdown.split("\\n\\s*\\n");
        int claimIndex = 0;
        int citationOrdinal = 0;
        String currentSection = "";
        for (String block : blocks) {
            String normalizedBlock = block == null ? "" : block.trim();
            if (normalizedBlock.isBlank()) {
                continue;
            }
            String contentBlock = normalizedBlock;
            if (normalizedBlock.startsWith("#")) {
                String[] headingAndBody = normalizedBlock.split("\\R", 2);
                currentSection = headingAndBody[0].replaceFirst("^#+\\s*", "").trim();
                if (headingAndBody.length < 2) {
                    continue;
                }
                contentBlock = headingAndBody[1].trim();
            }
            if (contentBlock.isBlank()
                    || shouldSkipSection(currentSection)
                    || isFencedCodeBlock(contentBlock)
                    || isStructuralBlock(contentBlock)) {
                continue;
            }
            List<String> claimCandidates = extractClaimCandidates(contentBlock);
            for (String claimCandidate : claimCandidates) {
                ClaimSegment claimSegment = buildClaimSegment(claimIndex, claimCandidate, citationOrdinal);
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
            String rawTargetKey = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            citations.add(new Citation(
                    ordinal,
                    buildCanonicalSourceLiteral(rawTargetKey),
                    CitationSourceType.SOURCE_FILE,
                    normalizeSourceTargetKey(rawTargetKey),
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

    private List<String> extractClaimCandidates(String contentBlock) {
        List<String> claimCandidates = new ArrayList<String>();
        String[] lines = contentBlock.split("\\R");
        if (hasCitationTableClaims(lines)) {
            return extractTableClaimCandidates(lines);
        }
        if (hasLineScopedClaims(lines)) {
            for (String line : lines) {
                String normalizedLine = normalizeListLine(line);
                if (normalizedLine.isBlank() || isReferenceLine(normalizedLine) || isStructuralLine(normalizedLine)) {
                    continue;
                }
                claimCandidates.addAll(splitSentenceClaims(normalizedLine));
            }
            return claimCandidates;
        }
        return splitSentenceClaims(contentBlock);
    }

    private ClaimSegment buildClaimSegment(int claimIndex, String rawParagraph, int startOrdinal) {
        String normalizedParagraph = rawParagraph == null ? "" : rawParagraph.trim();
        if (normalizedParagraph.isBlank() || normalizedParagraph.startsWith("#")) {
            return null;
        }
        String claimText = normalizeClaimText(stripCitationLiteral(normalizedParagraph));
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

    private boolean isStructuralBlock(String contentBlock) {
        if (contentBlock == null || contentBlock.isBlank()) {
            return true;
        }
        String[] lines = contentBlock.split("\\R");
        if (hasCitationTableClaims(lines)) {
            return false;
        }
        boolean sawNonBlankLine = false;
        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim();
            if (normalizedLine.isBlank()) {
                continue;
            }
            sawNonBlankLine = true;
            if (isReferenceLine(normalizedLine)) {
                continue;
            }
            if (isStructuralLine(normalizedLine)) {
                continue;
            }
            if (!normalizeClaimText(stripCitationLiteral(normalizedLine)).isBlank()) {
                return false;
            }
        }
        return sawNonBlankLine;
    }

    private boolean isReferenceLine(String line) {
        String normalizedLine = line == null ? "" : line.trim();
        if (normalizedLine.isBlank()) {
            return false;
        }
        if (normalizedLine.startsWith("[^") && normalizedLine.contains("]:")) {
            return true;
        }
        return normalizedLine.startsWith("> [") || normalizedLine.matches("^\\[[^\\]]+]:.*$");
    }

    /**
     * 判断当前 block 是否为 fenced code。
     *
     * @param contentBlock Markdown block
     * @return fenced code block 返回 true
     */
    private boolean isFencedCodeBlock(String contentBlock) {
        String normalizedBlock = contentBlock == null ? "" : contentBlock.trim();
        return normalizedBlock.startsWith("```") || normalizedBlock.startsWith("~~~");
    }

    /**
     * 判断当前行是否为结构性 Markdown，而不是可核验 claim。
     *
     * @param line Markdown 行
     * @return 结构性内容返回 true
     */
    private boolean isStructuralLine(String line) {
        String normalizedLine = line == null ? "" : line.trim();
        if (normalizedLine.isBlank()) {
            return true;
        }
        if (normalizedLine.startsWith("```") || normalizedLine.startsWith("~~~")) {
            return true;
        }
        if (normalizedLine.startsWith("|") && normalizedLine.endsWith("|")) {
            return true;
        }
        if (normalizedLine.matches("^[-*_]{3,}$")) {
            return true;
        }
        return normalizedLine.startsWith("![") || normalizedLine.startsWith("<details") || normalizedLine.startsWith("</details");
    }

    /**
     * 判断表格里是否存在带引用的数据行。
     *
     * @param lines Markdown block 行
     * @return 存在可核验表格行返回 true
     */
    private boolean hasCitationTableClaims(String[] lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim();
            if (isTableDataClaimLine(normalizedLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从带引用的 Markdown 表格数据行抽取 claim 候选。
     *
     * @param lines Markdown block 行
     * @return claim 候选
     */
    private List<String> extractTableClaimCandidates(String[] lines) {
        List<String> claimCandidates = new ArrayList<String>();
        List<String> headerCells = List.of();
        if (lines == null) {
            return claimCandidates;
        }
        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim();
            if (!isMarkdownTableRow(normalizedLine) || isMarkdownTableSeparatorLine(normalizedLine)) {
                continue;
            }
            List<String> rowCells = splitMarkdownTableCells(normalizedLine);
            if (!containsCitationLiteral(normalizedLine)) {
                if (headerCells.isEmpty()) {
                    headerCells = rowCells;
                }
                continue;
            }
            String tableClaim = buildTableClaimCandidate(headerCells, rowCells);
            if (!tableClaim.isBlank()) {
                claimCandidates.add(tableClaim);
            }
        }
        return claimCandidates;
    }

    /**
     * 判断当前行是否是可核验的表格数据行。
     *
     * @param normalizedLine 已归一化行
     * @return 可核验返回 true
     */
    private boolean isTableDataClaimLine(String normalizedLine) {
        return isMarkdownTableRow(normalizedLine)
                && !isMarkdownTableSeparatorLine(normalizedLine)
                && containsCitationLiteral(normalizedLine);
    }

    /**
     * 判断当前行是否是 Markdown 表格行。
     *
     * @param normalizedLine 已归一化行
     * @return 表格行返回 true
     */
    private boolean isMarkdownTableRow(String normalizedLine) {
        return normalizedLine != null && normalizedLine.startsWith("|") && normalizedLine.endsWith("|");
    }

    /**
     * 判断当前行是否是 Markdown 表格分隔行。
     *
     * @param normalizedLine 已归一化行
     * @return 分隔行返回 true
     */
    private boolean isMarkdownTableSeparatorLine(String normalizedLine) {
        if (!isMarkdownTableRow(normalizedLine)) {
            return false;
        }
        String withoutPipes = normalizedLine.replace("|", "").replace(":", "").replace("-", "").trim();
        return withoutPipes.isBlank();
    }

    /**
     * 拆分 Markdown 表格单元格。
     *
     * @param tableLine 表格行
     * @return 单元格列表
     */
    private List<String> splitMarkdownTableCells(String tableLine) {
        List<String> cells = new ArrayList<String>();
        if (tableLine == null || tableLine.isBlank()) {
            return cells;
        }
        String normalizedLine = tableLine.trim();
        if (normalizedLine.startsWith("|")) {
            normalizedLine = normalizedLine.substring(1);
        }
        if (normalizedLine.endsWith("|")) {
            normalizedLine = normalizedLine.substring(0, normalizedLine.length() - 1);
        }
        String[] rawCells = normalizedLine.split("\\|", -1);
        for (String rawCell : rawCells) {
            String cell = rawCell == null ? "" : rawCell.trim();
            cells.add(cell);
        }
        return cells;
    }

    /**
     * 把表格数据行转换为可校验 claim 文本。
     *
     * @param headerCells 表头
     * @param rowCells 数据行
     * @return claim 文本
     */
    private String buildTableClaimCandidate(List<String> headerCells, List<String> rowCells) {
        if (rowCells == null || rowCells.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<String>();
        for (int index = 0; index < rowCells.size(); index++) {
            String rowCell = rowCells.get(index) == null ? "" : rowCells.get(index).trim();
            if (rowCell.isBlank()) {
                continue;
            }
            String headerCell = resolveHeaderCell(headerCells, index);
            if (headerCell.isBlank()) {
                parts.add(rowCell);
                continue;
            }
            parts.add(headerCell + "：" + rowCell);
        }
        return String.join("；", parts);
    }

    /**
     * 取指定位置的表头单元格。
     *
     * @param headerCells 表头
     * @param index 单元格下标
     * @return 表头文本
     */
    private String resolveHeaderCell(List<String> headerCells, int index) {
        if (headerCells == null || index < 0 || index >= headerCells.size()) {
            return "";
        }
        String headerCell = headerCells.get(index);
        if (headerCell == null) {
            return "";
        }
        return stripCitationLiteral(headerCell).trim();
    }

    private String normalizeListLine(String line) {
        String normalizedLine = line == null ? "" : line.trim();
        normalizedLine = normalizedLine.replaceFirst("^[-*]\\s+", "");
        normalizedLine = normalizedLine.replaceFirst("^\\d+\\.\\s+", "");
        return normalizedLine.trim();
    }

    private String normalizeSourceTargetKey(String rawTargetKey) {
        String normalizedTargetKey = rawTargetKey == null ? "" : rawTargetKey.trim();
        Matcher matcher = SOURCE_LINE_RANGE_PATTERN.matcher(normalizedTargetKey);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return normalizedTargetKey;
    }

    private List<String> splitSentenceClaims(String contentBlock) {
        List<String> claimCandidates = new ArrayList<String>();
        if (contentBlock == null || contentBlock.isBlank()) {
            return claimCandidates;
        }
        String normalizedBlock = contentBlock.replaceAll("\\R+", " ").trim();
        String[] segments = normalizedBlock.split("(?<=[。；;])");
        for (String segment : segments) {
            String normalizedSegment = segment == null ? "" : segment.trim();
            if (normalizedSegment.isBlank()) {
                continue;
            }
            if (isCitationOnlySegment(normalizedSegment)) {
                appendCitationTail(claimCandidates, normalizedSegment);
                continue;
            }
            claimCandidates.add(normalizedSegment);
        }
        propagateFollowingCitationToPreviousClaims(claimCandidates);
        return claimCandidates;
    }

    private void propagateFollowingCitationToPreviousClaims(List<String> claimCandidates) {
        if (claimCandidates == null || claimCandidates.size() <= 1) {
            return;
        }
        List<Integer> pendingCitationIndexes = new ArrayList<Integer>();
        for (int index = 0; index < claimCandidates.size(); index++) {
            String claimCandidate = claimCandidates.get(index);
            if (!containsCitationLiteral(claimCandidate)) {
                pendingCitationIndexes.add(Integer.valueOf(index));
                continue;
            }
            if (pendingCitationIndexes.isEmpty()) {
                continue;
            }
            String citationTail = extractCitationTail(claimCandidate);
            if (citationTail.isBlank()) {
                pendingCitationIndexes.clear();
                continue;
            }
            for (Integer pendingCitationIndex : pendingCitationIndexes) {
                String previousClaim = claimCandidates.get(pendingCitationIndex.intValue());
                claimCandidates.set(pendingCitationIndex.intValue(), previousClaim + " " + citationTail);
            }
            pendingCitationIndexes.clear();
        }
    }

    private String extractCitationTail(String content) {
        if (content == null || content.isBlank() || !containsCitationLiteral(content)) {
            return "";
        }
        List<String> citationLiterals = new ArrayList<String>();
        Matcher articleMatcher = ARTICLE_PATTERN.matcher(content);
        while (articleMatcher.find()) {
            citationLiterals.add(articleMatcher.group());
        }
        Matcher sourceMatcher = SOURCE_PATTERN.matcher(content);
        while (sourceMatcher.find()) {
            citationLiterals.add(buildCanonicalSourceLiteral(sourceMatcher.group(1) != null
                    ? sourceMatcher.group(1)
                    : sourceMatcher.group(2)));
        }
        return String.join("", citationLiterals);
    }

    private void appendCitationTail(List<String> claimCandidates, String citationTail) {
        if (claimCandidates == null || claimCandidates.isEmpty() || citationTail == null || citationTail.isBlank()) {
            return;
        }
        boolean claimAlreadyContainsCitation = false;
        for (String claimCandidate : claimCandidates) {
            if (containsCitationLiteral(claimCandidate)) {
                claimAlreadyContainsCitation = true;
                break;
            }
        }
        if (claimAlreadyContainsCitation) {
            int lastIndex = claimCandidates.size() - 1;
            claimCandidates.set(lastIndex, claimCandidates.get(lastIndex) + citationTail);
            return;
        }
        for (int index = 0; index < claimCandidates.size(); index++) {
            claimCandidates.set(index, claimCandidates.get(index) + citationTail);
        }
    }

    private boolean containsCitationLiteral(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return ARTICLE_PATTERN.matcher(content).find() || SOURCE_PATTERN.matcher(content).find();
    }

    private boolean isCitationOnlySegment(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (!containsCitationLiteral(content)) {
            return false;
        }
        return stripCitationLiteral(content).isBlank();
    }

    private String buildCanonicalSourceLiteral(String rawTargetKey) {
        String normalizedTargetKey = normalizeSourceTargetKey(rawTargetKey);
        return "[→ " + normalizedTargetKey + "]";
    }

    private String normalizeClaimText(String claimText) {
        String normalizedClaimText = claimText == null ? "" : claimText.trim();
        normalizedClaimText = normalizedClaimText.replaceFirst("^[-*]\\s+", "");
        normalizedClaimText = normalizedClaimText.replaceFirst("^\\d+\\.\\s+", "");
        normalizedClaimText = normalizedClaimText.replaceAll("[。；;]+$", "");
        return normalizedClaimText.trim();
    }
}
