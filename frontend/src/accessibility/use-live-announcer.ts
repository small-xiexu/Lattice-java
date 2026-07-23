import { useContext } from "react";

import { LiveAnnouncerContext } from "./live-announcer-context";

export function useLiveAnnouncer() {
  const context = useContext(LiveAnnouncerContext);
  if (!context) {
    throw new Error("useLiveAnnouncer must be used inside LiveAnnouncerProvider");
  }
  return context;
}
