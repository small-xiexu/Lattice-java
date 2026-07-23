import { useCallback, useMemo, useSyncExternalStore } from "react";

export function useMediaQuery(query: string) {
  const mediaQueryList = useMemo(
    () =>
      typeof window.matchMedia === "function" ? window.matchMedia(query) : null,
    [query],
  );
  const subscribe = useCallback(
    (onChange: () => void) => {
      mediaQueryList?.addEventListener("change", onChange);
      return () => mediaQueryList?.removeEventListener("change", onChange);
    },
    [mediaQueryList],
  );
  const getSnapshot = useCallback(
    () => mediaQueryList?.matches ?? false,
    [mediaQueryList],
  );
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
