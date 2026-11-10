import { describe, expect, it } from "vitest";

import { formatLineNumbers, recommendationShareText } from "@/lib/share-text";
import type {
  RecommendationLineResponse,
  RecommendationSetResponse,
  StrategyType,
} from "@/types/api/recommendation";

const DISCLAIMER = "실제 당첨 확률과 무관합니다.";

function line(
  strategy: StrategyType,
  numbers: number[],
  evidence: string[] = [],
): RecommendationLineResponse {
  return {
    strategy,
    numbers,
    evidence,
    relaxation: "NONE",
    evidenceNumbers: [],
    statsMetric: null,
  };
}

const set: RecommendationSetResponse = {
  date: "2026-11-10",
  round: 1250,
  historyFromRound: 1,
  historyToRound: 1249,
  disclaimer: DISCLAIMER,
  notificationStatus: "SENT",
  carriedOver: false,
  roundClosed: false,
  todayPending: false,
  lines: [
    line(
      "RECENT_HOT",
      [1, 8, 15, 22, 33, 45],
      ["번호 8: 최근 50회 중 11회 출현"],
    ),
    line("LONG_COLD", [2, 9, 16, 23, 34, 44]),
    line("ALL_TIME", [3, 10, 17, 24, 35, 43]),
    line("PAIR", [4, 11, 18, 25, 36, 42]),
    line("BALANCED", [5, 12, 19, 26, 37, 41]),
    line("COMBINED", [7, 12, 24, 37, 39, 40]),
    line("COMBINED", [6, 13, 20, 27, 38, 40]),
  ],
};

describe("formatLineNumbers", () => {
  it("joins numbers with spaces without zero padding", () => {
    // given / when
    const text = formatLineNumbers([3, 7, 12, 27, 33, 40]);

    // then
    expect(text).toBe("3 7 12 27 33 40");
  });

  it("keeps the order the server sent", () => {
    // given / when
    const text = formatLineNumbers([40, 3]);

    // then
    expect(text).toBe("40 3");
  });
});

describe("recommendationShareText", () => {
  it("starts with round and date then one line per strategy", () => {
    // given / when
    const lines = recommendationShareText(set).split("\n");

    // then
    expect(lines).toEqual([
      "1250회 · 11월 10일 추천",
      "최근 빈도: 1 8 15 22 33 45",
      "장기 미출현: 2 9 16 23 34 44",
      "전체 빈도: 3 10 17 24 35 43",
      "번호 쌍: 4 11 18 25 36 42",
      "균형: 5 12 19 26 37 41",
      "근거 조합 1: 7 12 24 37 39 40",
      "근거 조합 2: 6 13 20 27 38 40",
      DISCLAIMER,
    ]);
  });

  it("leaves out evidence text", () => {
    // given / when
    const text = recommendationShareText(set);

    // then
    expect(text).not.toContain("번호 8:");
  });

  it("uses the date of a carried-over recommendation", () => {
    // given
    const carried = {
      ...set,
      date: "2026-11-07",
      round: 1249,
      carriedOver: true,
    };

    // when
    const first = recommendationShareText(carried).split("\n")[0];

    // then
    expect(first).toBe("1249회 · 11월 7일 추천");
  });
});
