import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RoundDetail } from "@/components/rounds/RoundDetail";
import type {
  RoundDetailResponse,
  WinningStoreResponse,
} from "@/types/api/round";

const fetchMock = vi.fn();

const detail: RoundDetailResponse = {
  round: 1248,
  numbers: [16, 18, 27, 36, 38, 42],
  bonus: 22,
  drawDate: "2026-10-31",
  prizes: [
    { rank: 1, winnerCount: 15, amountPerWinner: 1933658450 },
    { rank: 2, winnerCount: 91, amountPerWinner: 53122485 },
    { rank: 3, winnerCount: 3265, amountPerWinner: 1480597 },
    { rank: 4, winnerCount: 167745, amountPerWinner: 50000 },
    { rank: 5, winnerCount: 2723467, amountPerWinner: 5000 },
  ],
};

function store(
  rank: number,
  region: string,
  name: string,
): WinningStoreResponse {
  return {
    rank,
    region,
    name,
    address: `${region} 주소`,
    purchaseType: "자동",
    status: "영업중",
  };
}

function ok(data: unknown) {
  return new Response(JSON.stringify({ success: true, data }));
}

function mockApi(stores: WinningStoreResponse[], round: unknown = detail) {
  fetchMock.mockImplementation(async (path: string) => {
    if (path.endsWith("/winning-stores")) {
      return ok(stores);
    }
    if (path.endsWith("/results")) {
      return ok({
        round: 1248,
        lines: [],
        rankCounts: {
          FIRST: 0,
          SECOND: 0,
          THIRD: 0,
          FOURTH: 0,
          FIFTH: 0,
          NONE: 0,
        },
        prizeTotal: 0,
        cost: 0,
        profit: 0,
      });
    }
    if (round === null) {
      return new Response(
        JSON.stringify({
          success: false,
          error: { code: "COMMON_NOT_FOUND", message: "없음" },
        }),
        { status: 404 },
      );
    }
    return ok(round);
  });
}

function renderDetail(round = 1248) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <RoundDetail round={round} />
    </QueryClientProvider>,
  );
}

describe("RoundDetail", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows winner count and amount for ranks 1 to 5", async () => {
    // given
    mockApi([]);

    // when
    renderDetail();

    // then
    const first = (await screen.findByText("1등")).closest("tr")!;
    expect(within(first).getByText("15명")).toBeInTheDocument();
    expect(within(first).getByText("1,933,658,450원")).toBeInTheDocument();
    const fifth = screen.getByText("5등").closest("tr")!;
    expect(within(fifth).getByText("2,723,467명")).toBeInTheDocument();
    expect(within(fifth).getByText("5,000원")).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(6);
  });

  it("labels the region section as 당첨 판매점 지역", async () => {
    // given
    mockApi([store(1, "서울", "가게A")]);

    // when
    renderDetail();

    // then
    expect(
      await screen.findByRole("heading", { name: "당첨 판매점 지역" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("당첨자 지역")).not.toBeInTheDocument();
  });

  it("shows 지역 정보 없음 when no stores exist", async () => {
    // given
    mockApi([]);

    // when
    renderDetail(1);

    // then
    expect(await screen.findByText("지역 정보 없음")).toBeInTheDocument();
  });

  it("states that ranks 3 to 5 have no region data", async () => {
    // given
    mockApi([store(1, "서울", "가게A")]);

    // when
    renderDetail();

    // then
    expect(
      await screen.findByText("3~5등은 지역 정보가 제공되지 않습니다."),
    ).toBeInTheDocument();
  });

  it("shows the recommendation comparison section", async () => {
    // given
    mockApi([]);

    // when
    renderDetail();

    // then
    expect(
      await screen.findByRole("heading", { name: "추천 비교" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("이 회차 추천이 없습니다."),
    ).toBeInTheDocument();
  });

  it("shows a not-found message for a missing round", async () => {
    // given
    mockApi([], null);

    // when
    renderDetail(9999);

    // then
    expect(
      await screen.findByText("존재하지 않는 회차입니다."),
    ).toBeInTheDocument();
  });

  it("shows 온라인 as its own region group with store counts", async () => {
    // given
    mockApi([
      store(1, "서울", "가게A"),
      store(1, "온라인", "동행복권"),
      store(2, "온라인", "동행복권"),
    ]);

    // when
    renderDetail();

    // then
    const online = (await screen.findByText("온라인")).closest("summary")!;
    expect(within(online).getByText("2건")).toBeInTheDocument();
    const seoul = screen.getByText("서울").closest("summary")!;
    expect(within(seoul).getByText("1건")).toBeInTheDocument();
  });

  it("reveals the store list when a region group is opened", async () => {
    // given
    mockApi([store(1, "부산", "가게B")]);
    renderDetail();
    const summary = (await screen.findByText("부산")).closest("summary")!;

    // when
    await userEvent.click(summary);

    // then
    expect(screen.getByText("가게B")).toBeVisible();
  });
});
