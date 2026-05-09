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
        if (answerMarkdown == null || answerMarkdown.isBlank() || !support.looksLikeExactLookupQuestion(question)) {
            return answerMarkdown;
        }
        if (support.looksLikeComparisonQuestion(question) || support.looksLikeFlowQuestion(question)) {
            return answerMarkdown;
        }
        String[] rawParagraphs = answerMarkdown.split("\\n\\s*\\n", -1);
        if (rawParagraphs.length <= 1) {
            return answerMarkdown;
        }
        List<String> keptParagraphs = new ArrayList<String>();
        for (String rawParagraph : rawParagraphs) {
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
        String compactAnswer = String.join("\n\n", keptParagraphs).trim();
        if (compactAnswer.isBlank() || !support.containsCitationLiteral(compactAnswer)) {
            return answerMarkdown;
        }
        return compactAnswer;
    }

    private boolean isSummaryHeadingParagraph(String paragraph) {
        return paragraph.startsWith("## ") && paragraph.length() <= 120;
    }

    private boolean looksLikeDirectAnswerParagraph(String paragraph, String question) {
        String normalizedParagraph = lowerCase(support.stripEmbeddedCitationLiterals(paragraph));
        if (normalizedParagraph.startsWith("## ")) {
            return true;
        }
        if (normalizedParagraph.startsWith("- ")
                || normalizedParagraph.startsWith("* ")
                || normalizedParagraph.matches("^\\d+\\..*")) {
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
        String normalizedParagraph = support.stripEmbeddedCitationLiterals(paragraph).trim();
        return !support.containsCitationLiteral(paragraph)
                && (normalizedParagraph.endsWith(":") || normalizedParagraph.endsWith("："));
    }

    private int countMarkdownListItems(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String rawLine : markdown.split("\\R")) {
            String normalizedLine = rawLine.trim();
            if (normalizedLine.startsWith("- ")
                    || normalizedLine.startsWith("* ")
                    || normalizedLine.matches("^\\d+\\..*")) {
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
