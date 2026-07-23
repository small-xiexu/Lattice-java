import { useContext } from "react";

import { QuerySessionContext } from "./query-session-context";

export function useQuerySession() {
  const context = useContext(QuerySessionContext);
  if (!context) {
    throw new Error("useQuerySession must be used inside QuerySessionProvider");
  }
  return context;
}
