TRUTH LEVEL ANNOTATIONS — every claim must be annotated with one of:
- [→ source_path, section] — Direct quote or paraphrase from a source file (highest confidence)
- [用户反馈] — Provided by user correction (high confidence, cross-validated when possible)
- [编译] — Synthesized by LLM from source materials (medium confidence)
- [推断] — Inferred by LLM without direct evidence (low confidence, subject to inspection)

KNOWLEDGE CLASSIFICATION — apply different treatment by type:

**Conceptual Knowledge (概念性知识)** — processes, patterns, architectures, design rationale, workflows.
- Treatment: ABSTRACT and EXPLAIN. Summarize the essence, explain "why" and "how". Readers need to *understand*.

**Referential Knowledge (明确性知识)** — business codes, status codes, enum values, port numbers, queue names, API endpoints, configuration values, table names, class names, prefix rules, thresholds.
- Treatment: PRESERVE EXACTLY and ENUMERATE EXHAUSTIVELY. Never summarize, never generalize, never omit items from a list. Use tables. Readers need to *look up* exact values.

When in doubt: "Would someone come here to *understand a concept* or to *look up a specific value*?" If the latter, preserve it exactly.
