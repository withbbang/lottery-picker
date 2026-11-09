import type { StrategyType } from "@/types/api/recommendation";

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
};
