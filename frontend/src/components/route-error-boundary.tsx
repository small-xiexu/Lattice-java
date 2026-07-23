import { Component, type ErrorInfo, type ReactNode } from "react";
import { Link } from "react-router-dom";

import { PageState } from "./page-state";
import { RouteHeading } from "./route-heading";

interface RouteErrorBoundaryProps {
  children: ReactNode;
  onError?: (error: unknown, errorInfo: ErrorInfo) => void;
}

interface RouteErrorBoundaryState {
  failed: boolean;
}

export class RouteErrorBoundary extends Component<
  RouteErrorBoundaryProps,
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: unknown, errorInfo: ErrorInfo) {
    this.props.onError?.(error, errorInfo);
  }

  private readonly retry = () => {
    this.setState({ failed: false });
  };

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <div className="page-frame route-error-page">
        <header className="page-header">
          <RouteHeading>页面暂时无法显示</RouteHeading>
        </header>
        <PageState
          actionLabel="重试"
          description="当前页面发生异常，未提交的数据可能仍保留在浏览器内存中。"
          onAction={this.retry}
          status="error"
          title="加载页面失败"
        />
        <Link className="route-error-link" to="/ask">
          返回问答与研究
        </Link>
      </div>
    );
  }
}
