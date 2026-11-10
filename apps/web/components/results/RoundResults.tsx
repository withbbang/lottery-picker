"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { LottoBall } from "@/components/lotto/LottoBall";
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

export const RANK_LABELS: Record<PrizeRank, string> = {
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
    return <p className="text-muted">추첨 결과를 불러오는 중…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        추첨 결과를 불러오지 못했습니다.
      </p>
    );
  }
  if (data.pending) {
    return <p className="text-muted">아직 추첨 전입니다.</p>;
  }
  if (data.lines.length === 0) {
    return <p className="text-muted">이 회차에는 추천한 번호가 없습니다.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-rule bg-rule tabular-nums">
        <div className="flex flex-col gap-0.5 bg-surface px-4 py-3">
          <dt className="text-sm text-muted">가상 당첨금</dt>
          <dd className="text-xl font-bold">{won(data.prizeTotal)}</dd>
        </div>
        <div className="flex flex-col gap-0.5 bg-surface px-4 py-3">
          <dt className="text-sm text-muted">가상 수익</dt>
          <dd
            className={`text-xl font-bold ${data.profit > 0 ? "text-slip" : ""}`}
          >
            {signedWon(data.profit)}
          </dd>
        </div>
      </dl>
      <ul
        aria-label="등수별 줄 수"
        className="grid grid-cols-3 gap-x-4 text-sm tabular-nums"
      >
        {RANKS.map((rank) => (
          <li
            key={rank}
            className={`flex justify-between border-t border-rule py-1.5 ${
              rank !== "NONE" && data.rankCounts[rank] > 0
                ? "font-semibold text-slip"
                : "text-muted"
            }`}
          >
            <span>{RANK_LABELS[rank]}</span>
            <span>{data.rankCounts[rank]}줄</span>
          </li>
        ))}
      </ul>

      <section className="flex flex-col gap-2">
        <h3 className="flex items-baseline justify-between font-semibold">
          줄별 결과
          <span className="text-sm font-normal text-muted tabular-nums">
            {data.lines.length}줄
          </span>
        </h3>
        <ul className="flex flex-col text-sm tabular-nums">
          {data.lines.map((line, index) => (
            <li
              key={`${line.date}-${line.strategy}-${index}`}
              className="flex flex-col gap-1.5 border-t border-rule py-2.5"
            >
              <p className="flex justify-between">
                <span className="text-muted">
                  {line.date} · {STRATEGY_LABELS[line.strategy]}
                </span>
                <span
                  className={
                    line.prizeRank === "NONE"
                      ? "text-muted"
                      : "font-semibold text-slip"
                  }
                >
                  {RANK_LABELS[line.prizeRank]}
                  {line.prizeAmount > 0 ? ` · ${won(line.prizeAmount)}` : ""}
                </span>
              </p>
              <p className="flex flex-wrap items-center gap-1">
                {line.numbers.map((n) => (
                  <LottoBall key={n} value={n} size="sm" />
                ))}
                <span className="ml-1 text-muted">
                  {line.matchCount}개 일치
                </span>
              </p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
