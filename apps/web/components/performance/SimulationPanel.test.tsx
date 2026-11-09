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
  "실제 추천에서 거르는 고정·제외 번호, 많은 사람이 고르는 조합, 다른 줄과 겹치는 번호는 적용하지 않고 전략이 번호를 고르는 방식만 시험했습니다.";

function row(
  overrides: Partial<StrategySimulationResponse>,
): StrategySimulationResponse {
  return {
    strategy: "RECENT_HOT",
    lineCount: 229800,
    averageMatches: 0.8025,
    rankCounts: {
      FIRST: 0,
      SECOND: 1,
      THIRD: 30,
      FOURTH: 300,
      FIFTH: 5157,
      NONE: 224312,
    },
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
      row({
        strategy: "CONTROL",
        atLeastFifthRate: 0.02373,
        ciLow: 0.02311,
        ciHigh: 0.02435,
      }),
    ]);

    // then
    expect(await screen.findByText("무작위 대조군")).toBeInTheDocument();
    expect(screen.getByText("최근 빈도")).toBeInTheDocument();
    expect(
      screen.getByText(/5등 이상일 확률은 2\.38%입니다/),
    ).toBeInTheDocument();
    expect(screen.getByText("2.37%")).toBeInTheDocument();
    expect(screen.getByText("101~1249회의 회차마다")).toBeInTheDocument();
  });

  it("shows 차이 없음", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    const recentHot = (await screen.findByText("최근 빈도")).closest("tbody");
    expect(recentHot).not.toBeNull();
    expect(
      within(recentHot as HTMLElement).getByText("차이 없음"),
    ).toBeInTheDocument();
    expect(screen.getByText(/여러 전략을 동시에 비교/)).toBeInTheDocument();
    expect(screen.queryByText(/효과/)).not.toBeInTheDocument();
  });

  it("shows 차이 관측 only when the interval does not overlap the control", async () => {
    // when
    renderPanel([
      row({ differsFromControl: true }),
      row({ strategy: "PAIR" }),
      row({ strategy: "CONTROL" }),
    ]);

    // then
    expect(await screen.findByText("차이 관측")).toBeInTheDocument();
    expect(screen.getAllByText("차이 없음")).toHaveLength(1);
  });

  it("shows the rank distribution for each strategy", async () => {
    // when
    renderPanel([
      row({}),
      row({
        strategy: "CONTROL",
        rankCounts: {
          FIRST: 1,
          SECOND: 2,
          THIRD: 40,
          FOURTH: 290,
          FIFTH: 5120,
          NONE: 224347,
        },
      }),
    ]);

    // then
    const control = (await screen.findByText("무작위 대조군")).closest("tbody");
    expect(control).not.toBeNull();
    const ranks = within(control as HTMLElement).getByLabelText(
      "등수별 당첨 줄 수",
    );
    expect(within(ranks).getByText("5,120")).toBeInTheDocument();
    expect(within(ranks).getByText("290")).toBeInTheDocument();
  });

  it("states that only the number selection rules are evaluated", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    expect(await screen.findByText(SCOPE_NOTE)).toBeInTheDocument();
  });

  it("states that the interval is computed per round", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    expect(
      await screen.findByText(/회차 단위로 계산한 신뢰구간/),
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

  it("shows a line count shared by every strategy once above the table", async () => {
    // when
    renderPanel([row({}), row({ strategy: "CONTROL" })]);

    // then
    expect(await screen.findByText("전략마다 229,800줄")).toBeInTheDocument();
    expect(screen.queryByText("229,800줄")).not.toBeInTheDocument();
  });

  it("keeps each interval in its row when they differ", async () => {
    // when
    renderPanel([
      row({ ciLow: 0.0228, ciHigh: 0.0245 }),
      row({ strategy: "CONTROL", ciLow: 0.0231, ciHigh: 0.0249 }),
    ]);

    // then
    expect(await screen.findByText("2.28~2.45%")).toBeInTheDocument();
    expect(screen.getByText("2.31~2.49%")).toBeInTheDocument();
    expect(screen.queryByText("5등↑ 95% 구간")).not.toBeInTheDocument();
  });

  it("lists the combined strategy above the control group", async () => {
    // when: API는 enum 순서라 대조군 뒤에 근거 조합을 준다
    renderPanel([
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
