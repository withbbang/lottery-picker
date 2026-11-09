"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { STRATEGY_LABELS } from "@/components/recommendation/RecommendationCard";
import {
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
  CONTROL: "무작위 대조군",
};

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
    return <p className="text-muted">성적을 불러오는 중…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        성적을 불러오지 못했습니다.
      </p>
    );
  }

  const smallSample = data.strategies.some((s) => !s.sampleSufficient);
  const theory = percent(data.theoryAtLeastFifthRate);
  const sharedNoSettings = sharedValue(
    data.strategies.map((s) => s.noSettingsLineCount),
  );

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted">
        지금까지 추천한 줄을 실제 추첨 결과로 채점한 누적 성적입니다. 어떤
        번호를 고르든 한 줄이 5등 이상일 확률은 {theory}라서, 이보다 꾸준히
        높아야 의미가 있습니다.
      </p>
      {smallSample && (
        <p
          role="status"
          className="rounded-md bg-slip-soft px-3 py-2 text-sm font-medium text-slip"
        >
          아직 차이를 판단하기 이릅니다. 표본이 1,000줄에 못 미치는 전략이
          있습니다.
        </p>
      )}
      <SharedFacts
        items={
          sharedNoSettings === null
            ? []
            : [
                [
                  "무설정 기준 줄 수",
                  `전략마다 ${sharedNoSettings.toLocaleString("ko-KR")}줄`,
                ],
              ]
        }
      />
      <div className="overflow-x-auto">
        <table className="w-full text-sm tabular-nums">
          <thead className="text-muted">
            <tr>
              <th scope="col" className={HEAD_CELL}>
                전략
              </th>
              <th scope="col" className={HEAD_CELL}>
                줄 수
              </th>
              <th scope="col" className={HEAD_CELL}>
                평균
              </th>
              <th scope="col" className={HEAD_CELL}>
                <FifthOrBetter />
              </th>
              <th scope="col" className={HEAD_CELL}>
                무설정
              </th>
            </tr>
          </thead>
          {data.strategies.map((s) => {
            const empty = s.lineCount === 0;
            return (
              <tbody key={s.strategy} className="border-t border-rule">
                <tr>
                  <th scope="row" className={NAME_CELL}>
                    {PERFORMANCE_LABELS[s.strategy]}
                  </th>
                  <td className={NUM_CELL}>
                    {s.lineCount.toLocaleString("ko-KR")}줄
                  </td>
                  <td className={NUM_CELL}>
                    {empty ? "-" : s.averageMatches.toFixed(2)}
                  </td>
                  <td className={NUM_CELL}>
                    {empty ? "-" : percent(s.atLeastFifthRate)}
                  </td>
                  <td className={NUM_CELL}>
                    {s.noSettingsLineCount === 0
                      ? "-"
                      : percent(s.noSettingsAtLeastFifthRate)}
                    {sharedNoSettings === null && (
                      <span className={SUB_VALUE}>
                        {s.noSettingsLineCount.toLocaleString("ko-KR")}줄 기준
                      </span>
                    )}
                  </td>
                </tr>
                <tr>
                  <td colSpan={5} className={SUB_ROW_CELL}>
                    <RankBreakdown counts={s.rankCounts} />
                  </td>
                </tr>
              </tbody>
            );
          })}
        </table>
      </div>
      <Legend
        items={[
          ["평균", "한 줄에서 맞힌 번호 개수의 평균"],
          [<FifthOrBetter key="fifth" />, "5등 이상 당첨된 줄의 비율"],
          [
            "무설정",
            `고정·제외 번호 없이 만든 줄만 센 5등↑ 비율. 설정의 영향을 빼고 ${theory}와 비교할 때 봅니다.`,
          ],
        ]}
      />
    </div>
  );
}
