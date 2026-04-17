package com.xbk.lattice.infra.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 语义分块器
 *
 * 职责：按 Markdown 语义边界对文本进行分块，避免在代码围栏中间截断
 *
 * @author xiexu
 */
public class SemanticChunker {

    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+\\.\\s+.*$");

    /**
     * 执行语义分块。
     *
     * @param content 原始文本
     * @param maxChars 每块最大字符数
     * @param overlapRatio 重叠比例
     * @return 分块结果
     */
    public List<TextChunk> chunk(String content, int maxChars, float overlapRatio) {
        List<TextUnit> units = parseUnits(content);
        List<TextChunk> chunks = new ArrayList<TextChunk>();
        if (units.isEmpty()) {
            return chunks;
        }

        int chunkIndex = 0;
        int unitIndex = 0;
        int overlapChars = Math.max(0, Math.round(maxChars * overlapRatio));
        while (unitIndex < units.size()) {
            int startIndex = unitIndex;
            int chunkStartOffset = units.get(startIndex).getCharOffset();
            StringBuilder builder = new StringBuilder();

            while (unitIndex < units.size()) {
                TextUnit unit = units.get(unitIndex);
                if (builder.length() > 0 && builder.length() + unit.getText().length() > maxChars) {
                    break;
                }
                builder.append(unit.getText());
                unitIndex++;
            }

            if (builder.length() == 0) {
                TextUnit unit = units.get(unitIndex);
                builder.append(unit.getText());
                unitIndex++;
            }

            chunks.add(new TextChunk(builder.toString().trim(), chunkStartOffset, chunkIndex));
            chunkIndex++;
            if (unitIndex >= units.size()) {
                break;
            }
            unitIndex = rewindForOverlap(units, startIndex, unitIndex, overlapChars);
        }
        return chunks;
    }

    private List<TextUnit> parseUnits(String content) {
        List<TextUnit> units = new ArrayList<TextUnit>();
        if (isBlank(content)) {
            return units;
        }
        String[] lines = content.split("\\R", -1);
        StringBuilder current = new StringBuilder();
        int currentStartOffset = 0;
        int offset = 0;
        boolean inCodeBlock = false;

        for (String line : lines) {
            String lineWithBreak = line + "\n";
            String trimmed = line.trim();
            boolean isCodeFence = trimmed.startsWith("```");
            boolean isHeading = isHeading(trimmed);
            boolean isBlank = trimmed.isEmpty();

            if (isCodeFence && !inCodeBlock) {
                flushUnit(units, current, currentStartOffset);
                currentStartOffset = offset;
                current.append(lineWithBreak);
                inCodeBlock = true;
            }
            else if (inCodeBlock) {
                current.append(lineWithBreak);
                if (isCodeFence) {
                    inCodeBlock = false;
                    flushUnit(units, current, currentStartOffset);
                }
            }
            else if (isHeading) {
                flushUnit(units, current, currentStartOffset);
                currentStartOffset = offset;
                current.append(lineWithBreak);
            }
            else if (isBlank) {
                current.append(lineWithBreak);
                flushUnit(units, current, currentStartOffset);
            }
            else {
                if (current.length() == 0) {
                    currentStartOffset = offset;
                }
                current.append(lineWithBreak);
            }
            offset += lineWithBreak.length();
        }

        flushUnit(units, current, currentStartOffset);
        return units;
    }

    private int rewindForOverlap(List<TextUnit> units, int startIndex, int nextIndex, int overlapChars) {
        if (overlapChars <= 0) {
            return nextIndex;
        }
        int accumulated = 0;
        int rewindIndex = nextIndex;
        for (int index = nextIndex - 1; index >= startIndex; index--) {
            accumulated += units.get(index).getText().length();
            rewindIndex = index;
            if (accumulated >= overlapChars) {
                break;
            }
        }
        if (rewindIndex >= nextIndex) {
            return nextIndex;
        }
        return Math.max(startIndex + 1, rewindIndex);
    }

    private void flushUnit(List<TextUnit> units, StringBuilder builder, int startOffset) {
        String text = builder.toString();
        if (!isBlank(text)) {
            units.add(new TextUnit(text, startOffset));
        }
        builder.setLength(0);
    }

    private boolean isHeading(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.startsWith("#")
                || trimmed.startsWith("---")
                || trimmed.startsWith("- ")
                || trimmed.startsWith("* ")
                || ORDERED_LIST_PATTERN.matcher(trimmed).matches();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class TextUnit {

        private final String text;

        private final int charOffset;

        private TextUnit(String text, int charOffset) {
            this.text = text;
            this.charOffset = charOffset;
        }

        private String getText() {
            return text;
        }

        private int getCharOffset() {
            return charOffset;
        }
    }
}
