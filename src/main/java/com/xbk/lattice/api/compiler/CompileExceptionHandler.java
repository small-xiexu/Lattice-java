package com.xbk.lattice.api.compiler;

import com.xbk.lattice.source.service.SourceSyncConflictException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;

/**
 * 编译异常处理器
 *
 * 职责：为编译接口输出可读错误响应并记录关键失败日志
 *
 * @author xiexu
 */
@RestControllerAdvice
@Slf4j
public class CompileExceptionHandler {

    private final MultipartProperties multipartProperties;

    /**
     * 创建编译异常处理器。
     *
     * @param multipartProperties multipart 上传配置
     */
    public CompileExceptionHandler(MultipartProperties multipartProperties) {
        this.multipartProperties = multipartProperties;
    }

    /**
     * 处理非法编译请求。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CompileErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Compile request rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CompileErrorResponse("COMPILE_REQUEST_INVALID", ex.getMessage()));
    }

    /**
     * 处理资料源同步并发冲突。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(SourceSyncConflictException.class)
    public ResponseEntity<CompileErrorResponse> handleSourceSyncConflictException(SourceSyncConflictException ex) {
        log.warn("Compile request rejected due to source sync conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new CompileErrorResponse("SOURCE_SYNC_CONFLICT", ex.getMessage()));
    }

    /**
     * 处理上传体积超限异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CompileErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return buildUploadTooLargeResponse(ex);
    }

    /**
     * 处理 multipart 解析异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<CompileErrorResponse> handleMultipartException(MultipartException ex) {
        if (isUploadSizeExceeded(ex)) {
            return buildUploadTooLargeResponse(ex);
        }
        log.warn("Compile request rejected due to multipart exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CompileErrorResponse("COMPILE_REQUEST_INVALID", "上传请求解析失败，请检查文件后重试"));
    }

    /**
     * 处理编译过程中发生的 IO 异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<CompileErrorResponse> handleIoException(IOException ex) {
        log.error("Compile failed due to IO exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CompileErrorResponse("COMPILE_IO_ERROR", "编译过程中发生 IO 异常"));
    }

    /**
     * 处理编译过程中的运行时异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CompileErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        if (isUploadSizeExceeded(ex)) {
            return buildUploadTooLargeResponse(ex);
        }
        log.error("Compile failed due to illegal state", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CompileErrorResponse("COMPILE_EXECUTION_FAILED", ex.getMessage()));
    }

    /**
     * 构建上传体积超限响应。
     *
     * @param ex 异常
     * @return 错误响应
     */
    private ResponseEntity<CompileErrorResponse> buildUploadTooLargeResponse(Throwable ex) {
        String message = buildUploadTooLargeMessage(ex);
        log.warn("Compile upload rejected because payload is too large: {}", message);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new CompileErrorResponse("COMPILE_UPLOAD_TOO_LARGE", message));
    }

    /**
     * 判断异常是否由上传体积超限引起。
     *
     * @param ex 异常
     * @return 是否为上传体积超限
     */
    private boolean isUploadSizeExceeded(Throwable ex) {
        return findCause(ex, FileSizeLimitExceededException.class) != null
                || findCause(ex, SizeLimitExceededException.class) != null
                || ex instanceof MaxUploadSizeExceededException;
    }

    /**
     * 生成上传体积超限的用户可读文案。
     *
     * @param ex 异常
     * @return 用户可读文案
     */
    private String buildUploadTooLargeMessage(Throwable ex) {
        FileSizeLimitExceededException fileSizeException = findCause(ex, FileSizeLimitExceededException.class);
        if (fileSizeException != null) {
            long permittedSize = fileSizeException.getPermittedSize();
            String formattedLimit = formatDataSize(permittedSize, multipartProperties.getMaxFileSize());
            return "上传文件过大：单个文件不能超过 " + formattedLimit + "，请拆分后重试";
        }

        SizeLimitExceededException requestSizeException = findCause(ex, SizeLimitExceededException.class);
        if (requestSizeException != null) {
            long permittedSize = requestSizeException.getPermittedSize();
            String formattedLimit = formatDataSize(permittedSize, multipartProperties.getMaxRequestSize());
            return "上传内容过大：本次请求总大小不能超过 " + formattedLimit + "，请减少文件数量后重试";
        }

        if (ex instanceof MaxUploadSizeExceededException) {
            MaxUploadSizeExceededException maxUploadSizeExceededException = (MaxUploadSizeExceededException) ex;
            long permittedSize = maxUploadSizeExceededException.getMaxUploadSize();
            String formattedLimit = formatDataSize(permittedSize, multipartProperties.getMaxRequestSize());
            return "上传内容过大：本次请求总大小不能超过 " + formattedLimit + "，请减少文件数量后重试";
        }

        return "上传内容过大：已超过系统允许的大小限制，请拆分文件后重试";
    }

    /**
     * 格式化体积限制。
     *
     * @param configuredBytes 运行时异常中的体积上限
     * @param fallbackSize 配置中的默认上限
     * @return 友好的体积文案
     */
    private String formatDataSize(long configuredBytes, DataSize fallbackSize) {
        if (configuredBytes > 0) {
            return formatBytes(configuredBytes);
        }
        if (fallbackSize != null) {
            return formatBytes(fallbackSize.toBytes());
        }
        return "当前系统限制";
    }

    /**
     * 将字节数格式化为易读文案。
     *
     * @param bytes 字节数
     * @return 易读文案
     */
    private String formatBytes(long bytes) {
        long oneMb = 1024L * 1024L;
        long oneKb = 1024L;
        if (bytes >= oneMb && bytes % oneMb == 0) {
            return bytes / oneMb + " MB";
        }
        if (bytes >= oneKb && bytes % oneKb == 0) {
            return bytes / oneKb + " KB";
        }
        return bytes + " B";
    }

    /**
     * 从异常链中查找指定类型的根因。
     *
     * @param ex 异常
     * @param expectedType 目标类型
     * @param <T> 目标类型泛型
     * @return 命中的根因；若不存在则返回 null
     */
    private <T extends Throwable> T findCause(Throwable ex, Class<T> expectedType) {
        Throwable current = ex;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
