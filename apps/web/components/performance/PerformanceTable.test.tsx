import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { PerformanceTable } from "@/components/performance/PerformanceTable";
import type {
  StrategyPerformanceResponse,
  StrategyPerformancesResponse,
} from "@/types/api/performance";

const fetchMock = vi.fn();

function row(
  overrides: Partial<StrategyPerformanceResponse>,
): StrategyPerformanceResponse {
  return {
    strategy: "RECENT_HOT",
    lineCount: 1000,
    averageMatches: 0.75,
    rankCounts: {
      FIRST: 0,
      SECOND: 0,
      THIRD: 1,
      FOURTH: 20,
      FIFTH: 30,
      NONE: 949,
    },
    atLeastFifthRate: 0.051,
    noSettingsLineCount: 900,
    noSettingsAtLeastFifthRate: 0.0256,
    sampleSufficient: true,
    ...overrides,
  };
}

function renderTable(strategies: StrategyPerformanceResponse[]) {
  const data: StrategyPerformancesResponse = {
    theoryAtLeastFifthRate: 0.023834,
    strategies,
  };
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify({ success: true, data })),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <PerformanceTable />
    </QueryClientProvider>,
  );
}

describe("PerformanceTable", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows the control group", async () => {
    // when
    renderTable([row({}), row({ strategy: "CONTROL", lineCount: 1200 })]);

    // then
    expect(await screen.findByText("무작위 대조군")).toBeInTheDocument();
    expect(screen.getByText("최근 빈도")).toBeInTheDocument();
    expect(screen.getByText("1,200줄")).toBeInTheDocument();
  });

  it("shows 아직 차이를 판단하기 이르다 when the sample is small", async () => {
    // when
    renderTable([row({ lineCount: 35, sampleSufficient: false })]);

    // then
    expect(
      await screen.findByText(/아직 차이를 판단하기 이릅니다/),
    ).toBeInTheDocument();
  });

  it("does not show the small-sample notice when every sample is sufficient", async () => {
    // when
    renderTable([row({})]);

    // then
    await screen.findByText("최근 빈도");
    expect(
      screen.queryByText(/아직 차이를 판단하기 이릅니다/),
    ).not.toBeInTheDocument();
  });

  it("shows theory only with its line count", async () => {
    // when
    renderTable([row({})]);

    // then
    expect(
      await screen.findByText(/5등 이상일 확률은 2\.38%라서/),
    ).toBeInTheDocument();
    expect(screen.getByText("2.56%")).toBeInTheDocument();
    expect(screen.getByText("전략마다 900줄")).toBeInTheDocument();
  });

  it("shows dashes instead of rates when there are no lines", async () => {
    // when
    renderTable([
      row({
        lineCount: 0,
        averageMatches: 0,
        atLeastFifthRate: 0,
        noSettingsLineCount: 0,
        noSettingsAtLeastFifthRate: 0,
        sampleSufficient: false,
      }),
    ]);

    // then
    expect(await screen.findByText("전략마다 0줄")).toBeInTheDocument();
    expect(screen.getAllByText("-").length).toBeGreaterThan(0);
  });

  it("shows each no-settings line count in its row when they differ", async () => {
    // when
    renderTable([
      row({}),
      row({ strategy: "CONTROL", noSettingsLineCount: 1200 }),
    ]);

    // then
    expect(await screen.findByText("900줄 기준")).toBeInTheDocument();
    expect(screen.getByText("1,200줄 기준")).toBeInTheDocument();
    expect(screen.queryByText(/^전략마다/)).not.toBeInTheDocument();
  });

  it("lists the combined strategy above the control group", async () => {
    // when: API는 enum 순서라 대조군 뒤에 근거 조합을 준다
    renderTable([
      row({}),
      row({ strategy: "CONTROL" }),
      row({ strategy: "COMBINED" }),
    ]);

    // then
    await screen.findByText("근거 조합");
    const names = screen
      .getAllByRole("row")
      .map((r) => r.textContent ?? "")
      .filter((t) => /최근 빈도|근거 조합|무작위 대조군/.test(t));
    expect(names.findIndex((t) => t.includes("근거 조합"))).toBeLessThan(
      names.findIndex((t) => t.includes("무작위 대조군")),
    );
    expect(names[names.length - 1]).toContain("무작위 대조군");
  });
});
