import type { NumberMetric } from "@/types/api/statistics";

export type StrategyType =
  | "RECENT_HOT"
  | "LONG_COLD"
  | "ALL_TIME"
  | "PAIR"
  | "BALANCED"
  | "COMBINED";

export type Relaxation = "NONE" | "OVERLAP" | "OVERLAP_AND_POPULARITY";

export type RecommendationLineResponse = {
  strategy: StrategyType;
  numbers: number[];
  evidence: string[];
  relaxation: Relaxation;
  /** 근거 문구에 적힌 번호(번호 통계에서 강조할 번호). 서버가 준다. 지표 없는 전략은 빈 배열. */
  evidenceNumbers: number[];
  /** 이 줄의 근거를 확인할 번호 통계 지표. 없으면 null. */
  statsMetric: NumberMetric | null;
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
