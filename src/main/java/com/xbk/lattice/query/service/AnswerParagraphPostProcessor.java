package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 答案段落后处理器
 *
 * 职责：处理结构化答案中的段落压缩、保守引言移除与证据缺口段清理
 *
 * 不属于本类的事：不选择 citation、不解析 LLM JSON、不构造 deterministic fallback
 *
 * @author xiexu
 */
final class AnswerParagraphPostProcessor {

    private static final int MAX_SEQUENTIAL_COMPRESSED_PARAGRAPHS = 5;

    private final AnswerGenerationService support;

    /**
     * 创建答案段落后处理器。
     *
     * @param support 答案生成支撑逻辑
     */
    AnswerParagraphPostProcessor(AnswerGenerationService support) {
        this.support = support;
    }

    /**
     * 对精确查值题收敛结构化答案，只保留直接答案段与最必要的补充段。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @return 收敛后的答案
     */
    String compressStructuredExactLookupAnswer(String answerMarkdown, String question) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        if (support.looksLikeComparisonQuestion(question) || support.looksLikeFlowQuestion(question)) {
            return answerMarkdown;
        }
        if (shouldKeepExpandedMultiPointAnswer(question, answerMarkdown)) {
            return answerMarkdown;
        }
        String[] rawParagraphs = answerMarkdown.split("\\n\\s*\\n", -1);
        if (rawParagraphs.length <= 1) {
            return answerMarkdown;
        }
        boolean sequenceLikeQuestion = looksLikeSequentialExactLookupQuestion(question);
        boolean keptStructuredBodyAfterLeadIn = false;
        List<String> keptParagraphs = new ArrayList<String>();
        for (int paragraphIndex = 0; paragraphIndex < rawParagraphs.length; paragraphIndex++) {
            String rawParagraph = rawParagraphs[paragraphIndex];
            String normalizedParagraph = rawParagraph == null ? "" : rawParagraph.trim();
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            if (isSummaryHeadingParagraph(normalizedParagraph)) {
                keptParagraphs.add(rawParagraph);
                continue;
            }
            if (keptParagraphs.isEmpty()) {
                keptParagraphs.add(rawParagraph);
                continue;
            }
            if (looksLikeDanglingLeadInParagraph(keptParagraphs.get(keptParagraphs.size() - 1))
                    && looksLikeStructuredAnswerBodyParagraph(normalizedParagraph)) {
                keptParagraphs.add(rawParagraph);
                if (sequenceLikeQuestion
                        && hasSequentialSupplementAfterStructuredBody(rawParagraphs, paragraphIndex,
                        sequenceLikeQuestion)) {
                    keptStructuredBodyAfterLeadIn = true;
                    continue;
                }
                break;
            }
            if (keptStructuredBodyAfterLeadIn
                    && looksLikeSequentialSupplementParagraph(normalizedParagraph, sequenceLikeQuestion)) {
                keptParagraphs.add(rawParagraph);
                if (keptParagraphs.size() >= MAX_SEQUENTIAL_COMPRESSED_PARAGRAPHS) {
                    break;
                }
                continue;
            }
            if (keptParagraphs.size() == 1 && looksLikeDirectAnswerParagraph(normalizedParagraph, question)) {
                keptParagraphs.add(rawParagraph);
                continue;
            }
            break;
        }
        if (keptParagraphs.size() >= 2) {
            String lastParagraph = keptParagraphs.get(keptParagraphs.size() - 1);
            if (looksLikeDanglingLeadInParagraph(lastParagraph)) {
                keptParagraphs.remove(keptParagraphs.size() - 1);
            }
        }
        if (keptParagraphs.size() == 1 && looksLikeDanglingLeadInParagraph(keptParagraphs.get(0))) {
            return answerMarkdown;
        }
        String compactAnswer = String.join("\n\n", keptParagraphs).trim();
        if (compactAnswer.isBlank() || !support.containsCitationLiteral(compactAnswer)) {
            return answerMarkdown;
        }
        return compactAnswer;
    }

    /**
     * 判断多焦点问题的答案是否应保留展开形态，而不是继续压缩。
     *
     * @param question 用户问题
     * @param answerMarkdown 当前答案
     * @return 应保留展开形态返回 true
     */
    private boolean shouldKeepExpandedMultiPointAnswer(String question, String answerMarkdown) {
        if (question == null || question.isBlank() || answerMarkdown == null || answerMarkdown.isBlank()) {
            return false;
        }
        List<String> focusTokens = support.extractStructuredFactFocusTokens(question);
        if (focusTokens.size() < 2) {
            return false;
        }
        if (!support.querySemanticRules.containsAnyMultiFocusSeparator(question)
                && !support.looksLikeEnumerationQuestion(question)) {
            return false;
        }
        String normalizedAnswer = lowerCase(support.stripEmbeddedCitationLiterals(answerMarkdown));
        int matchedFocusTokenCount = 0;
        for (String focusToken : focusTokens) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (!normalizedFocusToken.isBlank() && normalizedAnswer.contains(normalizedFocusToken)) {
                matchedFocusTokenCount++;
            }
        }
        if (matchedFocusTokenCount < 2) {
            return false;
        }
        return countMarkdownListItems(answerMarkdown) >= 2
                || countStructuredKeyValueLines(answerMarkdown) >= 2
                || answerMarkdown.contains("\n\n");
    }

    /**
     * 统计答案中的结构化键值行数量。
     *
     * @param answerMarkdown 答案 Markdown
     * @return 键值行数量
     */
    private int countStructuredKeyValueLines(String answerMarkdown) {
        int structuredLineCount = 0;
        for (String rawLine : answerMarkdown.split("\\R")) {
            String normalizedLine = lowerCase(support.stripEmbeddedCitationLiterals(rawLine));
            if (looksLikeKeyValueAnswerLine(normalizedLine)) {
                structuredLineCount++;
            }
        }
        return structuredLineCount;
    }

    /**
     * 判断问题是否属于顺序、步骤或调整类精确查值。
     *
     * @param question 用户问题
     * @return 是顺序型问题返回 true
     */
    private boolean looksLikeSequentialExactLookupQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return support.querySemanticRules.containsAnySequenceSignal(question);
    }

    /**
     * 判断结构化主体之后是否还有顺序、步骤或调整补充段。
     *
     * @param rawParagraphs 原始段落
     * @param currentIndex 当前结构化主体段下标
     * @param sequenceLikeQuestion 问题是否属于顺序型问题
     * @return 后续仍有补充段返回 true
     */
    private boolean hasSequentialSupplementAfterStructuredBody(String[] rawParagraphs, int currentIndex,
            boolean sequenceLikeQuestion) {
        if (!sequenceLikeQuestion || rawParagraphs == null || rawParagraphs.length <= currentIndex + 1) {
            return false;
        }
        for (int index = currentIndex + 1; index < rawParagraphs.length; index++) {
            String normalizedParagraph = rawParagraphs[index] == null ? "" : rawParagraphs[index].trim();
            if (normalizedParagraph.isBlank()) {
                continue;
            }
            return looksLikeSequentialSupplementParagraph(normalizedParagraph, sequenceLikeQuestion);
        }
        return false;
    }

    /**
     * 判断 lead-in 与结构化主体之后的段落是否仍属于同一个顺序型答案主体。
     *
     * @param paragraph 待判断段落
     * @param sequenceLikeQuestion 问题是否属于顺序型问题
     * @return 应继续保留返回 true
     */
    private boolean looksLikeSequentialSupplementParagraph(String paragraph, boolean sequenceLikeQuestion) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        if (looksLikeDanglingLeadInParagraph(paragraph)) {
            return true;
        }
        if (looksLikeStructuredAnswerBodyParagraph(paragraph)) {
            return true;
        }
        String normalizedParagraph = lowerCase(support.stripEmbeddedCitationLiterals(paragraph));
        if (containsSequentialActionSignal(normalizedParagraph)) {
            return true;
        }
        if (!support.containsCitationLiteral(paragraph)) {
            return false;
        }
        return sequenceLikeQuestion || containsStructuredValueSignal(normalizedParagraph);
    }

    /**
     * 判断段落是否包含通用顺序、步骤或调整动作信号。
     *
     * @param paragraph 归一化段落
     * @return 命中返回 true
     */
    private boolean containsSequentialActionSignal(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        return paragraph.contains("sequence")
                || paragraph.contains("step")
                || paragraph.contains("order")
                || paragraph.contains("顺序")
                || paragraph.contains("先后")
                || paragraph.contains("步骤")
                || paragraph.contains("第一")
                || paragraph.contains("第二")
                || paragraph.contains("第三")
                || paragraph.contains("先")
                || paragraph.contains("再")
                || paragraph.contains("然后")
                || paragraph.contains("最后")
                || paragraph.contains("依次")
                || paragraph.contains("调整")
                || paragraph.contains("颠倒")
                || paragraph.contains("改成")
                || paragraph.contains("变更")
                || paragraph.contains("替换")
                || paragraph.contains("切换");
    }

    private boolean isSummaryHeadingParagraph(String paragraph) {
        return paragraph.startsWith("## ") && paragraph.length() <= 120;
    }

    private boolean looksLikeDirectAnswerParagraph(String paragraph, String question) {
        String normalizedParagraph = lowerCase(support.stripEmbeddedCitationLiterals(paragraph));
        if (normalizedParagraph.startsWith("## ")) {
            return true;
        }
        if (looksLikeMarkdownListItemLine(normalizedParagraph)) {
            return false;
        }
        if (normalizedParagraph.contains("|---|")) {
            return false;
        }
        if (support.looksLikeEnumerationQuestion(question) && countMarkdownListItems(paragraph) >= 2) {
            return false;
        }
        return normalizedParagraph.length() <= 220;
    }

    private boolean looksLikeDanglingLeadInParagraph(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        String normalizedParagraph = lowerCase(support.stripEmbeddedCitationLiterals(paragraph));
        if (normalizedParagraph.isBlank()
                || normalizedParagraph.startsWith("## ")
                || normalizedParagraph.startsWith("```")
                || looksLikeMarkdownListItemLine(normalizedParagraph)
                || looksLikeMarkdownTableLine(normalizedParagraph)) {
            return false;
        }
        if (normalizedParagraph.endsWith(":") || normalizedParagraph.endsWith("：")) {
            return true;
        }
        String normalizedWithoutTrailingPunctuation = stripTrailingLeadInPunctuation(normalizedParagraph);
        return normalizedWithoutTrailingPunctuation.endsWith("如下")
                || normalizedWithoutTrailingPunctuation.endsWith("如下所示")
                || normalizedWithoutTrailingPunctuation.endsWith("以下")
                || normalizedWithoutTrailingPunctuation.endsWith("包括")
                || normalizedWithoutTrailingPunctuation.endsWith("分别为")
                || normalizedWithoutTrailingPunctuation.endsWith("分别是")
                || normalizedWithoutTrailingPunctuation.endsWith("列表如下");
    }

    /**
     * 判断段落是否像列表、表格、代码块或键值模板等结构化答案主体。
     *
     * @param paragraph 待判断段落
     * @return 是结构化答案主体返回 true
     */
    private boolean looksLikeStructuredAnswerBodyParagraph(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        if (countMarkdownListItems(paragraph) > 0) {
            return true;
        }
        int tableLineCount = 0;
        for (String rawLine : paragraph.split("\\R")) {
            String normalizedLine = lowerCase(support.stripEmbeddedCitationLiterals(rawLine));
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (normalizedLine.startsWith("```")) {
                return true;
            }
            if (looksLikeMarkdownTableLine(normalizedLine)) {
                tableLineCount++;
                continue;
            }
            if (looksLikeKeyValueAnswerLine(normalizedLine)) {
                return true;
            }
        }
        return tableLineCount >= 2;
    }

    /**
     * 判断单行是否像 Markdown 列表项。
     *
     * @param line 待判断行
     * @return 是列表项返回 true
     */
    private boolean looksLikeMarkdownListItemLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalizedLine = line.trim();
        return normalizedLine.startsWith("- ")
                || normalizedLine.startsWith("* ")
                || normalizedLine.startsWith("+ ")
                || normalizedLine.matches("^\\d+[.、)].*");
    }

    /**
     * 判断单行是否像 Markdown 表格行。
     *
     * @param line 待判断行
     * @return 是表格行返回 true
     */
    private boolean looksLikeMarkdownTableLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalizedLine = line.trim();
        return normalizedLine.contains("|---")
                || (normalizedLine.startsWith("|") && normalizedLine.indexOf('|', 1) > 0);
    }

    /**
     * 判断单行是否像含具体值的键值答案项。
     *
     * @param line 待判断行
     * @return 是键值答案项返回 true
     */
    private boolean looksLikeKeyValueAnswerLine(String line) {
        if (line == null || line.isBlank() || line.length() > 260 || looksLikeDanglingLeadInParagraph(line)) {
            return false;
        }
        int delimiterIndex = firstKeyValueDelimiterIndex(line);
        if (delimiterIndex <= 0 || delimiterIndex >= line.length() - 1) {
            return false;
        }
        String value = line.substring(delimiterIndex + 1).trim();
        return containsStructuredValueSignal(value);
    }

    /**
     * 查找通用键值分隔符位置。
     *
     * @param line 待判断行
     * @return 分隔符位置；没有则返回 -1
     */
    private int firstKeyValueDelimiterIndex(String line) {
        int asciiColonIndex = line.indexOf(':');
        int chineseColonIndex = line.indexOf('：');
        int equalsIndex = line.indexOf('=');
        int delimiterIndex = Integer.MAX_VALUE;
        if (asciiColonIndex >= 0) {
            delimiterIndex = Math.min(delimiterIndex, asciiColonIndex);
        }
        if (chineseColonIndex >= 0) {
            delimiterIndex = Math.min(delimiterIndex, chineseColonIndex);
        }
        if (equalsIndex >= 0) {
            delimiterIndex = Math.min(delimiterIndex, equalsIndex);
        }
        return delimiterIndex == Integer.MAX_VALUE ? -1 : delimiterIndex;
    }

    /**
     * 判断键值答案的值部分是否含路径、编号、模板等通用结构信号。
     *
     * @param value 值部分
     * @return 含结构信号返回 true
     */
    private boolean containsStructuredValueSignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (Character.isDigit(currentChar)
                    || currentChar == '/'
                    || currentChar == '\\'
                    || currentChar == '.'
                    || currentChar == '-'
                    || currentChar == '_'
                    || currentChar == '`'
                    || currentChar == '@') {
                return true;
            }
        }
        return false;
    }

    /**
     * 移除引导语末尾的普通句末标点，便于识别“如下。”这类形式。
     *
     * @param value 待处理文本
     * @return 去掉句末标点后的文本
     */
    private String stripTrailingLeadInPunctuation(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalizedValue = value.trim();
        while (!normalizedValue.isBlank()) {
            char lastChar = normalizedValue.charAt(normalizedValue.length() - 1);
            if (lastChar != '。' && lastChar != '.' && lastChar != '；' && lastChar != ';') {
                break;
            }
            normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 1).trim();
        }
        return normalizedValue;
    }

    private int countMarkdownListItems(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String rawLine : markdown.split("\\R")) {
            String normalizedLine = rawLine.trim();
            if (looksLikeMarkdownListItemLine(normalizedLine)) {
                count++;
            }
        }
        return count;
    }

    private String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
