import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { AppQueryProvider } from "../../api/query-provider";
import { server } from "../../test/server";
import DeveloperPage from "./developer-page";

describe("developer access page", () => {
  it("renders the current endpoints, health, and copyable templates", async () => {
    server.use(http.get("/actuator/health", () => HttpResponse.json({ status: "UP" })));
    const writeText = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    renderPage();

    expect(await screen.findByRole("heading", { name: "MCP 接入" })).toBeVisible();
    const runtime = screen.getByRole("complementary", { name: "当前接入信息" });
    expect(within(runtime).getByText(`${window.location.origin}/mcp`)).toBeVisible();
    expect(await within(runtime).findByText("正常")).toBeVisible();

    await user.click(screen.getByRole("button", { name: /^CLI/ }));
    const template = screen.getByRole("region", { name: "状态检查命令" });
    await user.click(within(template).getByRole("button", { name: "复制" }));

    expect(await screen.findByRole("status")).toHaveTextContent("状态检查命令已复制");
    expect(writeText).toHaveBeenCalledWith(`./bin/lattice-cli status --server ${window.location.origin}`);
  });

  it("reports unhealthy Actuator components without claiming the service is healthy", async () => {
    server.use(http.get("/actuator/health", () => HttpResponse.json({
      status: "DOWN",
      components: {
        db: { status: "DOWN" },
        redis: { status: "UP" },
      },
    })));
    renderPage("/developer?section=http");

    expect(await screen.findByRole("heading", { name: "HTTP API 接入" })).toBeVisible();
    const runtime = screen.getByRole("complementary", { name: "当前接入信息" });
    expect(await within(runtime).findByText("DOWN")).toBeVisible();
    expect(within(runtime).getByText("异常组件：db")).toBeVisible();
  });
});

function renderPage(entry = "/developer") {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <AppQueryProvider><DeveloperPage /></AppQueryProvider>
    </MemoryRouter>,
  );
}
