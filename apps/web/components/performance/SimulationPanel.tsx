"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import {
  PERFORMANCE_LABELS,
  RANKS,
} from "@/components/performance/PerformanceTable";
import type { SimulationResponse } from "@/types/api/simulation";

function percent(rate: number) {
  return `${(rate * 100).toFixed(2)}%`;
}

function formatComputedAt(iso: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "Asia/Seoul",
  }).format(new Date(iso));
}

// 204면 apiFetch가 undefined를 주는데, React Query는 undefined 결과를 오류로 본다.
async function fetchLatestSimulation(): Promise<SimulationResponse | null> {
  const result = await apiFetch<SimulationResponse | undefined>(
    "/api/v1/simulations/latest",
  );
  return result ?? null;
}

export function SimulationPanel() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["simulations", "latest"],
    queryFn: fetchLatestSimulation,
  });

  if (isPending) {
    return <p>시뮬레이션 결과를 불러오는 중…</p>;
  }
  if (isError) {
    return <p role="alert">시뮬레이션 결과를 불러오지 못했습니다.</p>;
  }
  if (data === null) {
    return (
      <p>
        아직 시뮬레이션 결과가 없습니다. 새 회차를 수집하면 서버가 계산합니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm tabular-nums">
        <span>
          {data.fromRound}~{data.toRound}회, 회차마다 {data.repetitions}줄
        </span>
        <span className="block text-xs opacity-70">
          계산 {formatComputedAt(data.computedAt)}
        </span>
      </p>
      <p className="text-sm opacity-70">{data.scopeNote}</p>
      <p className="text-sm opacity-70">
        각 회차는 그 이전 회차만으로 뽑았습니다. 이론값{" "}
        {percent(data.theoryAtLeastFifthRate)}는 5등 이상에 해당하는 조합의
        비율입니다. 실제 당첨 확률과 무관합니다.
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-sm tabular-nums">
          <thead>
            <tr className="text-left opacity-70">
              <th scope="col">전략</th>
              <th scope="col">평균 일치</th>
              <th scope="col">5등 이상 (95% 구간)</th>
              <th scope="col">대조군 비교</th>
            </tr>
          </thead>
          <tbody>
            {data.strategies.map((s) => (
              <tr key={s.strategy} className="border-t border-current/20 align-top">
                <th scope="row" className="py-1 text-left font-normal">
                  <span>{PERFORMANCE_LABELS[s.strategy]}</span>
                  <span className="block text-xs opacity-70">
                    {s.lineCount.toLocaleString("ko-KR")}줄
                  </span>
                  <span className="block text-xs opacity-70">
                    {RANKS.map(
                      ([rank, label]) =>
                        `${label} ${s.rankCounts[rank].toLocaleString("ko-KR")}`,
                    ).join(" · ")}
                  </span>
                </th>
                <td>{s.averageMatches.toFixed(3)}</td>
                <td>
                  <span>{percent(s.atLeastFifthRate)}</span>
                  <span className="block text-xs opacity-70">
                    {percent(s.ciLow)}~{percent(s.ciHigh)}
                  </span>
                </td>
                <td>
                  {s.strategy === "CONTROL"
                    ? "기준"
                    : s.differsFromControl
                      ? "무작위와 차이 관측"
                      : "유의한 차이 없음"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs opacity-70">
        95% 구간이 대조군 구간과 겹치면 유의한 차이 없음으로 표시합니다. 여러
        전략을 동시에 비교한 결과라 차이가 관측되어도 우연일 수 있습니다.
      </p>
    </div>
  );
}
