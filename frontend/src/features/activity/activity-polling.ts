export interface AdaptivePollingState {
  signature: string;
  unchangedCount: number;
}

export function createAdaptivePollingState(): AdaptivePollingState {
  return { signature: "", unchangedCount: 0 };
}

export function resolveAdaptivePollingInterval(
  active: boolean,
  signature: string,
  state: AdaptivePollingState,
): number | false {
  if (!active) {
    state.signature = "";
    state.unchangedCount = 0;
    return false;
  }
  if (signature !== state.signature) {
    state.signature = signature;
    state.unchangedCount = 0;
    return 2_000;
  }
  state.unchangedCount += 1;
  return state.unchangedCount >= 2 ? 5_000 : 2_000;
}

export function isCompileJobActive(status: string) {
  return status === "QUEUED" || status === "RUNNING";
}
