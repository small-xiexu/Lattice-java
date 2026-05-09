package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;
import org.springframework.stereotype.Service;

/**
 * 运行态问答状态服务
 *
 * 职责：保留运行态查询扩展点；当前不在 query 主链内做具体系统模块的关键词旁路回答。
 *
 * @author xiexu
 */
@Service
public class OperationalQueryStatusService {

    public static final String RUNTIME_STATUS_DERIVATION = "RUNTIME_STATUS";

    /**
     * 尝试直接回答运行态问题。
     *
     * @param question 用户问题
     * @return 当前不做旁路回答，固定返回 null
     */
    public QueryResponse resolve(String question) {
        return null;
    }
}
