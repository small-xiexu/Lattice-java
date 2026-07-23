import type { QueryRequest } from "../../api/contracts/query";
import type { QueryMode } from "../../components/mode-selector";

export function buildQueryRequest(
  question: string,
  mode: QueryMode,
): QueryRequest {
  if (mode === "simple") {
    return { question, forceSimple: true };
  }
  if (mode === "deep") {
    return { question, forceDeep: true };
  }
  return { question };
}
