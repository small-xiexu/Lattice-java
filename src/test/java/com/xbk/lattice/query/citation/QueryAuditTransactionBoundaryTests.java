package com.xbk.lattice.query.citation;

import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询审计事务边界测试
 *
 * 职责：验证查询答案审计公开写入口均声明事务边界
 *
 * @author xiexu
 */
class QueryAuditTransactionBoundaryTests {

    /**
     * 验证 Deep Research 复用的答案审计入口声明事务。
     *
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    @Test
    void shouldDeclareTransactionOnDeepResearchAnswerAuditEntry() throws NoSuchMethodException {
        Method method = QueryAnswerAuditPersistenceService.class.getDeclaredMethod(
                "persist",
                String.class,
                int.class,
                String.class,
                String.class,
                AnswerOutcome.class,
                GenerationMode.class,
                String.class,
                boolean.class,
                String.class,
                CitationCheckReport.class,
                Long.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }
}
