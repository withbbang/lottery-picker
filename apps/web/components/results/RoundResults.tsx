"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { STRATEGY_LABELS } from "@/components/recommendation/RecommendationCard";
import type { PrizeRank, RoundResultResponse } from "@/types/api/result";

const RANKS: PrizeRank[] = [
  "FIRST",
  "SECOND",
  "THIRD",
  "FOURTH",
  "FIFTH",
  "NONE",
];

const RANK_LABELS: Record<PrizeRank, string> = {
  FIRST: "1등",
  SECOND: "2등",
  THIRD: "3등",
  FOURTH: "4등",
  FIFTH: "5등",
  NONE: "낙첨",
};

function won(amount: number) {
  return `${amount.toLocaleString("ko-KR")}원`;
}

function signedWon(amount: number) {
  return `${amount >= 0 ? "+" : "-"}${won(Math.abs(amount))}`;
}

export function RoundResults({ round }: { round: number }) {
  const { data, isPending, isError } = useQuery({
    queryKey: ["rounds", round, "results"],
    queryFn: () =>
      apiFetch<RoundResultResponse>(`/api/v1/rounds/${round}/results`),
  });

  if (isPending) {
    return <p>추천 비교를 불러오는 중…</p>;
  }
  if (isError) {
    return <p role="alert">추천 비교를 불러오지 못했습니다.</p>;
  }
  if (data.pending) {
    return <p>아직 비교 결과를 계산하지 않았습니다.</p>;
  }
  if (data.lines.length === 0) {
    return <p>이 회차 추천이 없습니다.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-2 gap-2 tabular-nums">
        <div className="flex flex-col">
          <dt className="text-sm opacity-70">가상 당첨금</dt>
          <dd className="text-xl font-semibold">{won(data.prizeTotal)}</dd>
        </div>
        <div className="flex flex-col">
          <dt className="text-sm opacity-70">가상 수익</dt>
          <dd className="text-xl font-semibold">{signedWon(data.profit)}</dd>
        </div>
      </dl>
      <p className="text-sm opacity-70">
        구매했다고 가정한 가상 금액입니다. 줄당 1,000원, {data.lines.length}줄{" "}
        {won(data.cost)}
      </p>

      <ul
        aria-label="등수별 줄 수"
        className="grid grid-cols-3 gap-2 text-sm tabular-nums"
      >
        {RANKS.map((rank) => (
          <li
            key={rank}
            className="flex justify-between border-t border-current/20 py-1"
          >
            <span>{RANK_LABELS[rank]}</span>
            <span>{data.rankCounts[rank]}줄</span>
          </li>
        ))}
      </ul>

      <details className="border-t border-current/20 py-2">
        <summary className="cursor-pointer">
          줄별 결과 {data.lines.length}줄
        </summary>
        <ul className="mt-2 flex flex-col gap-2 text-sm tabular-nums">
          {data.lines.map((line) => (
            <li key={`${line.date}-${line.strategy}`} className="flex flex-col">
              <p className="flex justify-between">
                <span className="opacity-70">
                  {line.date} · {STRATEGY_LABELS[line.strategy]}
                </span>
                <span>
                  {RANK_LABELS[line.prizeRank]}
                  {line.prizeAmount > 0 ? ` · ${won(line.prizeAmount)}` : ""}
                </span>
              </p>
              <p>
                {line.numbers.join(" · ")}{" "}
                <span className="opacity-70">({line.matchCount}개 일치)</span>
              </p>
            </li>
          ))}
        </ul>
      </details>
    </div>
  );
}
