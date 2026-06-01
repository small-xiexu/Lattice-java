package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 源文件批次。
 *
 * <p>表示同一分组内按字符数切分后的批次结果。{@code batchId} 唯一标识批次，
 * {@code groupKey} 标识归属分组，用于编译并发调度和进度追踪。
 *
 * @author xiexu
 */
@Getter
public class SourceBatch {

    /** 批次标识（唯一）。 */
    private final String batchId;

    /** 分组键。同一 groupKey 的批次在相同分组内处理。 */
    private final String groupKey;

    /** 源文件集合。 */
    private final List<RawSource> sources;

    @JsonCreator
    public SourceBatch(
            @JsonProperty("batchId") String batchId,
            @JsonProperty("groupKey") String groupKey,
            @JsonProperty("sources") List<RawSource> sources
    ) {
        this.batchId = batchId;
        this.groupKey = groupKey;
        this.sources = sources;
    }
}
