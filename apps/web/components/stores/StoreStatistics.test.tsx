import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { StoreStatistics } from "@/components/stores/StoreStatistics";
import type { StoreStatsParams } from "@/lib/store-stats-params";
import type { StoreResponse, StoreStatisticsResponse } from "@/types/api/store";

// jsdom에는 지도를 그릴 캔버스·크기가 없다. 지도에 넘어간 핀 수만 보인다.
vi.mock("next/dynamic", () => ({
  default: () =>
    function MapStub({ pins }: { pins: StoreResponse[] }) {
      return <p>지도 핀 {pins.length}개</p>;
    },
}));

const fetchMock = vi.fn();

function store(overrides: Partial<StoreResponse> = {}): StoreResponse {
  return {
    name: "망우점",
    address: "서울 중랑구 망우로 1",
    region: "서울",
    district: "중랑구",
    wins: 3,
    lastRound: 1249,
    latitude: 37.5,
    longitude: 127.1,
    status: "정상",
    ...overrides,
  };
}

const nationwide: StoreStatisticsResponse = {
  rank: 1,
  fromRound: 262,
  toRound: 1249,
  regions: [
    { name: "경기", count: 2100 },
    { name: "서울", count: 1900 },
    { name: "온라인", count: 120 },
  ],
  districts: null,
  repeatStores: [store()],
  pins: [],
};

const seoul: StoreStatisticsResponse = {
  ...nationwide,
  districts: [
    { name: "중랑구", count: 40 },
    { name: "강남구", count: 35 },
  ],
  pins: [store(), store({ name: "강남점", wins: 1 })],
};

function respond(data: StoreStatisticsResponse) {
  return new Response(JSON.stringify({ success: true, data }));
}

function renderStats(params: Partial<StoreStatsParams> = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <StoreStatistics
        params={{ rank: 1, region: null, district: null, ...params }}
      />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

describe("StoreStatistics", () => {
  it("shows the nationwide region ranking with period and notice", async () => {
    // given
    fetchMock.mockResolvedValue(respond(nationwide));

    // when
    renderStats();

    // then
    expect(
      await screen.findByText("판매점 정보가 있는 262회~1249회 기준"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("판매점이 많은 지역일수록 당첨도 많이 나옵니다."),
    ).toBeInTheDocument();
    const ranking = screen.getByRole("list", { name: "시·도 순위" });
    const first = within(ranking).getAllByRole("link")[0];
    expect(first).toHaveAccessibleName(/경기/);
    expect(first).toHaveAttribute(
      "href",
      "/rounds/stores?region=%EA%B2%BD%EA%B8%B0",
    );
    expect(screen.queryByText(/지도 핀/)).not.toBeInTheDocument();
  });

  it("requests the second rank when switched", async () => {
    // given
    fetchMock.mockResolvedValue(respond({ ...nationwide, rank: 2 }));

    // when
    renderStats({ rank: 2 });

    // then
    await screen.findByRole("list", { name: "시·도 순위" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/store-statistics?rank=2",
      undefined,
    );
    expect(screen.getByRole("link", { name: "2등" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "1등" })).toHaveAttribute(
      "href",
      "/rounds/stores",
    );
  });

  it("shows districts and the map for a chosen region", async () => {
    // given
    fetchMock.mockResolvedValue(respond(seoul));

    // when
    renderStats({ region: "서울" });

    // then
    const ranking = await screen.findByRole("list", {
      name: "서울 시·군·구 순위",
    });
    expect(
      within(ranking).getByRole("link", { name: /중랑구/ }),
    ).toHaveAttribute(
      "href",
      "/rounds/stores?region=%EC%84%9C%EC%9A%B8&district=%EC%A4%91%EB%9E%91%EA%B5%AC",
    );
    const trail = screen.getByRole("navigation", { name: "지역 경로" });
    expect(within(trail).getByRole("link", { name: "전국" })).toHaveAttribute(
      "href",
      "/rounds/stores",
    );
    expect(screen.getByText("지도 핀 2개")).toBeInTheDocument();
  });

  it("marks the chosen district", async () => {
    // given
    fetchMock.mockResolvedValue(respond(seoul));

    // when
    renderStats({ region: "서울", district: "중랑구" });

    // then
    const ranking = await screen.findByRole("list", {
      name: "서울 시·군·구 순위",
    });
    expect(
      within(ranking).getByRole("link", { name: /중랑구/ }),
    ).toHaveAttribute("aria-current", "page");
    const trail = screen.getByRole("navigation", { name: "지역 경로" });
    expect(within(trail).getByRole("link", { name: "서울" })).toHaveAttribute(
      "href",
      "/rounds/stores?region=%EC%84%9C%EC%9A%B8",
    );
  });

  it("shows a trail where region and district share a name", async () => {
    // given: 세종은 시·도와 시·군·구가 모두 "세종"이다
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});
    fetchMock.mockResolvedValue(
      respond({ ...seoul, districts: [{ name: "세종", count: 36 }] }),
    );

    // when
    renderStats({ region: "세종", district: "세종" });

    // then
    const trail = await screen.findByRole("navigation", { name: "지역 경로" });
    expect(within(trail).getByRole("link", { name: "세종" })).toHaveAttribute(
      "href",
      "/rounds/stores?region=%EC%84%B8%EC%A2%85",
    );
    expect(within(trail).getAllByText("세종")).toHaveLength(2);
    const duplicateKeys = errors.mock.calls.filter(([message]) =>
      String(message).includes("same key"),
    );
    expect(duplicateKeys).toEqual([]);
    errors.mockRestore();
  });

  it("hides the map for online sales", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond({ ...nationwide, districts: [], pins: [] }),
    );

    // when
    renderStats({ region: "온라인" });

    // then
    await screen.findByText("판매점 정보가 있는 262회~1249회 기준");
    expect(screen.queryByText(/지도 핀/)).not.toBeInTheDocument();
  });

  it("lists repeat stores with closed status", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond({ ...nationwide, repeatStores: [store({ status: "폐점" })] }),
    );

    // when
    renderStats();

    // then
    const list = await screen.findByRole("list", {
      name: "두 번 이상 당첨된 판매점",
    });
    expect(within(list).getByText("망우점")).toBeInTheDocument();
    expect(within(list).getByText("폐점")).toBeInTheDocument();
    expect(within(list).getByText("3회")).toBeInTheDocument();
    expect(within(list).getByRole("link", { name: "1249회" })).toHaveAttribute(
      "href",
      "/rounds/1249",
    );
  });

  it("shows an error with a link back for an unknown area", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          error: { code: "COMMON_NOT_FOUND", message: "not found" },
        }),
        { status: 404 },
      ),
    );

    // when
    renderStats({ region: "화성" });

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "그 지역의 당첨 판매점 정보가 없습니다.",
    );
    expect(screen.getByRole("link", { name: "전국 보기" })).toHaveAttribute(
      "href",
      "/rounds/stores",
    );
  });

  it("shows an empty message on 204", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    // when
    renderStats();

    // then
    expect(
      await screen.findByText("아직 판매점 정보가 없습니다."),
    ).toBeInTheDocument();
  });
});
