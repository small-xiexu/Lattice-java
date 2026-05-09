/**
 * 可观测性横切子域
 *
 * 职责：集中维护 trace 上下文、结构化日志输出与日志装运入口。
 *
 * <p>审计明细按业务子域就近维护：编译任务状态在 compiler/infra 持久化，
 * 查询检索审计在 query 子域，Deep Research 审计在 query/deepresearch 子域，
 * 治理历史与快照审计在 governance 子域。</p>
 *
 * @author xiexu
 */
package com.xbk.lattice.observability;
