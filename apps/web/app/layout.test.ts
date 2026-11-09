import { describe, expect, it, vi } from "vitest";

vi.mock("next/font/google", () => ({
  IBM_Plex_Sans_KR: () => ({ variable: "font-plex" }),
}));

describe("layout metadata", () => {
  it("sets light and dark theme colors", async () => {
    // when
    const { viewport } = await import("@/app/layout");

    // then
    expect(viewport.themeColor).toEqual([
      { media: "(prefers-color-scheme: light)", color: "#c42f4f" },
      { media: "(prefers-color-scheme: dark)", color: "#10151f" },
    ]);
  });

  it("lets iOS open the app full screen with a short title", async () => {
    // when
    const { metadata } = await import("@/app/layout");

    // then
    expect(metadata.appleWebApp).toEqual({
      capable: true,
      title: "로또 추첨",
      statusBarStyle: "default",
    });
  });

  it("exports a 180px apple icon", async () => {
    // when
    const { size, contentType } = await import("@/app/apple-icon");

    // then
    expect(size).toEqual({ width: 180, height: 180 });
    expect(contentType).toBe("image/png");
  });
});
