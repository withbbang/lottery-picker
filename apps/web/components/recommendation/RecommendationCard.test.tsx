import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RecommendationCard } from "@/components/recommendation/RecommendationCard";
import { recommendationShareText } from "@/lib/share-text";
import type { RecommendationSetResponse } from "@/types/api/recommendation";

const fetchMock = vi.fn();

const DISCLAIMER = "실제 당첨 확률과 무관합니다.";

const set: RecommendationSetResponse = {
  date: "2026-11-06",
  round: 1249,
  historyFromRound: 1,
  historyToRound: 1248,
  disclaimer: DISCLAIMER,
  notificationStatus: "SENT",
  carriedOver: false,
  roundClosed: false,
  todayPending: false,
  lines: [
    {
      strategy: "RECENT_HOT",
      numbers: [1, 8, 15, 22, 33, 45],
      evidence: ["번호 8: 최근 50회 중 11회 출현", "중복 제한 완화"],
      relaxation: "OVERLAP",
      evidenceNumbers: [8],
      statsMetric: "RECENT",
    },
    {
      strategy: "LONG_COLD",
      numbers: [2, 9, 16, 23, 34, 44],
      evidence: ["번호 9: 23회째 미출현"],
      relaxation: "NONE",
      evidenceNumbers: [9],
      statsMetric: "COLD",
    },
    {
      strategy: "ALL_TIME",
      numbers: [3, 10, 17, 24, 35, 43],
      evidence: ["번호 3: 전체 1248회 중 190회 출현"],
      relaxation: "NONE",
      evidenceNumbers: [3],
      statsMetric: "ALL_TIME",
    },
    {
      strategy: "PAIR",
      numbers: [4, 11, 18, 25, 36, 42],
      evidence: ["12·34 쌍이 9회 함께 출현"],
      relaxation: "NONE",
      evidenceNumbers: [],
      statsMetric: null,
    },
    {
      strategy: "BALANCED",
      numbers: [5, 12, 19, 26, 37, 41],
      evidence: ["홀3:짝3, 합계 137, 과거 분포 중앙 구간"],
      relaxation: "NONE",
      evidenceNumbers: [],
      statsMetric: null,
    },
  ],
};

function renderCard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <RecommendationCard />
    </QueryClientProvider>,
  );
}

describe("RecommendationCard", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("shows five lines with strategy and evidence", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();

    // then
    const lines = await screen.findAllByRole("article");
    expect(lines).toHaveLength(5);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/recommendations",
      undefined,
    );

    const first = within(lines[0]);
    expect(first.getByText("최근 빈도")).toBeInTheDocument();
    expect(
      first.getByText("번호 8: 최근 50회 중 11회 출현"),
    ).toBeInTheDocument();
    expect(first.getByText("중복 제한 완화")).toBeInTheDocument();
    expect(first.getByText("45")).toBeInTheDocument();
    expect(within(lines[3]).getByText("번호 쌍")).toBeInTheDocument();
    expect(screen.getByText("1249회")).toBeInTheDocument();
    expect(screen.getByText("2026-11-06")).toBeInTheDocument();
    expect(screen.getByText("분석 범위: 1~1248회")).toBeInTheDocument();
  });

  it("explains that today's set comes at 5 pm when showing an earlier set", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            ...set,
            date: "2026-11-05",
            carriedOver: true,
            todayPending: true,
          },
        }),
      ),
    );

    // when
    renderCard();

    // then
    expect(
      await screen.findByText(
        "오늘 추천은 오후 5시에 나옵니다. 아래는 11월 5일 추천입니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(5);
  });

  it("notes the numbers can still be bought when the round is closed", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            ...set,
            date: "2026-10-31",
            carriedOver: true,
            roundClosed: true,
            todayPending: true,
          },
        }),
      ),
    );

    // when
    renderCard();

    // then
    expect(
      await screen.findByText(
        "1249회는 추첨이 끝났지만, 번호는 이번 회차에도 살 수 있습니다.",
      ),
    ).toBeInTheDocument();
  });

  it("says today's set could not be made when 5 pm has passed", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { ...set, date: "2026-11-05", carriedOver: true },
        }),
      ),
    );

    // when
    renderCard();

    // then
    expect(
      await screen.findByText(
        "오늘 추천을 만들지 못했습니다. 아래는 11월 5일 추천입니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/오후 5시에 나옵니다/)).toBeNull();
  });

  it("shows no carry-over notice for today's set", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();

    // then
    await screen.findAllByRole("article");
    expect(screen.queryByText(/오후 5시에 나옵니다/)).toBeNull();
  });

  it("shows combined lines on a separate slip", async () => {
    // given
    const combined = [
      {
        strategy: "COMBINED" as const,
        numbers: [7, 12, 24, 37, 39, 40],
        evidence: [
          "7(최근 빈도) · 12(장기 미출현) · 24·39(번호 쌍) · 37(전체 빈도) · 40(최근 빈도·전체 빈도)",
        ],
        relaxation: "NONE" as const,
        evidenceNumbers: [],
        statsMetric: null,
      },
      {
        strategy: "COMBINED" as const,
        numbers: [7, 12, 24, 29, 37, 39],
        evidence: [
          "7(최근 빈도) · 12(장기 미출현) · 24·39(번호 쌍) · 29(장기 미출현) · 37(전체 빈도)",
        ],
        relaxation: "NONE" as const,
        evidenceNumbers: [],
        statsMetric: null,
      },
    ];
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { ...set, lines: [...set.lines, ...combined] },
        }),
      ),
    );

    // when
    renderCard();

    // then
    const slip = await screen.findByRole("region", { name: "근거 조합" });
    expect(within(slip).getAllByRole("article")).toHaveLength(2);
    // 줄 제목은 번호를 붙여 구분한다
    expect(
      within(slip)
        .getAllByRole("heading", { level: 2 })
        .map((h) => h.textContent),
    ).toEqual(["근거 조합 1", "근거 조합 2"]);
    expect(within(slip).getByText("40")).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(7);
  });

  it("hides the combined slip when there are no combined lines", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();

    // then
    await screen.findAllByRole("article");
    expect(screen.queryByRole("region", { name: "근거 조합" })).toBeNull();
  });

  it("shows each strategy's description next to its name", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();

    // then
    const lines = await screen.findAllByRole("article");
    expect(
      within(lines[0]).getByRole("heading", {
        name: "최근 빈도 최근 50회에 자주 나온 번호 위주",
      }),
    ).toBeInTheDocument();
  });

  it("hides the evidence until its button is pressed", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );
    renderCard();
    const [first] = await screen.findAllByRole("article");
    const toggle = within(first).getByRole("button", { name: "근거" });
    expect(within(first).getByText("중복 제한 완화")).not.toBeVisible();

    // when
    await userEvent.click(toggle);

    // then
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(within(first).getByText("중복 제한 완화")).toBeVisible();
  });

  it("shows identical evidence lines without a key warning", async () => {
    // given: 저장된 근거는 받은 그대로 표시하므로 같은 문구가 반복될 수 있다
    const consoleError = vi.spyOn(console, "error");
    const repeated = {
      ...set,
      lines: [
        { ...set.lines[0], evidence: ["23회째 미출현", "23회째 미출현"] },
        ...set.lines.slice(1),
      ],
    };
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: repeated })),
    );

    // when
    renderCard();

    // then
    const lines = await screen.findAllByRole("article");
    expect(within(lines[0]).getAllByText("23회째 미출현")).toHaveLength(2);
    expect(consoleError).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it("shows the disclaimer", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();

    // then
    expect(await screen.findByText(DISCLAIMER)).toBeInTheDocument();
  });

  it("shows a warning when the telegram send failed", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: { ...set, notificationStatus: "FAILED" },
        }),
      ),
    );

    // when
    renderCard();

    // then
    expect(
      await screen.findByText(
        "텔레그램 알림을 보내지 못했습니다. 번호는 여기서 확인하세요.",
      ),
    ).toBeInTheDocument();
  });

  it("hides the warning unless the telegram send failed", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();

    // then
    await screen.findAllByRole("article");
    expect(
      screen.queryByText(
        "텔레그램 알림을 보내지 못했습니다. 번호는 여기서 확인하세요.",
      ),
    ).not.toBeInTheDocument();
  });

  it("links recent-hot evidence to the number statistics of that day", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );
    renderCard();
    const lines = await screen.findAllByRole("article");

    // when
    await userEvent.click(
      within(lines[0]).getByRole("button", { name: /근거/ }),
    );

    // then
    expect(
      within(lines[0]).getByRole("link", { name: "통계에서 보기" }),
    ).toHaveAttribute(
      "href",
      "/numbers?date=2026-11-06&metric=RECENT&highlight=8",
    );
  });

  it("shows no stats link for pair and balanced lines", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: set })),
    );

    // when
    renderCard();
    const lines = await screen.findAllByRole("article");

    // then
    for (const line of [lines[3], lines[4]]) {
      expect(
        within(line).queryByRole("link", {
          name: "통계에서 보기",
          hidden: true,
        }),
      ).not.toBeInTheDocument();
    }
  });

  it("shows an empty message on 204", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    // when
    renderCard();

    // then
    expect(
      await screen.findByText(
        "오늘 추천이 아직 없습니다. 매일 오후 5시에 만들어집니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an error message when loading fails", async () => {
    // given
    fetchMock.mockRejectedValue(new TypeError("network"));

    // when
    renderCard();

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "추천을 불러오지 못했습니다.",
    );
  });

  describe("copy and share", () => {
    const combined: RecommendationSetResponse["lines"] = [
      {
        strategy: "COMBINED" as const,
        numbers: [7, 12, 24, 37, 39, 40],
        evidence: [],
        relaxation: "NONE" as const,
        evidenceNumbers: [],
        statsMetric: null,
      },
      {
        strategy: "COMBINED" as const,
        numbers: [7, 12, 24, 29, 37, 39],
        evidence: [],
        relaxation: "NONE" as const,
        evidenceNumbers: [],
        statsMetric: null,
      },
    ];
    const fullSet = { ...set, lines: [...set.lines, ...combined] };

    function respondWith(data: RecommendationSetResponse) {
      fetchMock.mockResolvedValue(
        new Response(JSON.stringify({ success: true, data })),
      );
    }

    it("copies a line from its copy button", async () => {
      // given
      respondWith(set);
      const user = userEvent.setup();
      const writeText = vi.fn().mockResolvedValue(undefined);
      vi.stubGlobal("navigator", { clipboard: { writeText } });
      renderCard();
      const [first] = await screen.findAllByRole("article");

      // when
      await user.click(
        within(first).getByRole("button", { name: "최근 빈도 번호 복사" }),
      );

      // then
      expect(writeText).toHaveBeenCalledWith("1 8 15 22 33 45");
    });

    it("names combined line copy buttons with their number", async () => {
      // given
      respondWith(fullSet);

      // when
      renderCard();

      // then
      expect(
        await screen.findByRole("button", { name: "근거 조합 1 번호 복사" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "근거 조합 2 번호 복사" }),
      ).toBeInTheDocument();
    });

    it("shares the whole recommendation", async () => {
      // given
      respondWith(fullSet);
      const user = userEvent.setup();
      const share = vi.fn().mockResolvedValue(undefined);
      vi.stubGlobal("navigator", { share });
      renderCard();

      // when
      await user.click(
        await screen.findByRole("button", { name: "번호 공유" }),
      );

      // then
      expect(share).toHaveBeenCalledWith({
        text: recommendationShareText(fullSet),
      });
    });
  });
});
