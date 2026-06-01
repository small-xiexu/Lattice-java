package com.xbk.lattice.documentparse.domain;

/**
 * 文档解析模式。
 *
 * <p>约束 RawSource 在进入编译层前的统一解析语义，决定下游编译消费路径。
 *
 * @author xiexu
 */
public enum DocumentParseMode {

    /** 纯文本读取（适用于 md / txt / 代码文件等文本格式）。 */
    TEXT_READ("text_read"),

    /** Office 文档提取（适用于 docx / doc / xlsx / pptx）。 */
    OFFICE_EXTRACT("office_extract"),

    /** PDF 直接文本提取（适用于非扫描版 PDF，无需 OCR）。 */
    PDF_TEXT("pdf_text"),

    /** 图片 OCR（适用于 png / jpg / bmp 等图片格式）。 */
    OCR_IMAGE("ocr_image"),

    /** 扫描 PDF OCR（适用于扫描版 PDF，需先转为图片再做 OCR）。 */
    OCR_SCANNED_PDF("ocr_scanned_pdf");

    private final String code;

    DocumentParseMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
