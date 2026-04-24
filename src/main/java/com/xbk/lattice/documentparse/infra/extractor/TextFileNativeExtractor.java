package com.xbk.lattice.documentparse.infra.extractor;

import com.xbk.lattice.documentparse.domain.DocumentParseMode;
import com.xbk.lattice.documentparse.domain.model.ParseOutput;
import com.xbk.lattice.documentparse.domain.model.ParseRequest;
import com.xbk.lattice.documentparse.port.NativeExtractor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文本文件本地抽取器
 *
 * 职责：处理纯文本类文件的直读与统一结果组装
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class TextFileNativeExtractor implements NativeExtractor {

    private static final Set<String> SUPPORTED_FORMATS = new HashSet<String>(Arrays.asList(
            "md", "txt", "markdown", "csv", "java", "xml", "properties",
            "yml", "yaml", "json", "vue", "js", "css", "html", "sh", "py"
    ));

    /**
     * 判断当前抽取器是否支持指定格式。
     *
     * @param format 文件格式
     * @return 是否支持
     */
    @Override
    public boolean supports(String format) {
        String normalizedFormat = normalize(format);
        return SUPPORTED_FORMATS.contains(normalizedFormat);
    }

    /**
     * 执行文本文件抽取。
     *
     * @param parseRequest 解析请求
     * @return 解析输出；无可用正文时返回 null
     * @throws IOException IO 异常
     */
    @Override
    public ParseOutput extract(ParseRequest parseRequest) throws IOException {
        String content = Files.readString(parseRequest.getFilePath(), StandardCharsets.UTF_8);
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            return null;
        }
        return new ParseOutput(
                null,
                parseRequest.getRelativePath(),
                normalizedContent,
                "",
                "",
                parseRequest.getFormat(),
                parseRequest.getFileSize(),
                DocumentParseMode.TEXT_READ,
                "filesystem",
                "{}",
                false,
                parseRequest.getRelativePath()
        );
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
