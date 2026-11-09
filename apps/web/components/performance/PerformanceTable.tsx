"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { STRATEGY_LABELS } from "@/components/recommendation/RecommendationCard";
import type { PrizeRank } from "@/types/api/result";
import type {
  StrategyPerformanceResponse,
  StrategyPerformancesResponse,
} from "@/types/api/performance";

/** 성적표와 시뮬레이션이 같이 쓰는 전략 이름. */
export const PERFORMANCE_LABELS: Record<
  StrategyPerformanceResponse["strategy"],
  string
> = {
  ...STRATEGY_LABELS,
  CONTROL: "대조군",
};

/** 등수 분포 표시 순서와 이름. 시뮬레이션 패널도 같이 쓴다. */
export const RANKS: [PrizeRank, string][] = [
  ["FIRST", "1등"],
  ["SECOND", "2등"],
  ["THIRD", "3등"],
  ["FOURTH", "4등"],
  ["FIFTH", "5등"],
];

function percent(rate: number) {
  return `${(rate * 100).toFixed(2)}%`;
}

export function PerformanceTable() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["strategy-performances"],
    queryFn: () =>
      apiFetch<StrategyPerformancesResponse>("/api/v1/strategy-performances"),
  });

  if (isPending) {
    return <p>성적표를 불러오는 중…</p>;
  }
  if (isError) {
    return <p role="alert">성적표를 불러오지 못했습니다.</p>;
  }

  const smallSample = data.strategies.some((s) => !s.sampleSufficient);

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm opacity-70">
        채점된 줄만 셉니다. 이론값 {percent(data.theoryAtLeastFifthRate)}는 5등
        이상에 해당하는 조합의 비율이며, 비교에는 고정·제외 번호가 없는 줄만
        씁니다. 실제 당첨 확률과 무관합니다.
      </p>
      {smallSample && (
        <p role="status" className="text-sm font-semibold">
          아직 차이를 판단하기 이르다. 표본이 1,000줄에 못 미치는 항목이
          있습니다.
        </p>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-sm tabular-nums">
          <thead>
            <tr className="text-left opacity-70">
              <th scope="col">전략</th>
              <th scope="col">줄 수</th>
              <th scope="col">평균 일치</th>
              <th scope="col">5등 이상</th>
              <th scope="col">이론값 비교</th>
            </tr>
          </thead>
          <tbody>
            {data.strategies.map((s) => {
              const empty = s.lineCount === 0;
              return (
                <tr key={s.strategy} className="border-t border-current/20 align-top">
                  <th scope="row" className="py-1 text-left font-normal">
                    <span>{PERFORMANCE_LABELS[s.strategy]}</span>
                    <span className="block text-xs opacity-70">
                      {RANKS.map(([rank, label]) => `${label} ${s.rankCounts[rank]}`).join(" · ")}
                    </span>
                  </th>
                  <td>{s.lineCount.toLocaleString("ko-KR")}줄</td>
                  <td>{empty ? "-" : s.averageMatches.toFixed(2)}</td>
                  <td>{empty ? "-" : percent(s.atLeastFifthRate)}</td>
                  <td>
                    <span>
                      {s.noSettingsLineCount === 0
                        ? "-"
                        : percent(s.noSettingsAtLeastFifthRate)}
                    </span>
                    <span className="block text-xs opacity-70">
                      설정 없는 {s.noSettingsLineCount.toLocaleString("ko-KR")}줄
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
