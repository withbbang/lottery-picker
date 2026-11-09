export type StrategyType =
  "RECENT_HOT" | "LONG_COLD" | "ALL_TIME" | "PAIR" | "BALANCED";

export type Relaxation = "NONE" | "OVERLAP" | "OVERLAP_AND_POPULARITY";

export type RecommendationLineResponse = {
  strategy: StrategyType;
  numbers: number[];
  evidence: string[];
  relaxation: Relaxation;
};

export type NotificationStatus = "PENDING" | "SENT" | "FAILED" | "SKIPPED";

export type RecommendationSetResponse = {
  date: string;
  round: number;
  lines: RecommendationLineResponse[];
  historyFromRound: number;
  historyToRound: number;
  disclaimer: string;
  notificationStatus: NotificationStatus;
  /** 오늘 추천이 아직 없어 가장 최근 추천을 대신 보여 주는지. 서버가 판단한다. */
  carriedOver: boolean;
  /** 이 추천의 회차가 판매 마감됐는지. */
  roundClosed: boolean;
  /** 이월 중이고 오늘 추천 생성 시각(17시) 전인지. false면 오늘 추천 생성을 놓친 것이다. */
  todayPending: boolean;
};

export type RecommendationSettingsRequest = {
  fixed: number[];
  excluded: number[];
};

export type RecommendationSettingsResponse = {
  fixed: number[];
  excluded: number[];
};
