export type StrategyType =
  "RECENT_HOT" | "LONG_COLD" | "ALL_TIME" | "PAIR" | "BALANCED";

export type Relaxation = "NONE" | "OVERLAP" | "OVERLAP_AND_POPULARITY";

export type RecommendationLineResponse = {
  strategy: StrategyType;
  numbers: number[];
  evidence: string[];
  relaxation: Relaxation;
};

export type RecommendationSetResponse = {
  date: string;
  round: number;
  lines: RecommendationLineResponse[];
  historyFromRound: number;
  historyToRound: number;
  disclaimer: string;
};

export type RecommendationSettingsRequest = {
  fixed: number[];
  excluded: number[];
};

export type RecommendationSettingsResponse = {
  fixed: number[];
  excluded: number[];
};
