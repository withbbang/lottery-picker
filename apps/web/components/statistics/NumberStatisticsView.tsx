"use client";

import { useSearchParams } from "next/navigation";

import { NumberStatistics } from "@/components/statistics/NumberStatistics";
import { parseNumberStatsParams } from "@/lib/number-stats-link";

export function NumberStatisticsView() {
  const searchParams = useSearchParams();
  const { date, metric, highlight } = parseNumberStatsParams(searchParams);
  // 다른 근거 링크로 오면 탭 선택을 새 주소 기준으로 다시 연다.
  return (
    <NumberStatistics
      key={searchParams.toString()}
      date={date}
      metric={metric}
      highlight={highlight}
    />
  );
}
