"use client";

import { useId, useState } from "react";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { LottoBall } from "@/components/lotto/LottoBall";
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
};

/** 전략이 번호를 고르는 방식. 백엔드 recommendation/engine 의 각 전략 구현을 한 줄로 옮긴 것. */
export const STRATEGY_DESCRIPTIONS: Record<StrategyType, string> = {
  RECENT_HOT: "최근 50회에 자주 나온 번호 위주",
  LONG_COLD: "오래 안 나온 번호 위주",
  ALL_TIME: "역대 자주 나온 번호 위주",
  PAIR: "함께 자주 나온 번호끼리",
  BALANCED: "홀짝·합계가 고른 조합",
};

/** "2026-11-05" → "11월 5일". 날짜 계산 없이 문자열만 나눈다. */
function monthDay(date: string) {
  const [, month, day] = date.split("-");
  return `${Number(month)}월 ${Number(day)}일`;
}

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
        {data.lines.map((line, index) => (
          <SlipLine
            key={line.strategy}
            letter={gameLetter(index)}
            line={line}
          />
        ))}
      </div>
      <div className="flex flex-col gap-1 text-sm">
        <p className="text-muted tabular-nums">
          분석 범위: {data.historyFromRound}~{data.historyToRound}회
        </p>
        <p className="text-muted">{data.disclaimer}</p>
      </div>
    </div>
  );
}

function SlipLine({
  letter,
  line,
}: {
  letter: string;
  line: RecommendationSetResponse["lines"][number];
}) {
  const [open, setOpen] = useState(false);
  const evidenceId = useId();

  return (
    <article className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-2.5 border-t border-slip/25 px-4 py-3">
      <span
        aria-hidden="true"
        className="flex size-7 items-center justify-center rounded-sm border border-slip text-sm font-bold text-slip"
      >
        {letter}
      </span>
      <h2 className="flex min-w-0 flex-wrap items-baseline gap-x-2">
        <span className="text-sm font-semibold">
          {STRATEGY_LABELS[line.strategy]}
        </span>{" "}
        <span className="text-xs text-muted">
          {STRATEGY_DESCRIPTIONS[line.strategy]}
        </span>
      </h2>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={evidenceId}
        onClick={() => setOpen((value) => !value)}
        className="flex items-center gap-1 rounded-md border border-rule px-2 py-1 text-xs font-medium text-muted hover:border-ink/40 hover:text-ink aria-expanded:border-slip/40 aria-expanded:text-slip"
      >
        근거
        <span
          aria-hidden="true"
          className={`transition-transform motion-reduce:transition-none ${open ? "rotate-180" : ""}`}
        >
          ▾
        </span>
      </button>
      {/* 근거 상자는 공 줄의 폭을 넘지 않는다. w-0 min-w-full: 상자 글이 폭 계산에 끼지 않고 공 줄 폭만큼 펼쳐진다. */}
      <div className="col-span-2 col-start-2 flex w-fit max-w-full flex-col gap-2.5">
        <div className="flex flex-wrap items-center gap-1.5">
          {line.numbers.map((n) => (
            <LottoBall key={n} value={n} />
          ))}
        </div>
        <ul
          id={evidenceId}
          hidden={!open}
          className="flex w-0 min-w-full flex-col gap-1 rounded-md bg-paper px-3 py-2 text-sm text-muted"
        >
          {line.evidence.map((text, i) => (
            <li key={i}>{text}</li>
          ))}
        </ul>
      </div>
    </article>
  );
}
