package com.xbk.lattice.documentparse.domain;

/**
 * 文档解析模式
 *
 * 职责：约束 RawSource 在进入编译层前的统一解析语义
 *
 * @author xiexu
 */
public enum DocumentParseMode {

    TEXT_READ("text_read"),

    OFFICE_EXTRACT("office_extract"),

    PDF_TEXT("pdf_text"),

    OCR_IMAGE("ocr_image"),

    OCR_SCANNED_PDF("ocr_scanned_pdf");

    private final String code;

    DocumentParseMode(String code) {
        this.code = code;
    }

    /**
     * 返回模式编码。
     *
     * @return 模式编码
     */
    public String getCode() {
        return code;
    }
}
