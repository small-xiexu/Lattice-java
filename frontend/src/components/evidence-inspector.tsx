import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";

import type { CitationBinding, CitationSource } from "../citations/citation-types";
import { InlineAlert } from "./inline-alert";

interface EvidenceInspectorProps {
  binding: CitationBinding | null;
  onReturnToCitation: (markerId: string) => void;
}

export function EvidenceInspector({
  binding,
  onReturnToCitation,
}: EvidenceInspectorProps) {
  const titleRef = useRef<HTMLHeadingElement>(null);
  const markerId = binding?.marker.markerId;

  useEffect(() => {
    if (markerId) {
      titleRef.current?.focus();
    }
  }, [markerId]);

  if (!binding) {
    return <p className="evidence-placeholder">选择回答中的引用后查看证据。</p>;
  }
  return (
    <div className="evidence-inspector">
      <h2 ref={titleRef} tabIndex={-1}>
        引用 {binding.marker.markerOrdinal}
      </h2>
      {binding.mode === "evidence-list" ? (
        <InlineAlert
          description="该引用存在零匹配、多匹配、跨节点或来源歧义，未推测正文位置。"
          title="正文位置无法精确确定"
          tone="warning"
        />
      ) : null}
      <div className="evidence-list">
        {binding.marker.sources.map((source, index) => (
          <EvidenceItem
            key={`${source.targetKey ?? source.title ?? "source"}-${index}`}
            source={source}
          />
        ))}
      </div>
      <button
        className="secondary-button evidence-return"
        onClick={() => onReturnToCitation(binding.marker.markerId)}
        type="button"
      >
        返回回答
      </button>
    </div>
  );
}

function EvidenceItem({ source }: { source: CitationSource }) {
  const title = source.title ?? source.targetKey ?? source.sourceType;
  const target = resolveEvidenceTarget(source);
  return (
    <article className="evidence-item">
      <header>
        {target ? <Link to={target}>{title}</Link> : <strong>{title}</strong>}
        {source.validationStatus ? (
          <span>{resolveValidationLabel(source.validationStatus)}</span>
        ) : null}
      </header>
      {source.matchedExcerpt ? <blockquote>{source.matchedExcerpt}</blockquote> : null}
      {source.sourcePaths.length > 0 ? (
        <code>{source.sourcePaths.join(" · ")}</code>
      ) : null}
      {source.reason ? <p>{source.reason}</p> : null}
    </article>
  );
}

function resolveEvidenceTarget(source: CitationSource) {
  if (source.sourceType === "ARTICLE" && source.articleKey) {
    return `/library/articles/${encodeURIComponent(source.articleKey)}`;
  }
  if (source.sourceId) {
    return `/library/sources/${source.sourceId}`;
  }
  if (source.articleKey) {
    return `/library/articles/${encodeURIComponent(source.articleKey)}`;
  }
  return null;
}

function resolveValidationLabel(status: string) {
  switch (status) {
    case "VERIFIED":
      return "已验证";
    case "DEMOTED":
      return "已降级";
    case "SKIPPED":
      return "未核验";
    default:
      return `核验状态：${status}`;
  }
}
