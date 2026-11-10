import { afterEach, describe, expect, it, vi } from "vitest";

import { copyText, shareOrCopy } from "@/lib/share";

function stubNavigator(nav: {
  clipboard?: { writeText: (text: string) => Promise<void> };
  share?: (data: ShareData) => Promise<void>;
}) {
  vi.stubGlobal("navigator", nav);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("copyText", () => {
  it("copies text to the clipboard", async () => {
    // given
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ clipboard: { writeText } });

    // when
    const result = await copyText("3 7");

    // then
    expect(writeText).toHaveBeenCalledWith("3 7");
    expect(result).toBe("copied");
  });

  it("reports failure when the clipboard is unavailable", async () => {
    // given
    stubNavigator({});

    // when
    const result = await copyText("3 7");

    // then
    expect(result).toBe("failed");
  });

  it("reports failure when the clipboard write is rejected", async () => {
    // given
    const writeText = vi.fn().mockRejectedValue(new Error("denied"));
    stubNavigator({ clipboard: { writeText } });

    // when
    const result = await copyText("3 7");

    // then
    expect(result).toBe("failed");
  });
});

describe("shareOrCopy", () => {
  it("uses the share sheet when available", async () => {
    // given
    const share = vi.fn().mockResolvedValue(undefined);
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ share, clipboard: { writeText } });

    // when
    const result = await shareOrCopy("글");

    // then
    expect(share).toHaveBeenCalledWith({ text: "글" });
    expect(writeText).not.toHaveBeenCalled();
    expect(result).toBe("shared");
  });

  it("treats a dismissed share sheet as cancelled", async () => {
    // given
    const share = vi.fn().mockRejectedValue(new DOMException("", "AbortError"));
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ share, clipboard: { writeText } });

    // when
    const result = await shareOrCopy("글");

    // then
    expect(writeText).not.toHaveBeenCalled();
    expect(result).toBe("cancelled");
  });

  it("falls back to the clipboard when sharing fails", async () => {
    // given
    const share = vi
      .fn()
      .mockRejectedValue(new DOMException("", "NotAllowedError"));
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ share, clipboard: { writeText } });

    // when
    const result = await shareOrCopy("글");

    // then
    expect(writeText).toHaveBeenCalledWith("글");
    expect(result).toBe("copied");
  });

  it("copies when the share sheet is not supported", async () => {
    // given
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ clipboard: { writeText } });

    // when
    const result = await shareOrCopy("글");

    // then
    expect(result).toBe("copied");
  });
});
