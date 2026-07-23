import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import {
  compileJobFixture,
  sourceCredentialFixture,
  sourceDetailFixture,
  sourceRunFixture,
  sourceValidationFixture,
} from "../../test/source-import-fixtures";
import { server } from "../../test/server";
import { SourceImportWorkspace } from "./source-import-workspace";

describe("source import workspace", () => {
  it("uploads a selected directory and exposes source run destinations", async () => {
    const user = userEvent.setup();
    let uploadCount = 0;
    server.use(
      http.post("/api/v1/admin/uploads", () => {
        uploadCount += 1;
        return HttpResponse.json(
          sourceRunFixture({ sourceType: "UPLOAD", sourceName: "Local Docs" }),
        );
      }),
    );
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: "下一步" }));
    const file = new File(["# Local"], "local.md", { type: "text/markdown" });
    Object.defineProperty(file, "webkitRelativePath", { value: "docs/local.md" });

    await user.upload(screen.getByLabelText("选择文件夹"), file);
    expect(screen.getByText("docs/local.md")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "开始导入" }));

    expect(await screen.findByText("导入已提交")).toBeVisible();
    expect(uploadCount).toBe(1);
    expect(screen.getByRole("link", { name: "查看资料源" })).toHaveAttribute(
      "href",
      "/library/sources/12",
    );
    expect(screen.getByRole("link", { name: "查看处理任务" })).toHaveAttribute(
      "href",
      "/activity?kind=source-run&id=33",
    );
  });

  it("saves a masked credential and imports a private Git repository", async () => {
    const user = userEvent.setup();
    let credentialRequest: Record<string, unknown> = {};
    let sourceRequest: Record<string, unknown> = {};
    server.use(
      http.get("/api/v1/admin/source-credentials", () => HttpResponse.json([])),
      http.post("/api/v1/admin/source-credentials", async ({ request }) => {
        credentialRequest = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(sourceCredentialFixture());
      }),
      http.post("/api/v1/admin/sources/git", async ({ request }) => {
        sourceRequest = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(sourceDetailFixture());
      }),
      http.post("/api/v1/admin/sources/12/validate", () =>
        HttpResponse.json(sourceValidationFixture()),
      ),
      http.post("/api/v1/admin/sources/12/sync", () =>
        HttpResponse.json(sourceRunFixture()),
      ),
    );
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: /Git 仓库/ }));
    await user.click(screen.getByRole("button", { name: "下一步" }));
    await user.click(screen.getByRole("button", { name: "私有仓库" }));
    await user.click(screen.getByText("新增访问凭据"));
    await user.type(screen.getByLabelText("凭据编码"), "git-private-main");
    await user.type(screen.getByLabelText("凭据明文"), "ghp_sensitive");
    await user.click(screen.getByRole("button", { name: "保存凭据" }));

    expect(await screen.findByText(/git-private-main 已保存/)).toBeVisible();
    expect(screen.getByLabelText("凭据明文")).toHaveValue("");
    expect(screen.getByLabelText("访问凭据")).toHaveValue("git-private-main");
    await user.type(
      screen.getByLabelText("仓库地址"),
      "https://git.example.com/payments/docs.git",
    );
    await user.click(screen.getByRole("button", { name: "开始导入" }));

    expect(await screen.findByText("导入已提交")).toBeVisible();
    expect(credentialRequest.secret).toBe("ghp_sensitive");
    expect(sourceRequest).toMatchObject({
      name: "docs",
      credentialRef: "git-private-main",
      branch: "main",
    });
    expect(document.body).not.toHaveTextContent("ghp_sensitive");
  });

  it("keeps the created source recoverable when validation initially fails", async () => {
    const user = userEvent.setup();
    let validationCount = 0;
    server.use(
      http.get("/api/v1/admin/source-credentials", () => HttpResponse.json([])),
      http.post("/api/v1/admin/sources/git", () =>
        HttpResponse.json(sourceDetailFixture()),
      ),
      http.post("/api/v1/admin/sources/12/validate", () => {
        validationCount += 1;
        return validationCount === 1
          ? HttpResponse.json(
              { code: "GIT_UNAVAILABLE", message: "仓库暂时不可访问" },
              { status: 503 },
            )
          : HttpResponse.json(sourceValidationFixture());
      }),
      http.post("/api/v1/admin/sources/12/sync", () =>
        HttpResponse.json(sourceRunFixture()),
      ),
    );
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: /Git 仓库/ }));
    await user.click(screen.getByRole("button", { name: "下一步" }));
    await user.type(screen.getByLabelText("仓库地址"), "https://git.example.com/repo.git");
    await user.click(screen.getByRole("button", { name: "开始导入" }));

    expect(await screen.findByText("资料源已创建，尚未同步")).toBeVisible();
    expect(screen.getByText("仓库暂时不可访问")).toBeVisible();
    expect(screen.getByRole("link", { name: "查看资料源" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "重新校验并同步" }));

    expect(await screen.findByText("导入已提交")).toBeVisible();
    expect(screen.getByRole("link", { name: "查看处理任务" })).toBeVisible();
    expect(validationCount).toBe(2);
  });

  it("submits server directory and direct upload compile jobs", async () => {
    const user = userEvent.setup();
    let directoryRequest: Record<string, unknown> = {};
    let directUploadCount = 0;
    server.use(
      http.post("/api/v1/admin/compile/jobs", async ({ request }) => {
        directoryRequest = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(compileJobFixture());
      }),
      http.post("/api/v1/admin/compile/upload", () => {
        directUploadCount += 1;
        return HttpResponse.json(compileJobFixture({ sourceNames: ["direct.md"] }));
      }),
    );
    const view = renderWorkspace();
    await user.click(screen.getByRole("button", { name: /服务端目录/ }));
    await user.click(screen.getByRole("button", { name: "下一步" }));
    await user.type(screen.getByLabelText("服务端目录"), "/srv/lattice/docs");
    await user.click(screen.getByRole("button", { name: "提交编译" }));

    expect(await screen.findByText("compile-44")).toBeVisible();
    expect(directoryRequest).toEqual({
      sourceDir: "/srv/lattice/docs",
      incremental: false,
      async: true,
    });

    view.unmount();
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: /直接编译/ }));
    await user.click(screen.getByRole("button", { name: "下一步" }));
    await user.upload(
      screen.getByLabelText("选择文件"),
      new File(["direct"], "direct.md", { type: "text/markdown" }),
    );
    await user.click(screen.getByRole("button", { name: "提交编译" }));

    expect(await screen.findByRole("link", { name: "查看编译作业" })).toHaveAttribute(
      "href",
      "/activity?kind=compile-job&id=compile-44",
    );
    expect(directUploadCount).toBe(1);
  });
});

function renderWorkspace() {
  return render(
    <MemoryRouter>
      <AppQueryProvider>
        <SourceImportWorkspace onClose={() => undefined} />
      </AppQueryProvider>
    </MemoryRouter>,
  );
}
