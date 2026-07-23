import { useMemo } from "react";

import { locateCitationBindings } from "../citations/citation-locator";
import type { CitationMarkerData } from "../citations/citation-types";
import { MarkdownReport } from "./markdown-report";

interface CitedMarkdownReportProps {
  content: string;
  markers: CitationMarkerData[];
  activeMarkerId?: string | null;
  onCitationActivate: (markerId: string) => void;
}

export function CitedMarkdownReport({
  content,
  markers,
  activeMarkerId,
  onCitationActivate,
}: CitedMarkdownReportProps) {
  const bindings = useMemo(
    () => locateCitationBindings(content, markers),
    [content, markers],
  );
  const fallbackBindings = bindings.filter(
    (binding) => binding.mode === "evidence-list",
  );
  return (
    <>
      <MarkdownReport
        activeMarkerId={activeMarkerId}
        citationBindings={bindings}
        content={content}
        label="回答正文"
        onCitationActivate={onCitationActivate}
      />
      {fallbackBindings.length > 0 ? (
        <div aria-label="无法精确定位的引用" className="citation-fallback-list">
          {fallbackBindings.map((binding) => (
            <button
              aria-pressed={activeMarkerId === binding.marker.markerId}
              id={`citation-fallback-${binding.marker.markerId}`}
              key={binding.marker.markerId}
              onClick={() => onCitationActivate(binding.marker.markerId)}
              type="button"
            >
              查看引用 {binding.marker.markerOrdinal} 的证据
            </button>
          ))}
        </div>
      ) : null}
    </>
  );
}
