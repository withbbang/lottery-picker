import { describe, expect, it } from "vitest";

import {
  numberStatsHref,
  parseNumberStatsParams,
} from "@/lib/number-stats-link";

describe("numberStatsHref", () => {
  it("builds a link with date, metric and highlighted numbers", () => {
    expect(numberStatsHref("2026-11-10", "RECENT", [7, 12])).toBe(
      "/numbers?date=2026-11-10&metric=RECENT&highlight=7%2C12",
    );
  });
});

describe("parseNumberStatsParams", () => {
  it("reads date, metric and highlight back from a built link", () => {
    const href = numberStatsHref("2026-11-10", "COLD", [30]);
    const params = new URLSearchParams(href.split("?")[1]);

    expect(parseNumberStatsParams(params)).toEqual({
      date: "2026-11-10",
      metric: "COLD",
      highlight: [30],
    });
  });

  it("falls back to RECENT for an unknown metric", () => {
    const params = new URLSearchParams("metric=PAIR");

    expect(parseNumberStatsParams(params).metric).toBe("RECENT");
  });

  it("keeps only whole numbers between 1 and 45 in highlight", () => {
    const params = new URLSearchParams("highlight=abc,99,7,0,12,3.5");

    expect(parseNumberStatsParams(params).highlight).toEqual([7, 12]);
  });

  it("returns null date when date is missing or malformed", () => {
    expect(parseNumberStatsParams(new URLSearchParams("")).date).toBeNull();
    expect(
      parseNumberStatsParams(new URLSearchParams("date=11/10")).date,
    ).toBeNull();
  });
});
