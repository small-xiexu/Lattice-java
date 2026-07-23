import { useQuery } from "@tanstack/react-query";

import { qualityApi } from "./contracts/quality";
import { queryKeys } from "./query-keys";

export function useAdminOverview() {
  return useQuery({
    queryKey: queryKeys.overview,
    queryFn: ({ signal }) => qualityApi.overview(signal),
  });
}
