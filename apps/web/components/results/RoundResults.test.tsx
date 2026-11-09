import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RoundResults } from "@/components/results/RoundResults";
import type { RoundResultResponse } from "@/types/api/result";

const fetchMock = vi.fn();

const scored: RoundResultResponse = {
  round: 1249,
  lines: [
    {
      date: "2026-11-01",
      strategy: "RECENT_HOT",
      numbers: [1, 2, 3, 4, 30, 31],
      matchCount: 4,
      prizeRank: "FOURTH",
      prizeAmount: 50000,
    },
    {
      date: "2026-11-01",
      strategy: "LONG_COLD",
      numbers: [1, 2, 3, 30, 31, 32],
      matchCount: 3,
      prizeRank: "FIFTH",
      prizeAmount: 5000,
    },
    {
      date: "2026-11-01",
      strategy: "ALL_TIME",
      numbers: [20, 21, 22, 23, 24, 25],
      matchCount: 0,
      prizeRank: "NONE",
      prizeAmount: 0,
    },
  ],
  rankCounts: { FIRST: 0, SECOND: 0, THIRD: 0, FOURTH: 1, FIFTH: 1, NONE: 1 },
  prizeTotal: 55000,
  cost: 3000,
  profit: 52000,
  pending: false,
};

function renderResults(data: RoundResultResponse) {
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify({ success: true, data })),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <RoundResults round={data.round} />
    </QueryClientProvider>,
  );
}

describe("RoundResults", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows rank counts and prize total", async () => {
    // when
    renderResults(scored);

    // then
    const counts = await screen.findByRole("list", { name: "등수별 줄 수" });
    expect(within(counts).getByText("4등").closest("li")).toHaveTextContent(
      "1줄",
    );
    expect(within(counts).getByText("낙첨").closest("li")).toHaveTextContent(
      "1줄",
    );
    expect(screen.getByText("55,000원")).toBeInTheDocument();
    expect(screen.getByText("+52,000원")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/rounds/1249/results",
      undefined,
    );
  });

  it("labels amounts as 구매했다고 가정", async () => {
    // when
    renderResults(scored);

    // then
    expect(
      await screen.findByText(
        "구매했다고 가정한 가상 금액입니다. 줄당 1,000원, 3줄 3,000원",
      ),
    ).toBeInTheDocument();
  });

  it("shows an empty message without recommendations", async () => {
    // when
    renderResults({
      round: 1249,
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
      pending: false,
    });

    // then
    expect(
      await screen.findByText("이 회차 추천이 없습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/가상 금액/)).not.toBeInTheDocument();
  });

  it("shows a neutral message while results are not computed yet", async () => {
    // when
    renderResults({
      round: 1249,
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
      pending: true,
    });

    // then
    expect(
      await screen.findByText("아직 비교 결과를 계산하지 않았습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("이 회차 추천이 없습니다."),
    ).not.toBeInTheDocument();
  });
});
