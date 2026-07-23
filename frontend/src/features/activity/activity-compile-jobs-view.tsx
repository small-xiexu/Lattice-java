import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";

import { activityApi } from "../../api/contracts/activity";
import { queryKeys } from "../../api/query-keys";
import { PageState } from "../../components/page-state";
import { CompileJobDetail } from "./activity-detail";
import {
  createAdaptivePollingState,
  isCompileJobActive,
  resolveAdaptivePollingInterval,
} from "./activity-polling";
import {
  ActivityLayout,
  ActivityList,
  ActivityToolbar,
  DetailPlaceholder,
} from "./activity-shared";
import { resolveActivityError } from "./activity-utils";

interface ActivityCompileJobsViewProps {
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function ActivityCompileJobsView({
  selectedId,
  onSelect,
}: ActivityCompileJobsViewProps) {
  const listPolling = useRef(createAdaptivePollingState());
  const detailPolling = useRef(createAdaptivePollingState());
  const listQuery = useQuery({
    queryKey: queryKeys.activity.compileJobs,
    queryFn: ({ signal }) => activityApi.listCompileJobs(signal),
    refetchInterval: (state) => {
      const data = state.state.data;
      const active = data?.items.some((job) => isCompileJobActive(job.status)) ?? false;
      const signature = data?.items.map((job) => `${job.jobId}:${job.status}:${job.progressCurrent}:${job.progressTotal}`).join("|") ?? "";
      return resolveAdaptivePollingInterval(active, signature, listPolling.current);
    },
  });
  const detailQuery = useQuery({
    enabled: Boolean(selectedId),
    queryKey: queryKeys.activity.detail("compile-job", selectedId ?? "none"),
    queryFn: ({ signal }) => activityApi.getCompileJob(selectedId!, signal),
    refetchInterval: (state) => {
      const data = state.state.data;
      const signature = data ? `${data.status}:${data.progressCurrent}:${data.progressTotal}:${data.lastHeartbeatAt ?? ""}` : "";
      return resolveAdaptivePollingInterval(data ? isCompileJobActive(data.status) : false, signature, detailPolling.current);
    },
  });

  return (
    <>
      <ActivityToolbar count={listQuery.data?.count} />
      {listQuery.isPending ? <PageState status="loading" title="正在加载编译作业" /> : null}
      {listQuery.isError ? (
        <PageState actionLabel="重试" description={resolveActivityError(listQuery.error)} onAction={() => void listQuery.refetch()} status="error" title="编译作业加载失败" />
      ) : null}
      {listQuery.data ? (
        <ActivityLayout
          list={listQuery.data.items.length ? (
            <ActivityList
              entries={listQuery.data.items.map((job) => ({
                id: job.jobId,
                title: job.sourceNames[0] || "目录编译",
                meta: job.jobId,
                status: job.derivedStatus,
                tone: job.status === "FAILED" ? "danger" : job.status === "SUCCEEDED" ? "success" : "info",
                progress: job.progressTotal > 0 ? `${job.progressCurrent}/${job.progressTotal}` : job.progressMessage,
                time: job.requestedAt,
              }))}
              label="编译作业列表"
              onSelect={onSelect}
              selectedId={selectedId}
            />
          ) : <PageState status="empty" title="暂无编译作业" />}
          detail={resolveDetail()}
        />
      ) : null}
    </>
  );

  function resolveDetail() {
    if (!selectedId) return <DetailPlaceholder title="选择一个编译作业查看详情" />;
    if (detailQuery.isPending) return <PageState status="loading" title="正在加载编译作业详情" />;
    if (detailQuery.isError) {
      return <PageState actionLabel="重试" description={resolveActivityError(detailQuery.error)} onAction={() => void detailQuery.refetch()} status="error" title="编译作业详情加载失败" />;
    }
    return detailQuery.data ? <CompileJobDetail job={detailQuery.data} /> : <DetailPlaceholder title="选择一个编译作业查看详情" />;
  }
}
