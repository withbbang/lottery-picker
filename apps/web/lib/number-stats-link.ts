import { NUMBER_METRICS, type NumberMetric } from "@/types/api/statistics";

const DATE = /^\d{4}-\d{2}-\d{2}$/;

export type NumberStatsParams = {
  date: string | null;
  metric: NumberMetric;
  highlight: number[];
};

/** "YYYY-MM-DD" 모양이면서 달력에 있는 날인지. 2026-02-30처럼 없는 날은 서버가 400을 준다. */
function isCalendarDate(date: string) {
  if (!DATE.test(date)) {
    return false;
  }
  const [year, month, day] = date.split("-").map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return (
    parsed.getUTCFullYear() === year &&
    parsed.getUTCMonth() === month - 1 &&
    parsed.getUTCDate() === day
  );
}

/** 추천 근거에서 번호 통계 화면으로 가는 주소. 강조할 번호는 서버가 준 근거 번호를 그대로 넘긴다. */
export function numberStatsHref(
  date: string,
  metric: NumberMetric,
  highlight: number[],
): string {
  const params = new URLSearchParams({
    date,
    metric,
    highlight: highlight.join(","),
  });
  return `/numbers?${params.toString()}`;
}

/** 추천 줄의 근거 영역에 달 통계 링크. 서버가 지표를 주지 않았거나 근거 번호가 없으면 링크가 없다. */
export function evidenceStatsHref(
  date: string,
  line: { statsMetric: NumberMetric | null; evidenceNumbers: number[] },
): string | null {
  if (line.statsMetric === null || line.evidenceNumbers.length === 0) {
    return null;
  }
  return numberStatsHref(date, line.statsMetric, line.evidenceNumbers);
}

/**
 * 주소 파라미터를 읽는다. 사람이 고친 주소도 화면이 깨지지 않게, 모르는 지표는 RECENT로,
 * 강조 번호는 1~45 정수만 남긴다. 번호 계산이 아니라 주소 검증이다.
 */
export function parseNumberStatsParams(params: {
  get(name: string): string | null;
}): NumberStatsParams {
  const raw = params.get("date");
  const date = raw !== null && isCalendarDate(raw) ? raw : null;
  const metric = params.get("metric");
  // 강조 번호는 그 날짜 추천의 근거다. 날짜를 못 쓰면 최근 추천을 보이므로 남의 번호가 되지 않게 버린다.
  const highlight =
    date === null
      ? []
      : (params.get("highlight") ?? "")
          .split(",")
          .filter((part) => /^\d+$/.test(part))
          .map(Number)
          .filter((n) => n >= 1 && n <= 45);
  return {
    date,
    metric: NUMBER_METRICS.find((m) => m === metric) ?? "RECENT",
    highlight,
  };
}
