"use client";

import { useId, useState } from "react";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { monthDay } from "@/lib/format-date";
import { evidenceStatsHref } from "@/lib/number-stats-link";
import { recommendationShareText } from "@/lib/share-text";
import { LottoBall } from "@/components/lotto/LottoBall";
import {
  EvidenceButton,
  EvidencePanel,
} from "@/components/recommendation/EvidencePanel";
import {
  CopyLineButton,
  ShareSetButton,
} from "@/components/recommendation/ShareButtons";
import type {
  RecommendationSetResponse,
  StrategyType,
} from "@/types/api/recommendation";

export const STRATEGY_LABELS: Record<StrategyType, string> = {
  RECENT_HOT: "최근 빈도",
  LONG_COLD: "장기 미출현",
  ALL_TIME: "전체 빈도",
  PAIR: "번호 쌍",
  BALANCED: "균형",
  COMBINED: "근거 조합",
};

/** 전략이 번호를 고르는 방식. 백엔드 recommendation/engine 의 각 전략 구현을 한 줄로 옮긴 것. */
export const STRATEGY_DESCRIPTIONS: Record<StrategyType, string> = {
  RECENT_HOT: "최근 50회에 자주 나온 번호 위주",
  LONG_COLD: "오래 안 나온 번호 위주",
  ALL_TIME: "역대 자주 나온 번호 위주",
  PAIR: "함께 자주 나온 번호끼리",
  BALANCED: "홀짝·합계가 고른 조합",
  COMBINED: "각 전략 근거 번호를 모은 조합",
};

/** 로또 용지의 게임 칸 이름. 줄 순서대로 A, B, C… */
function gameLetter(index: number) {
  return String.fromCharCode(65 + index);
}

// 204면 apiFetch가 undefined를 주는데, React Query는 undefined 결과를 오류로 본다.
async function fetchTodayRecommendation(): Promise<RecommendationSetResponse | null> {
  const set = await apiFetch<RecommendationSetResponse | undefined>(
    "/api/v1/recommendations",
  );
  return set ?? null;
}

type Line = RecommendationSetResponse["lines"][number];

export function RecommendationCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["recommendations"],
    queryFn: fetchTodayRecommendation,
  });

  if (isPending) {
    return <p className="text-muted">오늘 추천을 불러오는 중…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        추천을 불러오지 못했습니다. 잠시 후 새로고침해 주세요.
      </p>
    );
  }
  if (data === null) {
    return (
      <p className="rounded-lg border border-dashed border-slip/50 px-4 py-6 text-muted">
        오늘 추천이 아직 없습니다. 매일 오후 5시에 만들어집니다.
      </p>
    );
  }

  const strategyLines = data.lines.filter((l) => l.strategy !== "COMBINED");
  const combinedLines = data.lines.filter((l) => l.strategy === "COMBINED");

  return (
    <div className="flex flex-col gap-4">
      {data.carriedOver && (
        <div className="flex flex-col gap-0.5 rounded-md border border-rule px-3 py-2 text-sm text-muted">
          <p>
            {data.todayPending
              ? "오늘 추천은 오후 5시에 나옵니다."
              : "오늘 추천을 만들지 못했습니다."}{" "}
            아래는 {monthDay(data.date)} 추천입니다.
          </p>
          {data.roundClosed && (
            <p>
              {data.round}회는 추첨이 끝났지만, 번호는 이번 회차에도 살 수
              있습니다.
            </p>
          )}
        </div>
      )}
      {data.notificationStatus === "FAILED" && (
        <p className="rounded-md bg-slip-soft px-3 py-2 text-sm font-medium text-slip">
          텔레그램 알림을 보내지 못했습니다. 번호는 여기서 확인하세요.
        </p>
      )}
      <div className="overflow-hidden rounded-lg border-2 border-slip bg-surface">
        <p className="flex items-baseline justify-between bg-slip-soft px-4 py-2.5 text-slip tabular-nums">
          <span className="text-lg font-bold">{data.round}회</span>
          <span className="text-sm font-medium">{data.date}</span>
        </p>
        {strategyLines.map((line, index) => (
          <SlipLine
            key={`${line.strategy}-${index}`}
            date={data.date}
            letter={gameLetter(index)}
            line={line}
          />
        ))}
      </div>
      {combinedLines.length > 0 && (
        <CombinedSlip date={data.date} lines={combinedLines} />
      )}
      <ShareSetButton text={recommendationShareText(data)} />
      <div className="flex flex-col gap-1 text-sm">
        <p className="text-muted tabular-nums">
          분석 범위: {data.historyFromRound}~{data.historyToRound}회
        </p>
        <p className="text-muted">{data.disclaimer}</p>
      </div>
    </div>
  );
}

/** 근거 조합은 용지 한 장을 따로 쓴다. 설명은 머리에 한 번만 둔다. */
function CombinedSlip({ date, lines }: { date: string; lines: Line[] }) {
  const titleId = useId();
  return (
    <section
      aria-labelledby={titleId}
      className="overflow-hidden rounded-lg border-2 border-slip bg-surface"
    >
      <p className="flex flex-wrap items-baseline justify-between gap-x-3 bg-slip-soft px-4 py-2.5 text-slip">
        <span id={titleId} className="text-lg font-bold">
          {STRATEGY_LABELS.COMBINED}
        </span>
        <span className="text-sm font-medium">
          {STRATEGY_DESCRIPTIONS.COMBINED}
        </span>
      </p>
      {lines.map((line, index) => (
        <SlipLine
          key={`${line.strategy}-${index}`}
          date={date}
          letter={gameLetter(index)}
          line={line}
          title={`${STRATEGY_LABELS.COMBINED} ${index + 1}`}
        />
      ))}
    </section>
  );
}

function SlipLine({
  date,
  letter,
  line,
  title,
}: {
  /** 이 추천의 날짜. 근거 통계 링크의 기준이다. */
  date: string;
  letter: string;
  line: Line;
  /** 근거 조합처럼 같은 전략이 여러 줄이면 "근거 조합 1"처럼 따로 붙인다. 설명은 용지 머리에 있어 생략한다. */
  title?: string;
}) {
  const [open, setOpen] = useState(false);
  const evidenceId = useId();
  const statsHref = evidenceStatsHref(date, line);
  // 근거 문구도 통계 링크도 없으면 열어도 빈 상자라 버튼을 두지 않는다.
  const hasEvidence = line.evidence.length > 0 || statsHref !== null;
  const name = title ?? STRATEGY_LABELS[line.strategy];

  return (
    <article className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-2.5 border-t border-slip/25 px-4 py-3">
      <span
        aria-hidden="true"
        className="flex size-7 items-center justify-center rounded-sm border border-slip text-sm font-bold text-slip"
      >
        {letter}
      </span>
      <h2 className="flex min-w-0 flex-wrap items-baseline gap-x-2">
        <span className="text-sm font-semibold">{name}</span>
        {title === undefined && (
          <>
            {" "}
            <span className="text-xs text-muted">
              {STRATEGY_DESCRIPTIONS[line.strategy]}
            </span>
          </>
        )}
      </h2>
      <div className="flex gap-1.5">
        <CopyLineButton numbers={line.numbers} label={name} />
        {hasEvidence && (
          <EvidenceButton
            open={open}
            controls={evidenceId}
            onToggle={() => setOpen((value) => !value)}
          />
        )}
      </div>
      {/* 공 줄과 근거 상자는 칸 이름 열부터 용지 안쪽 폭을 다 쓴다(최근 당첨번호와 같은 배치). */}
      <div className="col-span-full flex flex-col gap-2.5">
        <div className="flex w-full items-center justify-between">
          {line.numbers.map((n) => (
            <LottoBall key={n} value={n} />
          ))}
        </div>
        {hasEvidence && (
          <EvidencePanel
            id={evidenceId}
            open={open}
            evidence={line.evidence}
            statsHref={statsHref}
          />
        )}
      </div>
    </article>
  );
}
