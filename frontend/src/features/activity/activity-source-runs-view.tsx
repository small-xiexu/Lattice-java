import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";

import { activityApi } from "../../api/contracts/activity";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { SourceTaskDetail } from "./activity-detail";
import {
  createAdaptivePollingState,
  resolveAdaptivePollingInterval,
} from "./activity-polling";
import {
  ActivityLayout,
  ActivityList,
  ActivityToolbar,
  DetailPlaceholder,
  LimitField,
} from "./activity-shared";
import { resolveActivityError } from "./activity-utils";

interface ActivitySourceRunsViewProps {
  limit: number;
  selectedId: string | null;
  onLimitChange: (value: number) => void;
  onSelect: (id: string) => void;
}

export function ActivitySourceRunsView({
  limit,
  selectedId,
  onLimitChange,
  onSelect,
}: ActivitySourceRunsViewProps) {
  const listPolling = useRef(createAdaptivePollingState());
  const detailPolling = useRef(createAdaptivePollingState());
  const runId = parseRunId(selectedId);
  const listQuery = useQuery({
    queryKey: queryKeys.activity.sourceRuns({ limit }),
    queryFn: ({ signal }) => activityApi.listSourceRuns(limit, signal),
    refetchInterval: (state) => {
      const data = state.state.data;
      const active = data?.some((run) => run.processingActive) ?? false;
      const signature = data?.map((run) => `${run.runId}:${run.displayStatus}:${run.progressText ?? ""}`).join("|") ?? "";
      return resolveAdaptivePollingInterval(active, signature, listPolling.current);
    },
  });
  const detailQuery = useQuery({
    enabled: runId !== null,
    queryKey: queryKeys.activity.detail("source-run", runId ?? "invalid"),
    queryFn: ({ signal }) => activityApi.getSourceRun(runId!, signal),
    refetchInterval: (state) => {
      const data = state.state.data;
      const signature = data ? `${data.displayStatus}:${data.progressText ?? ""}:${data.updatedAt ?? ""}` : "";
      return resolveAdaptivePollingInterval(data?.processingActive ?? false, signature, detailPolling.current);
    },
  });

  return (
    <>
      <ActivityToolbar count={listQuery.data?.length}>
        <LimitField onChange={onLimitChange} value={limit} />
      </ActivityToolbar>
      {listQuery.isPending ? <PageState status="loading" title="正在加载同步运行" /> : null}
      {listQuery.isError ? (
        <PageState actionLabel="重试" description={resolveActivityError(listQuery.error)} onAction={() => void listQuery.refetch()} status="error" title="同步运行加载失败" />
      ) : null}
      {listQuery.data ? (
        <ActivityLayout
          list={listQuery.data.length ? (
            <ActivityList
              entries={listQuery.data.map((run) => ({
                id: String(run.runId),
                title: run.sourceName || `同步运行 #${run.runId}`,
                meta: `#${run.runId} · ${run.sourceType || "未知来源"}`,
                status: run.displayStatusLabel,
                tone: run.displayTone,
                progress: run.progressText,
                time: run.updatedAt,
              }))}
              label="同步运行列表"
              onSelect={onSelect}
              selectedId={selectedId}
            />
          ) : <PageState status="empty" title="暂无同步运行" />}
          detail={resolveDetail()}
        />
      ) : null}
    </>
  );

  function resolveDetail() {
    if (runId === null) return <DetailPlaceholder title={selectedId ? "同步运行标识无效" : "选择一次同步运行查看详情"} />;
    if (detailQuery.isPending) return <PageState status="loading" title="正在加载同步运行详情" />;
    if (detailQuery.isError) {
      return <PageState actionLabel="重试" description={resolveActivityError(detailQuery.error)} onAction={() => void detailQuery.refetch()} status="error" title="同步运行详情加载失败" />;
    }
    return detailQuery.data ? <SourceTaskDetail task={detailQuery.data} /> : <DetailPlaceholder title="选择一次同步运行查看详情" />;
  }
}

function parseRunId(value: string | null) {
  if (!value) return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}
