import { Search } from "lucide-react";

import { useListUrlState } from "../state/list-url-state";
import { PageHeader } from "./page-header";
import { PageState } from "./page-state";

interface PageScaffoldProps {
  title: string;
  emptyLabel: string;
  context?: string;
  searchable?: boolean;
}

export function PageScaffold({
  title,
  emptyLabel,
  context,
  searchable = false,
}: PageScaffoldProps) {
  const [listState, setListState] = useListUrlState();
  return (
    <div className="page-frame">
      <PageHeader context={context} title={title} />
      {searchable ? (
        <div className="page-toolbar">
          <label className="search-field">
            <Search aria-hidden="true" size={17} />
            <span className="sr-only">搜索{title}</span>
            <input
              onChange={(event) =>
                setListState(
                  { query: event.target.value, page: 1 },
                  { replace: true },
                )
              }
              placeholder={`搜索${title}`}
              type="search"
              value={listState.query}
            />
          </label>
          <span className="result-count">0 项</span>
        </div>
      ) : null}
      <PageState status="empty" title={emptyLabel} />
    </div>
  );
}
