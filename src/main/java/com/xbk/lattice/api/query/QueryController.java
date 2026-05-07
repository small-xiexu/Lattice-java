package com.xbk.lattice.api.query;

import com.xbk.lattice.query.service.QueryFacadeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询控制器
 *
 * 职责：暴露最小知识查询入口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/query")
public class QueryController {

    private final QueryFacadeService queryFacadeService;

    /**
     * 创建查询控制器。
     *
     * @param queryFacadeService 查询门面服务
     */
    public QueryController(QueryFacadeService queryFacadeService) {
        this.queryFacadeService = queryFacadeService;
    }

    /**
     * 执行最小知识查询。
     *
     * @param queryRequest 查询请求
     * @return 查询响应
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public QueryResponse query(@Valid @RequestBody QueryRequest queryRequest) {
        return queryFacadeService.query(queryRequest);
    }
}
