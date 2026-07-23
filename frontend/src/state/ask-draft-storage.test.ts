import { createAskDraftStore } from "./ask-draft-storage";

function createMemoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    values,
  };
}

describe("ask draft storage", () => {
  it("persists only whitelisted input fields", () => {
    const storage = createMemoryStorage();
    const store = createAskDraftStore({ storage, now: () => 1_000 });

    expect(
      store.save({
        question: "如何处理重试？",
        mode: "auto",
        apiKey: "secret",
        answer: "禁止持久化的答案",
        evidenceJson: { content: "禁止持久化的证据" },
      }),
    ).toBe("saved");

    const raw = [...storage.values.values()][0] ?? "";
    expect(raw).not.toContain("secret");
    expect(raw).not.toContain("禁止持久化的答案");
    expect(raw).not.toContain("禁止持久化的证据");
    expect(store.load()).toEqual({
      question: "如何处理重试？",
      mode: "auto",
    });
  });

  it("removes an expired or incompatible draft", () => {
    const storage = createMemoryStorage();
    const writer = createAskDraftStore({ storage, now: () => 1_000 });
    expect(writer.save({ question: "draft", mode: "simple" })).toBe("saved");

    const expiredReader = createAskDraftStore({
      storage,
      now: () => 1_000 + 2 * 60 * 60 * 1000 + 1,
    });
    expect(expiredReader.load()).toBeNull();
    expect(storage.values.size).toBe(0);

    storage.setItem(
      "lattice:draft:ask:v1",
      JSON.stringify({ version: 2, expiresAt: 99_999, payload: {} }),
    );
    expect(writer.load()).toBeNull();
    expect(storage.values.size).toBe(0);
  });

  it("rejects invalid and over-capacity drafts", () => {
    const storage = createMemoryStorage();
    const store = createAskDraftStore({ storage });

    expect(store.save({ question: "missing mode" })).toBe("invalid");
    expect(store.save({ question: "知".repeat(4000), mode: "deep" })).toBe(
      "too-large",
    );
    expect(storage.values.size).toBe(0);
  });
});
