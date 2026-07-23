import axe from "axe-core";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router-dom";

import { App } from "../app";
import { overviewFixture } from "../test/quality-fixtures";
import { server } from "../test/server";
import { LiveAnnouncerProvider } from "./live-announcer-provider";
import { useLiveAnnouncer } from "./use-live-announcer";

function AnnouncementHarness() {
  const { announce } = useLiveAnnouncer();
  return (
    <button onClick={() => announce("保存成功")} type="button">
      保存
    </button>
  );
}

describe("accessibility baseline", () => {
  beforeEach(() => {
    server.use(
      http.get("/api/v1/admin/overview", () => HttpResponse.json(overviewFixture())),
    );
  });

  it("provides landmarks, skip navigation, route focus and no axe violations", async () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/library/sources"]}>
        <App />
      </MemoryRouter>,
    );

    const heading = await screen.findByRole("heading", { name: "资料源" });
    await waitFor(() => expect(heading).toHaveFocus());
    expect(screen.getByRole("link", { name: "跳到主内容" })).toHaveAttribute(
      "href",
      "#main-content",
    );
    expect(screen.getByRole("navigation", { name: "主导航" })).toBeVisible();
    expect(screen.getByRole("main")).toHaveAttribute("id", "main-content");

    const results = await axe.run(container, {
      rules: { "color-contrast": { enabled: false } },
    });
    expect(results.violations).toEqual([]);
  });

  it("traps focus in the command dialog and restores the trigger focus", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/ask"]}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: "问答与研究" });
    const trigger = screen.getByRole("button", { name: "搜索" });
    trigger.focus();
    await user.click(trigger);

    const input = screen.getByRole("textbox", { name: "搜索页面" });
    await waitFor(() => expect(input).toHaveFocus());
    await user.keyboard("{Shift>}{Tab}{/Shift}");
    expect(screen.getByRole("button", { name: "开发者接入" })).toHaveFocus();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("announces repeated asynchronous outcomes through the global live region", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <LiveAnnouncerProvider>
        <AnnouncementHarness />
      </LiveAnnouncerProvider>,
    );

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(container.querySelector("#global-announcer")).toHaveTextContent(
      "保存成功",
    );
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(container.querySelector("#global-announcer span")).toHaveTextContent(
      "保存成功",
    );
  });
});
