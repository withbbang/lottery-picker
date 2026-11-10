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

  it("uses the short app name as the tab title", async () => {
    // when
    const { metadata } = await import("@/app/layout");

    // then
    expect(metadata.title).toBe("로또 추첨");
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
});
