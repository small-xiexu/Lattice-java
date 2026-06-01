package com.xbk.lattice.documentparse.domain.model;

/**
 * 解析能力类型。
 *
 * <p>约束 OCR Provider 当前可承接的文档解析能力——Provider 在注册时声明支持的能力集，
 * 路由策略据此匹配 Provider。
 *
 * @author xiexu
 */
public enum ParseCapability {

    /** 图片 OCR 解析能力。 */
    IMAGE_OCR,

    /** 扫描 PDF OCR 解析能力。 */
    SCANNED_PDF_OCR
}
