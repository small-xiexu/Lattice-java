package com.xbk.lattice.api.query;

import com.xbk.lattice.query.error.EvidenceConflictException;
import com.xbk.lattice.query.error.NoEvidenceException;
import com.xbk.lattice.query.error.QueryReviewTimeoutException;
import com.xbk.lattice.shared.error.LatticeBusinessException;
import com.xbk.lattice.shared.error.LatticeIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 查询异常处理器
 *
 * 职责：为 query 子域输出统一错误响应，避免复用 compile 侧错误码
 *
 * @author xiexu
 */
@RestControllerAdvice(basePackageClasses = {
        QueryController.class,
        PendingQueryController.class,
        SearchController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class QueryExceptionHandler {

    /**
     * 处理非法查询请求。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<QueryErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Query request rejected: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "QUERY_REQUEST_INVALID", ex.getMessage());
    }

    /**
     * 处理查询无证据异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(NoEvidenceException.class)
    public ResponseEntity<QueryErrorResponse> handleNoEvidenceException(NoEvidenceException ex) {
        log.warn("Query request has no usable evidence: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 处理查询证据冲突异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(EvidenceConflictException.class)
    public ResponseEntity<QueryErrorResponse> handleEvidenceConflictException(EvidenceConflictException ex) {
        log.warn("Query request rejected due to evidence conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 处理查询审查超时异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(QueryReviewTimeoutException.class)
    public ResponseEntity<QueryErrorResponse> handleQueryReviewTimeoutException(QueryReviewTimeoutException ex) {
        log.warn("Query review timed out: {}", ex.getMessage());
        return buildResponse(HttpStatus.GATEWAY_TIMEOUT, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 处理 query 子域业务异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(LatticeBusinessException.class)
    public ResponseEntity<QueryErrorResponse> handleLatticeBusinessException(LatticeBusinessException ex) {
        log.warn("Query request rejected due to business exception: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 处理 query 子域集成异常。
     *
     * @param ex 异常
     * @return 错误响应
     */
    @ExceptionHandler(LatticeIntegrationException.class)
    public ResponseEntity<QueryErrorResponse> handleLatticeIntegrationException(LatticeIntegrationException ex) {
        log.error("Query execution failed due to integration exception", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 构建错误响应。
     *
     * @param status HTTP 状态
     * @param code 错误码
     * @param message 错误信息
     * @return 错误响应
     */
    private ResponseEntity<QueryErrorResponse> buildResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new QueryErrorResponse(code, message));
    }
}
