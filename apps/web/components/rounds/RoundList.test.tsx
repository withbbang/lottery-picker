import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RoundList } from "@/components/rounds/RoundList";

const fetchMock = vi.fn();

describe("RoundList", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows an empty message and no pager when there are no rounds", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
      ),
    );
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    // when
    render(
      <QueryClientProvider client={client}>
        <RoundList />
      </QueryClientProvider>,
    );

    // then
    expect(await screen.findByText("조회된 회차가 없습니다.")).toBeInTheDocument();
    expect(screen.queryByText("1 / 0")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "다음" })).not.toBeInTheDocument();
  });
});
