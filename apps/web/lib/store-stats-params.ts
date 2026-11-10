export type StoreRank = 1 | 2;

export type StoreStatsParams = {
  rank: StoreRank;
  region: string | null;
  district: string | null;
};

/** 주소 파라미터를 읽는다. 등수는 "2"만 2등, 그 밖은 1등. 시·도 없는 시·군·구는 버린다. */
export function parseStoreStatsParams(params: {
  get(name: string): string | null;
}): StoreStatsParams {
  const rank = params.get("rank") === "2" ? 2 : 1;
  const region = params.get("region")?.trim() || null;
  const district =
    region === null ? null : params.get("district")?.trim() || null;
  return { rank, region, district };
}

function query(params: StoreStatsParams, withDefaultRank: boolean) {
  const search = new URLSearchParams();
  if (withDefaultRank || params.rank !== 1) {
    search.set("rank", String(params.rank));
  }
  if (params.region !== null) {
    search.set("region", params.region);
    if (params.district !== null) {
      search.set("district", params.district);
    }
  }
  return search.toString();
}

/** 판매점 통계 화면 주소. 기본값(1등, 전국)은 뺀다. */
export function storeStatsHref(params: StoreStatsParams): string {
  const search = query(params, false);
  return search ? `/rounds/stores?${search}` : "/rounds/stores";
}

/** 판매점 통계 API 경로. */
export function storeStatsApiPath(params: StoreStatsParams): string {
  return `/api/v1/store-statistics?${query(params, true)}`;
}
