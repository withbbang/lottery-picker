"use client";

import { useState } from "react";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { DrawnBalls } from "@/components/lotto/LottoBall";
import { Pagination } from "@/components/ui/Pagination";
import type { PageResponse, RoundSummaryResponse } from "@/types/api/round";

const PAGE_SIZE = 20;

export function RoundList() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError } = useQuery({
    queryKey: ["rounds", { page }],
    queryFn: () =>
      apiFetch<PageResponse<RoundSummaryResponse>>(
        `/api/v1/rounds?page=${page}&size=${PAGE_SIZE}`,
      ),
    placeholderData: (previous) => previous,
  });

  if (isPending) {
    return <p className="text-muted">회차 목록을 불러오는 중…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        회차 목록을 불러오지 못했습니다.
      </p>
    );
  }

  if (data.items.length === 0) {
    return <p className="text-muted">아직 수집한 회차가 없습니다.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col overflow-hidden rounded-lg border border-rule bg-surface">
        {data.items.map((r) => (
          <li key={r.round} className="border-t border-rule first:border-t-0">
            <Link
              href={`/rounds/${r.round}`}
              className="flex flex-col gap-2 px-4 py-3 hover:bg-paper"
            >
              <span className="flex items-baseline justify-between text-sm tabular-nums">
                <span className="font-semibold">{r.round}회</span>
                <span className="text-muted">{r.drawDate}</span>
              </span>
              <DrawnBalls numbers={r.numbers} bonus={r.bonus} fill />
            </Link>
          </li>
        ))}
      </ul>
      <Pagination
        page={page}
        totalPages={data.totalPages}
        onChange={setPage}
      />
    </div>
  );
}
