import { unified } from "unified";
import { visit } from "unist-util-visit";
import remarkGfm from "remark-gfm";
import remarkParse from "remark-parse";

import type {
  CitationBinding,
  CitationMarkerData,
  ExactCitationBinding,
  FallbackCitationBinding,
} from "./citation-types";

interface TextMatch {
  startOffset: number;
  endOffset: number;
}

export function locateCitationBindings(
  markdown: string,
  markers: CitationMarkerData[],
): CitationBinding[] {
  const tree = unified().use(remarkParse).use(remarkGfm).parse(markdown);
  const candidates = markers.map((marker): CitationBinding => {
    const literal = resolveLiteral(marker);
    if (!literal) {
      return fallback(marker, null, "missing-literal");
    }
    if (marker.sourceCount !== 1 || marker.sources.length !== 1) {
      return fallback(marker, literal, "ambiguous-sources");
    }
    const matches: TextMatch[] = [];
    visit(tree, "text", (node) => {
      const nodeStart = node.position?.start.offset;
      if (nodeStart === undefined) {
        return;
      }
      let localIndex = node.value.indexOf(literal);
      while (localIndex >= 0) {
        matches.push({
          startOffset: nodeStart + localIndex,
          endOffset: nodeStart + localIndex + literal.length,
        });
        localIndex = node.value.indexOf(literal, localIndex + 1);
      }
    });
    if (matches.length === 0) {
      return fallback(marker, literal, "no-text-match");
    }
    if (matches.length > 1) {
      return fallback(marker, literal, "multiple-text-matches");
    }
    return {
      mode: "exact",
      marker,
      literal,
      ...matches[0],
    } satisfies ExactCitationBinding;
  });

  const exactLocations = new Map<string, number>();
  candidates.forEach((binding) => {
    if (binding.mode === "exact") {
      const key = `${binding.startOffset}:${binding.endOffset}`;
      exactLocations.set(key, (exactLocations.get(key) ?? 0) + 1);
    }
  });
  return candidates.map((binding) => {
    if (binding.mode !== "exact") {
      return binding;
    }
    const key = `${binding.startOffset}:${binding.endOffset}`;
    return exactLocations.get(key) === 1
      ? binding
      : fallback(binding.marker, binding.literal, "marker-conflict");
  });
}

function resolveLiteral(marker: CitationMarkerData): string | null {
  const primary = marker.citationLiteral?.trim();
  if (primary) {
    return primary;
  }
  const literals = [...new Set(marker.citationLiterals.map((value) => value.trim()))]
    .filter(Boolean);
  return literals.length === 1 ? literals[0] : null;
}

function fallback(
  marker: CitationMarkerData,
  literal: string | null,
  reason: FallbackCitationBinding["reason"],
): FallbackCitationBinding {
  return { mode: "evidence-list", marker, literal, reason };
}
