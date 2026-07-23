import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  llmBindingFixture,
  llmConnectionFixture,
  llmModelFixture,
} from "../../test/llm-settings-fixtures";
import { server } from "../../test/server";
import ModelSettingsPage from "./model-settings-page";

describe("model settings page", () => {
  it("shows only the masked stored secret and tests without persisting", async () => {
    let testBody: unknown;
    server.use(
      connectionListHandler(),
      http.post("/api/v1/admin/llm/connections/test", async ({ request }) => {
        testBody = await request.json();
        return HttpResponse.json({
          success: true,
          providerType: "openai_compatible",
          latencyMs: 18,
          endpoint: "/v1/models",
          message: "连接测试成功",
        });
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/models?view=connections&id=1");

    const apiKey = await screen.findByLabelText("API 密钥");
    expect(apiKey).toHaveValue("");
    expect(apiKey).toHaveAttribute("placeholder", "sk-test****7788");
    expect(screen.queryByDisplayValue("sk-plaintext-secret")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "测试连接" }));

    expect(await screen.findByText("连接测试成功")).toBeVisible();
    expect(testBody).toEqual({
      connectionId: 1,
      providerType: "openai_compatible",
      baseUrl: "http://127.0.0.1:8888",
      apiKey: "",
    });
  });

  it("keeps edited fields after a failed save", async () => {
    server.use(
      connectionListHandler(),
      http.put("/api/v1/admin/llm/connections/1", () => HttpResponse.json(
        { code: "CONFIG_CHANGED", message: "连接配置已被其他操作更新" },
        { status: 409 },
      )),
    );
    const user = userEvent.setup();
    renderPage("/settings/models?view=connections&id=1");

    const remarks = await screen.findByLabelText("备注");
    await user.clear(remarks);
    await user.type(remarks, "保留这次编辑内容");
    await user.click(screen.getByRole("button", { name: "保存连接" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("连接配置已被其他操作更新");
    expect(remarks).toHaveValue("保留这次编辑内容");
  });

  it("blocks an embedding model save until a positive dimension is supplied", async () => {
    let modelWrites = 0;
    server.use(
      connectionListHandler(),
      modelListHandler(),
      bindingListHandler(),
      http.put("/api/v1/admin/llm/models/:id", () => {
        modelWrites += 1;
        return HttpResponse.json(llmModelFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/models?view=models&id=1");

    await user.click(await screen.findByRole("button", { name: "EMBEDDING" }));
    await user.click(screen.getByRole("button", { name: "保存模型" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Embedding 模型必须填写正整数向量维度");
    expect(modelWrites).toBe(0);
  });

  it("prevents duplicate scene-role bindings before sending a request", async () => {
    let bindingWrites = 0;
    server.use(
      modelListHandler(),
      bindingListHandler(),
      http.post("/api/v1/admin/llm/bindings", () => {
        bindingWrites += 1;
        return HttpResponse.json(llmBindingFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/models?view=bindings");

    await user.click(await screen.findByRole("button", { name: "新增" }));
    await user.click(screen.getByRole("button", { name: "保存绑定" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("该场景角色已存在绑定 #1");
    expect(bindingWrites).toBe(0);
  });

  it("requires confirmation before discarding a dirty form or deleting a record", async () => {
    server.use(connectionListHandler());
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);
    renderPage("/settings/models?view=connections&id=1");

    await user.type(await screen.findByLabelText("备注"), " 尚未保存");
    await waitFor(() => expect(screen.getByText("未保存")).toBeVisible());
    await user.click(screen.getByRole("tab", { name: "模型档案" }));

    expect(confirmSpy).toHaveBeenCalledWith("当前表单有未保存改动，确定放弃并继续吗？");
    expect(screen.getByRole("heading", { name: "编辑 local_openai" })).toBeVisible();

    await user.click(screen.getByRole("button", { name: "删除模型连接" }));
    const dialog = screen.getByRole("dialog", { name: "删除模型连接" });
    expect(within(dialog).getByText("当前连接已启用，请先确认没有模型仍在使用")).toBeVisible();
    expect(within(dialog).getByRole("button", { name: "确认删除连接" })).toBeVisible();
    confirmSpy.mockRestore();
  });
});

function renderPage(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <ModelSettingsPage />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}

function connectionListHandler() {
  return http.get("/api/v1/admin/llm/connections", () => HttpResponse.json({ count: 1, items: [llmConnectionFixture()] }));
}

function modelListHandler() {
  return http.get("/api/v1/admin/llm/models", () => HttpResponse.json({ count: 1, items: [llmModelFixture()] }));
}

function bindingListHandler() {
  return http.get("/api/v1/admin/llm/bindings", () => HttpResponse.json({ count: 1, items: [llmBindingFixture()] }));
}
