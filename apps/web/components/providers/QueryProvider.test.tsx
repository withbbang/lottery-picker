import { useMutation, useQuery } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";

const { pathnameMock, replaceMock } = vi.hoisted(() => ({
  pathnameMock: vi.fn(() => "/"),
  replaceMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: pathnameMock,
  useRouter: () => ({ replace: replaceMock }),
}));

function rejectAuthRequired(): Promise<never> {
  return Promise.reject(new ApiError("AUTH_REQUIRED", "로그인이 필요합니다."));
}

function FailingQuery({ queryKey }: { queryKey: string }) {
  const { isError } = useQuery({
    queryKey: [queryKey],
    queryFn: rejectAuthRequired,
  });
  return isError ? <p>{queryKey} 실패</p> : null;
}

function FailingMutation() {
  const { mutate, isError } = useMutation({ mutationFn: rejectAuthRequired });
  return (
    <button onClick={() => mutate()}>{isError ? "변경 실패" : "변경"}</button>
  );
}

describe("QueryProvider", () => {
  afterEach(() => {
    window.history.pushState({}, "", "/");
    pathnameMock.mockReturnValue("/");
    replaceMock.mockReset();
  });

  it("renders its children", () => {
    // when
    render(
      <QueryProvider>
        <button>확인</button>
      </QueryProvider>,
    );

    // then
    expect(screen.getByRole("button", { name: "확인" })).toBeInTheDocument();
  });

  it("redirects to /login once on AUTH_REQUIRED", async () => {
    // when
    render(
      <QueryProvider>
        <FailingQuery queryKey="first" />
        <FailingQuery queryKey="second" />
      </QueryProvider>,
    );

    // then
    await screen.findByText("first 실패");
    await screen.findByText("second 실패");
    expect(replaceMock).toHaveBeenCalledTimes(1);
    expect(replaceMock).toHaveBeenCalledWith("/login");
  });

  it("redirects to /login when a mutation fails with AUTH_REQUIRED", async () => {
    // given
    render(
      <QueryProvider>
        <FailingMutation />
      </QueryProvider>,
    );

    // when
    screen.getByRole("button", { name: "변경" }).click();

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
  });

  it("redirects again when AUTH_REQUIRED follows a navigation back from /login", async () => {
    // given
    const tree = () => (
      <QueryProvider>
        <FailingMutation />
      </QueryProvider>
    );
    const { rerender } = render(tree());
    screen.getByRole("button", { name: "변경" }).click();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledTimes(1));
    await screen.findByRole("button", { name: "변경 실패" });

    // when
    pathnameMock.mockReturnValue("/login");
    rerender(tree());
    pathnameMock.mockReturnValue("/");
    rerender(tree());
    screen.getByRole("button", { name: "변경 실패" }).click();

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledTimes(2));
  });

  it("does not redirect when already on /login", async () => {
    // given
    window.history.pushState({}, "", "/login");

    // when
    render(
      <QueryProvider>
        <FailingQuery queryKey="first" />
      </QueryProvider>,
    );

    // then
    await screen.findByText("first 실패");
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
