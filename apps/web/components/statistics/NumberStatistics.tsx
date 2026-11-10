"use client";

import { useId, useState } from "react";

import { useQuery } from "@tanstack/react-query";

import { LottoBall } from "@/components/lotto/LottoBall";
import { ApiError, apiFetch } from "@/lib/api";
import { monthDay } from "@/lib/format-date";
import {
  NUMBER_METRICS,
  type NumberMetric,
  type NumberStatisticsResponse,
  type RankedNumberResponse,
} from "@/types/api/statistics";

function tabLabel(metric: NumberMetric, recentWindow: number) {
  switch (metric) {
    case "RECENT":
      return `최근 ${recentWindow}회 출현`;
    case "COLD":
      return "미출현 회차";
    case "ALL_TIME":
      return "전체 출현";
  }
}

/** 서버가 준 값을 단위만 붙여 적는다. */
function valueText(metric: NumberMetric, value: number) {
  if (metric === "COLD") {
    return value === 0 ? "직전 회차 출현" : `${value}회째`;
  }
  return `${value}회`;
}

function rankText(row: RankedNumberResponse) {
  return row.tied ? `공동 ${row.rank}위` : `${row.rank}위`;
}

// 204면 apiFetch가 undefined를 주는데, React Query는 undefined 결과를 오류로 본다.
async function fetchStatistics(
  date: string | null,
): Promise<NumberStatisticsResponse | null> {
  const query = date === null ? "" : `?date=${date}`;
  const stats = await apiFetch<NumberStatisticsResponse | undefined>(
    `/api/v1/number-statistics${query}`,
  );
  return stats ?? null;
}

export function NumberStatistics({
  date,
  metric: initialMetric,
  highlight,
}: {
  /** 기준 추천 날짜. null이면 가장 최근 추천. */
  date: string | null;
  metric: NumberMetric;
  /** 강조할 근거 번호. 서버가 준 근거 번호를 주소로 넘겨받는다. */
  highlight: number[];
}) {
  const [metric, setMetric] = useState<NumberMetric>(initialMetric);
  const panelId = useId();
  const { data, isPending, error } = useQuery({
    queryKey: ["number-statistics", date ?? "latest"],
    queryFn: () => fetchStatistics(date),
  });

  if (isPending) {
    return <p className="text-muted">번호 통계를 불러오는 중…</p>;
  }
  if (error) {
    return (
      <p role="alert" className="text-danger">
        {error instanceof ApiError && error.code === "COMMON_NOT_FOUND"
          ? "이 날짜의 추천이 없어 통계를 보여 줄 수 없습니다."
          : "통계를 불러오지 못했습니다. 잠시 후 새로고침해 주세요."}
      </p>
    );
  }
  if (data === null) {
    return (
      <p className="rounded-lg border border-dashed border-slip/50 px-4 py-6 text-muted">
        아직 추천이 없습니다. 첫 추천이 나오면 그 근거를 여기서 확인할 수
        있습니다.
      </p>
    );
  }

  const rows = data.metrics[metric];
  const picked = highlight
    .map((n) => rows.find((row) => row.number === n))
    .filter((row): row is RankedNumberResponse => row !== undefined);
  // 막대 길이의 기준. 서버 정렬 순서에 기대지 않고 가장 큰 값을 쓴다.
  const scale = Math.max(1, ...rows.map((row) => row.value));

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted tabular-nums">
        {monthDay(data.date)} 추천 기준 · {data.historyFromRound}~
        {data.historyToRound}회
      </p>

      <div
        role="tablist"
        aria-label="통계 기준"
        className="grid grid-cols-3 gap-1 rounded-lg border border-rule bg-surface p-1"
      >
        {NUMBER_METRICS.map((m) => (
          <button
            key={m}
            type="button"
            role="tab"
            aria-selected={m === metric}
            aria-controls={panelId}
            onClick={() => setMetric(m)}
            className="rounded-md px-2 py-2 text-sm font-medium text-muted hover:text-ink aria-selected:bg-ink aria-selected:text-surface"
          >
            {tabLabel(m, data.recentWindow)}
          </button>
        ))}
      </div>

      <div id={panelId} role="tabpanel" className="flex flex-col gap-4">
        {picked.length > 0 && (
          <section
            aria-label="추천 근거 번호"
            className="flex flex-col divide-y divide-slip/20 rounded-lg border border-slip/40 bg-slip-soft"
          >
            {picked.map((row) => (
              <div
                key={row.number}
                className="flex items-center gap-3 px-4 py-3"
              >
                <LottoBall value={row.number} size="lg" />
                <p className="flex flex-1 flex-col">
                  <span className="text-xl font-bold text-slip tabular-nums">
                    45개 중 {rankText(row)}
                  </span>
                  <span className="text-sm text-muted">
                    {tabLabel(metric, data.recentWindow)}{" "}
                    <span className="font-semibold text-ink tabular-nums">
                      {valueText(metric, row.value)}
                    </span>
                  </span>
                </p>
              </div>
            ))}
          </section>
        )}

        <ol
          aria-label="번호 순위"
          className="flex flex-col overflow-hidden rounded-lg border border-rule bg-surface"
        >
          {rows.map((row) => {
            const marked = highlight.includes(row.number);
            return (
              <li
                key={row.number}
                data-number={row.number}
                className={`grid grid-cols-[auto_1fr_auto] items-center gap-x-3 border-t border-rule/60 px-4 py-1.5 first:border-t-0 ${marked ? "bg-slip-soft" : ""}`}
              >
                <LottoBall value={row.number} size="sm" />
                <meter
                  aria-hidden="true"
                  min={0}
                  max={scale}
                  value={row.value}
                  className={`h-2 w-full appearance-none bg-none [&::-moz-meter-bar]:rounded-full [&::-moz-meter-bar]:bg-none [&::-webkit-meter-bar]:border-0 [&::-webkit-meter-bar]:bg-transparent [&::-webkit-meter-bar]:bg-none [&::-webkit-meter-optimum-value]:rounded-full [&::-webkit-meter-optimum-value]:bg-none ${
                    marked
                      ? "[&::-moz-meter-bar]:bg-slip [&::-webkit-meter-optimum-value]:bg-slip"
                      : "[&::-moz-meter-bar]:bg-muted/40 [&::-webkit-meter-optimum-value]:bg-muted/40"
                  }`}
                />
                <span className="flex items-baseline gap-2 text-sm tabular-nums">
                  <span className={marked ? "font-semibold" : "text-muted"}>
                    {valueText(metric, row.value)}
                  </span>
                  {marked && (
                    <span className="font-bold text-slip">{rankText(row)}</span>
                  )}
                </span>
              </li>
            );
          })}
        </ol>
      </div>

      <p className="text-xs text-muted">
        과거에 자주·드물게 나온 정도일 뿐, 실제 당첨 확률과 무관합니다.
      </p>
    </div>
  );
}
