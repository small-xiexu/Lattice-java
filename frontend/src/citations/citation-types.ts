export interface CitationSource {
  sourceType: string;
  targetKey: string | null;
  sourceId: number | null;
  articleKey: string | null;
  conceptId: string | null;
  title: string | null;
  sourcePaths: string[];
  matchedExcerpt: string | null;
  validationStatus: string | null;
  reason: string | null;
  score: number;
}

export interface CitationMarkerData {
  markerOrdinal: number;
  markerId: string;
  citationLiteral: string | null;
  citationLiterals: string[];
  claimText: string | null;
  sourceCount: number;
  sources: CitationSource[];
}

export type CitationFallbackReason =
  | "missing-literal"
  | "no-text-match"
  | "multiple-text-matches"
  | "ambiguous-sources"
  | "marker-conflict";

export interface ExactCitationBinding {
  mode: "exact";
  marker: CitationMarkerData;
  literal: string;
  startOffset: number;
  endOffset: number;
}

export interface FallbackCitationBinding {
  mode: "evidence-list";
  marker: CitationMarkerData;
  literal: string | null;
  reason: CitationFallbackReason;
}

export type CitationBinding = ExactCitationBinding | FallbackCitationBinding;
