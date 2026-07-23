import { createContext } from "react";

export interface QuerySession {
  question: string;
  result: object;
  selectedCitationMarkerId: string | null;
}

export interface QuerySessionContextValue {
  session: QuerySession | null;
  setResult(question: string, result: object): void;
  selectCitation(markerId: string | null): void;
  clear(): void;
}

export const QuerySessionContext = createContext<QuerySessionContextValue | null>(
  null,
);
