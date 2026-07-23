import {
  createAdaptivePollingState,
  isCompileJobActive,
  resolveAdaptivePollingInterval,
} from "./activity-polling";

describe("activity polling", () => {
  it("backs off unchanged active work and resets after progress", () => {
    const state = createAdaptivePollingState();

    expect(resolveAdaptivePollingInterval(true, "RUNNING:1", state)).toBe(2_000);
    expect(resolveAdaptivePollingInterval(true, "RUNNING:1", state)).toBe(2_000);
    expect(resolveAdaptivePollingInterval(true, "RUNNING:1", state)).toBe(5_000);
    expect(resolveAdaptivePollingInterval(true, "RUNNING:2", state)).toBe(2_000);
  });

  it("stops terminal work and clears the previous signature", () => {
    const state = createAdaptivePollingState();
    resolveAdaptivePollingInterval(true, "RUNNING", state);

    expect(resolveAdaptivePollingInterval(false, "SUCCEEDED", state)).toBe(false);
    expect(state).toEqual({ signature: "", unchangedCount: 0 });
    expect(isCompileJobActive("QUEUED")).toBe(true);
    expect(isCompileJobActive("RUNNING")).toBe(true);
    expect(isCompileJobActive("FAILED")).toBe(false);
  });
});
