"use client";

import { useQuery } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import { DrawnBalls } from "@/components/lotto/LottoBall";
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
    return <p className="text-muted">회차 정보를 불러오는 중…</p>;
  }
  if (detail.isError) {
    if (
      detail.error instanceof ApiError &&
      detail.error.code === "COMMON_NOT_FOUND"
    ) {
      return (
        <p role="alert" className="text-danger">
          존재하지 않는 회차입니다.
        </p>
      );
    }
    return (
      <p role="alert" className="text-danger">
        회차 정보를 불러오지 못했습니다.
      </p>
    );
  }

  const data = detail.data;
  return (
    <div className="flex flex-col gap-10">
      <header className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between tabular-nums">
          <h1 className="text-2xl font-bold tracking-tight">{data.round}회</h1>
          <p className="text-sm text-muted">{data.drawDate}</p>
        </div>
        <DrawnBalls numbers={data.numbers} bonus={data.bonus} size="lg" fill />
      </header>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">추천 결과</h2>
        <RoundResults round={data.round} />
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">등별 당첨금</h2>
        <table className="w-full text-left text-sm tabular-nums">
          <thead className="text-muted">
            <tr>
              <th scope="col" className="py-1.5 font-medium">
                등위
              </th>
              <th scope="col" className="py-1.5 text-right font-medium">
                당첨자
              </th>
              <th scope="col" className="py-1.5 text-right font-medium">
                1인당 금액
              </th>
            </tr>
          </thead>
          <tbody>
            {data.prizes.map((p) => (
              <tr key={p.rank} className="border-t border-rule">
                <th scope="row" className="py-2 font-semibold">
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

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">당첨 판매점</h2>
        {stores.isPending ? (
          <p className="text-muted">판매점 정보를 불러오는 중…</p>
        ) : stores.isError ? (
          <p role="alert" className="text-danger">
            판매점 정보를 불러오지 못했습니다.
          </p>
        ) : (
          <WinningStores stores={stores.data} />
        )}
        <p className="text-sm text-muted">
          3~5등은 판매점 정보가 공개되지 않습니다.
        </p>
      </section>
    </div>
  );
}
