import { useMutation } from "@tanstack/react-query";
import { ArrowRight, Send } from "lucide-react";
import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from "react";
import { Link } from "react-router-dom";

import { useLiveAnnouncer } from "../../accessibility/use-live-announcer";
import {
  queryApi,
  queryResponseSchema,
} from "../../api/contracts/query";
import type { QueryRequest } from "../../api/contracts/query";
import { isApiError } from "../../api/api-error";
import { useAdminOverview } from "../../api/use-admin-overview";
import { locateCitationBindings } from "../../citations/citation-locator";
import { EvidenceInspector } from "../../components/evidence-inspector";
import { InlineAlert } from "../../components/inline-alert";
import { ModeSelector, type QueryMode } from "../../components/mode-selector";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { SplitView } from "../../components/split-view";
import { createAskDraftStore } from "../../state/ask-draft-storage";
import { useQuerySession } from "../../state/use-query-session";
import { QueryAnswer } from "./query-answer";
import { buildQueryRequest } from "./query-request";

interface QueryVariables {
  mode: QueryMode;
  question: string;
  request: QueryRequest;
}

export default function AskPage() {
  const { announce } = useLiveAnnouncer();
  const { session, setResult, selectCitation } = useQuerySession();
  const draftStore = useMemo(() => createAskDraftStore(), []);
  const initialDraft = useMemo(() => draftStore.load(), [draftStore]);
  const [question, setQuestion] = useState(
    () => initialDraft?.question ?? session?.question ?? "",
  );
  const [mode, setMode] = useState<QueryMode>(initialDraft?.mode ?? "auto");
  const [mobileEvidenceOpen, setMobileEvidenceOpen] = useState(false);
  const overviewQuery = useAdminOverview();
  const sessionResult = session?.result;
  const response = useMemo(() => {
    if (!sessionResult) {
      return null;
    }
    const parsedResponse = queryResponseSchema.safeParse(sessionResult);
    return parsedResponse.success ? parsedResponse.data : null;
  }, [sessionResult]);
  const citationBindings = useMemo(
    () =>
      response
        ? locateCitationBindings(
            response.answer ?? "",
            response.citationMarkers,
          )
        : [],
    [response],
  );
  const selectedBinding =
    citationBindings.find(
      (binding) => binding.marker.markerId === session?.selectedCitationMarkerId,
    ) ?? null;

  useEffect(() => {
    draftStore.save({ mode, question });
  }, [draftStore, mode, question]);

  const mutation = useMutation({
    mutationFn: ({ request }: QueryVariables) => queryApi.query(request),
    onMutate: () => {
      selectCitation(null);
      setMobileEvidenceOpen(false);
      announce("正在等待回答");
    },
    onSuccess: (result, variables) => {
      setResult(variables.question, result);
      announce("回答已返回");
    },
    onError: () => announce("查询失败"),
  });

  const submit = (event?: FormEvent) => {
    event?.preventDefault();
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion || mutation.isPending) {
      return;
    }
    mutation.mutate({
      mode,
      question: normalizedQuestion,
      request: buildQueryRequest(normalizedQuestion, mode),
    });
  };

  const activateCitation = (markerId: string) => {
    selectCitation(markerId);
    setMobileEvidenceOpen(true);
  };
  const closeEvidence = () => {
    setMobileEvidenceOpen(false);
    const markerId = session?.selectedCitationMarkerId;
    if (markerId) {
      window.requestAnimationFrame(() => focusCitation(markerId));
    }
  };

  const primary = (
    <div className="ask-workspace">
      <PageHeader title="问答与研究" />
      <section aria-label="知识准备度" className="ask-readiness">
        <div>
          <strong>知识准备度</strong>
          {overviewQuery.isPending ? <span>正在获取</span> : null}
          {overviewQuery.isError ? <span>暂时不可用，不影响提问</span> : null}
          {overviewQuery.data ? (
            <span>
              文章 {overviewQuery.data.status.articleCount} · 源文件 {overviewQuery.data.status.sourceFileCount} · 待处理 {overviewQuery.data.pending.count}
            </span>
          ) : null}
        </div>
        <Link to="/library/quality">
          查看知识质量
          <ArrowRight aria-hidden="true" size={15} />
        </Link>
      </section>
      <form className="ask-form" onSubmit={submit}>
        <label htmlFor="ask-question">问题</label>
        <textarea
          id="ask-question"
          maxLength={4000}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={(event: KeyboardEvent<HTMLTextAreaElement>) => {
            if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
              event.preventDefault();
              submit();
            }
          }}
          placeholder="输入问题"
          rows={3}
          value={question}
        />
        <div className="ask-form-actions">
          <ModeSelector disabled={mutation.isPending} onChange={setMode} value={mode} />
          <span className="ask-character-count">{question.length}/4000</span>
          <button
            className="primary-button"
            disabled={!question.trim() || mutation.isPending}
            type="submit"
          >
            <Send aria-hidden="true" size={17} />
            {mutation.isPending ? "等待中" : "提问"}
          </button>
        </div>
      </form>
      <div aria-busy={mutation.isPending} className="ask-result-region">
        {mutation.isPending ? (
          <PageState status="loading" title="正在等待回答" />
        ) : mutation.isError ? (
          <InlineAlert
            actionLabel="重试"
            description={resolveErrorMessage(mutation.error)}
            onAction={() => {
              if (mutation.variables) {
                mutation.mutate(mutation.variables);
              }
            }}
            title="查询失败"
            tone="error"
          />
        ) : response ? (
          <QueryAnswer
            activeMarkerId={session?.selectedCitationMarkerId ?? null}
            onCitationActivate={activateCitation}
            question={session?.question ?? ""}
            response={response}
          />
        ) : (
          <PageState status="empty" title="尚无查询结果" />
        )}
      </div>
    </div>
  );

  return (
    <SplitView
      mobileSecondaryOpen={mobileEvidenceOpen}
      onMobileSecondaryClose={closeEvidence}
      primary={primary}
      secondary={
        <EvidenceInspector
          binding={selectedBinding}
          onReturnToCitation={(markerId) => {
            setMobileEvidenceOpen(false);
            window.requestAnimationFrame(() => focusCitation(markerId));
          }}
        />
      }
      secondaryLabel="引用证据"
    />
  );
}

function focusCitation(markerId: string) {
  const exactMarker = document.getElementById(`citation-marker-${markerId}`);
  const fallbackMarker = document.getElementById(`citation-fallback-${markerId}`);
  (exactMarker ?? fallbackMarker)?.focus();
}

function resolveErrorMessage(error: unknown) {
  return isApiError(error) ? error.message : "请求未能完成，请重试。";
}
