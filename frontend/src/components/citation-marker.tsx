interface CitationMarkerProps {
  markerId: string;
  ordinal: number;
  literal: string;
  active?: boolean;
  onActivate: (markerId: string) => void;
}

export function CitationMarker({
  markerId,
  ordinal,
  literal,
  active = false,
  onActivate,
}: CitationMarkerProps) {
  return (
    <sup className="citation-marker-wrap">
      <button
        aria-label={`引用 ${ordinal}`}
        aria-pressed={active}
        className="citation-marker"
        data-marker-id={markerId}
        id={`citation-marker-${markerId}`}
        onClick={() => onActivate(markerId)}
        type="button"
      >
        {literal}
      </button>
    </sup>
  );
}
