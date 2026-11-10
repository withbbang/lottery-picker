/** 지역 하나의 당첨 건수. 백엔드 AreaCountResponse와 같다. */
export type AreaCountResponse = {
  name: string;
  count: number;
};

/** 판매점 하나. 좌표·상태는 가장 최근 당첨 행 값. 백엔드 StoreResponse와 같다. */
export type StoreResponse = {
  name: string;
  address: string;
  region: string;
  district: string | null;
  wins: number;
  lastRound: number;
  latitude: number | null;
  longitude: number | null;
  status: string;
};

/** 당첨 판매점 지역 통계. 백엔드 StoreStatisticsResponse와 같다. */
export type StoreStatisticsResponse = {
  rank: 1 | 2;
  fromRound: number;
  toRound: number;
  regions: AreaCountResponse[];
  districts: AreaCountResponse[] | null;
  repeatStores: StoreResponse[];
  pins: StoreResponse[];
};
