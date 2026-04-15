package com.xbk.lattice.compiler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文档章节选择器
 *
 * 职责：根据概念关键词从长文本中选择更相关的内容片段
 *
 * @author xiexu
 */
public class DocumentSectionSelector {

    /**
     * 选择与概念相关的内容片段。
     *
     * @param content 原始内容
     * @param conceptTerms 概念关键词
     * @param maxChars 最大字符数
     * @return 选出的内容片段
     */
    public String select(String content, List<String> conceptTerms, int maxChars) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        String[] sections = content.split("(?m)^=== ");
        List<String> matchedSections = new ArrayList<String>();
        for (String section : sections) {
            String normalizedSection = section.trim();
            if (normalizedSection.isEmpty()) {
                continue;
            }
            String lowercaseSection = normalizedSection.toLowerCase(Locale.ROOT);
            if (matchesAny(lowercaseSection, conceptTerms)) {
                matchedSections.add("=== " + normalizedSection);
            }
        }
        String selected = matchedSections.isEmpty() ? content : String.join("\n\n", matchedSections);
        if (selected.length() <= maxChars) {
            return selected;
        }
        return selected.substring(0, maxChars);
    }

    /**
     * 判断文本是否命中任一概念关键词。
     *
     * @param lowercaseSection 小写文本
     * @param conceptTerms 概念关键词
     * @return 是否命中
     */
    private boolean matchesAny(String lowercaseSection, List<String> conceptTerms) {
        for (String conceptTerm : conceptTerms) {
            if (conceptTerm == null || conceptTerm.isBlank()) {
                continue;
            }
            if (lowercaseSection.contains(conceptTerm.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
