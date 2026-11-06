"use client";

import { useParams } from "next/navigation";

import { RoundDetail } from "@/components/rounds/RoundDetail";

export function RoundDetailView() {
  const params = useParams<{ round: string }>();
  const round = Number(params.round);

  if (!Number.isInteger(round) || round < 1) {
    return <p role="alert">존재하지 않는 회차입니다.</p>;
  }
  return <RoundDetail round={round} />;
}
