"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { PERFORMANCE_LABELS } from "@/components/performance/PerformanceTable";
import {
  controlLast,
  FifthOrBetter,
  HEAD_CELL,
  Legend,
  NAME_CELL,
  NUM_CELL,
  RankBreakdown,
  SharedFacts,
  sharedValue,
  SUB_ROW_CELL,
  SUB_VALUE,
} from "@/components/performance/StatTable";
import type { SimulationResponse } from "@/types/api/simulation";

function percent(rate: number) {
  return `${(rate * 100).toFixed(2)}%`;
}

function interval(low: number, high: number) {
  return `${(low * 100).toFixed(2)}~${percent(high)}`;
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
    return <p className="text-muted">시뮬레이션 결과를 불러오는 중…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        시뮬레이션 결과를 불러오지 못했습니다.
      </p>
    );
  }
  if (data === null) {
    return (
      <p className="text-muted">
        아직 시뮬레이션 결과가 없습니다. 새 회차를 수집한 뒤 계산합니다.
      </p>
    );
  }

  const theory = percent(data.theoryAtLeastFifthRate);
  const sharedLines = sharedValue(data.strategies.map((s) => s.lineCount));
  const sharedInterval = sharedValue(
    data.strategies.map((s) => interval(s.ciLow, s.ciHigh)),
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5 text-sm text-muted">
        <p>
          과거 회차에 전략을 미리 적용해 본 결과입니다.{" "}
          <span className="tabular-nums">
            {data.fromRound}~{data.toRound}회의 회차마다
          </span>{" "}
          그 회차보다 앞선 당첨번호만 보고 전략별로{" "}
          <span className="tabular-nums">{data.repetitions}줄</span>씩 고른 뒤,
          그 회차 당첨번호와 맞춰 봤습니다.
        </p>
        <p>
          아무 번호나 골라도 한 줄이 5등 이상일 확률은 {theory}입니다. 이보다
          꾸준히 높아야 의미가 있습니다.
        </p>
        <p>{data.scopeNote}</p>
        <p className="text-right text-xs tabular-nums">
          {formatComputedAt(data.computedAt)} 계산
        </p>
      </div>
      <SharedFacts
        items={[
          ...(sharedLines === null
            ? []
            : [
                [
                  "추첨한 줄 수",
                  `전략마다 ${sharedLines.toLocaleString("ko-KR")}줄`,
                ] as [string, string],
              ]),
          ...(sharedInterval === null
            ? []
            : [
                ["5등↑ 95% 구간", `모두 ${sharedInterval}`] as [string, string],
              ]),
        ]}
      />
      <div className="overflow-x-auto">
        <table className="w-full text-sm tabular-nums">
          <thead className="text-muted">
            <tr>
              <th scope="col" className={HEAD_CELL}>
                전략
              </th>
              <th scope="col" className={HEAD_CELL}>
                평균
              </th>
              <th scope="col" className={HEAD_CELL}>
                <FifthOrBetter />
              </th>
              <th scope="col" className={HEAD_CELL}>
                대조군 대비
              </th>
            </tr>
          </thead>
          {controlLast(data.strategies).map((s) => (
            <tbody key={s.strategy} className="border-t border-rule">
              <tr>
                <th scope="row" className={NAME_CELL}>
                  {PERFORMANCE_LABELS[s.strategy]}
                  {sharedLines === null && (
                    <span className={`${SUB_VALUE} tabular-nums`}>
                      {s.lineCount.toLocaleString("ko-KR")}줄
                    </span>
                  )}
                </th>
                <td className={NUM_CELL}>{s.averageMatches.toFixed(3)}</td>
                <td className={NUM_CELL}>
                  {percent(s.atLeastFifthRate)}
                  {sharedInterval === null && (
                    <span className={SUB_VALUE}>
                      {interval(s.ciLow, s.ciHigh)}
                    </span>
                  )}
                </td>
                <td
                  className={`${NUM_CELL} ${s.differsFromControl ? "font-semibold text-slip" : ""}`}
                >
                  {s.strategy === "CONTROL"
                    ? "기준"
                    : s.differsFromControl
                      ? "차이 관측"
                      : "차이 없음"}
                </td>
              </tr>
              <tr>
                <td colSpan={4} className={SUB_ROW_CELL}>
                  <RankBreakdown counts={s.rankCounts} />
                </td>
              </tr>
            </tbody>
          ))}
        </table>
      </div>
      <Legend
        items={[
          ["평균", "한 줄에서 맞힌 번호 개수의 평균"],
          [
            <FifthOrBetter key="fifth" />,
            "5등 이상 당첨된 줄의 비율. 95% 구간은 회차 단위로 계산한 신뢰구간으로, 우연에 따른 흔들림을 감안한 범위입니다.",
          ],
          [
            "대조군 대비",
            "무작위로 뽑은 대조군과 95% 구간이 겹치면 차이 없음, 겹치지 않으면 차이 관측입니다. 여러 전략을 동시에 비교했으므로 차이가 관측되어도 우연일 수 있습니다.",
          ],
        ]}
      />
    </div>
  );
}
