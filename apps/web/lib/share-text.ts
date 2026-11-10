import { monthDay } from "@/lib/format-date";
import { STRATEGY_LABELS } from "@/components/recommendation/RecommendationCard";
import type { RecommendationSetResponse } from "@/types/api/recommendation";

/** 한 줄 번호를 화면과 같은 모양(0 채움 없음, 서버 순서)으로. 예: "3 7 12 27 33 40" */
export function formatLineNumbers(numbers: number[]) {
  return numbers.join(" ");
}

/** 추천 전체를 남에게 보낼 글. 첫 줄 회차·날짜, 줄마다 이름과 번호, 마지막에 고지. 근거 문구는 넣지 않는다. */
export function recommendationShareText(set: RecommendationSetResponse) {
  let combined = 0;
  const lines = set.lines.map((line) => {
    const label =
      line.strategy === "COMBINED"
        ? `${STRATEGY_LABELS.COMBINED} ${++combined}`
        : STRATEGY_LABELS[line.strategy];
    return `${label}: ${formatLineNumbers(line.numbers)}`;
  });
  return [
    `${set.round}회 · ${monthDay(set.date)} 추천`,
    ...lines,
    set.disclaimer,
  ].join("\n");
}
