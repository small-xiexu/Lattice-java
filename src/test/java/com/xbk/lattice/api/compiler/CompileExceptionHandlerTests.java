package com.xbk.lattice.api.compiler;

import com.xbk.lattice.source.service.SourceSyncConflictException;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileExceptionHandler 测试
 *
 * 职责：验证上传超限与普通非法状态的异常映射
 *
 * @author xiexu
 */
class CompileExceptionHandlerTests {

    /**
     * 验证单文件超限时返回 413 与友好文案。
     */
    @Test
    void shouldReturnPayloadTooLargeWhenSingleFileExceedsLimit() {
        CompileExceptionHandler handler = new CompileExceptionHandler(createMultipartProperties());
        FileSizeLimitExceededException cause = new FileSizeLimitExceededException("too large", 2 * 1024L * 1024L, 20 * 1024L * 1024L);
        IllegalStateException exception = new IllegalStateException("multipart parse failed", cause);

        ResponseEntity<CompileErrorResponse> response = handler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMPILE_UPLOAD_TOO_LARGE");
        assertThat(response.getBody().getMessage()).isEqualTo("上传文件过大：单个文件不能超过 20 MB，请拆分后重试");
    }

    /**
     * 验证请求总大小超限时返回 413 与总量提示。
     */
    @Test
    void shouldReturnPayloadTooLargeWhenRequestExceedsLimit() {
        CompileExceptionHandler handler = new CompileExceptionHandler(createMultipartProperties());
        SizeLimitExceededException cause = new SizeLimitExceededException("too large", 120 * 1024L * 1024L, 100 * 1024L * 1024L);
        IllegalStateException exception = new IllegalStateException("multipart parse failed", cause);

        ResponseEntity<CompileErrorResponse> response = handler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMPILE_UPLOAD_TOO_LARGE");
        assertThat(response.getBody().getMessage()).isEqualTo("上传内容过大：本次请求总大小不能超过 100 MB，请减少文件数量后重试");
    }

    /**
     * 验证普通非法状态仍然按 500 返回。
     */
    @Test
    void shouldKeepInternalServerErrorForOtherIllegalState() {
        CompileExceptionHandler handler = new CompileExceptionHandler(createMultipartProperties());
        IllegalStateException exception = new IllegalStateException("compile step failed");

        ResponseEntity<CompileErrorResponse> response = handler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMPILE_EXECUTION_FAILED");
        assertThat(response.getBody().getMessage()).isEqualTo("compile step failed");
    }

    /**
     * 验证资料源活动运行冲突会映射为 409。
     */
    @Test
    void shouldReturnConflictForSourceSyncConflict() {
        CompileExceptionHandler handler = new CompileExceptionHandler(createMultipartProperties());
        SourceSyncConflictException exception = new SourceSyncConflictException("active source sync run already exists: 12");

        ResponseEntity<CompileErrorResponse> response = handler.handleSourceSyncConflictException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SOURCE_SYNC_CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("active source sync run already exists: 12");
    }

    /**
     * 创建测试用 multipart 配置。
     *
     * @return multipart 配置
     */
    private MultipartProperties createMultipartProperties() {
        MultipartProperties multipartProperties = new MultipartProperties();
        multipartProperties.setMaxFileSize(DataSize.ofMegabytes(20));
        multipartProperties.setMaxRequestSize(DataSize.ofMegabytes(100));
        return multipartProperties;
    }
}
