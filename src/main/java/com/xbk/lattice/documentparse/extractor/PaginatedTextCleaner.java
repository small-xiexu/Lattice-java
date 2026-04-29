package com.xbk.lattice.documentparse.extractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分页文本清理器
 *
 * 职责：清理 PDF 等分页文档抽取结果中的重复页眉页脚与页码噪声
 *
 * @author xiexu
 */
final class PaginatedTextCleaner {

    private static final int MIN_REPEATED_LINE_COUNT = 3;

    private static final double MIN_REPEATED_LINE_RATIO = 0.25D;

    private static final int MIN_REPEATED_LINE_LENGTH = 6;

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
            "\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b|\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b"
    );

    private static final Pattern PAGE_COUNTER_PATTERN = Pattern.compile(
            "(?i)\\bpage\\s*\\d+\\s*(?:of|/)\\s*\\d+\\b|\\b\\d+\\s*/\\s*\\d+\\b|第\\s*\\d+\\s*页"
    );

    /**
     * 清理并拼接为带页标记的正文。
     *
     * @param pageTexts 按页抽取的正文
     * @return 清理后的正文
     */
    String cleanAndJoin(List<String> pageTexts) {
        if (pageTexts == null || pageTexts.isEmpty()) {
            return "";
        }
        Map<String, Integer> signatureFrequency = countRepeatedLineSignatures(pageTexts);
        StringBuilder contentBuilder = new StringBuilder();
        for (int pageIndex = 0; pageIndex < pageTexts.size(); pageIndex++) {
            String cleanedPageText = cleanPageText(pageTexts.get(pageIndex), signatureFrequency, pageTexts.size());
            if (cleanedPageText.isBlank()) {
                continue;
            }
            if (contentBuilder.length() > 0) {
                contentBuilder.append("\n\n");
            }
            contentBuilder.append("=== Page: ").append(pageIndex + 1).append(" ===").append("\n");
            contentBuilder.append(cleanedPageText);
        }
        return contentBuilder.toString();
    }

    /**
     * 统计跨页重复行签名。
     *
     * @param pageTexts 按页抽取的正文
     * @return 签名频次
     */
    private Map<String, Integer> countRepeatedLineSignatures(List<String> pageTexts) {
        Map<String, Integer> signatureFrequency = new HashMap<String, Integer>();
        for (String pageText : pageTexts) {
            Set<String> pageSignatures = new HashSet<String>();
            String[] lines = pageText == null ? new String[0] : pageText.split("\\R");
            for (String line : lines) {
                String signature = lineSignature(line);
                if (signature.isBlank()) {
                    continue;
                }
                pageSignatures.add(signature);
            }
            for (String signature : pageSignatures) {
                signatureFrequency.merge(signature, Integer.valueOf(1), Integer::sum);
            }
        }
        return signatureFrequency;
    }

    /**
     * 清理单页正文。
     *
     * @param pageText 原始单页正文
     * @param signatureFrequency 跨页重复签名频次
     * @param pageCount 总页数
     * @return 清理后的单页正文
     */
    private String cleanPageText(String pageText, Map<String, Integer> signatureFrequency, int pageCount) {
        if (pageText == null || pageText.isBlank()) {
            return "";
        }
        List<String> keptLines = new ArrayList<String>();
        String[] lines = pageText.split("\\R");
        for (String line : lines) {
            String normalizedLine = normalizeLine(line);
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (shouldDropLine(normalizedLine, signatureFrequency, pageCount)) {
                continue;
            }
            keptLines.add(normalizedLine);
        }
        return String.join("\n", keptLines).trim();
    }

    /**
     * 判断当前行是否应视为页眉页脚噪声。
     *
     * @param normalizedLine 归一化单行文本
     * @param signatureFrequency 跨页重复签名频次
     * @param pageCount 总页数
     * @return 应丢弃返回 true
     */
    private boolean shouldDropLine(
            String normalizedLine,
            Map<String, Integer> signatureFrequency,
            int pageCount
    ) {
        if (looksLikeStandalonePageCounter(normalizedLine)) {
            return true;
        }
        String signature = lineSignature(normalizedLine);
        if (signature.isBlank()) {
            return false;
        }
        int frequency = signatureFrequency.getOrDefault(signature, Integer.valueOf(0)).intValue();
        int threshold = repeatedLineThreshold(pageCount);
        return frequency >= threshold && looksLikePageChrome(normalizedLine);
    }

    /**
     * 计算重复行阈值。
     *
     * @param pageCount 总页数
     * @return 重复阈值
     */
    private int repeatedLineThreshold(int pageCount) {
        int ratioThreshold = (int) Math.ceil(pageCount * MIN_REPEATED_LINE_RATIO);
        return Math.max(MIN_REPEATED_LINE_COUNT, ratioThreshold);
    }

    /**
     * 判断单行是否像页眉页脚。
     *
     * @param normalizedLine 归一化单行文本
     * @return 像页眉页脚返回 true
     */
    private boolean looksLikePageChrome(String normalizedLine) {
        if (normalizedLine.length() < MIN_REPEATED_LINE_LENGTH) {
            return false;
        }
        if (URL_PATTERN.matcher(normalizedLine).find()) {
            return true;
        }
        if (PAGE_COUNTER_PATTERN.matcher(normalizedLine).find()) {
            return true;
        }
        if (DATE_TIME_PATTERN.matcher(normalizedLine).find()) {
            return true;
        }
        if (containsStructuredFactSignal(normalizedLine)) {
            return false;
        }
        return normalizedLine.length() <= 80 && !containsSentencePunctuation(normalizedLine);
    }

    /**
     * 判断是否包含结构化事实信号。
     *
     * @param normalizedLine 归一化单行文本
     * @return 包含返回 true
     */
    private boolean containsStructuredFactSignal(String normalizedLine) {
        return normalizedLine.contains("=")
                || normalizedLine.contains("->")
                || normalizedLine.contains("`")
                || normalizedLine.startsWith("/")
                || normalizedLine.contains("://");
    }

    /**
     * 判断是否为独立页码行。
     *
     * @param normalizedLine 归一化单行文本
     * @return 独立页码返回 true
     */
    private boolean looksLikeStandalonePageCounter(String normalizedLine) {
        return PAGE_COUNTER_PATTERN.matcher(normalizedLine).matches()
                || normalizedLine.matches("\\d{1,4}");
    }

    /**
     * 判断行中是否包含自然语言句读符。
     *
     * @param normalizedLine 归一化单行文本
     * @return 包含返回 true
     */
    private boolean containsSentencePunctuation(String normalizedLine) {
        return normalizedLine.contains("。")
                || normalizedLine.contains("；")
                || normalizedLine.contains("，")
                || normalizedLine.contains(". ")
                || normalizedLine.contains("; ");
    }

    /**
     * 生成用于跨页重复识别的行签名。
     *
     * @param line 原始行
     * @return 行签名
     */
    private String lineSignature(String line) {
        String normalizedLine = normalizeLine(line);
        if (normalizedLine.isBlank()) {
            return "";
        }
        normalizedLine = URL_PATTERN.matcher(normalizedLine).replaceAll("<url>");
        normalizedLine = DATE_TIME_PATTERN.matcher(normalizedLine).replaceAll("<date>");
        normalizedLine = PAGE_COUNTER_PATTERN.matcher(normalizedLine).replaceAll("<page>");
        normalizedLine = normalizedLine.replaceAll("\\d+", "<num>");
        return normalizedLine.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * 归一化单行空白。
     *
     * @param line 原始行
     * @return 归一化行
     */
    private String normalizeLine(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        return line.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
