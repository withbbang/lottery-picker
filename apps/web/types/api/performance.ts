import type { PrizeRank } from "@/types/api/result";
import type { StrategyType } from "@/types/api/recommendation";

export type StrategyPerformanceResponse = {
  strategy: StrategyType | "CONTROL";
  lineCount: number;
  averageMatches: number;
  rankCounts: Record<PrizeRank, number>;
  /** 0~1. 줄이 없으면 0 */
  atLeastFifthRate: number;
  /** 고정·제외 번호가 없는 줄만의 줄 수와 5등 이상 비율 */
  noSettingsLineCount: number;
  noSettingsAtLeastFifthRate: number;
  sampleSufficient: boolean;
};

export type StrategyPerformancesResponse = {
  theoryAtLeastFifthRate: number;
  strategies: StrategyPerformanceResponse[];
};
