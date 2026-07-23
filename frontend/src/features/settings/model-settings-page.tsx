import { useQueryClient } from "@tanstack/react-query";
import { Cable, Boxes, RefreshCw, Workflow } from "lucide-react";
import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { LlmBindingsPanel } from "./llm-bindings-panel";
import { LlmConnectionsPanel } from "./llm-connections-panel";
import { LlmModelsPanel } from "./llm-models-panel";
import { confirmDiscardChanges } from "./llm-settings-utils";

type SettingsView = "connections" | "models" | "bindings";

const SETTINGS_VIEWS = [
  { value: "connections" as const, label: "模型连接", icon: Cable },
  { value: "models" as const, label: "模型档案", icon: Boxes },
  { value: "bindings" as const, label: "场景绑定", icon: Workflow },
];

export default function ModelSettingsPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [dirty, setDirty] = useState(false);
  const requestedView = searchParams.get("view");
  const view: SettingsView = SETTINGS_VIEWS.some((item) => item.value === requestedView)
    ? requestedView as SettingsView
    : "connections";
  const onDirtyChange = useCallback((nextDirty: boolean) => setDirty(nextDirty), []);

  const changeView = (nextView: SettingsView) => {
    if (nextView === view || !confirmDiscardChanges(dirty)) return;
    const next = new URLSearchParams(searchParams);
    next.set("view", nextView);
    next.delete("id");
    setSearchParams(next);
  };

  return (
    <div className="page-frame llm-settings-page">
      <PageHeader
        actions={(
          <button
            aria-label="刷新模型与绑定配置"
            className="icon-button llm-refresh-button"
            onClick={() => void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.root })}
            title="刷新模型与绑定配置"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="connections · model profiles · runtime bindings"
        title="模型与绑定"
      />
      <nav aria-label="模型设置视图" className="llm-settings-tabs" role="tablist">
        {SETTINGS_VIEWS.map((item) => {
          const Icon = item.icon;
          return (
            <button
              aria-controls="llm-settings-panel"
              aria-selected={view === item.value}
              key={item.value}
              onClick={() => changeView(item.value)}
              role="tab"
              type="button"
            >
              <Icon aria-hidden="true" size={16} />
              {item.label}
            </button>
          );
        })}
      </nav>
      <div aria-label={SETTINGS_VIEWS.find((item) => item.value === view)?.label} id="llm-settings-panel" role="tabpanel">
        {view === "connections" ? <LlmConnectionsPanel onDirtyChange={onDirtyChange} /> : null}
        {view === "models" ? <LlmModelsPanel onDirtyChange={onDirtyChange} /> : null}
        {view === "bindings" ? <LlmBindingsPanel onDirtyChange={onDirtyChange} /> : null}
      </div>
    </div>
  );
}
