import { describe, expect, it } from "vitest";

import {
  parseStoreStatsParams,
  storeStatsApiPath,
  storeStatsHref,
} from "@/lib/store-stats-params";

describe("parseStoreStatsParams", () => {
  it("reads rank region and district", () => {
    // given
    const params = new URLSearchParams("rank=2&region=서울&district=중랑구");

    // when / then
    expect(parseStoreStatsParams(params)).toEqual({
      rank: 2,
      region: "서울",
      district: "중랑구",
    });
  });

  it("falls back to rank 1 for anything but 2", () => {
    // given / when / then
    expect(parseStoreStatsParams(new URLSearchParams("rank=3")).rank).toBe(1);
    expect(parseStoreStatsParams(new URLSearchParams("rank=abc")).rank).toBe(1);
    expect(parseStoreStatsParams(new URLSearchParams("")).rank).toBe(1);
  });

  it("drops a district without a region", () => {
    // given / when
    const parsed = parseStoreStatsParams(
      new URLSearchParams("region=&district=중랑구"),
    );

    // then
    expect(parsed).toEqual({ rank: 1, region: null, district: null });
  });
});

describe("storeStatsHref", () => {
  it("builds a page link without defaults", () => {
    // given / when / then
    expect(storeStatsHref({ rank: 1, region: null, district: null })).toBe(
      "/rounds/stores",
    );
    expect(
      storeStatsHref({ rank: 2, region: "서울", district: "중랑구" }),
    ).toBe(
      "/rounds/stores?rank=2&region=%EC%84%9C%EC%9A%B8&district=%EC%A4%91%EB%9E%91%EA%B5%AC",
    );
  });
});

describe("storeStatsApiPath", () => {
  it("builds the api path", () => {
    // given / when / then
    expect(storeStatsApiPath({ rank: 1, region: null, district: null })).toBe(
      "/api/v1/store-statistics?rank=1",
    );
    expect(storeStatsApiPath({ rank: 2, region: "경기", district: null })).toBe(
      "/api/v1/store-statistics?rank=2&region=%EA%B2%BD%EA%B8%B0",
    );
  });
});
