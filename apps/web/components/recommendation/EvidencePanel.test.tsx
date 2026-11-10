import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { EvidencePanel } from "@/components/recommendation/EvidencePanel";

describe("EvidencePanel", () => {
  it("renders evidence and a stats link when href is given", () => {
    // when
    render(
      <EvidencePanel
        id="e1"
        open
        evidence={["번호 7: 최근 50회 중 4회 출현"]}
        statsHref="/numbers?date=2026-11-10&metric=RECENT&highlight=7"
      />,
    );

    // then
    expect(
      screen.getByText("번호 7: 최근 50회 중 4회 출현"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "통계에서 보기" })).toHaveAttribute(
      "href",
      "/numbers?date=2026-11-10&metric=RECENT&highlight=7",
    );
  });

  it("renders no link when href is null", () => {
    // when
    render(<EvidencePanel id="e2" open evidence={["균형"]} statsHref={null} />);

    // then
    expect(
      screen.queryByRole("link", { name: "통계에서 보기" }),
    ).not.toBeInTheDocument();
  });

  it("stays hidden while closed", () => {
    // when
    render(
      <EvidencePanel
        id="e3"
        open={false}
        evidence={["근거"]}
        statsHref={null}
      />,
    );

    // then
    expect(screen.queryByText("근거")).not.toBeVisible();
  });
});
