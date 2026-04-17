package com.xbk.lattice.infra.chunking;

import java.util.Objects;

/**
 * 文本分块
 *
 * 职责：表示一次语义分块后的结果片段
 *
 * @author xiexu
 */
public final class TextChunk {

    private final String text;

    private final int charOffset;

    private final int chunkIndex;

    /**
     * 创建文本分块。
     *
     * @param text 分块文本
     * @param charOffset 原始文本起始偏移
     * @param chunkIndex 分块序号
     */
    public TextChunk(String text, int charOffset, int chunkIndex) {
        this.text = text;
        this.charOffset = charOffset;
        this.chunkIndex = chunkIndex;
    }

    /**
     * 返回分块文本。
     *
     * @return 分块文本
     */
    public String getText() {
        return text;
    }

    /**
     * 返回原始文本起始偏移。
     *
     * @return 起始偏移
     */
    public int getCharOffset() {
        return charOffset;
    }

    /**
     * 返回分块序号。
     *
     * @return 分块序号
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 比较文本分块是否相等。
     *
     * @param other 另一对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextChunk)) {
            return false;
        }
        TextChunk textChunk = (TextChunk) other;
        return charOffset == textChunk.charOffset
                && chunkIndex == textChunk.chunkIndex
                && Objects.equals(text, textChunk.text);
    }

    /**
     * 返回哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(text, charOffset, chunkIndex);
    }

    /**
     * 返回文本分块描述。
     *
     * @return 文本描述
     */
    @Override
    public String toString() {
        return "TextChunk{"
                + "text='" + text + '\''
                + ", charOffset=" + charOffset
                + ", chunkIndex=" + chunkIndex
                + '}';
    }
}
