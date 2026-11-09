import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RecommendationCard } from "@/components/recommendation/RecommendationCard";
import type { RecommendationSetResponse } from "@/types/api/recommendation";

const fetchMock = vi.fn();

const DISCLAIMER = "번호 선택 방식 설명이며 당첨 확률과 무관합니다.";

const set: RecommendationSetResponse = {
  date: "2026-11-06",
  round: 1249,
  historyFromRound: 1,
  historyToRound: 1248,
  disclaimer: DISCLAIMER,
  notificationStatus: "SENT",
  lines: [
    {
      strategy: "RECENT_HOT",
      numbers: [1, 8, 15, 22, 33, 45],
      evidence: ["번호 8: 최근 50회 중 11회 출현", "중복 제한 완화"],
      relaxation: "OVERLAP",
    },
    {
      strategy: "LONG_COLD",
      numbers: [2, 9, 16, 23, 34, 44],
      evidence: ["번호 9: 23회째 미출현"],
      relaxation: "NONE",
    },
    {
      strategy: "ALL_TIME",
      numbers: [3, 10, 17, 24, 35, 43],
      evidence: ["번호 3: 전체 1248회 중 190회 출현"],
      relaxation: "NONE",
    },
    {
      strategy: "PAIR",
      numbers: [4, 11, 18, 25, 36, 42],
      evidence: ["12·34 쌍이 9회 함께 출현"],
      relaxation: "NONE",
    },
    {
      strategy: "BALANCED",
      numbers: [5, 12, 19, 26, 37, 41],
      evidence: ["홀3:짝3, 합계 137, 과거 분포 중앙 구간"],
      relaxation: "NONE",
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
    expect(screen.getByText("통계 입력: 1~1248회")).toBeInTheDocument();
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
    expect(await screen.findByText("텔레그램 발송 실패")).toBeInTheDocument();
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
    expect(screen.queryByText("텔레그램 발송 실패")).not.toBeInTheDocument();
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
});
