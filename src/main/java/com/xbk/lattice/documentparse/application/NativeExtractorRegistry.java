package com.xbk.lattice.documentparse.application;

import com.xbk.lattice.documentparse.port.NativeExtractor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 本地抽取器注册表
 *
 * 职责：根据文件格式为解析编排层定位可用的本地抽取器
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class NativeExtractorRegistry {

    private final List<NativeExtractor> nativeExtractors;

    /**
     * 创建本地抽取器注册表。
     *
     * @param nativeExtractors 本地抽取器集合
     */
    public NativeExtractorRegistry(List<NativeExtractor> nativeExtractors) {
        this.nativeExtractors = nativeExtractors;
    }

    /**
     * 按格式查找本地抽取器。
     *
     * @param format 文件格式
     * @return 本地抽取器
     */
    public Optional<NativeExtractor> findExtractor(String format) {
        String normalizedFormat = normalize(format);
        for (NativeExtractor nativeExtractor : nativeExtractors) {
            if (nativeExtractor.supports(normalizedFormat)) {
                return Optional.of(nativeExtractor);
            }
        }
        return Optional.empty();
    }

    /**
     * 规范化文件格式。
     *
     * @param format 文件格式
     * @return 规范化结果
     */
    private String normalize(String format) {
        return format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
    }
}
