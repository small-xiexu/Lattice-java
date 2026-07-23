import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Cable, RefreshCw, Route } from "lucide-react";
import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { documentParseSettingsApi } from "../../api/contracts/document-parse-settings";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { DocumentParseConnectionsPanel } from "./document-parse-connections-panel";
import { DocumentParsePolicyPanel } from "./document-parse-policy-panel";
import { confirmDiscardChanges } from "./llm-settings-utils";

type ParsingView = "connections" | "policy";

const PARSING_VIEWS = [
  { value: "connections" as const, label: "解析连接", icon: Cable },
  { value: "policy" as const, label: "默认策略", icon: Route },
];

export default function ParsingSettingsPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [dirty, setDirty] = useState(false);
  const requestedView = searchParams.get("view");
  const view: ParsingView = PARSING_VIEWS.some((item) => item.value === requestedView)
    ? requestedView as ParsingView
    : "connections";
  const onDirtyChange = useCallback((nextDirty: boolean) => setDirty(nextDirty), []);
  const policyQuery = useQuery({
    queryKey: queryKeys.settings.documentParse.policy,
    queryFn: ({ signal }) => documentParseSettingsApi.getPolicy(signal),
  });

  const changeView = (nextView: ParsingView) => {
    if (nextView === view || !confirmDiscardChanges(dirty)) return;
    const next = new URLSearchParams(searchParams);
    next.set("view", nextView);
    next.delete("id");
    setSearchParams(next);
  };

  const refresh = () => {
    if (!confirmDiscardChanges(dirty)) return;
    void queryClient.invalidateQueries({ queryKey: queryKeys.settings.documentParse.root });
    void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.models });
  };

  return (
    <div className="page-frame parsing-settings-page">
      <PageHeader
        actions={(
          <button
            aria-label="刷新文档解析配置"
            className="icon-button parsing-refresh-button"
            onClick={refresh}
            title="刷新文档解析配置"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="provider descriptors · encrypted credentials · default routing"
        title="文档解析"
      />
      <nav aria-label="文档解析设置视图" className="llm-settings-tabs" role="tablist">
        {PARSING_VIEWS.map((item) => {
          const Icon = item.icon;
          return (
            <button
              aria-controls="parsing-settings-panel"
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
      <div aria-label={PARSING_VIEWS.find((item) => item.value === view)?.label} id="parsing-settings-panel" role="tabpanel">
        {view === "connections" ? (
          <DocumentParseConnectionsPanel
            onDirtyChange={onDirtyChange}
            policyImageConnectionId={policyQuery.data?.imageConnectionId ?? null}
            policyScannedPdfConnectionId={policyQuery.data?.scannedPdfConnectionId ?? null}
          />
        ) : null}
        {view === "policy" ? <DocumentParsePolicyPanel onDirtyChange={onDirtyChange} /> : null}
      </div>
    </div>
  );
}
