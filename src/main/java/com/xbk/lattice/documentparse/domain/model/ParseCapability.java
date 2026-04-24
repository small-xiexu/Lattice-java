package com.xbk.lattice.documentparse.domain.model;

/**
 * 解析能力类型
 *
 * 职责：约束 OCR Provider 当前可承接的文档解析能力
 *
 * @author xiexu
 */
public enum ParseCapability {

    IMAGE_OCR,

    SCANNED_PDF_OCR
}
