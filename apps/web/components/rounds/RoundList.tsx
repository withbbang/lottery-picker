"use client";

import { useState } from "react";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { DrawnBalls } from "@/components/lotto/LottoBall";
import type { PageResponse, RoundSummaryResponse } from "@/types/api/round";

const PAGE_SIZE = 20;
const PAGE_WINDOW = 5;
const PAGE_BUTTON =
  "flex size-9 items-center justify-center rounded-md font-medium hover:bg-surface disabled:opacity-30 disabled:hover:bg-transparent";

/** 현재 페이지가 속한 5개 묶음(1~5, 6~10…)의 0부터 센 페이지 번호. */
function pageWindow(page: number, totalPages: number) {
  const start = Math.floor(page / PAGE_WINDOW) * PAGE_WINDOW;
  const end = Math.min(start + PAGE_WINDOW, totalPages);
  return Array.from({ length: end - start }, (_, i) => start + i);
}

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
      <nav
        aria-label="페이지"
        className="flex items-center justify-center gap-0.5 text-sm tabular-nums"
      >
        <button
          type="button"
          aria-label="첫 페이지"
          className={PAGE_BUTTON}
          disabled={page === 0}
          onClick={() => setPage(0)}
        >
          <span aria-hidden="true">«</span>
        </button>
        <button
          type="button"
          aria-label="이전 페이지"
          className={PAGE_BUTTON}
          disabled={page === 0}
          onClick={() => setPage((p) => p - 1)}
        >
          <span aria-hidden="true">‹</span>
        </button>
        {pageWindow(data.page, data.totalPages).map((p) =>
          p === data.page ? (
            <span
              key={p}
              aria-current="page"
              className="flex size-9 items-center justify-center rounded-md bg-slip font-semibold text-on-slip"
            >
              {p + 1}
            </span>
          ) : (
            <button
              key={p}
              type="button"
              className={`${PAGE_BUTTON} text-muted hover:text-ink`}
              onClick={() => setPage(p)}
            >
              {p + 1}
            </button>
          ),
        )}
        <button
          type="button"
          aria-label="다음 페이지"
          className={PAGE_BUTTON}
          disabled={page + 1 >= data.totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          <span aria-hidden="true">›</span>
        </button>
        <button
          type="button"
          aria-label="마지막 페이지"
          className={PAGE_BUTTON}
          disabled={page + 1 >= data.totalPages}
          onClick={() => setPage(data.totalPages - 1)}
        >
          <span aria-hidden="true">»</span>
        </button>
      </nav>
    </div>
  );
}
