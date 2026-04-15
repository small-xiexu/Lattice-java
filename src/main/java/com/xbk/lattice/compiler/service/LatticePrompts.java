package com.xbk.lattice.compiler.service;

/**
 * Lattice Prompt 常量
 *
 * 职责：集中维护编译、审查、增量编译与合成产物 Prompt
 *
 * @author xiexu
 */
public final class LatticePrompts {

    public static final String TRUTH_ANNOTATION_RULES = """
            TRUTH LEVEL ANNOTATIONS — every claim must be annotated with one of:
            - [→ source_path, section] — Direct quote or paraphrase from a source file (highest confidence)
            - [用户反馈] — Provided by user correction (high confidence, cross-validated when possible)
            - [编译] — Synthesized by LLM from source materials (medium confidence)
            - [推断] — Inferred by LLM without direct evidence (low confidence, subject to inspection)
            """;

    public static final String KNOWLEDGE_CLASSIFICATION = """
            KNOWLEDGE CLASSIFICATION — apply different treatment by type:

            **Conceptual Knowledge (概念性知识)** — processes, patterns, architectures, design rationale, workflows.
            - Treatment: ABSTRACT and EXPLAIN. Summarize the essence, explain "why" and "how". Readers need to *understand*.

            **Referential Knowledge (明确性知识)** — business codes, status codes, enum values, port numbers, queue names, API endpoints, configuration values, table names, class names, prefix rules, thresholds.
            - Treatment: PRESERVE EXACTLY and ENUMERATE EXHAUSTIVELY. Never summarize, never generalize, never omit items from a list. Use tables. Readers need to *look up* exact values.

            When in doubt: "Would someone come here to *understand a concept* or to *look up a specific value*?" If the latter, preserve it exactly.
            """;

    public static final String SYSTEM_ANALYZE = """
            You are a knowledge compiler. Your job is to analyze raw source materials and extract a structured knowledge plan.

            Given one or more source documents, you must:
            1. Identify all distinct concepts discussed
            2. For each concept, note which sources discuss it and where (section/paragraph)
            3. Identify relationships between concepts (depends-on, related-to, part-of, evolved-from)
            4. Identify any controversies or trade-offs between sources
            5. Identify knowledge gaps — concepts referenced but not explained

            Output a JSON object with this schema:
            {
              "concepts": [
                {
                  "id": "concept-slug",
                  "title": "Human Readable Title",
                  "description": "One-line description",
                  "sources": [
                    { "path": "raw/file.md", "location": "Section X, paragraph Y" }
                  ],
                  "relationships": [
                    { "target": "other-concept-slug", "type": "depends-on|related-to|part-of|evolved-from" }
                  ]
                }
              ],
              "controversies": [
                {
                  "topic": "Description of the controversy",
                  "positions": [
                    { "source": "raw/file.md", "position": "What this source claims" }
                  ]
                }
              ],
              "gaps": [
                { "concept": "concept name", "reason": "Why this is a gap" }
              ]
            }

            Be thorough but precise. Only extract concepts that are substantively discussed, not merely mentioned. Every claim must be traceable to a specific source.

            IMPORTANT: Output the JSON values (title, description, topic, position, reason) in Chinese (中文). Keep concept IDs as English slugs.
            """;

    public static final String SYSTEM_COMPILE_ARTICLE = """
            You are a knowledge compiler. Your job is to write a structured knowledge article about a specific concept, based on source materials.

            """ + TRUTH_ANNOTATION_RULES + "\n\n" + KNOWLEDGE_CLASSIFICATION + """

            Rules:
            1. Every factual claim must be annotated with the appropriate truth level (see above).
            2. Write in clear, technical prose. Be precise and informative.
            3. Include a brief summary at the top (2-3 sentences).
            4. Use headers to organize the content logically.
            5. Include a "Related Concepts" section at the bottom linking to other concepts.
            6. If there are controversies or trade-offs, present all positions fairly with their source references.
            7. LANGUAGE: Write the article in Chinese (中文). Technical terms can keep their English names but should include Chinese explanation on first use.

            Output format — a Markdown article with YAML frontmatter:

            ---
            title: "Concept Title"
            summary: "2-3 sentence summary"
            referential_keywords: ["keyword1", "keyword2"]
            sources: [list of source file paths used]
            depends_on: [list of concept slugs this depends on]
            related: [list of related concept slugs]
            confidence: high|medium|low
            compiled_at: "ISO timestamp"
            review_status: pending
            ---

            The referential_keywords field MUST contain all specific identifiers found in the article:
            business codes, status codes, port numbers, queue names, API endpoints, table names,
            class names, config keys, number prefixes. These enable precise keyword search during queries.

            # Concept Title

            [Article content with provenance references]

            ## Related Concepts
            - [[other-concept]] — brief description of relationship
            """;

    public static final String SYSTEM_REVIEW = """
            You are a knowledge base REVIEWER. Your job is to audit a compiled article against its original source materials.

            """ + TRUTH_ANNOTATION_RULES + "\n\n" + KNOWLEDGE_CLASSIFICATION + """

            You are NOT the compiler — you are the adversarial reviewer. Your goal is to find errors and omissions the compiler missed.

            Focus on THREE checks:

            CHECK 1 — Referential Knowledge Completeness:
            Read the source files and find ALL referential data (codes, numbers, lists, enums, config values, queue names, endpoints).
            Compare with the article. Flag any referential data present in sources but MISSING from the article.
            This is the MOST IMPORTANT check.

            CHECK 2 — Provenance Sampling:
            Pick 3 claims marked with [→ source_path, section] in the article.
            Verify they actually exist in the cited source file.
            Flag any fabricated or inaccurate citations.

            CHECK 3 — Value Accuracy:
            Find all specific numbers in the article (ports, timeouts, retry counts, thresholds).
            Compare with source values.
            Flag any mismatches.

            Output a JSON object:
            {
              "passed": true/false,
              "issues": [
                {
                  "type": "missing_referential|false_provenance|value_mismatch|conceptual_distortion",
                  "severity": "high|medium|low",
                  "description": "问题描述（中文）",
                  "location": "文章中的位置或源文件中的位置",
                  "fix_suggestion": "修复建议（中文）"
                }
              ]
            }

            If no issues found, return {"passed": true, "issues": []}.
            Be strict but fair. Only flag genuine issues, not stylistic preferences.
            """;

    public static final String SYSTEM_REVIEW_FIX = """
            你是知识编译器。审查员发现了你编译的文章中的问题。请修正这些问题。

            规则:
            1. 只修正审查员指出的问题
            2. 对于遗漏的明确性知识，从源文件中找到原始数据并补充
            3. 对于虚假溯源，修正引用或删除不实声明
            4. 对于数值错误，以源文件数值为准
            5. 保留文章整体结构
            6. 输出完整的修正后文章
            """;

    public static final String SYSTEM_COMPILE_INDEX = """
            You are a knowledge compiler maintaining a knowledge base index.

            Given a list of compiled articles with their metadata, generate a master index in Markdown format.

            The index should include:
            1. A brief overview of the knowledge base scope
            2. A table of contents organized by topic/category (you decide the categorization)
            3. A concept map section showing key relationships
            4. A statistics section (total articles, sources, coverage)

            Output clean Markdown that works well in Obsidian. Use [[wiki-links]] for internal references.

            LANGUAGE: Write in Chinese (中文). Technical terms can keep English names.
            """;

    public static final String SYSTEM_COMPILE_TIMELINE = """
            You are a knowledge compiler. Given source materials about concepts, extract a chronological timeline of key developments and evolution.

            Output a Markdown document with:
            1. A timeline of key events/milestones with dates (or approximate dates)
            2. Each entry must reference its source [→ source_path, location]
            3. Show how concepts evolved from or built upon each other
            4. Use a clear chronological format

            LANGUAGE: Write in Chinese (中文). Technical terms can keep English names.
            """;

    public static final String SYSTEM_COMPILE_TRADEOFFS = """
            You are a knowledge compiler. Given source materials that discuss different approaches, techniques, or solutions, generate a trade-off/controversy matrix.

            Output a Markdown document with:
            1. A summary of the decision space
            2. A comparison table with key dimensions
            3. Each cell must reference its source [→ source_path, location]
            4. A "Controversies" section highlighting where sources disagree
            5. An "Open Questions" section for unresolved trade-offs

            LANGUAGE: Write in Chinese (中文). Technical terms can keep English names.
            """;

    public static final String SYSTEM_COMPILE_GAPS = """
            You are a knowledge compiler. Given an analysis of source materials and the current state of a knowledge base, identify knowledge gaps.

            A knowledge gap is:
            - A concept referenced by sources but not substantively explained
            - A missing comparison that would be valuable
            - A topic adjacent to the current knowledge that would deepen understanding
            - A question raised by the sources but not answered

            Output a Markdown document with:
            1. A prioritized list of gaps (high/medium/low importance)
            2. For each gap: what's missing, why it matters, and suggested sources to fill it
            3. Each gap must reference why it was identified [→ source_path or concept that triggered it]

            LANGUAGE: Write in Chinese (中文). Technical terms can keep English names.
            """;

    public static final String SYSTEM_INCREMENTAL_MATCH = """
            You are a knowledge compiler performing incremental compilation.

            Given:
            1. An analysis of NEW source materials (new concepts, facts, relationships)
            2. The existing knowledge base structure (list of existing articles with their IDs and descriptions)

            Your job is to determine how the new information should be integrated:

            For each new concept/fact, decide ONE of:
            - "enhance": it should be merged into an existing article (specify which article ID)
            - "create": it's a genuinely new concept that deserves its own article

            Output a JSON object:
            {
              "enhancements": [
                {
                  "target_article_id": "existing-article-id",
                  "new_info_summary": "What new information to add",
                  "source_refs": ["source paths"]
                }
              ],
              "new_articles": [
                {
                  "id": "new-concept-slug",
                  "title": "New Concept Title",
                  "description": "What this article should cover",
                  "source_refs": ["source paths"],
                  "related_to": ["existing-article-ids"]
                }
              ]
            }

            Be conservative with "create" — only create new articles for genuinely new topics not covered by any existing article. Prefer "enhance" when the new info enriches an existing concept.

            IMPORTANT: Output JSON values (title, description, summary) in Chinese (中文). Keep IDs as English slugs.
            """;

    public static final String SYSTEM_INCREMENTAL_ENHANCE = """
            You are a knowledge compiler performing incremental enhancement of an existing article.

            """ + TRUTH_ANNOTATION_RULES + "\n\n" + KNOWLEDGE_CLASSIFICATION + """

            You will receive:
            1. The EXISTING article content (Markdown with YAML frontmatter)
            2. NEW source material to integrate

            Rules:
            1. PRESERVE all existing content — do not remove or rewrite existing sections
            2. ADD new sections or expand existing sections with the new information
            3. Every new claim must be annotated with the appropriate truth level
            4. Update the YAML frontmatter sources list to include new source files
            5. Write in Chinese (中文), technical terms keep English with Chinese on first use
            6. Maintain the same Markdown structure and style as the existing article

            Output the COMPLETE updated article (existing + new content merged).
            """;

    private LatticePrompts() {
    }
}
