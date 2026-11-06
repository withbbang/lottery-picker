import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SettingsForm } from "@/components/recommendation/SettingsForm";
import type { RecommendationSettingsResponse } from "@/types/api/recommendation";

const fetchMock = vi.fn();

function ok(data: RecommendationSettingsResponse) {
  return new Response(JSON.stringify({ success: true, data }));
}

function failure(code: string, message: string, status: number) {
  return new Response(
    JSON.stringify({ success: false, error: { code, message } }),
    { status },
  );
}

function mockApi(
  current: RecommendationSettingsResponse,
  onPut: () => Response = () => ok(current),
) {
  fetchMock.mockImplementation(async (_path: string, init?: RequestInit) =>
    init?.method === "PUT" ? onPut() : ok(current),
  );
}

function putCalls() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT");
}

function getCalls() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method !== "PUT");
}

async function renderForm() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <SettingsForm />
    </QueryClientProvider>,
  );
  return {
    fixed: await screen.findByRole("textbox", { name: "고정 번호" }),
    excluded: screen.getByRole("textbox", { name: "제외 번호" }),
    save: screen.getByRole("button", { name: "저장" }),
  };
}

describe("SettingsForm", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("saves fixed and excluded numbers", async () => {
    // given
    mockApi({ fixed: [], excluded: [] }, () =>
      ok({ fixed: [3, 7], excluded: [1, 45] }),
    );
    const { fixed, excluded, save } = await renderForm();

    // when
    await userEvent.type(fixed, "7, 3");
    await userEvent.type(excluded, "45 1");
    await userEvent.click(save);

    // then
    expect(await screen.findByRole("status")).toHaveTextContent(
      "저장했습니다.",
    );
    const [path, init] = putCalls()[0];
    expect(path).toBe("/api/v1/recommendation-settings");
    expect(init.body).toBe(
      JSON.stringify({ fixed: [7, 3], excluded: [45, 1] }),
    );
    expect(fixed).toHaveValue("3, 7");
    expect(excluded).toHaveValue("1, 45");
  });

  it("refetches the settings after saving", async () => {
    // given
    mockApi({ fixed: [], excluded: [] });
    const { save } = await renderForm();
    expect(getCalls()).toHaveLength(1);

    // when
    await userEvent.click(save);

    // then
    await waitFor(() => expect(getCalls()).toHaveLength(2));
  });

  it("shows the saved values when opened", async () => {
    // given
    mockApi({ fixed: [3, 7], excluded: [45] });

    // when
    const { fixed, excluded } = await renderForm();

    // then
    expect(fixed).toHaveValue("3, 7");
    expect(excluded).toHaveValue("45");
  });

  it("shows the server message on conflict", async () => {
    // given
    mockApi({ fixed: [], excluded: [] }, () =>
      failure(
        "DRAW_FIXED_EXCLUDED_OVERLAP",
        "고정 번호와 제외 번호가 겹칩니다.",
        400,
      ),
    );
    const { fixed, excluded, save } = await renderForm();

    // when
    await userEvent.type(fixed, "3");
    await userEvent.type(excluded, "3");
    await userEvent.click(save);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "고정 번호와 제외 번호가 겹칩니다.",
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("does not send input that is not a number list", async () => {
    // given
    mockApi({ fixed: [], excluded: [] });
    const { fixed, save } = await renderForm();

    // when
    await userEvent.type(fixed, "3, 1.5, x");
    await userEvent.click(save);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "번호는 쉼표나 공백으로 구분한 숫자로 입력해 주세요.",
    );
    expect(putCalls()).toHaveLength(0);
  });

  it("says changes apply from the next generation", async () => {
    // given
    mockApi({ fixed: [], excluded: [] });

    // when
    await renderForm();

    // then
    expect(
      screen.getByText("변경 내용은 다음 추천 생성부터 적용됩니다."),
    ).toBeInTheDocument();
  });
});
