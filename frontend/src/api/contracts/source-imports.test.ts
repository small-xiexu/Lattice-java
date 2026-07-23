import { http, HttpResponse } from "msw";

import {
  compileJobFixture,
  sourceCredentialFixture,
  sourceDetailFixture,
  sourceRunFixture,
  sourceValidationFixture,
} from "../../test/source-import-fixtures";
import { server } from "../../test/server";
import { createApiClient } from "../api-client";
import { createSourceImportsApi } from "./source-imports";

const api = createSourceImportsApi(createApiClient({ baseUrl: "http://localhost" }));

describe("source import contracts", () => {
  it("preserves relative file names in multipart uploads", async () => {
    let receivedFileName = "";
    const multipartApi = createSourceImportsApi(
      createApiClient({
        baseUrl: "http://localhost",
        fetchImplementation: async (_input, init) => {
          const formData = init?.body as FormData;
          receivedFileName = (formData.get("files") as File).name;
          expect(formData.get("sourceId")).toBe("12");
          return jsonResponse(sourceRunFixture());
        },
      }),
    );
    const file = new File(["# Intro"], "intro.md", { type: "text/markdown" });

    const run = await multipartApi.upload([{ file, path: "docs/intro.md" }], 12);

    expect(receivedFileName).toBe("docs/intro.md");
    expect(run.runId).toBe(33);
    expect(run.progressSteps[0]?.status).toBe("COMPLETED");
  });

  it("creates, validates and synchronizes a Git source with exact request fields", async () => {
    let requestBody: unknown;
    server.use(
      http.post("http://localhost/api/v1/admin/sources/git", async ({ request }) => {
        requestBody = await request.json();
        return HttpResponse.json(sourceDetailFixture());
      }),
      http.post("http://localhost/api/v1/admin/sources/12/validate", () =>
        HttpResponse.json(sourceValidationFixture()),
      ),
      http.post("http://localhost/api/v1/admin/sources/12/sync", () =>
        HttpResponse.json(sourceRunFixture()),
      ),
    );

    const source = await api.createGit({
      sourceCode: "payments-docs",
      name: "Payments Docs",
      remoteUrl: "https://git.example.com/payments/docs.git",
      branch: "main",
      credentialRef: "git-private-main",
      contentProfile: "DOCUMENT",
      visibility: "NORMAL",
      defaultSyncMode: "AUTO",
    });
    const validation = await api.validate(source.id);
    const run = await api.sync(source.id);

    expect(requestBody).toMatchObject({
      remoteUrl: "https://git.example.com/payments/docs.git",
      credentialRef: "git-private-main",
    });
    expect(validation.valid).toBe(true);
    expect(run.sourceId).toBe(12);
  });

  it("loads source-scoped runs without inventing pagination", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/sources/12/runs", () =>
        HttpResponse.json([sourceRunFixture()]),
      ),
    );

    const runs = await api.listRuns(12);

    expect(runs).toHaveLength(1);
    expect(runs[0]?.runId).toBe(33);
  });

  it("sends credential secrets once and only accepts a masked response", async () => {
    let requestBody: Record<string, unknown> = {};
    server.use(
      http.post("http://localhost/api/v1/admin/source-credentials", async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(sourceCredentialFixture());
      }),
    );

    const credential = await api.saveCredential({
      credentialCode: "git-private-main",
      credentialType: "GIT_TOKEN",
      secret: "ghp_sensitive",
      updatedBy: "tester",
    });

    expect(requestBody.secret).toBe("ghp_sensitive");
    expect(credential.secretMask).toBe("ghp_***");
    expect(credential).not.toHaveProperty("secret");
  });

  it("maps server directory and direct upload compile requests", async () => {
    const requests: Array<Record<string, unknown>> = [];
    server.use(
      http.post("http://localhost/api/v1/admin/compile/jobs", async ({ request }) => {
        requests.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json(compileJobFixture());
      }),
    );
    const multipartApi = createSourceImportsApi(
      createApiClient({
        baseUrl: "http://localhost",
        fetchImplementation: async (_input, init) => {
          const formData = init?.body as FormData;
          requests.push({
            fileName: (formData.get("files") as File).name,
            incremental: formData.get("incremental"),
            async: formData.get("async"),
          });
          return jsonResponse(compileJobFixture({ sourceNames: ["docs/a.md"] }));
        },
      }),
    );

    await api.compileDirectory({ sourceDir: "/srv/lattice/docs", incremental: false, async: true });
    await multipartApi.compileUpload(
      [{ file: new File(["a"], "a.md"), path: "docs/a.md" }],
      true,
    );

    expect(requests).toEqual([
      { sourceDir: "/srv/lattice/docs", incremental: false, async: true },
      { fileName: "docs/a.md", incremental: "true", async: "true" },
    ]);
  });
});

function jsonResponse(payload: unknown) {
  return new Response(JSON.stringify(payload), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
