import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiFetch } from "@/lib/api";

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

describe("apiFetch", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("calls the relative path and returns unwrapped data on success", async () => {
    // given
    fetchMock.mockResolvedValue(
      jsonResponse({ success: true, data: { status: "UP" } }),
    );

    // when
    const data = await apiFetch<{ status: string }>("/api/v1/health");

    // then
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/health",
      undefined,
    );
    expect(data).toEqual({ status: "UP" });
  });

  it("throws ApiError with the server code and message on failure", async () => {
    // given
    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          success: false,
          error: { code: "COMMON_NOT_FOUND", message: "없습니다." },
        },
        404,
      ),
    );

    // when
    const result = apiFetch("/api/v1/none");

    // then
    await expect(result).rejects.toEqual(
      new ApiError("COMMON_NOT_FOUND", "없습니다."),
    );
    await expect(result).rejects.toMatchObject({ code: "COMMON_NOT_FOUND" });
  });

  it("returns undefined for a 204 response", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    // when
    const data = await apiFetch<void>("/api/v1/draws/1");

    // then
    expect(data).toBeUndefined();
  });
});
