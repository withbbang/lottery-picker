"use client";

import { useState } from "react";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { LottoBall } from "@/components/lotto/LottoBall";
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
    return <p>회차 목록을 불러오는 중…</p>;
  }
  if (isError) {
    return <p role="alert">회차 목록을 불러오지 못했습니다.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col">
        {data.items.map((r) => (
          <li key={r.round} className="border-t border-current/20">
            <Link href={`/rounds/${r.round}`} className="flex flex-col gap-2 py-3">
              <span className="flex justify-between text-sm tabular-nums">
                <span className="font-semibold">{r.round}회</span>
                <span className="opacity-70">{r.drawDate}</span>
              </span>
              <span className="flex flex-wrap items-center gap-1">
                {r.numbers.map((n) => (
                  <LottoBall key={n} value={n} />
                ))}
                <span aria-hidden="true">+</span>
                <LottoBall value={r.bonus} />
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <nav className="flex items-center justify-between text-sm tabular-nums">
        <button
          type="button"
          className="rounded border border-current px-3 py-2 disabled:opacity-40"
          disabled={page === 0}
          onClick={() => setPage((p) => p - 1)}
        >
          이전
        </button>
        <span>
          {data.page + 1} / {data.totalPages}
        </span>
        <button
          type="button"
          className="rounded border border-current px-3 py-2 disabled:opacity-40"
          disabled={page + 1 >= data.totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          다음
        </button>
      </nav>
    </div>
  );
}
