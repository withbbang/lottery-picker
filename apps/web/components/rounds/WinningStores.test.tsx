import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { WinningStores } from "@/components/rounds/WinningStores";
import type { WinningStoreResponse } from "@/types/api/round";

describe("WinningStores", () => {
  it("omits the purchase type separator when purchaseType is null", () => {
    // given
    const stores: WinningStoreResponse[] = [
      {
        rank: 1,
        region: "서울",
        name: "가게A",
        address: "서울 주소",
        purchaseType: null,
        status: "영업중",
      },
    ];

    // when
    render(<WinningStores stores={stores} />);

    // then
    expect(screen.getByText("가게A").closest("p")).not.toHaveTextContent("·");
  });
});
