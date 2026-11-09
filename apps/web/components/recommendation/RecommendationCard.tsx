"use client";

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
    return <p>오늘 추천을 불러오는 중…</p>;
  }
  if (isError) {
    return <p role="alert">추천을 불러오지 못했습니다.</p>;
  }
  if (data === null) {
    return <p>오늘 추천이 아직 없습니다. 매일 오후 5시에 만들어집니다.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="flex items-baseline justify-between text-sm tabular-nums">
        <span className="font-semibold">{data.round}회</span>
        <span className="opacity-70">{data.date}</span>
      </p>
      {data.notificationStatus === "FAILED" && (
        <p className="text-sm font-medium">텔레그램 발송 실패</p>
      )}
      <div className="flex flex-col">
        {data.lines.map((line) => (
          <article
            key={line.strategy}
            className="flex flex-col gap-2 border-t border-current/20 py-3"
          >
            <h2 className="text-sm font-semibold">
              {STRATEGY_LABELS[line.strategy]}
            </h2>
            <div className="flex flex-wrap items-center gap-1">
              {line.numbers.map((n) => (
                <LottoBall key={n} value={n} />
              ))}
            </div>
            <ul className="flex flex-col gap-1 text-sm opacity-70">
              {line.evidence.map((text, index) => (
                <li key={index}>{text}</li>
              ))}
            </ul>
          </article>
        ))}
      </div>
      <p className="text-sm tabular-nums opacity-70">
        통계 입력: {data.historyFromRound}~{data.historyToRound}회
      </p>
      <p className="text-sm font-medium">{data.disclaimer}</p>
    </div>
  );
}
