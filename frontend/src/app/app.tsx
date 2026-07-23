import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { LiveAnnouncerProvider } from "../accessibility/live-announcer-provider";
import { AppQueryProvider } from "../api/query-provider";
import { QuerySessionProvider } from "../state/query-session-provider";
import { AppShell } from "./app-shell";

const AskPage = lazy(() => import("../features/ask/ask-page"));
const SourceListPage = lazy(
  () => import("../features/library/source-list-page"),
);
const SourceDetailPage = lazy(
  () => import("../features/library/source-detail-page"),
);
const ArticleListPage = lazy(
  () => import("../features/library/article-list-page"),
);
const ArticleDetailPage = lazy(
  () => import("../features/library/article-detail-page"),
);
const QualityPage = lazy(
  () => import("../features/library/quality-page"),
);
const ActivityPage = lazy(
  () => import("../features/activity/activity-page"),
);
const ReviewsPage = lazy(() => import("../features/reviews/reviews-page"));
const FeedbackPage = lazy(
  () => import("../features/feedback/feedback-page"),
);
const ModelSettingsPage = lazy(
  () => import("../features/settings/model-settings-page"),
);
const VectorSettingsPage = lazy(
  () => import("../features/settings/vector-settings-page"),
);
const ParsingSettingsPage = lazy(
  () => import("../features/settings/parsing-settings-page"),
);
const RetrievalSettingsPage = lazy(
  () => import("../features/settings/retrieval-settings-page"),
);
const MaintenancePage = lazy(
  () => import("../features/settings/maintenance-page"),
);
const DeveloperPage = lazy(
  () => import("../features/developer/developer-page"),
);

export function App() {
  return (
    <AppQueryProvider>
      <LiveAnnouncerProvider>
        <QuerySessionProvider>
          <Routes>
        <Route element={<AppShell />}>
          <Route path="ask" element={<AskPage />} />
          <Route path="library/sources" element={<SourceListPage />} />
          <Route
            path="library/sources/:sourceId"
            element={<SourceDetailPage />}
          />
          <Route path="library/articles" element={<ArticleListPage />} />
          <Route
            path="library/articles/:articleKey"
            element={<ArticleDetailPage />}
          />
          <Route path="library/quality" element={<QualityPage />} />
          <Route path="activity" element={<ActivityPage />} />
          <Route path="reviews" element={<ReviewsPage />} />
          <Route path="feedback" element={<FeedbackPage />} />
          <Route path="settings/models" element={<ModelSettingsPage />} />
          <Route path="settings/vector" element={<VectorSettingsPage />} />
          <Route path="settings/parsing" element={<ParsingSettingsPage />} />
          <Route
            path="settings/retrieval"
            element={<RetrievalSettingsPage />}
          />
          <Route path="settings/maintenance" element={<MaintenancePage />} />
          <Route path="developer" element={<DeveloperPage />} />
          <Route index element={<Navigate replace to="ask" />} />
          <Route path="*" element={<Navigate replace to="ask" />} />
        </Route>
          </Routes>
        </QuerySessionProvider>
      </LiveAnnouncerProvider>
    </AppQueryProvider>
  );
}
