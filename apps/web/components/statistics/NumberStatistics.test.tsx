import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { NumberStatistics } from "@/components/statistics/NumberStatistics";
import type {
  NumberMetric,
  NumberStatisticsResponse,
  RankedNumberResponse,
} from "@/types/api/statistics";

const fetchMock = vi.fn();

/** 서버가 정한 순서 그대로. 화면은 다시 정렬하지 않아야 한다. */
function ranked(
  rows: [number, number, number, boolean][],
): RankedNumberResponse[] {
  return rows.map(([number, value, rank, tied]) => ({
    number,
    value,
    rank,
    tied,
  }));
}

const stats: NumberStatisticsResponse = {
  date: "2026-11-10",
  historyFromRound: 1,
  historyToRound: 1199,
  totalRounds: 1199,
  recentWindow: 50,
  metrics: {
    RECENT: ranked([
      [27, 9, 1, false],
      [7, 8, 2, false],
      [12, 6, 3, true],
      [40, 6, 3, true],
    ]),
    COLD: ranked([
      [33, 21, 1, false],
      [12, 18, 2, false],
    ]),
    ALL_TIME: ranked([
      [43, 190, 1, false],
      [7, 171, 2, false],
    ]),
  },
};

function respond(data: NumberStatisticsResponse) {
  return new Response(JSON.stringify({ success: true, data }));
}

function renderStats(
  props: Partial<{
    date: string | null;
    metric: NumberMetric;
    highlight: number[];
  }> = {},
) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <NumberStatistics
        date={props.date === undefined ? "2026-11-10" : props.date}
        metric={props.metric ?? "RECENT"}
        highlight={props.highlight ?? []}
      />
    </QueryClientProvider>,
  );
}

/** 순위 목록의 행에 적힌 번호를 위에서부터. */
async function listedNumbers() {
  const list = await screen.findByRole("list", { name: "번호 순위" });
  return within(list)
    .getAllByRole("listitem")
    .map((row) => row.getAttribute("data-number"));
}

describe("NumberStatistics", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("asks for the statistics of the given date", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats({ date: "2026-11-10" });

    // then
    await screen.findByRole("list", { name: "번호 순위" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/number-statistics?date=2026-11-10",
      undefined,
    );
  });

  it("asks for the latest statistics when no date is given", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats({ date: null });

    // then
    await screen.findByRole("list", { name: "번호 순위" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/number-statistics",
      undefined,
    );
  });

  it("shows rows in the order the server sent", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats();

    // then
    expect(await listedNumbers()).toEqual(["27", "7", "12", "40"]);
  });

  it("opens the tab given by the metric parameter", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats({ metric: "COLD" });

    // then
    expect(
      await screen.findByRole("tab", { name: "미출현 회차" }),
    ).toHaveAttribute("aria-selected", "true");
    expect(await listedNumbers()).toEqual(["33", "12"]);
  });

  it("switches metric when another tab is clicked", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));
    renderStats();
    await screen.findByRole("list", { name: "번호 순위" });

    // when
    await userEvent.click(screen.getByRole("tab", { name: "전체 출현" }));

    // then
    expect(screen.getByRole("tab", { name: "전체 출현" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(await listedNumbers()).toEqual(["43", "7"]);
  });

  it("labels the recent tab with the window from the server", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats();

    // then
    expect(
      await screen.findByRole("tab", { name: "최근 50회 출현" }),
    ).toBeInTheDocument();
  });

  it("shows the basis date and round range", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats();

    // then
    expect(
      await screen.findByText("11월 10일 추천 기준 · 1~1199회"),
    ).toBeInTheDocument();
  });

  it("marks highlighted numbers with their rank", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats({ highlight: [7, 12] });

    // then: 혼자인 값은 "2위", 같은 값이 또 있으면 "공동 3위"
    const list = await screen.findByRole("list", { name: "번호 순위" });
    const rows = within(list).getAllByRole("listitem");
    expect(within(rows[1]).getByText("2위")).toBeInTheDocument();
    expect(within(rows[2]).getByText("공동 3위")).toBeInTheDocument();
    expect(within(rows[0]).queryByText(/위$/)).not.toBeInTheDocument();
  });

  it("summarizes where the highlighted numbers stand", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats({ highlight: [7] });

    // then
    const summary = await screen.findByRole("region", {
      name: "추천 근거 번호",
    });
    expect(within(summary).getByText("45개 중 2위")).toBeInTheDocument();
    expect(within(summary).getByText("8회")).toBeInTheDocument();
  });

  it("writes cold values as rounds since last seen", async () => {
    // given
    fetchMock.mockResolvedValue(respond(stats));

    // when
    renderStats({ metric: "COLD" });

    // then
    expect(await screen.findByText("21회째")).toBeInTheDocument();
  });

  it("explains when the recommendation for the date does not exist", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          error: {
            code: "COMMON_NOT_FOUND",
            message: "요청한 리소스를 찾을 수 없습니다.",
          },
        }),
        { status: 404 },
      ),
    );

    // when
    renderStats();

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이 날짜의 추천이 없어 통계를 보여 줄 수 없습니다.",
    );
  });

  it("explains when there is no recommendation yet", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    // when
    renderStats({ date: null });

    // then
    expect(
      await screen.findByText(
        "아직 추천이 없습니다. 첫 추천이 나오면 그 근거를 여기서 확인할 수 있습니다.",
      ),
    ).toBeInTheDocument();
  });
});
