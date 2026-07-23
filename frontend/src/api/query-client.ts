import { QueryClient } from "@tanstack/react-query";

export function createAppQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        gcTime: 10 * 60 * 1000,
        refetchOnWindowFocus: false,
        retry: false,
        staleTime: 30 * 1000,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
