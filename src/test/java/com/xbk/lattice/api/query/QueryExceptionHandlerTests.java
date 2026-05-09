package com.xbk.lattice.api.query;

import com.xbk.lattice.query.error.EvidenceConflictException;
import com.xbk.lattice.query.error.NoEvidenceException;
import com.xbk.lattice.query.error.QueryExecutionException;
import com.xbk.lattice.query.error.QueryReviewTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryExceptionHandler 测试
 *
 * 职责：验证 query 子域领域异常会映射为独立错误响应
 *
 * @author xiexu
 */
class QueryExceptionHandlerTests {

    /**
     * 验证非法查询请求返回 query 专属错误码。
     */
    @Test
    void shouldReturnQueryRequestInvalidForIllegalArgument() {
        QueryExceptionHandler handler = new QueryExceptionHandler();
        IllegalArgumentException exception = new IllegalArgumentException("question 不能为空");

        ResponseEntity<QueryErrorResponse> response = handler.handleIllegalArgumentException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("QUERY_REQUEST_INVALID");
        assertThat(response.getBody().getMessage()).isEqualTo("question 不能为空");
    }

    /**
     * 验证无证据异常返回 422。
     */
    @Test
    void shouldReturnUnprocessableEntityForNoEvidence() {
        QueryExceptionHandler handler = new QueryExceptionHandler();
        NoEvidenceException exception = new NoEvidenceException("query graph did not produce final response");

        ResponseEntity<QueryErrorResponse> response = handler.handleNoEvidenceException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("QUERY_NO_EVIDENCE");
        assertThat(response.getBody().getMessage()).isEqualTo("query graph did not produce final response");
    }

    /**
     * 验证证据冲突异常返回 409。
     */
    @Test
    void shouldReturnConflictForEvidenceConflict() {
        QueryExceptionHandler handler = new QueryExceptionHandler();
        EvidenceConflictException exception = new EvidenceConflictException("当前请求未命中 Deep Research 路由");

        ResponseEntity<QueryErrorResponse> response = handler.handleEvidenceConflictException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("QUERY_EVIDENCE_CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("当前请求未命中 Deep Research 路由");
    }

    /**
     * 验证审查超时异常返回 504。
     */
    @Test
    void shouldReturnGatewayTimeoutForReviewTimeout() {
        QueryExceptionHandler handler = new QueryExceptionHandler();
        QueryReviewTimeoutException exception = new QueryReviewTimeoutException("review timed out");

        ResponseEntity<QueryErrorResponse> response = handler.handleQueryReviewTimeoutException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("QUERY_REVIEW_TIMEOUT");
        assertThat(response.getBody().getMessage()).isEqualTo("review timed out");
    }

    /**
     * 验证查询集成异常返回 503。
     */
    @Test
    void shouldReturnServiceUnavailableForIntegrationException() {
        QueryExceptionHandler handler = new QueryExceptionHandler();
        QueryExecutionException exception = new QueryExecutionException(
                "query graph execute failed",
                new IllegalStateException("graph failed")
        );

        ResponseEntity<QueryErrorResponse> response = handler.handleLatticeIntegrationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("QUERY_EXECUTION_FAILED");
        assertThat(response.getBody().getMessage()).isEqualTo("query graph execute failed");
    }
}
