import { useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { useSearchParams } from "react-router-dom";

import {
  processingTaskStatusSchema,
  type ProcessingTaskStatus,
} from "../../api/contracts/activity";
import { PageHeader } from "../../components/page-header";
import { ActivityCompileJobsView } from "./activity-compile-jobs-view";
import { ActivityProcessingView } from "./activity-processing-view";
import { ActivitySourceRunsView } from "./activity-source-runs-view";

const VIEWS = [
  { value: "processing", label: "处理任务" },
  { value: "source-run", label: "同步运行" },
  { value: "compile-job", label: "编译作业" },
] as const;

export type ActivityKind = typeof VIEWS[number]["value"];

export default function ActivityPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const kind = parseKind(searchParams.get("kind"));
  const selectedId = searchParams.get("id");
  const limit = parseLimit(searchParams.get("limit"));
  const status = parseStatus(searchParams.get("status"));

  const updateSearch = (
    values: Record<string, string | number | null>,
    replace = false,
  ) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(values).forEach(([key, value]) => {
      if (value === null) next.delete(key);
      else next.set(key, String(value));
    });
    setSearchParams(next, { replace });
  };

  const selectKind = (nextKind: ActivityKind) => {
    updateSearch({
      kind: nextKind === "processing" ? null : nextKind,
      id: null,
      status: nextKind === "processing" && status !== "all" ? status : null,
    });
  };

  return (
    <div className="page-frame activity-page">
      <PageHeader
        actions={
          <button
            aria-label="刷新当前任务视图"
            className="icon-button activity-refresh-button"
            onClick={() =>
              void queryClient.invalidateQueries({ queryKey: ["admin"] })
            }
            title="刷新当前任务视图"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        }
        context="processing task / source run / compile job"
        title="处理中心"
      />

      <div aria-label="处理中心视图" className="activity-tabs" role="tablist">
        {VIEWS.map((view) => (
          <button
            aria-controls={`activity-panel-${view.value}`}
            aria-selected={kind === view.value}
            id={`activity-tab-${view.value}`}
            key={view.value}
            onClick={() => selectKind(view.value)}
            role="tab"
            type="button"
          >
            {view.label}
          </button>
        ))}
      </div>

      <div
        aria-labelledby={`activity-tab-${kind}`}
        id={`activity-panel-${kind}`}
        role="tabpanel"
      >
        {kind === "processing" ? (
          <ActivityProcessingView
            limit={limit}
            onLimitChange={(value) => updateSearch({ limit: value }, true)}
            onSelect={(id) => updateSearch({ id })}
            onStatusChange={(value) =>
              updateSearch({ status: value === "all" ? null : value, id: null })
            }
            selectedId={selectedId}
            status={status}
          />
        ) : null}
        {kind === "source-run" ? (
          <ActivitySourceRunsView
            limit={limit}
            onLimitChange={(value) => updateSearch({ limit: value }, true)}
            onSelect={(id) => updateSearch({ id })}
            selectedId={selectedId}
          />
        ) : null}
        {kind === "compile-job" ? (
          <ActivityCompileJobsView
            onSelect={(id) => updateSearch({ id })}
            selectedId={selectedId}
          />
        ) : null}
      </div>
    </div>
  );
}

function parseKind(value: string | null): ActivityKind {
  return VIEWS.some((view) => view.value === value)
    ? value as ActivityKind
    : "processing";
}

function parseStatus(value: string | null): ProcessingTaskStatus {
  const parsed = processingTaskStatusSchema.safeParse(value ?? "all");
  return parsed.success ? parsed.data : "all";
}

function parseLimit(value: string | null) {
  const parsed = Number(value ?? 20);
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 50 ? parsed : 20;
}
