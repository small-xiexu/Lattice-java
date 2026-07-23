import ReactMarkdown from "react-markdown";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import remarkGfm from "remark-gfm";

import { remarkCitationMarkers } from "../citations/remark-citation-markers";
import type {
  CitationBinding,
  ExactCitationBinding,
} from "../citations/citation-types";
import { CitationMarker } from "./citation-marker";

interface MarkdownReportProps {
  content: string;
  label?: string;
  citationBindings?: CitationBinding[];
  activeMarkerId?: string | null;
  onCitationActivate?: (markerId: string) => void;
}

const MARKDOWN_SANITIZE_SCHEMA = {
  ...defaultSchema,
  tagNames: [
    ...(defaultSchema.tagNames?.filter((tagName) => tagName !== "img") ?? []),
    "button",
  ],
  attributes: {
    ...defaultSchema.attributes,
    button: ["type", "className", "dataMarkerId"],
  },
  protocols: {
    ...defaultSchema.protocols,
    href: ["http", "https", "mailto"],
  },
};

export function MarkdownReport({
  content,
  label = "Markdown 报告",
  citationBindings = [],
  activeMarkerId,
  onCitationActivate,
}: MarkdownReportProps) {
  const exactBindings = citationBindings.filter(
    (binding): binding is ExactCitationBinding => binding.mode === "exact",
  );
  const bindingsById = new Map(
    exactBindings.map((binding) => [binding.marker.markerId, binding]),
  );
  return (
    <article aria-label={label} className="markdown-report">
      <ReactMarkdown
        components={{
          h1: ({ children }) => <h2>{children}</h2>,
          h2: ({ children }) => <h3>{children}</h3>,
          h3: ({ children }) => <h4>{children}</h4>,
          h4: ({ children }) => <h5>{children}</h5>,
          h5: ({ children }) => <h6>{children}</h6>,
          h6: ({ children }) => <h6>{children}</h6>,
          a: ({ children, href }) => {
            const external = /^https?:\/\//.test(href ?? "");
            return (
              <a
                href={href}
                rel={external ? "noreferrer" : undefined}
                target={external ? "_blank" : undefined}
              >
                {children}
              </a>
            );
          },
          button: (properties) => {
            const markerId = (
              properties as typeof properties & { "data-marker-id"?: string }
            )["data-marker-id"];
            const binding = markerId ? bindingsById.get(markerId) : undefined;
            if (!markerId || !binding || !onCitationActivate) {
              return null;
            }
            return (
              <CitationMarker
                active={activeMarkerId === markerId}
                literal={binding.literal}
                markerId={markerId}
                onActivate={onCitationActivate}
                ordinal={binding.marker.markerOrdinal}
              />
            );
          },
          table: ({ children }) => (
            <div className="markdown-table-scroll" tabIndex={0}>
              <table>{children}</table>
            </div>
          ),
        }}
        rehypePlugins={[[rehypeSanitize, MARKDOWN_SANITIZE_SCHEMA]]}
        remarkPlugins={[remarkGfm, remarkCitationMarkers(exactBindings)]}
        skipHtml
      >
        {content}
      </ReactMarkdown>
    </article>
  );
}
