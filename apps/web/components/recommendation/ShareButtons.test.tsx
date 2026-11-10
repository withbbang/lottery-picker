import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  CopyLineButton,
  ShareSetButton,
} from "@/components/recommendation/ShareButtons";
import { copyText, shareOrCopy } from "@/lib/share";

vi.mock("@/lib/share", () => ({
  copyText: vi.fn(),
  shareOrCopy: vi.fn(),
}));

const NUMBERS = [3, 7, 12, 27, 33, 40];

function setup() {
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

function copyButton() {
  return screen.getByRole("button", { name: "최근 빈도 번호 복사" });
}

describe("CopyLineButton", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(copyText).mockResolvedValue("copied");
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("copies the line numbers in slip format", async () => {
    // given
    const user = setup();
    render(<CopyLineButton numbers={NUMBERS} label="최근 빈도" />);

    // when
    await user.click(copyButton());

    // then
    expect(copyText).toHaveBeenCalledWith("3 7 12 27 33 40");
  });

  it("shows copied for two seconds", async () => {
    // given
    const user = setup();
    render(<CopyLineButton numbers={NUMBERS} label="최근 빈도" />);

    // when
    await user.click(copyButton());

    // then
    expect(copyButton()).toHaveTextContent("복사됨");
    expect(screen.getByRole("status")).toHaveTextContent("복사됨");
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(copyButton()).toHaveTextContent(/^복사$/);
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("shows a failure message when copying fails", async () => {
    // given
    vi.mocked(copyText).mockResolvedValue("failed");
    const user = setup();
    render(<CopyLineButton numbers={NUMBERS} label="최근 빈도" />);

    // when
    await user.click(copyButton());

    // then
    expect(copyButton()).toHaveTextContent("복사 실패");
  });

  it("restarts the feedback on another click", async () => {
    // given
    const user = setup();
    render(<CopyLineButton numbers={NUMBERS} label="최근 빈도" />);
    await user.click(copyButton());
    act(() => {
      vi.advanceTimersByTime(1500);
    });

    // when
    await user.click(copyButton());
    act(() => {
      vi.advanceTimersByTime(1000);
    });

    // then
    expect(copyButton()).toHaveTextContent("복사됨");
  });

  it("leaves no timer behind after unmount", async () => {
    // given
    let finish: (result: "copied") => void = () => {};
    vi.mocked(copyText).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const user = setup();
    const { unmount } = render(
      <CopyLineButton numbers={NUMBERS} label="최근 빈도" />,
    );
    await user.click(copyButton());

    // when
    unmount();
    await act(async () => {
      finish("copied");
    });

    // then
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe("ShareSetButton", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  function shareButton() {
    return screen.getByRole("button", { name: /번호 공유|복사/ });
  }

  it("shares the whole text", async () => {
    // given
    vi.mocked(shareOrCopy).mockResolvedValue("shared");
    const user = setup();
    render(<ShareSetButton text={"1250회\n최근 빈도: 1 2"} />);

    // when
    await user.click(shareButton());

    // then
    expect(shareOrCopy).toHaveBeenCalledWith("1250회\n최근 빈도: 1 2");
  });

  it("shows nothing extra when the share sheet is closed", async () => {
    // given
    vi.mocked(shareOrCopy).mockResolvedValue("cancelled");
    const user = setup();
    render(<ShareSetButton text="글" />);

    // when
    await user.click(shareButton());

    // then
    expect(shareButton()).toHaveTextContent(/^번호 공유$/);
  });

  it("shows copied when sharing fell back to the clipboard", async () => {
    // given
    vi.mocked(shareOrCopy).mockResolvedValue("copied");
    const user = setup();
    render(<ShareSetButton text="글" />);

    // when
    await user.click(shareButton());

    // then
    expect(shareButton()).toHaveTextContent("복사됨");
  });
});
