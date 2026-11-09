import type { ReactNode } from "react";

import type { PrizeRank } from "@/types/api/result";

/** 성적표·시뮬레이션 표가 같이 쓰는 칸 스타일. 첫 칸(전략)은 왼쪽, 숫자 칸은 오른쪽 정렬. */
export const HEAD_CELL =
  "whitespace-nowrap py-2 pl-3 text-right font-medium first:pl-0 first:text-left";
export const NAME_CELL = "pt-2.5 text-left align-top font-semibold";
export const NUM_CELL = "whitespace-nowrap pt-2.5 pl-3 text-right align-top";
export const SUB_ROW_CELL = "pb-3 pt-2 font-normal";
/** 숫자 칸 값 아래에 붙는 작은 보조 숫자 (줄 수, 구간 등). */
export const SUB_VALUE = "mt-0.5 block text-xs font-normal text-muted";

/** 머리글은 "5등↑"으로 짧게, 화면낭독기에는 "5등 이상"으로 읽힌다. */
export function FifthOrBetter() {
  return (
    <>
      <span aria-hidden="true">5등↑</span>
      <span className="sr-only">5등 이상</span>
    </>
  );
}

/** 표 아래 용어 설명. 머리글을 짧게 줄인 대신 뜻은 여기서 풀어 쓴다. */
export function Legend({ items }: { items: [ReactNode, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs text-muted">
      {items.map(([term, description], index) => (
        <div key={index} className="contents">
          <dt className="font-semibold whitespace-nowrap text-ink">{term}</dt>
          <dd>{description}</dd>
        </div>
      ))}
    </dl>
  );
}

/** 등수 분포 표시 순서와 이름. */
const RANKS: [PrizeRank, string][] = [
  ["FIRST", "1등"],
  ["SECOND", "2등"],
  ["THIRD", "3등"],
  ["FOURTH", "4등"],
  ["FIFTH", "5등"],
];

/** 1~5등 당첨 줄 수를 다섯 칸으로 나란히 보여준다. 행마다 같은 칸에 같은 등수가 와서 위아래로 비교된다. */
export function RankBreakdown({
  counts,
}: {
  counts: Record<PrizeRank, number>;
}) {
  return (
    <dl
      aria-label="등수별 당첨 줄 수"
      className="grid grid-cols-5 rounded-md border border-rule bg-surface py-1.5 text-center text-xs tabular-nums"
    >
      {RANKS.map(([rank, label]) => (
        <div key={rank} className="flex flex-col-reverse gap-0.5">
          <dd
            className={
              counts[rank] > 0
                ? "text-sm font-semibold text-ink"
                : "text-sm text-muted/70"
            }
          >
            {counts[rank].toLocaleString("ko-KR")}
          </dd>
          <dt className="text-muted">{label}</dt>
        </div>
      ))}
    </dl>
  );
}

/** 모든 값이 같으면 그 값, 하나라도 다르면 null. 전략마다 같은 숫자는 표 밖에 한 번만 보여주려고 쓴다. */
export function sharedValue<T>(values: T[]): T | null {
  if (values.length === 0) {
    return null;
  }
  return values.every((value) => value === values[0]) ? values[0] : null;
}

/** 전략마다 똑같은 숫자를 표 위에 한 번만 보여준다. */
export function SharedFacts({ items }: { items: [ReactNode, ReactNode][] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <dl className="flex flex-col gap-1 rounded-md border border-rule bg-surface px-3 py-2 text-sm">
      {items.map(([label, value], index) => (
        <div key={index} className="flex items-baseline justify-between gap-3">
          <dt className="text-muted">{label}</dt>
          <dd className="font-semibold tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
