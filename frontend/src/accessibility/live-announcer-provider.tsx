import { useCallback, useMemo, useState, type PropsWithChildren } from "react";

import {
  LiveAnnouncerContext,
  type LiveAnnouncement,
} from "./live-announcer-context";

export function LiveAnnouncerProvider({ children }: PropsWithChildren) {
  const [announcement, setAnnouncement] = useState<LiveAnnouncement>({
    id: 0,
    message: "",
  });
  const announce = useCallback((message: string) => {
    setAnnouncement((current) => ({ id: current.id + 1, message }));
  }, []);
  const value = useMemo(() => ({ announce }), [announce]);

  return (
    <LiveAnnouncerContext.Provider value={value}>
      {children}
      <div
        aria-atomic="true"
        aria-live="polite"
        className="sr-only"
        id="global-announcer"
      >
        <span key={announcement.id}>{announcement.message}</span>
      </div>
    </LiveAnnouncerContext.Provider>
  );
}
