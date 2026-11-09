import type { PrizeRank } from "@/types/api/result";
import type { StrategyType } from "@/types/api/recommendation";

export type StrategySimulationResponse = {
  strategy: StrategyType | "CONTROL";
  lineCount: number;
  averageMatches: number;
  rankCounts: Record<PrizeRank, number>;
  /** 0~1 */
  atLeastFifthRate: number;
  /** 5등 이상 비율의 95% 신뢰구간(정규근사), 0~1 */
  ciLow: number;
  ciHigh: number;
  /** 구간이 대조군 구간과 겹치지 않으면 true. 대조군 자신은 false */
  differsFromControl: boolean;
};

export type SimulationResponse = {
  fromRound: number;
  toRound: number;
  repetitions: number;
  computedAt: string;
  theoryAtLeastFifthRate: number;
  scopeNote: string;
  strategies: StrategySimulationResponse[];
};
