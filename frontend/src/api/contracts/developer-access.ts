import { z } from "zod";

import type { ApiClient } from "../api-client";
import { apiClient } from "../api-client";

export const healthSchema = z.object({
  status: z.string(),
  components: z.record(
    z.string(),
    z.object({ status: z.string().optional() }).passthrough(),
  ).optional(),
}).passthrough();

export type Health = z.infer<typeof healthSchema>;

export function createDeveloperAccessApi(client: ApiClient = apiClient) {
  return {
    getHealth(signal?: AbortSignal) {
      return client.get("/actuator/health", {
        schema: healthSchema,
        signal,
      });
    },
  };
}

export const developerAccessApi = createDeveloperAccessApi();
