import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LatestRoundSummary } from "@/components/recommendation/LatestRoundSummary";

const fetchMock = vi.fn();

function page(items: unknown[]) {
  return new Response(
    JSON.stringify({
      success: true,
      data: {
        items,
        page: 0,
        size: 1,
        totalElements: items.length,
        totalPages: items.length,
      },
    }),
  );
}

function renderSummary() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <LatestRoundSummary />
    </QueryClientProvider>,
  );
}

describe("LatestRoundSummary", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("links to the latest round with its numbers and bonus", async () => {
    // given
    fetchMock.mockResolvedValue(
      page([
        {
          round: 1248,
          numbers: [16, 18, 27, 36, 38, 42],
          bonus: 22,
          drawDate: "2026-10-31",
        },
      ]),
    );

    // when
    renderSummary();

    // then
    const link = await screen.findByRole("link", { name: /1248회/ });
    expect(link).toHaveAttribute("href", "/rounds/1248");
    expect(screen.getByText("2026-10-31")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("22")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/rounds?page=0&size=1",
      undefined,
    );
  });

  it("says so when no round is stored", async () => {
    // given
    fetchMock.mockResolvedValue(page([]));

    // when
    renderSummary();

    // then
    expect(
      await screen.findByText("저장된 회차가 없습니다."),
    ).toBeInTheDocument();
  });
});
