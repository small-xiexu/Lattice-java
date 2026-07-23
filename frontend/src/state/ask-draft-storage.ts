import { z } from "zod";

const ASK_DRAFT_KEY = "lattice:draft:ask:v1";
const ASK_DRAFT_VERSION = 1;
const ASK_DRAFT_TTL_MS = 2 * 60 * 60 * 1000;
const ASK_DRAFT_MAX_BYTES = 8 * 1024;

const askDraftSchema = z.object({
  question: z.string().max(4000),
  mode: z.enum(["auto", "simple", "deep"]),
});

const storedAskDraftSchema = z.object({
  version: z.literal(ASK_DRAFT_VERSION),
  expiresAt: z.number().int().positive(),
  payload: askDraftSchema,
});

export type AskDraft = z.infer<typeof askDraftSchema>;
export type DraftSaveResult = "saved" | "invalid" | "too-large" | "unavailable";

interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

interface AskDraftStoreOptions {
  storage?: StorageLike | null;
  now?: () => number;
}

export function createAskDraftStore({
  storage = resolveSessionStorage(),
  now = Date.now,
}: AskDraftStoreOptions = {}) {
  return {
    load(): AskDraft | null {
      if (!storage) {
        return null;
      }
      try {
        const raw = storage.getItem(ASK_DRAFT_KEY);
        if (!raw) {
          return null;
        }
        if (byteLength(raw) > ASK_DRAFT_MAX_BYTES) {
          storage.removeItem(ASK_DRAFT_KEY);
          return null;
        }
        const parsed = storedAskDraftSchema.safeParse(JSON.parse(raw));
        if (!parsed.success || parsed.data.expiresAt <= now()) {
          storage.removeItem(ASK_DRAFT_KEY);
          return null;
        }
        return parsed.data.payload;
      } catch {
        storage.removeItem(ASK_DRAFT_KEY);
        return null;
      }
    },
    save(candidate: unknown): DraftSaveResult {
      if (!storage) {
        return "unavailable";
      }
      const payload = askDraftSchema.safeParse(candidate);
      if (!payload.success) {
        return "invalid";
      }
      const raw = JSON.stringify({
        version: ASK_DRAFT_VERSION,
        expiresAt: now() + ASK_DRAFT_TTL_MS,
        payload: payload.data,
      });
      if (byteLength(raw) > ASK_DRAFT_MAX_BYTES) {
        return "too-large";
      }
      try {
        storage.setItem(ASK_DRAFT_KEY, raw);
        return "saved";
      } catch {
        return "unavailable";
      }
    },
    clear() {
      storage?.removeItem(ASK_DRAFT_KEY);
    },
  };
}

function resolveSessionStorage(): StorageLike | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

function byteLength(value: string): number {
  return new TextEncoder().encode(value).byteLength;
}
