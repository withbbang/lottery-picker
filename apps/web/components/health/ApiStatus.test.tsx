import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiStatus } from "@/components/health/ApiStatus";

const fetchMock = vi.fn();

function renderStatus() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ApiStatus />
    </QueryClientProvider>,
  );
  return client;
}

describe("ApiStatus", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows the status from the relative health path", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { status: "UP" } })),
    );

    // when
    renderStatus();

    // then
    expect(await screen.findByText("API 상태: UP")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/health", undefined);
  });

  it("shows an alert when the API cannot be reached", async () => {
    // given
    fetchMock.mockRejectedValue(new TypeError("failed to fetch"));

    // when
    renderStatus();

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "API에 연결할 수 없습니다.",
    );
  });

  it("shows an alert when a refetch fails after a good status", async () => {
    // given
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: { status: "UP" } })),
    );
    const client = renderStatus();
    await screen.findByText("API 상태: UP");
    fetchMock.mockRejectedValueOnce(new TypeError("failed to fetch"));

    // when
    await act(() => client.refetchQueries({ queryKey: ["health"] }));

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "API에 연결할 수 없습니다.",
    );
    expect(screen.queryByText("API 상태: UP")).not.toBeInTheDocument();
  });
});
