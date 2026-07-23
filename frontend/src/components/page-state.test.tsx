import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import { PageState } from "./page-state";
import { RouteErrorBoundary } from "./route-error-boundary";

function ThrowingContent({ shouldThrow }: { shouldThrow: () => boolean }) {
  if (shouldThrow()) {
    throw new Error("sensitive stack details");
  }
  return <p>恢复成功</p>;
}

describe("page states", () => {
  it.each([
    ["loading", "正在加载"],
    ["empty", "暂无数据"],
    ["error", "请求失败"],
  ] as const)("renders the %s state", (status, title) => {
    render(<PageState status={status} title={title} />);
    expect(screen.getByRole("heading", { name: title })).toBeVisible();
  });

  it("isolates a route render error without exposing the raw exception", async () => {
    const user = userEvent.setup();
    let shouldThrow = true;
    render(
      <MemoryRouter>
        <RouteErrorBoundary onError={() => (shouldThrow = false)}>
          <ThrowingContent shouldThrow={() => shouldThrow} />
        </RouteErrorBoundary>
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("heading", { name: "页面暂时无法显示" }),
    ).toBeVisible();
    expect(screen.queryByText("sensitive stack details")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(screen.getByText("恢复成功")).toBeVisible();
  });
});
