import type { ReactNode } from "react";

import { RouteHeading } from "./route-heading";

interface PageHeaderProps {
  title: string;
  context?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, context, actions }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-heading-group">
        <RouteHeading>{title}</RouteHeading>
        {context ? <code className="page-context">{context}</code> : null}
      </div>
      {actions ? <div className="page-header-actions">{actions}</div> : null}
    </header>
  );
}
