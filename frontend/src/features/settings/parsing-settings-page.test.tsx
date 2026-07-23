import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  documentParseConnectionFixture,
  documentParsePolicyFixture,
  documentParseProviderFixture,
  textInProviderFixture,
} from "../../test/document-parse-settings-fixtures";
import { llmModelFixture } from "../../test/llm-settings-fixtures";
import { server } from "../../test/server";
import ParsingSettingsPage from "./parsing-settings-page";

describe("document parse settings page", () => {
  it("renders provider-driven fields and applies defaults when the provider changes", async () => {
    server.use(...readHandlers({ connections: [] }));
    const user = userEvent.setup();
    renderPage();

    const provider = await screen.findByLabelText("Provider");
    expect(screen.getByText("尚未配置解析连接")).toBeVisible();
    expect(screen.getByLabelText("Secret ID")).toBeVisible();
    expect(screen.getByLabelText("接口路径")).toHaveValue("/ocr/v1/general-basic");

    await user.selectOptions(provider, "textin_xparse");

    expect(screen.getByLabelText("API 地址")).toHaveValue("https://api.textin.com");
    expect(screen.getByLabelText("App ID")).toBeVisible();
    expect(screen.getByLabelText("解析配置 JSON")).toHaveValue("{}");
    expect(screen.getByText("textin_multipart_sync")).toBeVisible();
  });

  it("does not reveal stored credentials and tests an unchanged saved connection by id", async () => {
    let testBody: unknown;
    server.use(
      ...readHandlers(),
      http.post("/api/v1/admin/document-parse/connections/test", async ({ request }) => {
        testBody = await request.json();
        return HttpResponse.json({
          success: true,
          providerType: "tencent_ocr",
          latencyMs: 28,
          endpoint: "https://ocr.example.test/ocr/v1/general-basic",
          message: "连接测试成功",
        });
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/parsing?id=7");

    const secretId = await screen.findByLabelText("Secret ID");
    expect(secretId).toHaveValue("");
    expect(secretId).toHaveAttribute("placeholder", "请输入 Secret ID；留空保持不变");
    expect(screen.getByText(/已保存 secretId=doc-/)).toBeVisible();
    expect(screen.queryByDisplayValue("doc-secret-id-plaintext")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "测试连接" }));

    expect(await screen.findByText(/连接测试成功/)).toBeVisible();
    expect(testBody).toEqual({
      connectionId: 7,
      providerType: "tencent_ocr",
      baseUrl: "https://ocr.example.test",
      credentialJson: "",
      configJson: "{\"endpointPath\":\"/ocr/v1/general-basic\"}",
    });
  });

  it("keeps connection edits after a failed save and preserves stored credentials", async () => {
    let saveBody: unknown;
    server.use(
      ...readHandlers(),
      http.put("/api/v1/admin/document-parse/connections/7", async ({ request }) => {
        saveBody = await request.json();
        return HttpResponse.json(
          { code: "CONFIG_CHANGED", message: "解析连接已被其他操作更新" },
          { status: 409 },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/parsing?id=7");

    const baseUrl = await screen.findByLabelText("API 地址");
    await user.clear(baseUrl);
    await user.type(baseUrl, "https://ocr-v2.example.test");
    await user.click(screen.getByRole("button", { name: "保存连接" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("解析连接已被其他操作更新");
    expect(baseUrl).toHaveValue("https://ocr-v2.example.test");
    expect(screen.getByText("未保存")).toBeVisible();
    expect(saveBody).toEqual(expect.objectContaining({ credentialJson: "" }));
  });

  it("requires credentials before testing unsaved parameters", async () => {
    let testWrites = 0;
    server.use(
      ...readHandlers(),
      http.post("/api/v1/admin/document-parse/connections/test", () => {
        testWrites += 1;
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/parsing?id=7");

    const baseUrl = await screen.findByLabelText("API 地址");
    await user.clear(baseUrl);
    await user.type(baseUrl, "https://ocr-v2.example.test");
    await user.click(screen.getByRole("button", { name: "测试连接" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("测试未保存参数时需重新填写完整凭证");
    expect(testWrites).toBe(0);
  });

  it("shows policy references before deleting a connection", async () => {
    server.use(...readHandlers());
    const user = userEvent.setup();
    renderPage("/settings/parsing?id=7");

    await user.click(await screen.findByRole("button", { name: "删除解析连接" }));

    const dialog = screen.getByRole("dialog", { name: "删除解析连接" });
    expect(within(dialog).getByText("将清除：图片 OCR、扫描 PDF OCR")).toBeVisible();
    expect(within(dialog).getByText("tencent-ocr-main / #7")).toBeVisible();
    expect(within(dialog).getByRole("button", { name: "确认删除连接" })).toBeVisible();
  });

  it("restricts cleanup to enabled chat models and validates fallback JSON before saving", async () => {
    let policyWrites = 0;
    server.use(
      ...readHandlers(),
      http.put("/api/v1/admin/document-parse/policies/default", () => {
        policyWrites += 1;
        return HttpResponse.json(documentParsePolicyFixture());
      }),
    );
    const user = userEvent.setup();
    renderPage("/settings/parsing?view=policy");

    const cleanupModel = await screen.findByLabelText("后整理对话模型");
    expect(within(cleanupModel).getByRole("option", { name: /chat-main/ })).toBeVisible();
    expect(within(cleanupModel).queryByRole("option", { name: /embedding-main/ })).not.toBeInTheDocument();

    await user.click(screen.getByLabelText("启用识别后整理"));
    await user.click(screen.getByRole("button", { name: "保存策略" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("必须选择对话模型");
    expect(policyWrites).toBe(0);

    await user.selectOptions(cleanupModel, "11");
    const fallback = screen.getByLabelText("降级策略 JSON");
    fireEvent.change(fallback, { target: { value: "[]" } });
    await user.click(screen.getByRole("button", { name: "保存策略" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("必须是合法的 JSON 对象"));
    expect(policyWrites).toBe(0);
  });

  it("keeps policy edits after a failed save", async () => {
    server.use(
      ...readHandlers(),
      http.put("/api/v1/admin/document-parse/policies/default", () => HttpResponse.json(
        { code: "POLICY_CHANGED", message: "默认策略已被其他操作更新" },
        { status: 409 },
      )),
    );
    const user = userEvent.setup();
    renderPage("/settings/parsing?view=policy");

    const imageRoute = await screen.findByLabelText("图片 OCR 连接");
    await user.selectOptions(imageRoute, "");
    await user.click(screen.getByRole("button", { name: "保存策略" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("默认策略已被其他操作更新");
    expect(imageRoute).toHaveValue("");
    expect(screen.getByText("未保存")).toBeVisible();
  });
});

function renderPage(initialEntry = "/settings/parsing") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AppQueryProvider>
        <ParsingSettingsPage />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}

function readHandlers({ connections = [documentParseConnectionFixture()] } = {}) {
  return [
    http.get("/api/v1/admin/document-parse/providers", () => HttpResponse.json({
      count: 2,
      items: [documentParseProviderFixture(), textInProviderFixture()],
    })),
    http.get("/api/v1/admin/document-parse/connections", () => HttpResponse.json({
      count: connections.length,
      items: connections,
    })),
    http.get("/api/v1/admin/document-parse/policies/default", () => HttpResponse.json(documentParsePolicyFixture())),
    http.get("/api/v1/admin/llm/models", () => HttpResponse.json({
      count: 2,
      items: [
        llmModelFixture({ id: 11, modelCode: "chat-main", modelKind: "CHAT", enabled: true }),
        llmModelFixture({ id: 12, modelCode: "embedding-main", modelKind: "EMBEDDING", enabled: true }),
      ],
    })),
  ];
}
