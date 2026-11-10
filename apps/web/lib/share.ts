export type ShareResult = "shared" | "copied" | "cancelled" | "failed";

/** 클립보드에 쓴다. API가 없거나(http 접속·구형 브라우저) 거부되면 예외 없이 "failed". */
export async function copyText(text: string): Promise<"copied" | "failed"> {
  try {
    if (!navigator.clipboard) {
      return "failed";
    }
    await navigator.clipboard.writeText(text);
    return "copied";
  } catch {
    return "failed";
  }
}

/** 기기 공유 시트가 있으면 쓰고, 없거나 실패하면 클립보드로. 사용자가 공유 시트를 닫으면 "cancelled". */
export async function shareOrCopy(text: string): Promise<ShareResult> {
  if (typeof navigator.share === "function") {
    try {
      await navigator.share({ text });
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return "cancelled";
      }
    }
  }
  return copyText(text);
}
