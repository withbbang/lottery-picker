export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RoundSummaryResponse = {
  round: number;
  numbers: number[];
  bonus: number;
  drawDate: string;
};

export type PrizeTierResponse = {
  rank: number;
  winnerCount: number;
  amountPerWinner: number;
};

export type RoundDetailResponse = RoundSummaryResponse & {
  prizes: PrizeTierResponse[];
};

export type WinningStoreResponse = {
  rank: number;
  region: string;
  name: string;
  address: string;
  purchaseType: string | null;
  status: string | null;
};
