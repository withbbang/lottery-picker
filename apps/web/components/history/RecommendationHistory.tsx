"use client";

import { useId, useState } from "react";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { monthDay } from "@/lib/format-date";
import { evidenceStatsHref } from "@/lib/number-stats-link";
import { LottoBall } from "@/components/lotto/LottoBall";
import {
  EvidenceButton,
  EvidencePanel,
} from "@/components/recommendation/EvidencePanel";
import { STRATEGY_LABELS } from "@/components/recommendation/RecommendationCard";
import { RANK_LABELS } from "@/components/results/RoundResults";
import { Pagination } from "@/components/ui/Pagination";
import type {
  PrizeRank,
  RecommendationHistoryResponse,
} from "@/types/api/result";
import type { PageResponse } from "@/types/api/round";

// 하루 7줄이라 한 주치를 한 페이지에 둔다.
const PAGE_SIZE = 7;

/** 높은 등수부터. 서버가 매긴 등수 중 가장 좋은 것을 고르는 데만 쓴다. */
const WINNING_RANKS: PrizeRank[] = [
  "FIRST",
  "SECOND",
  "THIRD",
  "FOURTH",
  "FIFTH",
];

type Day = RecommendationHistoryResponse;
type Line = Day["lines"][number];

type Verdict =
  { kind: "pending" } | { kind: "lost" } | { kind: "won"; best: PrizeRank };

/** 하루 용지의 결과. 추첨 전이든 추첨 뒤 채점 대기든 한 줄이라도 채점 전이면 채점 전으로 본다. */
function dayVerdict(lines: Line[]): Verdict {
  if (lines.some((l) => l.prizeRank === null)) {
    return { kind: "pending" };
  }
  const winners = lines.filter((l) => l.prizeRank !== "NONE");
  const best = WINNING_RANKS.find((rank) =>
    winners.some((l) => l.prizeRank === rank),
  );
  return best === undefined ? { kind: "lost" } : { kind: "won", best };
}

/** 근거 조합은 여러 줄이라 "근거 조합 1"처럼 순번을 붙인다. */
function lineTitles(lines: Line[]) {
  let combined = 0;
  return lines.map((l) =>
    l.strategy === "COMBINED"
      ? `${STRATEGY_LABELS.COMBINED} ${++combined}`
      : STRATEGY_LABELS[l.strategy],
  );
}

export function RecommendationHistory() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError } = useQuery({
    queryKey: ["recommendations", "history", { page }],
    queryFn: () =>
      apiFetch<PageResponse<RecommendationHistoryResponse>>(
        `/api/v1/recommendations/history?page=${page}&size=${PAGE_SIZE}`,
      ),
    placeholderData: (previous) => previous,
  });

  if (isPending) {
    return <HistorySkeleton />;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        지난 추천을 불러오지 못했습니다. 잠시 후 새로고침해 주세요.
      </p>
    );
  }
  if (data.items.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-rule px-4 py-6 text-muted">
        아직 추천이 없습니다. 추천은 매일 오후 5시에 만들어지고 바로 여기에
        쌓입니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      {data.items.map((day) => (
        <DaySlip key={day.date} day={day} />
      ))}
      <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
    </div>
  );
}

function DaySlip({ day }: { day: Day }) {
  const verdict = dayVerdict(day.lines);
  const titles = lineTitles(day.lines);

  return (
    <article className="overflow-hidden rounded-lg border border-rule bg-surface">
      <header className="flex items-center justify-between gap-3 px-4 pt-3 pb-2.5 tabular-nums">
        <h2 className="flex items-baseline gap-2">
          <span className="text-base font-semibold">{monthDay(day.date)}</span>
          <Link
            href={`/rounds/${day.round}`}
            className="rounded-sm text-sm text-muted underline decoration-rule underline-offset-4 hover:text-ink hover:decoration-ink/40"
          >
            {day.round}회
          </Link>
        </h2>
        <VerdictMark verdict={verdict} />
      </header>
      <ul className="flex flex-col">
        {day.lines.map((line, index) => (
          <HistoryLine
            key={`${line.strategy}-${index}`}
            date={day.date}
            line={line}
            title={titles[index]}
          />
        ))}
      </ul>
    </article>
  );
}

/** 당첨된 날만 용지 빨강으로 도장처럼 찍는다. 나머지는 조용히 둔다. */
function VerdictMark({ verdict }: { verdict: Verdict }) {
  if (verdict.kind === "won") {
    return (
      <span className="shrink-0 rounded-sm border border-slip px-1.5 py-0.5 text-sm font-bold text-slip">
        {RANK_LABELS[verdict.best]} 당첨
      </span>
    );
  }
  return (
    <span className="shrink-0 text-sm text-muted">
      {verdict.kind === "pending" ? "추첨 전" : "낙첨"}
    </span>
  );
}

function HistoryLine({
  date,
  line,
  title,
}: {
  date: string;
  line: Line;
  title: string;
}) {
  const [open, setOpen] = useState(false);
  const evidenceId = useId();
  const statsHref = evidenceStatsHref(date, line);
  // 근거 문구도 통계 링크도 없으면 열어도 빈 상자라 버튼을 두지 않는다.
  const hasEvidence = line.evidence.length > 0 || statsHref !== null;
  // 채점 전이면 null이라 모든 공을 그대로 칠한다.
  const matched = line.matchedNumbers;
  const bonus = line.matchedBonus;
  const won = line.prizeRank !== null && line.prizeRank !== "NONE";

  return (
    <li className="flex flex-col gap-1.5 border-t border-rule px-4 py-2">
      <div className="flex items-center justify-between gap-3 text-sm tabular-nums">
        <span className="flex items-center gap-2">
          <span className="text-muted">{title}</span>
          {hasEvidence && (
            <EvidenceButton
              open={open}
              controls={evidenceId}
              onToggle={() => setOpen((value) => !value)}
            />
          )}
        </span>
        {line.matchCount !== null && line.prizeRank !== null && (
          <span className="flex items-baseline gap-1.5">
            <span className="text-muted">
              {line.matchCount}개 일치
              {bonus !== null && " +보너스"}
            </span>
            {won && (
              <span className="font-bold text-slip">
                {RANK_LABELS[line.prizeRank]}
              </span>
            )}
          </span>
        )}
      </div>
      {/* 최근 당첨번호·오늘 추천과 같이 공 줄이 용지 안쪽 폭을 다 쓴다. */}
      <p className="flex w-full items-center justify-between">
        {line.numbers.map((n) => (
          <LottoBall
            key={n}
            value={n}
            bonus={n === bonus}
            missed={matched !== null && !matched.includes(n) && n !== bonus}
          />
        ))}
      </p>
      {hasEvidence && (
        <EvidencePanel
          id={evidenceId}
          open={open}
          evidence={line.evidence}
          statsHref={statsHref}
        />
      )}
    </li>
  );
}

/** 불러오는 동안 용지 모양 자리만 잡아 둔다. */
function HistorySkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-5">
      <p className="sr-only">지난 추천을 불러오는 중…</p>
      {[0, 1].map((slip) => (
        <div
          key={slip}
          aria-hidden="true"
          className="overflow-hidden rounded-lg border border-rule bg-surface motion-safe:animate-pulse"
        >
          <div className="px-4 pt-3 pb-2.5">
            <div className="h-5 w-28 rounded-sm bg-rule" />
          </div>
          {[0, 1, 2].map((row) => (
            <div
              key={row}
              className="flex flex-col gap-1.5 border-t border-rule px-4 py-2"
            >
              <div className="h-4 w-16 rounded-sm bg-rule" />
              <div className="flex w-full justify-between">
                {[0, 1, 2, 3, 4, 5].map((ball) => (
                  <div key={ball} className="size-9 rounded-full bg-rule" />
                ))}
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
