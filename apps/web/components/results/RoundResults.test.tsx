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

  it("labels amounts as 가상 and lists every line without opening anything", async () => {
    // when
    renderResults(scored);

    // then
    expect(await screen.findByText("가상 당첨금")).toBeInTheDocument();
    expect(screen.getByText("가상 수익")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /줄별 결과/ })).toBeVisible();
    expect(screen.getByText("4개 일치")).toBeVisible();
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
      await screen.findByText("이 회차에는 추천한 번호가 없습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByText("가상 당첨금")).not.toBeInTheDocument();
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
      await screen.findByText("아직 채점 전입니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("이 회차에는 추천한 번호가 없습니다."),
    ).not.toBeInTheDocument();
  });

  it("lists every combined line of the same day", async () => {
    // given: 근거 조합은 하루에 여러 줄이다
    const combined = (numbers: number[]) => ({
      date: "2026-11-01",
      strategy: "COMBINED" as const,
      numbers,
      matchCount: 0,
      prizeRank: "NONE" as const,
      prizeAmount: 0,
    });
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});

    // when
    renderResults({
      ...scored,
      lines: [
        ...scored.lines,
        combined([5, 6, 7, 8, 9, 10]),
        combined([11, 12, 13, 14, 15, 16]),
      ],
    });

    // then
    expect(await screen.findAllByText(/근거 조합/)).toHaveLength(2);
    expect(
      errors.mock.calls.some((call) => String(call[0]).includes("same key")),
    ).toBe(false);
    errors.mockRestore();
  });
});
