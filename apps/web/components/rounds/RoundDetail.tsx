"use client";

import { useQuery } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import { LottoBall } from "@/components/lotto/LottoBall";
import { RoundResults } from "@/components/results/RoundResults";
import { WinningStores } from "@/components/rounds/WinningStores";
import type {
  RoundDetailResponse,
  WinningStoreResponse,
} from "@/types/api/round";

export function RoundDetail({ round }: { round: number }) {
  const detail = useQuery({
    queryKey: ["rounds", round],
    queryFn: () => apiFetch<RoundDetailResponse>(`/api/v1/rounds/${round}`),
  });
  const stores = useQuery({
    queryKey: ["rounds", round, "stores"],
    queryFn: () =>
      apiFetch<WinningStoreResponse[]>(
        `/api/v1/rounds/${round}/winning-stores`,
      ),
  });

  if (detail.isPending) {
    return <p>회차 정보를 불러오는 중…</p>;
  }
  if (detail.isError) {
    if (
      detail.error instanceof ApiError &&
      detail.error.code === "COMMON_NOT_FOUND"
    ) {
      return <p role="alert">존재하지 않는 회차입니다.</p>;
    }
    return <p role="alert">회차 정보를 불러오지 못했습니다.</p>;
  }

  const data = detail.data;
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3">
        <h1 className="text-2xl font-semibold tabular-nums">{data.round}회</h1>
        <p className="text-sm tabular-nums opacity-70">{data.drawDate}</p>
        <div className="flex flex-wrap items-center gap-2">
          {data.numbers.map((n) => (
            <LottoBall key={n} value={n} />
          ))}
          <span aria-hidden="true">+</span>
          <LottoBall value={data.bonus} />
        </div>
      </header>

      <section className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">등위별 당첨</h2>
        <table className="w-full text-left text-sm tabular-nums">
          <thead>
            <tr>
              <th scope="col" className="py-1 font-medium">
                등위
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                당첨자
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                1인당 금액
              </th>
            </tr>
          </thead>
          <tbody>
            {data.prizes.map((p) => (
              <tr key={p.rank} className="border-t border-current/20">
                <th scope="row" className="py-2 font-normal">
                  {p.rank}등
                </th>
                <td className="py-2 text-right">
                  {p.winnerCount.toLocaleString("ko-KR")}명
                </td>
                <td className="py-2 text-right">
                  {p.amountPerWinner.toLocaleString("ko-KR")}원
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">추천 비교</h2>
        <RoundResults round={data.round} />
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">당첨 판매점 지역</h2>
        {stores.isPending ? (
          <p>판매점 정보를 불러오는 중…</p>
        ) : stores.isError ? (
          <p role="alert">판매점 정보를 불러오지 못했습니다.</p>
        ) : (
          <WinningStores stores={stores.data} />
        )}
        <p className="text-sm opacity-70">
          3~5등은 지역 정보가 제공되지 않습니다.
        </p>
      </section>
    </div>
  );
}
