import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SimulationPanel } from "@/components/performance/SimulationPanel";
import type {
  SimulationResponse,
  StrategySimulationResponse,
} from "@/types/api/simulation";

const fetchMock = vi.fn();

const SCOPE_NOTE =
  "전략의 번호 추출 규칙만 평가합니다. 고정·제외 번호와 인기 조합·중복 제약은 적용하지 않습니다.";

function row(
  overrides: Partial<StrategySimulationResponse>,
): StrategySimulationResponse {
  return {
    strategy: "RECENT_HOT",
    lineCount: 229800,
    averageMatches: 0.8025,
    rankCounts: { FIRST: 0, SECOND: 1, THIRD: 30, FOURTH: 300, FIFTH: 5157, NONE: 224312 },
    atLeastFifthRate: 0.02388,
    ciLow: 0.02326,
    ciHigh: 0.02451,
    differsFromControl: false,
    ...overrides,
  };
}

function renderPanel(strategies: StrategySimulationResponse[] | null) {
  const data: SimulationResponse | null =
    strategies === null
      ? null
      : {
          fromRound: 101,
          toRound: 1249,
          repetitions: 200,
          computedAt: "2026-11-09T10:00:00+09:00",
          theoryAtLeastFifthRate: 194130 / 8145060,
          scopeNote: SCOPE_NOTE,
          strategies,
        };
  fetchMock.mockResolvedValue(
    data === null
      ? new Response(null, { status: 204 })
      : new Response(JSON.stringify({ success: true, data })),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <SimulationPanel />
    </QueryClientProvider>,
  );
}

describe("SimulationPanel", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows control and theory", async () => {
    // when
    renderPanel([
      row({}),
      row({ strategy: "CONTROL", atLeastFifthRate: 0.02373, ciLow: 0.02311, ciHigh: 0.02435 }),
    ]);

    // then
    expect(await screen.findByText("대조군")).toBeInTheDocument();
    expect(screen.getByText("최근 빈도")).toBeInTheDocument();
    expect(screen.getByText(/이론값 2\.38%/)).toBeInTheDocument();
    expect(screen.getByText("2.37%")).toBeInTheDocument();
    expect(screen.getByText("101~1249회, 회차마다 200줄")).toBeInTheDocument();
  });

  it("shows 유의한 차이 없음", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    const recentHot = (await screen.findByText("최근 빈도")).closest("tr");
    expect(recentHot).not.toBeNull();
    expect(within(recentHot as HTMLElement).getByText("유의한 차이 없음")).toBeInTheDocument();
    expect(screen.getByText(/여러 전략을 동시에 비교/)).toBeInTheDocument();
    expect(screen.queryByText(/효과/)).not.toBeInTheDocument();
  });

  it("shows 무작위와 차이 관측 only when the interval does not overlap the control", async () => {
    // when
    renderPanel([
      row({ differsFromControl: true }),
      row({ strategy: "PAIR" }),
      row({ strategy: "CONTROL" }),
    ]);

    // then
    expect(await screen.findByText("무작위와 차이 관측")).toBeInTheDocument();
    expect(screen.getAllByText("유의한 차이 없음")).toHaveLength(1);
  });

  it("shows the rank distribution for each strategy", async () => {
    // when
    renderPanel([
      row({}),
      row({
        strategy: "CONTROL",
        rankCounts: { FIRST: 1, SECOND: 2, THIRD: 40, FOURTH: 290, FIFTH: 5120, NONE: 224347 },
      }),
    ]);

    // then
    const control = (await screen.findByText("대조군")).closest("tr");
    expect(control).not.toBeNull();
    expect(
      within(control as HTMLElement).getByText(
        "1등 1 · 2등 2 · 3등 40 · 4등 290 · 5등 5,120",
      ),
    ).toBeInTheDocument();
  });

  it("states that only the number selection rules are evaluated", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    expect(await screen.findByText(SCOPE_NOTE)).toBeInTheDocument();
    expect(screen.getByText(/실제 당첨 확률과 무관합니다\./)).toBeInTheDocument();
  });

  it("states that the interval is computed per round", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    expect(
      await screen.findByText(/95% 구간은 회차 단위로 계산합니다/),
    ).toBeInTheDocument();
  });

  it("says the result is not ready when there is none", async () => {
    // when
    renderPanel(null);

    // then
    expect(
      await screen.findByText(/아직 시뮬레이션 결과가 없습니다/),
    ).toBeInTheDocument();
  });
});
