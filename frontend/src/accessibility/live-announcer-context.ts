import { createContext } from "react";

export interface LiveAnnouncement {
  id: number;
  message: string;
}

export interface LiveAnnouncerContextValue {
  announce(message: string): void;
}

export const LiveAnnouncerContext =
  createContext<LiveAnnouncerContextValue | null>(null);
