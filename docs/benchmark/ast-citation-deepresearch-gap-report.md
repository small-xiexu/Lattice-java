# AST / Citation / Deep Research 对标报告

- 数据集版本：2026-04-24
- Java runner：java-shadow
- TS runner：ts-shadow

## 对标结论

- unsupportedClaimRate：Java=0.000，TS=0.200
- citationPrecision：Java=1.000，TS=0.750
- citationCoverage：Java=1.000，TS=0.800
- multiHopCompleteness：Java=1.000，TS=0.889
- graphFactAcceptedRate：Java=1.000（原版无同构 shadow 指标）
- 是否满足“超过原版”门槛：是

## 逐题差异

| 场景 | 类型 | unsupported Δ | precision Δ | coverage Δ | multi-hop Δ | gaps |
| --- | --- | --- | --- | --- | --- | --- |
| citation_verified_article | citation | 0.000 | 0.000 | 0.000 | N/A | - |
| citation_demoted_missing_literal | citation | -1.000 | 1.000 | 1.000 | N/A | - |
| citation_verified_source_file | citation | 0.000 | N/A | 0.000 | N/A | - |
| citation_skip_general_claim | citation | 0.000 | N/A | 0.000 | N/A | - |
| deepresearch_complete_coverage | deepresearch | 0.000 | 0.000 | 0.000 | 0.000 | - |
| deepresearch_conflict_surfaced | deepresearch | 0.000 | N/A | 1.000 | 0.000 | - |
| deepresearch_ts_grounding_loss | deepresearch | -0.500 | 0.500 | 0.500 | 0.000 | - |
| deepresearch_ts_miss_one_subclaim | deepresearch | 0.000 | 0.000 | 0.000 | 0.333 | - |

## Top Gaps

- 当前共享题集上未发现 Java 相比原版退化的 Top gap。

## Graph 备注

- graph_routeplanner_grounding：accepted=2/2，top_hit=图谱实体：payment.routing.RoutePlanner；factsBlock=- 图谱实体：payment.routing.RoutePlanner: 实体=payment.routing.RoutePlanner；annotation=@RequestMapping；calls->payment.PaymentService.plan
