import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { MemoryRouter } from "react-router-dom";

import { locateCitationBindings } from "../citations/citation-locator";
import type {
  CitationBinding,
  CitationMarkerData,
} from "../citations/citation-types";
import { CitedMarkdownReport } from "./cited-markdown-report";
import { EvidenceInspector } from "./evidence-inspector";

const MARKER: CitationMarkerData = {
  markerOrdinal: 1,
  markerId: "marker-1",
  citationLiteral: "[1]",
  citationLiterals: ["[1]"],
  claimText: "结论",
  sourceCount: 1,
  sources: [
    {
      sourceType: "ARTICLE",
      targetKey: "article-1",
      sourceId: 3,
      articleKey: "article-1",
      conceptId: "concept-1",
      title: "证据文章",
      sourcePaths: ["kb/article.md"],
      matchedExcerpt: "支撑结论的原文",
      validationStatus: "VERIFIED",
      reason: null,
      score: 0.9,
    },
  ],
};

describe("citation interaction", () => {
  it("renders an exact marker as a keyboard button and moves focus to evidence", async () => {
    const user = userEvent.setup();
    const binding = locateCitationBindings("结论 [1]", [MARKER])[0] ?? null;
    const onActivate = vi.fn();

    function CitationHarness() {
      const [selectedBinding, setSelectedBinding] =
        useState<CitationBinding | null>(null);
      return (
        <>
          <CitedMarkdownReport
            content="结论 [1]"
            markers={[MARKER]}
            onCitationActivate={(markerId) => {
              onActivate(markerId);
              setSelectedBinding(binding);
            }}
          />
          <EvidenceInspector
            binding={selectedBinding}
            onReturnToCitation={(markerId) =>
              document.getElementById(`citation-marker-${markerId}`)?.focus()
            }
          />
        </>
      );
    }

    render(
      <MemoryRouter>
        <CitationHarness />
      </MemoryRouter>,
    );

    const markerButton = screen.getByRole("button", { name: "引用 1" });
    markerButton.focus();
    await user.keyboard("{Enter}");
    expect(onActivate).toHaveBeenCalledWith("marker-1");
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "引用 1" })).toHaveFocus(),
    );
    expect(screen.getByRole("link", { name: "证据文章" })).toHaveAttribute(
      "href",
      "/library/articles/article-1",
    );
    expect(screen.getByText("已验证")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "返回回答" }));
    expect(screen.getByRole("button", { name: "引用 1" })).toHaveFocus();
  });

  it("routes source-file evidence by sourceId even when an articleKey exists", () => {
    const sourceBinding = locateCitationBindings("结论 [1]", [
      {
        ...MARKER,
        sources: [
          {
            ...MARKER.sources[0],
            sourceType: "SOURCE_FILE",
          },
        ],
      },
    ])[0];

    render(
      <MemoryRouter>
        <EvidenceInspector
          binding={sourceBinding ?? null}
          onReturnToCitation={vi.fn()}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "证据文章" })).toHaveAttribute(
      "href",
      "/library/sources/3",
    );
  });

  it("exposes ambiguous citations through an evidence-list trigger", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    const ambiguousMarker = { ...MARKER, sourceCount: 2 };
    render(
      <CitedMarkdownReport
        content="结论 [1]"
        markers={[ambiguousMarker]}
        onCitationActivate={onActivate}
      />,
    );

    expect(screen.queryByRole("button", { name: "引用 1" })).not.toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "查看引用 1 的证据" }),
    );
    expect(onActivate).toHaveBeenCalledWith("marker-1");
  });
});
