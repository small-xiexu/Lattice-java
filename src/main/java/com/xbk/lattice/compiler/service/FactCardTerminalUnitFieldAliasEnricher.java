package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;

import java.util.List;

/**
 * Terminal unit 字段别名增强器接口。
 *
 * 职责：为 terminal unit 的 fieldAliases 提供增强能力（如 LLM 生成中文别名）。
 * 实现必须返回修改后的 records 列表，不得原地修改。
 *
 * @author xiexu
 */
public interface FactCardTerminalUnitFieldAliasEnricher {

    /**
     * 增强 terminal unit 列表的字段别名。
     *
     * @param records        terminal unit 记录（不可原地修改）
     * @param factCardRecord 所属事实卡
     * @return 增强后的记录列表
     */
    List<FactCardTerminalUnitRecord> enrich(
            List<FactCardTerminalUnitRecord> records,
            FactCardRecord factCardRecord
    );

    /**
     * 在 compile job scope 下增强字段别名。
     *
     * 有 scope 的实现应使用 scoped route resolution（routeResolutionFor）+ scoped LLM 调用（generateTextWithScope）。
     * 默认委托到无 scope 的 enrich，保持旧路径兼容。
     *
     * @param records        terminal unit 记录
     * @param factCardRecord 所属事实卡
     * @param scopeId        compile job scope
     * @return 增强后的记录列表
     */
    default List<FactCardTerminalUnitRecord> enrich(
            List<FactCardTerminalUnitRecord> records,
            FactCardRecord factCardRecord,
            String scopeId
    ) {
        return enrich(records, factCardRecord);
    }

    /**
     * No-Op 实现：原样返回 records，不做任何别名增强。
     */
    final class NoOp implements FactCardTerminalUnitFieldAliasEnricher {

        /**
         * 原样返回 terminal unit 列表。
         *
         * @param records        terminal unit 记录（不可原地修改）
         * @param factCardRecord 所属事实卡
         * @return 原始记录列表
         */
        @Override
        public List<FactCardTerminalUnitRecord> enrich(
                List<FactCardTerminalUnitRecord> records,
                FactCardRecord factCardRecord
        ) {
            return records;
        }
    }
}
