import { useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";

import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { FeedbackQueue } from "./feedback-queue";

export default function FeedbackPage() {
  const queryClient = useQueryClient();
  return (
    <div className="page-frame feedback-page">
      <PageHeader
        actions={(
          <button
            aria-label="刷新反馈治理数据"
            className="icon-button feedback-refresh-button"
            onClick={() => void queryClient.invalidateQueries({ queryKey: queryKeys.feedback.root })}
            title="刷新反馈治理数据"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="query feedback governance"
        title="结果反馈"
      />
      <FeedbackQueue />
    </div>
  );
}
