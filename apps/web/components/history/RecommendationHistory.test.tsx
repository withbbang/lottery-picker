import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RecommendationHistory } from "@/components/history/RecommendationHistory";
import type {
  RecommendationHistoryLineResponse,
  RecommendationHistoryResponse,
} from "@/types/api/result";

const fetchMock = vi.fn();

function line(
  overrides: Partial<RecommendationHistoryLineResponse>,
): RecommendationHistoryLineResponse {
  return {
    strategy: "RECENT_HOT",
    numbers: [1, 2, 3, 4, 30, 45],
    evidence: ["근거"],
    relaxation: "NONE",
    evidenceNumbers: [],
    statsMetric: null,
    matchCount: null,
    prizeRank: null,
    matchedNumbers: null,
    matchedBonus: null,
    ...overrides,
  };
}

function respond(
  items: RecommendationHistoryResponse[],
  page = 0,
  totalPages = 1,
) {
  return new Response(
    JSON.stringify({
      success: true,
      data: { items, page, size: 10, totalElements: items.length, totalPages },
    }),
  );
}

function renderHistory() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <RecommendationHistory />
    </QueryClientProvider>,
  );
}

describe("RecommendationHistory", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows an empty message when there are no recommendations", async () => {
    // given
    fetchMock.mockResolvedValue(respond([], 0, 0));

    // when
    renderHistory();

    // then
    expect(
      await screen.findByText(/아직 추천이 없습니다/),
    ).toBeInTheDocument();
  });

  it("shows each day with its round and the server's per-line results", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond([
        {
          date: "2026-11-09",
          round: 1250,
          lines: [line({ strategy: "PAIR" })],
        },
        {
          date: "2026-11-07",
          round: 1249,
          lines: [
            line({
              matchCount: 4,
              prizeRank: "FOURTH",
              matchedNumbers: [1, 2, 3, 4],
            }),
            line({
              strategy: "COMBINED",
              matchCount: 1,
              prizeRank: "NONE",
              matchedNumbers: [1],
            }),
            line({
              strategy: "COMBINED",
              matchCount: 0,
              prizeRank: "NONE",
              matchedNumbers: [],
            }),
          ],
        },
      ]),
    );

    // when
    renderHistory();

    // then
    const days = await screen.findAllByRole("article");
    expect(
      within(days[0]).getByRole("link", { name: "1250회" }),
    ).toHaveAttribute("href", "/rounds/1250");
    expect(within(days[0]).getByText("추첨 전")).toBeInTheDocument();
    expect(within(days[1]).getByText("4등 당첨")).toBeInTheDocument();
    expect(within(days[1]).getByText("4개 일치")).toBeInTheDocument();
    expect(within(days[1]).getByText("4등")).toBeInTheDocument();
    expect(within(days[1]).getByText("근거 조합 1")).toBeInTheDocument();
    expect(within(days[1]).getByText("근거 조합 2")).toBeInTheDocument();
  });

  it("notes the bonus number when the server reports one on a line", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond([
        {
          date: "2026-11-07",
          round: 1249,
          lines: [
            line({
              matchCount: 5,
              prizeRank: "SECOND",
              matchedNumbers: [1, 2, 3, 4, 30],
              matchedBonus: 45,
            }),
          ],
        },
      ]),
    );

    // when
    renderHistory();

    // then
    expect(await screen.findByText("5개 일치 +보너스")).toBeInTheDocument();
    expect(screen.getByText("2등")).toBeInTheDocument();
  });

  it("toggles evidence on a past line", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond([
        {
          date: "2026-11-07",
          round: 1249,
          lines: [line({ evidence: ["번호 30: 18회째 미출현"] })],
        },
      ]),
    );
    renderHistory();
    const button = await screen.findByRole("button", { name: /근거/ });
    expect(screen.getByText("번호 30: 18회째 미출현")).not.toBeVisible();

    // when
    await userEvent.click(button);

    // then
    expect(screen.getByText("번호 30: 18회째 미출현")).toBeVisible();
    expect(button).toHaveAttribute("aria-expanded", "true");
  });

  it("hides the evidence toggle on a line with nothing to show", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond([
        {
          date: "2026-11-07",
          round: 1249,
          lines: [line({ evidence: [] })],
        },
      ]),
    );

    // when
    renderHistory();
    await screen.findByText("11월 7일");

    // then
    expect(
      screen.queryByRole("button", { name: /근거/ }),
    ).not.toBeInTheDocument();
  });

  it("links past evidence to the statistics of that date", async () => {
    // given
    fetchMock.mockResolvedValue(
      respond([
        {
          date: "2026-11-07",
          round: 1249,
          lines: [
            line({
              strategy: "LONG_COLD",
              evidence: ["번호 30: 18회째 미출현"],
              evidenceNumbers: [30],
              statsMetric: "COLD",
            }),
          ],
        },
      ]),
    );
    renderHistory();

    // when
    await userEvent.click(await screen.findByRole("button", { name: /근거/ }));

    // then
    expect(screen.getByRole("link", { name: "통계에서 보기" })).toHaveAttribute(
      "href",
      "/numbers?date=2026-11-07&metric=COLD&highlight=30",
    );
  });

  it("requests the clicked page", async () => {
    // given
    fetchMock.mockImplementation(async (url: string) => {
      const page = Number(
        new URL(url, "http://localhost").searchParams.get("page"),
      );
      return respond(
        [
          {
            date: page === 0 ? "2026-11-09" : "2026-10-30",
            round: 1250,
            lines: [line({})],
          },
        ],
        page,
        2,
      );
    });
    renderHistory();
    await screen.findByText("11월 9일");

    // when
    await userEvent.click(screen.getByRole("button", { name: "2" }));

    // then
    expect(await screen.findByText("10월 30일")).toBeInTheDocument();
    expect(fetchMock.mock.lastCall?.[0]).toBe(
      "/api/v1/recommendations/history?page=1&size=7",
    );
  });

  it("moves two pages when next is pressed twice while the next page is loading", async () => {
    // given: 1페이지 이후 응답은 바로 오지 않는다
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    fetchMock.mockImplementation(async (url: string) => {
      const page = Number(
        new URL(url, "http://localhost").searchParams.get("page"),
      );
      if (page > 0) {
        await gate;
      }
      return respond(
        [{ date: `2026-10-0${page + 1}`, round: 1250, lines: [line({})] }],
        page,
        5,
      );
    });
    renderHistory();
    await screen.findByText("10월 1일");

    // when
    await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
    await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
    release();

    // then
    expect(await screen.findByText("10월 3일")).toBeInTheDocument();
    expect(fetchMock.mock.lastCall?.[0]).toBe(
      "/api/v1/recommendations/history?page=2&size=7",
    );
  });
});
