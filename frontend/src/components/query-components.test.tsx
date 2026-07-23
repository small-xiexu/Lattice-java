import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { InlineAlert } from "./inline-alert";
import { ModeSelector, type QueryMode } from "./mode-selector";
import { PageHeader } from "./page-header";
import { SplitView } from "./split-view";

describe("query foundation components", () => {
  it("renders one route heading with contextual actions", () => {
    render(
      <PageHeader
        actions={<button type="button">新建</button>}
        context="source-42"
        title="资料源详情"
      />,
    );
    expect(
      screen.getByRole("heading", { level: 1, name: "资料源详情" }),
    ).toBeVisible();
    expect(screen.getByText("source-42")).toBeVisible();
    expect(screen.getByRole("button", { name: "新建" })).toBeVisible();
  });

  it("keeps query modes mutually exclusive", async () => {
    const user = userEvent.setup();
    let selected: QueryMode = "auto";
    const { rerender } = render(
      <ModeSelector onChange={(mode) => (selected = mode)} value={selected} />,
    );

    await user.click(screen.getByRole("radio", { name: "深度研究" }));
    expect(selected).toBe("deep");
    rerender(<ModeSelector onChange={(mode) => (selected = mode)} value={selected} />);
    expect(screen.getByRole("radio", { name: "深度研究" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "智能模式" })).not.toBeChecked();
  });

  it("provides a nearby recovery action for failures", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <InlineAlert
        actionLabel="重试"
        onAction={onRetry}
        title="请求失败"
        tone="error"
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("请求失败");
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("keeps the evidence region as a named complementary landmark", () => {
    render(
      <SplitView
        primary={<p>回答</p>}
        secondary={<p>证据</p>}
        secondaryLabel="证据面板"
      />,
    );
    expect(screen.getByText("回答")).toBeVisible();
    expect(screen.getByRole("complementary", { name: "证据面板" })).toBeVisible();
  });
});
