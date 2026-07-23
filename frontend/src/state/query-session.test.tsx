import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";

import { QuerySessionProvider } from "./query-session-provider";
import { useQuerySession } from "./use-query-session";

function AskHarness() {
  const { session, setResult, selectCitation } = useQuerySession();
  const navigate = useNavigate();
  return (
    <>
      <output aria-label="query-session">
        {session
          ? `${session.question}:${session.selectedCitationMarkerId ?? "none"}`
          : "empty"}
      </output>
      <button
        onClick={() => setResult("问题", { queryId: "q-1", answer: "结果" })}
        type="button"
      >
        保存结果
      </button>
      <button onClick={() => selectCitation("citation-2")} type="button">
        选择引用
      </button>
      <button onClick={() => navigate("/other")} type="button">
        离开
      </button>
    </>
  );
}

function OtherHarness() {
  const navigate = useNavigate();
  return (
    <button onClick={() => navigate(-1)} type="button">
      返回问答
    </button>
  );
}

function TestApplication() {
  return (
    <QuerySessionProvider>
      <MemoryRouter initialEntries={["/ask"]}>
        <Routes>
          <Route path="ask" element={<AskHarness />} />
          <Route path="other" element={<OtherHarness />} />
        </Routes>
      </MemoryRouter>
    </QuerySessionProvider>
  );
}

describe("query session memory", () => {
  it("survives route navigation but resets with a new application instance", async () => {
    const user = userEvent.setup();
    const application = render(<TestApplication />);

    await user.click(screen.getByRole("button", { name: "保存结果" }));
    await user.click(screen.getByRole("button", { name: "选择引用" }));
    await user.click(screen.getByRole("button", { name: "离开" }));
    await user.click(screen.getByRole("button", { name: "返回问答" }));
    expect(screen.getByLabelText("query-session")).toHaveTextContent(
      "问题:citation-2",
    );

    application.unmount();
    render(<TestApplication />);
    expect(screen.getByLabelText("query-session")).toHaveTextContent("empty");
  });
});
