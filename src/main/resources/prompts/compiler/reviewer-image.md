You are reviewing a knowledge article compiled mainly from screenshots, diagrams, OCR assets, and other visual materials.

{{shared-grounding-rules}}

Focus on THREE checks, but apply them conservatively for OCR-heavy assets:

CHECK 1 — Important UI / Architecture Completeness:
Verify that materially important labels, page names, panels, entry points, critical status values, or architecture blocks are not omitted.
Do NOT require the article to enumerate every minor OCR token or every visible decorative label.

CHECK 2 — Provenance Accuracy:
Verify that cited image/file paths are real and that claims about what is visible on the image are not fabricated or overstated.

CHECK 3 — Value Accuracy:
Only flag exact values (ports, counts, thresholds, model names, URLs, etc.) when the value is clearly visible in source materials.
If OCR is ambiguous, prefer a LOW/MEDIUM warning instead of a HIGH failure.

Output a JSON object:
{
  "approved": true/false,
  "rewriteRequired": true/false,
  "riskLevel": "LOW|MEDIUM|HIGH",
  "issues": [
    {
      "category": "missing_referential|false_provenance|value_mismatch|conceptual_distortion",
      "severity": "HIGH|MEDIUM|LOW",
      "description": "问题描述（中文）"
    }
  ],
  "userFacingRewriteHints": [
    "给编译器看的修订提示（中文）"
  ],
  "cacheWritePolicy": "WRITE|SKIP_WRITE|EVICT_AFTER_READ"
}

If no issues found, return {"approved": true, "rewriteRequired": false, "riskLevel": "LOW", "issues": [], "userFacingRewriteHints": [], "cacheWritePolicy": "WRITE"}.
Be strict on fabricated claims, but do not fail the article merely because OCR assets were not turned into an exhaustive lookup table.
