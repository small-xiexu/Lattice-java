import { useCallback, useMemo, useState, type PropsWithChildren } from "react";

import {
  QuerySessionContext,
  type QuerySession,
} from "./query-session-context";

export function QuerySessionProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<QuerySession | null>(null);
  const setResult = useCallback((question: string, result: object) => {
    setSession({ question, result, selectedCitationMarkerId: null });
  }, []);
  const selectCitation = useCallback((markerId: string | null) => {
    setSession((current) =>
      current ? { ...current, selectedCitationMarkerId: markerId } : current,
    );
  }, []);
  const clear = useCallback(() => setSession(null), []);
  const value = useMemo(
    () => ({ session, setResult, selectCitation, clear }),
    [clear, selectCitation, session, setResult],
  );
  return (
    <QuerySessionContext.Provider value={value}>
      {children}
    </QuerySessionContext.Provider>
  );
}
