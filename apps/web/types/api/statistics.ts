/** 번호 통계 지표. 최근 50회 출현 / 미출현 회차 / 전체 출현. */
export type NumberMetric = "RECENT" | "COLD" | "ALL_TIME";

export type RankedNumberResponse = {
  number: number;
  value: number;
  /** 공동 순위(1, 2, 2, 4…). 서버가 계산한다. */
  rank: number;
  /** 같은 값의 번호가 또 있으면 true("공동 N위"). */
  tied: boolean;
};

/** 한 날짜 추천을 만들 때 쓴 회차 범위 기준의 번호 통계. */
export type NumberStatisticsResponse = {
  date: string;
  historyFromRound: number;
  historyToRound: number;
  totalRounds: number;
  recentWindow: number;
  /** 지표마다 45개 번호를 순위 순서대로. 받은 순서 그대로 그린다. */
  metrics: Record<NumberMetric, RankedNumberResponse[]>;
};
