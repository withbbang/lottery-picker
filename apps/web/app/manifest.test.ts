import { describe, expect, it } from "vitest";

import manifest from "@/app/manifest";

describe("manifest", () => {
  it("opens standalone at the home page", () => {
    // when
    const m = manifest();

    // then
    expect(m.display).toBe("standalone");
    expect(m.start_url).toBe("/");
    expect(m.name).toBe("로또 번호 추첨");
    expect(m.short_name).toBe("로또 추첨");
  });

  it("uses the slip red theme and paper background", () => {
    // when
    const m = manifest();

    // then
    expect(m.theme_color).toBe("#c42f4f");
    expect(m.background_color).toBe("#f4f6f9");
  });

  it("lists 192, 512 and maskable png icons", () => {
    // when
    const m = manifest();

    // then
    expect(m.icons).toEqual([
      { src: "/icons/192", sizes: "192x192", type: "image/png" },
      { src: "/icons/512", sizes: "512x512", type: "image/png" },
      {
        src: "/icons/maskable",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ]);
  });
});
