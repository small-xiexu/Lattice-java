package com.xbk.lattice.api.compiler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        log.error("Compile failed due to illegal state", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CompileErrorResponse("COMPILE_EXECUTION_FAILED", ex.getMessage()));
    }
}
