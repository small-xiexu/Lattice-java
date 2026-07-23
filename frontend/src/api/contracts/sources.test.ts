import { http, HttpResponse } from "msw";

import { createApiClient } from "../api-client";
import { server } from "../../test/server";
import { sourceDetailFixture } from "../../test/source-import-fixtures";
import { createSourcesApi, sourcePageSchema } from "./sources";

const client = createApiClient({ baseUrl: "http://localhost" });

describe("sources contract", () => {
  it("maps every list parameter and validates the exact summary response", async () => {
    server.use(
      http.get("http://localhost/api/v1/admin/sources", ({ request }) => {
        const url = new URL(request.url);
        expect(Object.fromEntries(url.searchParams)).toEqual({
          keyword: "支付",
          status: "ACTIVE",
          sourceType: "GIT",
          page: "2",
          size: "10",
        });
        return HttpResponse.json(sourcePageFixture());
      }),
    );

    const response = await createSourcesApi(client).list({
      keyword: "支付",
      status: "ACTIVE",
      sourceType: "GIT",
      page: 2,
      size: 10,
    });

    expect(response.items[0]).toMatchObject({
      id: 7,
      sourceCode: "payments-git",
      sourceType: "GIT",
      status: "ACTIVE",
      lastSyncRunId: 19,
    });
  });

  it("rejects malformed enum and pagination fields", () => {
    expect(() =>
      sourcePageSchema.parse({
        ...sourcePageFixture(),
        page: 0,
        items: [{ ...sourcePageFixture().items[0], sourceType: "HTTP" }],
      }),
    ).toThrow();
  });

  it("loads source files and sends editable fields as a JSON patch", async () => {
    let patchBody: unknown;
    server.use(
      http.get("http://localhost/api/v1/admin/sources/12/files", () =>
        HttpResponse.json([
          {
            id: 41,
            sourceId: 12,
            relativePath: "docs/readme.md",
            format: "md",
            fileSize: 128,
            parseMode: "text_read",
            parseProvider: "filesystem",
            contentPreview: "# Readme",
          },
        ]),
      ),
      http.patch("http://localhost/api/v1/admin/sources/12", async ({ request }) => {
        patchBody = await request.json();
        return HttpResponse.json(
          sourceDetailFixture({ name: "Payments V2", configJson: '{"branch":"release"}' }),
        );
      }),
    );
    const api = createSourcesApi(client);

    const files = await api.files(12);
    const source = await api.update(12, {
      name: "Payments V2",
      status: "ACTIVE",
      visibility: "NORMAL",
      defaultSyncMode: "INCREMENTAL",
      configJson: { remoteUrl: "https://git.example.com/docs.git", branch: "release" },
    });

    expect(files[0]?.relativePath).toBe("docs/readme.md");
    expect(patchBody).toMatchObject({
      name: "Payments V2",
      defaultSyncMode: "INCREMENTAL",
      configJson: { branch: "release" },
    });
    expect(source.name).toBe("Payments V2");
  });
});

function sourcePageFixture() {
  return {
    page: 2,
    size: 10,
    total: 12,
    items: [
      {
        id: 7,
        sourceCode: "payments-git",
        name: "Payments Git",
        displayName: "支付知识库",
        primaryDocumentTitle: "支付接入说明",
        sourceType: "GIT",
        contentProfile: "DOCUMENT",
        status: "ACTIVE",
        visibility: "NORMAL",
        defaultSyncMode: "INCREMENTAL",
        lastSyncRunId: 19,
        lastSyncStatus: "SUCCEEDED",
        lastSyncAt: "2026-07-22T20:10:00+08:00",
        updatedAt: "2026-07-22T20:10:02+08:00",
      },
    ],
  };
}
