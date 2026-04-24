package com.xbk.lattice.documentparse.port;

import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;

import java.io.IOException;

/**
 * 本地抽取器
 *
 * 职责：为文本、PDF、Office 等本地可直接抽取的文件提供统一抽取接口
 *
 * @author xiexu
 */
public interface NativeExtractor {

    /**
     * 判断当前抽取器是否支持指定格式。
     *
     * @param format 文件格式
     * @return 是否支持
     */
    boolean supports(String format);

    /**
     * 执行本地抽取。
     *
     * @param parseRequest 解析请求
     * @return 解析输出；无可用正文时返回 null
     * @throws IOException IO 异常
     */
    ParseOutput extract(ParseRequest parseRequest) throws IOException;
}
