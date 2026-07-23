import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useLocation, useNavigate, MemoryRouter } from "react-router-dom";

import { useListUrlState } from "./list-url-state";

function StateHarness() {
  const [state, setState] = useListUrlState();
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <output aria-label="state">{JSON.stringify(state)}</output>
      <output aria-label="location">{location.search}</output>
      <button
        onClick={() =>
          setState({ query: "mirror", page: 1, selected: "source-7" })
        }
        type="button"
      >
        更新
      </button>
      <button onClick={() => navigate(-1)} type="button">
        返回
      </button>
    </>
  );
}

describe("list URL state", () => {
  it("restores filters and selection with browser back navigation", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter
        initialEntries={["/library/sources?q=git&page=2&selected=source-3"]}
      >
        <StateHarness />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("button", { name: "更新" }));
    expect(screen.getByLabelText("location")).toHaveTextContent(
      "?q=mirror&selected=source-7",
    );

    await user.click(screen.getByRole("button", { name: "返回" }));
    await waitFor(() =>
      expect(screen.getByLabelText("state")).toHaveTextContent('"query":"git"'),
    );
    expect(screen.getByLabelText("state")).toHaveTextContent('"page":2');
    expect(screen.getByLabelText("state")).toHaveTextContent(
      '"selected":"source-3"',
    );
  });

  it("reconstructs valid state after a fresh render and normalizes invalid values", () => {
    render(
      <MemoryRouter
        initialEntries={[
          "/activity?q=%20failed%20&page=-1&size=999&order=sideways&sourceType=GIT&view=jobs",
        ]}
      >
        <StateHarness />
      </MemoryRouter>,
    );

    expect(screen.getByLabelText("state")).toHaveTextContent(
      '"query":"failed"',
    );
    expect(screen.getByLabelText("state")).toHaveTextContent('"page":1');
    expect(screen.getByLabelText("state")).toHaveTextContent('"size":20');
    expect(screen.getByLabelText("state")).toHaveTextContent('"order":"desc"');
    expect(screen.getByLabelText("state")).toHaveTextContent(
      '"sourceType":"GIT"',
    );
    expect(screen.getByLabelText("location")).toHaveTextContent("view=jobs");
  });
});
