package com.xbk.lattice.documentparse.extractor;

/**
 * 源文件抽取结果
 *
 * 职责：承载 PDF/Excel 等非纯文本文件抽取后的正文与元数据
 *
 * @author xiexu
 */
public class SourceExtractionResult {

    private final String content;

    private final String metadataJson;

    private final boolean verbatim;

    /**
     * 创建源文件抽取结果。
     *
     * @param content 抽取后的正文
     * @param metadataJson 元数据 JSON
     * @param verbatim 是否按原文保留
     */
    public SourceExtractionResult(String content, String metadataJson, boolean verbatim) {
        this.content = content;
        this.metadataJson = metadataJson;
        this.verbatim = verbatim;
    }

    /**
     * 获取抽取后的正文。
     *
     * @return 抽取后的正文
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取元数据 JSON。
     *
     * @return 元数据 JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 是否按原文保留。
     *
     * @return 是否按原文保留
     */
    public boolean isVerbatim() {
        return verbatim;
    }
}
