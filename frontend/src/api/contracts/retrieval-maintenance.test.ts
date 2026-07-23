import { createApiClient } from "../api-client";
import {
  chunkRebuildResultFixture,
  repoBaselineResultFixture,
  repoDiffFixture,
  repoRollbackResultFixture,
  repoSnapshotFixture,
  retrievalAuditDetailFixture,
  retrievalAuditRunFixture,
  retrievalConfigFixture,
  vaultExportResultFixture,
  vaultSyncResultFixture,
} from "../../test/retrieval-maintenance-fixtures";
import { createRepositoryMaintenanceApi } from "./repository-maintenance";
import { createRetrievalSettingsApi } from "./retrieval-settings";

describe("retrieval and repository maintenance API contracts", () => {
  it("parses retrieval configuration, recent runs, and latest channel hits", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(retrievalConfigFixture()))
      .mockResolvedValueOnce(jsonResponse({ count: 1, items: [retrievalAuditRunFixture()] }))
      .mockResolvedValueOnce(jsonResponse(retrievalAuditDetailFixture()));
    const api = createRetrievalSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    const config = await api.getConfig();
    const recent = await api.listRecent(10);
    const detail = await api.getLatest("query-208", 3);

    expect(config).toMatchObject({ parallelEnabled: true, rrfK: 60 });
    expect(recent.items[0].channelRuns).toHaveLength(2);
    expect(detail.channelHits[0]).toMatchObject({ fusedRank: 2, channelName: "fts" });
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/admin/query/retrieval/audits/recent?limit=10");
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/admin/query/retrieval/audits/latest?queryId=query-208&historyLimit=3");
  });

  it("sends the complete retrieval configuration and parses synchronous chunk rebuilds", async () => {
    const config = retrievalConfigFixture({ graphWeight: 0 });
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(config))
      .mockResolvedValueOnce(jsonResponse(chunkRebuildResultFixture()));
    const api = createRetrievalSettingsApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.updateConfig(config);
    const rebuild = await api.rebuildChunks();

    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual(config);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/admin/query/retrieval/config");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/admin/compile/rebuild-chunks");
    expect(rebuild.articleChunkCount).toBe(320);
  });

  it("keeps the Vault path explicit for history diff and baseline requests", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ count: 1, items: [repoSnapshotFixture()] }))
      .mockResolvedValueOnce(jsonResponse(repoDiffFixture()))
      .mockResolvedValueOnce(jsonResponse(repoBaselineResultFixture()));
    const api = createRepositoryMaintenanceApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.listSnapshots(8);
    await api.getDiff(12, "/tmp/lattice vault");
    await api.createBaseline({ vaultDir: "/tmp/lattice-vault", description: "release" });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/admin/snapshot/repo?limit=8");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/admin/snapshot/repo/12/diff?vaultDir=%2Ftmp%2Flattice+vault");
    expect(JSON.parse(String((fetchMock.mock.calls[2][1] as RequestInit).body))).toEqual({
      vaultDir: "/tmp/lattice-vault",
      description: "release",
    });
  });

  it("sends both target snapshot and Vault path for repository rollback", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse(repoRollbackResultFixture()));
    const api = createRepositoryMaintenanceApi(createApiClient({ fetchImplementation: fetchMock }));

    const result = await api.rollback({ snapshotId: 12, vaultDir: "/tmp/lattice-vault" });

    expect(result.restoredSnapshotId).toBe(12);
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual({
      snapshotId: 12,
      vaultDir: "/tmp/lattice-vault",
    });
  });

  it("sends explicit Vault targets and force semantics for export and sync", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(vaultExportResultFixture()))
      .mockResolvedValueOnce(jsonResponse(vaultSyncResultFixture()));
    const api = createRepositoryMaintenanceApi(createApiClient({ fetchImplementation: fetchMock }));

    await api.exportVault({ vaultDir: "/tmp/lattice-vault" });
    await api.syncVault({ vaultDir: "/tmp/lattice-vault", force: false });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/admin/vault/export");
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual({
      vaultDir: "/tmp/lattice-vault",
    });
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/admin/vault/sync");
    expect(JSON.parse(String((fetchMock.mock.calls[1][1] as RequestInit).body))).toEqual({
      vaultDir: "/tmp/lattice-vault",
      force: false,
    });
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}
