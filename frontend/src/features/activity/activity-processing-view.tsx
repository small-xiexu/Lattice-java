import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { useNavigate } from "react-router-dom";

import {
  activityApi,
  type ProcessingTaskStatus,
} from "../../api/contracts/activity";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageState } from "../../components/page-state";
import { SourceTaskDetail } from "./activity-detail";
import {
  createAdaptivePollingState,
  resolveAdaptivePollingInterval,
} from "./activity-polling";
import { resolveActivityError } from "./activity-utils";
import {
  ActivityLayout,
  ActivityList,
  ActivityToolbar,
  DetailPlaceholder,
  LimitField,
} from "./activity-shared";

interface ActivityProcessingViewProps {
  limit: number;
  status: ProcessingTaskStatus;
  selectedId: string | null;
  onLimitChange: (value: number) => void;
  onStatusChange: (value: ProcessingTaskStatus) => void;
  onSelect: (id: string) => void;
}

export function ActivityProcessingView({
  limit,
  status,
  selectedId,
  onLimitChange,
  onStatusChange,
  onSelect,
}: ActivityProcessingViewProps) {
  const navigate = useNavigate();
  const polling = useRef(createAdaptivePollingState());
  const filters = { limit, status };
  const query = useQuery({
    queryKey: queryKeys.activity.processingTasks(filters),
    queryFn: ({ signal }) =>
      activityApi.listProcessingTasks({ limit, status, signal }),
    refetchInterval: (state) => {
      const data = state.state.data;
      const active = data?.items.some((item) => item.processingActive) ?? false;
      const signature = data?.items
        .map((item) => `${item.taskId}:${item.displayStatus}:${item.progressText ?? ""}`)
        .join("|") ?? "";
      return resolveAdaptivePollingInterval(active, signature, polling.current);
    },
  });
  const selected = query.data?.items.find((item) => item.taskId === selectedId);

  return (
    <>
      <ActivityToolbar count={query.data?.items.length}>
        <label className="filter-field">
          <span>状态</span>
          <select
            onChange={(event) => onStatusChange(event.target.value as ProcessingTaskStatus)}
            value={status}
          >
            <option value="all">全部</option>
            <option value="active">进行中</option>
            <option value="terminal">已结束</option>
          </select>
        </label>
        <LimitField onChange={onLimitChange} value={limit} />
      </ActivityToolbar>

      {query.data?.summary.helpState ? (
        <InlineAlert
          actionLabel={query.data.summary.helpState.actions.some((action) => action.action === "knowledge-upload")
            ? "打开资料导入"
            : undefined}
          description={query.data.summary.helpState.description}
          onAction={query.data.summary.helpState.actions.some((action) => action.action === "knowledge-upload")
            ? () => navigate("/library/sources")
            : undefined}
          title={query.data.summary.helpState.title}
          tone={normalizeAlertTone(query.data.summary.helpState.tone)}
        />
      ) : null}

      {query.isPending ? <PageState status="loading" title="正在加载处理任务" /> : null}
      {query.isError ? (
        <PageState
          actionLabel="重试"
          description={resolveActivityError(query.error)}
          onAction={() => void query.refetch()}
          status="error"
          title="处理任务加载失败"
        />
      ) : null}
      {query.data ? (
        <ActivityLayout
          list={query.data.items.length ? (
            <>
              <div aria-label="任务汇总" className="activity-summary-strip">
                <div><span>运行中</span><strong>{query.data.summary.runningCount}</strong></div>
                <div><span>待确认</span><strong>{query.data.summary.waitingCount}</strong></div>
                <div><span>疑似卡住</span><strong>{query.data.summary.stalledCount}</strong></div>
                <div><span>已完成</span><strong>{query.data.summary.succeededCount}</strong></div>
                <div><span>失败</span><strong>{query.data.summary.failedCount}</strong></div>
              </div>
              <ActivityList
                entries={query.data.items.map((item) => ({
                  id: item.taskId,
                  title: item.title,
                  meta: item.taskType === "SOURCE_SYNC" ? "资料同步" : "独立编译",
                  status: item.displayStatusLabel,
                  tone: item.displayTone,
                  progress: item.progressText,
                  time: item.updatedAt,
                }))}
                label="处理任务列表"
                onSelect={onSelect}
                selectedId={selectedId}
              />
            </>
          ) : <PageState status="empty" title="当前筛选下没有处理任务" />}
          detail={selected
            ? <SourceTaskDetail task={selected} />
            : <DetailPlaceholder title={selectedId ? "当前列表中没有该任务" : "选择一个任务查看步骤和恢复操作"} />}
        />
      ) : null}
    </>
  );
}

function normalizeAlertTone(tone: string): "info" | "success" | "warning" | "error" {
  if (tone === "success" || tone === "warning" || tone === "info") return tone;
  return "error";
}
