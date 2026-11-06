"use client";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { LottoBall } from "@/components/lotto/LottoBall";
import type { PageResponse, RoundSummaryResponse } from "@/types/api/round";

export function LatestRoundSummary() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["rounds", "latest"],
    queryFn: () =>
      apiFetch<PageResponse<RoundSummaryResponse>>(
        "/api/v1/rounds?page=0&size=1",
      ),
  });

  if (isPending) {
    return <p>최신 회차를 불러오는 중…</p>;
  }
  if (isError) {
    return <p role="alert">최신 회차를 불러오지 못했습니다.</p>;
  }

  const latest = data.items[0];
  if (!latest) {
    return <p>저장된 회차가 없습니다.</p>;
  }

  return (
    <Link href={`/rounds/${latest.round}`} className="flex flex-col gap-2">
      <span className="flex justify-between text-sm tabular-nums">
        <span className="font-semibold">{latest.round}회</span>
        <span className="opacity-70">{latest.drawDate}</span>
      </span>
      <span className="flex flex-wrap items-center gap-1">
        {latest.numbers.map((n) => (
          <LottoBall key={n} value={n} />
        ))}
        <span aria-hidden="true">+</span>
        <LottoBall value={latest.bonus} />
      </span>
    </Link>
  );
}
