import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { QueryProvider } from "@/components/providers/QueryProvider";

describe("QueryProvider", () => {
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
});
