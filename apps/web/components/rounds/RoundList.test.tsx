import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
          data: {
            items: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
          },
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
    expect(
      await screen.findByText("아직 수집한 회차가 없습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("navigation", { name: "페이지" }),
    ).not.toBeInTheDocument();
  });

  it("shows the five pages around the current one and moves to a clicked page", async () => {
    // given
    fetchMock.mockImplementation(async (url: string) => {
      const page = Number(
        new URL(url, "http://localhost").searchParams.get("page"),
      );
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            items: [
              {
                round: 1248 - page,
                numbers: [1, 2, 3, 4, 5, 6],
                bonus: 7,
                drawDate: "2026-11-07",
              },
            ],
            page,
            size: 20,
            totalElements: 1260,
            totalPages: 63,
          },
        }),
      );
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <RoundList />
      </QueryClientProvider>,
    );
    const pager = await screen.findByRole("navigation", { name: "페이지" });
    expect(within(pager).getByText("1")).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("button", { name: "5" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "6" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "이전 페이지" })).toBeDisabled();

    // when
    await userEvent.click(screen.getByRole("button", { name: "3" }));

    // then
    expect(await screen.findByText("1246회")).toBeInTheDocument();
    expect(pager.querySelector("[aria-current=page]")).toHaveTextContent("3");
  });

  it("jumps to the last and first page", async () => {
    // given
    fetchMock.mockImplementation(async (url: string) => {
      const page = Number(
        new URL(url, "http://localhost").searchParams.get("page"),
      );
      return new Response(
        JSON.stringify({
          success: true,
          data: {
            items: [
              {
                round: 63 - page,
                numbers: [1, 2, 3, 4, 5, 6],
                bonus: 7,
                drawDate: "2026-11-07",
              },
            ],
            page,
            size: 20,
            totalElements: 1260,
            totalPages: 63,
          },
        }),
      );
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <RoundList />
      </QueryClientProvider>,
    );
    const pager = await screen.findByRole("navigation", { name: "페이지" });

    // when
    await userEvent.click(
      within(pager).getByRole("button", { name: "마지막 페이지" }),
    );

    // then
    expect(await screen.findByText("1회")).toBeInTheDocument();
    expect(pager.querySelector("[aria-current=page]")).toHaveTextContent("63");
    expect(
      within(pager).getByRole("button", { name: "마지막 페이지" }),
    ).toBeDisabled();

    // when
    await userEvent.click(
      within(pager).getByRole("button", { name: "첫 페이지" }),
    );

    // then
    expect(await screen.findByText("63회")).toBeInTheDocument();
    expect(pager.querySelector("[aria-current=page]")).toHaveTextContent("1");
  });
});
