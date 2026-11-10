import { describe, expect, it } from "vitest";

import { monthDay } from "@/lib/format-date";

describe("monthDay", () => {
  it("writes an ISO date as month and day without leading zeros", () => {
    expect(monthDay("2026-11-05")).toBe("11월 5일");
    expect(monthDay("2026-01-10")).toBe("1월 10일");
  });
});
