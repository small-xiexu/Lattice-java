import { http, HttpResponse } from "msw";

import {
  compileJobFixture,
  processingTaskFixture,
  processingTaskListFixture,
  sourceRunFixture,
} from "../../test/activity-fixtures";
import { server } from "../../test/server";
import { createApiClient } from "../api-client";
import {
  createActivityApi,
  processingTaskSchema,
} from "./activity";

const api = createActivityApi(createApiClient({ baseUrl: "http://localhost" }));

describe("activity contracts", () => {
  it("accepts standalone compile tasks without a source run id", () => {
    const task = processingTaskSchema.parse(processingTaskFixture({
      taskId: "compile-job:job-41",
      taskType: "COMPILE_JOB",
      title: "独立编译",
      runId: null,
      sourceId: null,
      sourceName: null,
    }));

    expect(task.runId).toBeNull();
    expect(task.compileReviewSummary?.reviewModeLabel).toBe("LLM 审查");
  });

  it("sends bounded processing filters and parses the aggregate response", async () => {
    let search = new URLSearchParams();
    server.use(
      http.get("http://localhost/api/v1/admin/processing-tasks", ({ request }) => {
        search = new URL(request.url).searchParams;
        return HttpResponse.json(processingTaskListFixture());
      }),
    );

    const result = await api.listProcessingTasks({ limit: 20, status: "active" });

    expect(search.get("limit")).toBe("20");
    expect(search.get("status")).toBe("active");
    expect(result.items[0]?.taskId).toBe("source-run:41");
  });

  it("uses the server confirmation decision and target source exactly once", async () => {
    let requestBody: unknown;
    let requestCount = 0;
    server.use(
      http.post("http://localhost/api/v1/admin/source-runs/41/confirm", async ({ request }) => {
        requestCount += 1;
        requestBody = await request.json();
        return HttpResponse.json(sourceRunFixture({ status: "COMPILE_QUEUED" }));
      }),
    );

    const run = await api.confirmSourceRun(41, {
      decision: "EXISTING_SOURCE_APPEND",
      sourceId: 12,
    });

    expect(requestCount).toBe(1);
    expect(requestBody).toEqual({ decision: "EXISTING_SOURCE_APPEND", sourceId: 12 });
    expect(run.status).toBe("COMPILE_QUEUED");
  });

  it("loads and retries compile jobs through the dedicated job endpoints", async () => {
    const paths: string[] = [];
    server.use(
      http.get("http://localhost/api/v1/admin/jobs", ({ request }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json({ count: 1, items: [compileJobFixture()] });
      }),
      http.get("http://localhost/api/v1/admin/jobs/job-41", ({ request }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json(compileJobFixture());
      }),
      http.post("http://localhost/api/v1/admin/jobs/job-41/retry", ({ request }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json(compileJobFixture({ attemptCount: 2 }));
      }),
    );

    const list = await api.listCompileJobs();
    const detail = await api.getCompileJob("job-41");
    const retried = await api.retryCompileJob("job-41");

    expect(paths).toEqual([
      "/api/v1/admin/jobs",
      "/api/v1/admin/jobs/job-41",
      "/api/v1/admin/jobs/job-41/retry",
    ]);
    expect(list.count).toBe(1);
    expect(detail.jobId).toBe("job-41");
    expect(retried.attemptCount).toBe(2);
  });
});
