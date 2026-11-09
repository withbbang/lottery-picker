"use client";

import { useId, useState } from "react";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { DrawnBalls } from "@/components/lotto/LottoBall";
import { RoundResults } from "@/components/results/RoundResults";
import type { PageResponse, RoundSummaryResponse } from "@/types/api/round";

export function LatestRoundSummary() {
  const [open, setOpen] = useState(false);
  const resultsId = useId();
  const { data, isPending, isError } = useQuery({
    queryKey: ["rounds", "latest"],
    queryFn: () =>
      apiFetch<PageResponse<RoundSummaryResponse>>(
        "/api/v1/rounds?page=0&size=1",
      ),
  });

  if (isPending) {
    return <p className="text-muted">최근 회차를 불러오는 중…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger">
        최근 회차를 불러오지 못했습니다.
      </p>
    );
  }

  const latest = data.items[0];
  if (!latest) {
    return <p className="text-muted">아직 수집한 회차가 없습니다.</p>;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-rule bg-surface">
      <Link
        href={`/rounds/${latest.round}`}
        className="flex flex-col gap-3 px-4 py-3 hover:bg-paper"
      >
        <span className="flex items-baseline justify-between text-sm tabular-nums">
          <span className="font-semibold">{latest.round}회</span>
          <span className="text-muted">{latest.drawDate}</span>
        </span>
        <DrawnBalls numbers={latest.numbers} bonus={latest.bonus} fill />
      </Link>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={resultsId}
        onClick={() => setOpen((value) => !value)}
        className="flex w-full items-center justify-between border-t border-rule px-4 py-2.5 text-sm font-medium text-slip hover:bg-paper"
      >
        채점 결과 보기
        <span
          aria-hidden="true"
          className={`transition-transform motion-reduce:transition-none ${open ? "rotate-180" : ""}`}
        >
          ▾
        </span>
      </button>
      <div
        id={resultsId}
        hidden={!open}
        className="border-t border-rule px-4 py-4"
      >
        {/* 펼칠 때만 그려서, 닫혀 있으면 채점 결과를 요청하지 않는다. */}
        {open && <RoundResults round={latest.round} />}
      </div>
    </div>
  );
}
