package com.xbk.lattice.infra.chunking;

/**
 * 文本分块
 *
 * 职责：表示一次语义分块后的结果片段
 *
 * @param text 分块文本
 * @param charOffset 原始文本起始偏移
 * @param chunkIndex 分块序号
 * @author xiexu
 */
public record TextChunk(String text, int charOffset, int chunkIndex) {
}
