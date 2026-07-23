import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { AppQueryProvider } from "../../api/query-provider";
import { llmModelFixture } from "../../test/llm-settings-fixtures";
import {
  vectorConfigFixture,
  vectorRebuildFixture,
  vectorStatusFixture,
} from "../../test/vector-settings-fixtures";
import { server } from "../../test/server";
import VectorSettingsPage from "./vector-settings-page";

describe("vector settings page", () => {
  it("shows runtime diagnostics and only lists embedding profiles", async () => {
    server.use(...readHandlers());

    renderPage();

    expect(await screen.findByText("40 / 40")).toBeVisible();
    expect(screen.getByText("Profile / Schema").nextElementSibling).toHaveTextContent("2000 / 2000");
    const profile = screen.getByLabelText("Embedding 模型");
    expect(within(profile).getByRole("option", { name: /embedding-main/ })).toBeVisible();
    expect(within(profile).queryByRole("option", { name: /chat-main/ })).not.toBeInTheDocument();
    expect(screen.getByText("ANN 索引未就绪，向量相似度查询可能退化为全表扫描。")).toBeVisible();
  });

  it("keeps the selected profile and dirty state after a failed save", async () => {
    server.use(
      ...readHandlers(),
      http.put("/api/v1/admin/vector/config", () => HttpResponse.json(
        { code: "CONFIG_CONFLICT", message: "向量配置已被其他操作更新" },
        { status: 409 },
      )),
    );
    const user = userEvent.setup();
    renderPage();

    const profile = await screen.findByLabelText("Embedding 模型");
    await user.selectOptions(profile, "3");
    await user.click(screen.getByRole("button", { name: "保存配置" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("向量配置已被其他操作更新");
    expect(profile).toHaveValue("3");
    expect(screen.getByText("未保存")).toBeVisible();
  });

  it("requires cleanup for a dimension mismatch and confirms rebuild before posting", async () => {
    let rebuildBody: unknown;
    let rebuildCount = 0;
    server.use(
      ...readHandlers(vectorStatusFixture({
        profileDimensions: 3072,
        schemaDimensions: 2000,
        dimensionsMatch: false,
        dimensionsConsistent: false,
      })),
      http.post("/api/v1/admin/vector/rebuild", async ({ request }) => {
        rebuildCount += 1;
        rebuildBody = await request.json();
        return HttpResponse.json(vectorRebuildFixture({ truncateFirst: true }));
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "准备重建" }));
    expect(screen.getByText(/维度不一致且存在历史向量，请勾选/)).toBeVisible();
    expect(rebuildCount).toBe(0);

    await user.click(screen.getByLabelText("先清空文章与分块向量"));
    await user.click(screen.getByRole("button", { name: "准备重建" }));
    const dialog = screen.getByRole("dialog", { name: "重建向量索引" });
    expect(within(dialog).getByText("40 篇文章及其全部分块")).toBeVisible();
    expect(within(dialog).getByText("先清空旧向量，再执行全量重建")).toBeVisible();
    expect(rebuildCount).toBe(0);

    await user.click(within(dialog).getByRole("button", { name: "确认重建索引" }));

    await waitFor(() => expect(rebuildCount).toBe(1));
    expect(rebuildBody).toEqual({ truncateFirst: true, operator: "admin" });
    expect(await screen.findByRole("status", { name: "最近重建结果" })).toHaveTextContent("重建完成");
  });
});

function renderPage() {
  return render(
    <AppQueryProvider>
      <VectorSettingsPage />
    </AppQueryProvider>,
  );
}

function readHandlers(status = vectorStatusFixture()) {
  return [
    http.get("/api/v1/admin/vector/config", () => HttpResponse.json(vectorConfigFixture())),
    http.get("/api/v1/admin/vector/status", () => HttpResponse.json(status)),
    http.get("/api/v1/admin/llm/models", () => HttpResponse.json({
      count: 3,
      items: [
        llmModelFixture({ id: 1, modelCode: "chat-main", modelKind: "CHAT" }),
        llmModelFixture({ id: 2, modelCode: "embedding-main", modelName: "embedding-3", modelKind: "EMBEDDING", expectedDimensions: 2000 }),
        llmModelFixture({ id: 3, modelCode: "embedding-large", modelName: "embedding-3-large", modelKind: "EMBEDDING", expectedDimensions: 3072 }),
      ],
    })),
  ];
}
