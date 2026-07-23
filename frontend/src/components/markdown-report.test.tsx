import { render, screen, within } from "@testing-library/react";

import { MarkdownReport } from "./markdown-report";

describe("MarkdownReport", () => {
  it("renders GFM tables, task lists, deleted text and code", () => {
    render(
      <MarkdownReport
        content={`# 研究结论

- [x] 已核验
- ~~旧结论~~ 新结论

| 字段 | 值 |
| --- | --- |
| status | READY |

\`\`\`json
{"ready": true}
\`\`\``}
      />,
    );

    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "研究结论" }),
    ).toBeVisible();
    expect(screen.getByRole("checkbox")).toBeChecked();
    expect(screen.getByText("旧结论").tagName).toBe("DEL");
    expect(screen.getByRole("table")).toHaveTextContent("status");
    expect(screen.getByText('{"ready": true}')).toBeVisible();
  });

  it("removes raw HTML, remote images and unsafe link protocols", () => {
    const { container } = render(
      <MarkdownReport
        content={`<script>alert("x")</script>

<img src="https://tracker.example/pixel" onerror="alert(1)">

[危险链接](javascript:alert(1))

[安全链接](https://example.com/docs)`}
      />,
    );

    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("危险链接").closest("a")).not.toHaveAttribute("href");
    expect(screen.getByRole("link", { name: "安全链接" })).toHaveAttribute(
      "rel",
      "noreferrer",
    );
  });

  it("keeps long content inside the report boundary", () => {
    const longToken = "a".repeat(4000);
    render(<MarkdownReport content={longToken} label="长报告" />);

    const report = screen.getByRole("article", { name: "长报告" });
    expect(report).toHaveClass("markdown-report");
    expect(within(report).getByText(longToken)).toBeVisible();
  });
});
