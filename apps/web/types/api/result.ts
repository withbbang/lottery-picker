import type { Relaxation, StrategyType } from "@/types/api/recommendation";
import type { NumberMetric } from "@/types/api/statistics";

export type PrizeRank =
  "FIRST" | "SECOND" | "THIRD" | "FOURTH" | "FIFTH" | "NONE";

export type RoundResultLineResponse = {
  date: string;
  strategy: StrategyType;
  numbers: number[];
  matchCount: number;
  prizeRank: PrizeRank;
  prizeAmount: number;
};

export type RoundResultResponse = {
  round: number;
  lines: RoundResultLineResponse[];
  rankCounts: Record<PrizeRank, number>;
  prizeTotal: number;
  cost: number;
  profit: number;
  /** 추천은 있는데 아직 채점하지 않은 줄이 있으면 true */
  pending: boolean;
};

/** 지난 추천 한 줄. 채점 전이면 matchCount, prizeRank, matchedNumbers, matchedBonus가 null이다. */
export type RecommendationHistoryLineResponse = {
  strategy: StrategyType;
  numbers: number[];
  evidence: string[];
  relaxation: Relaxation;
  /** 오늘 추천 응답과 같은 뜻. */
  evidenceNumbers: number[];
  statsMetric: NumberMetric | null;
  matchCount: number | null;
  prizeRank: PrizeRank | null;
  /** 당첨 본번호와 겹친 번호. 서버가 계산한다. 보너스는 넣지 않는다. */
  matchedNumbers: number[] | null;
  /** 이 줄에 있는 보너스 번호. 없거나 채점 전이면 null. */
  matchedBonus: number | null;
};

/** 하루치 추천 한 벌. */
export type RecommendationHistoryResponse = {
  date: string;
  round: number;
  lines: RecommendationHistoryLineResponse[];
};
