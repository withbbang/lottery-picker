import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LoginForm } from "@/components/auth/LoginForm";
import { QueryProvider } from "@/components/providers/QueryProvider";

const fetchMock = vi.fn();
const { replaceMock } = vi.hoisted(() => ({ replaceMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

function renderForm() {
  render(
    <QueryProvider>
      <LoginForm />
    </QueryProvider>,
  );
  return {
    input: screen.getByRole("textbox", { name: "OTP 코드" }),
    submit: screen.getByRole("button", { name: "로그인" }),
  };
}

describe("LoginForm", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    replaceMock.mockReset();
  });

  it("submits the six-digit code and moves to home", async () => {
    // given
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const { input, submit } = renderForm();

    // when
    await userEvent.type(input, "123456");
    await userEvent.click(submit);

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/sessions",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ code: "123456" }),
      }),
    );
  });

  it("shows the server message when the code is wrong", async () => {
    // given
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          error: {
            code: "AUTH_INVALID_CODE",
            message: "OTP 코드가 올바르지 않습니다.",
          },
        }),
        { status: 400 },
      ),
    );
    const { input, submit } = renderForm();

    // when
    await userEvent.type(input, "000000");
    await userEvent.click(submit);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "OTP 코드가 올바르지 않습니다.",
    );
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("disables submit until six digits are entered", async () => {
    // given
    const { input, submit } = renderForm();

    // when, then
    expect(input).toHaveAttribute("inputmode", "numeric");
    expect(input).toHaveAttribute("autocomplete", "one-time-code");
    await userEvent.type(input, "12345");
    expect(submit).toBeDisabled();

    await userEvent.type(input, "6");
    expect(submit).toBeEnabled();
  });
});
