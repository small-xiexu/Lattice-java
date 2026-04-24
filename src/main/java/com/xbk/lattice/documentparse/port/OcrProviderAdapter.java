package com.xbk.lattice.documentparse.port;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.domain.model.ProviderProbeResult;

import java.io.IOException;

/**
 * OCR Provider 适配器
 *
 * 职责：隔离各 OCR / Document AI 厂商协议差异，对外暴露统一探测与解析能力
 *
 * @author xiexu
 */
public interface OcrProviderAdapter {

    /**
     * 判断当前适配器是否支持指定 Provider。
     *
     * @param providerType Provider 类型
     * @return 是否支持
     */
    boolean supportsProvider(String providerType);

    /**
     * 判断当前适配器是否支持指定能力。
     *
     * @param parseCapability 解析能力
     * @return 是否支持
     */
    boolean supportsCapability(ParseCapability parseCapability);

    /**
     * 探测连接是否可用。
     *
     * @param connection 连接配置
     * @return 探测结果
     */
    ProviderProbeResult probe(ProviderConnection connection);

    /**
     * 调用 OCR Provider 解析文件。
     *
     * @param connection 连接配置
     * @param parseCapability 解析能力
     * @param parseRequest 解析请求
     * @return 解析输出
     * @throws IOException IO 异常
     */
    ParseOutput parse(
            ProviderConnection connection,
            ParseCapability parseCapability,
            ParseRequest parseRequest
    ) throws IOException;
}
