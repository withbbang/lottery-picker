import Link from "next/link";

import { RoundList } from "@/components/rounds/RoundList";

export default function RoundsPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 pt-8 pb-10">
      <div className="flex items-baseline justify-between gap-3">
        <h1 className="text-2xl font-bold tracking-tight">회차별 당첨번호</h1>
        <Link
          href="/rounds/stores"
          className="shrink-0 text-sm font-medium text-slip hover:underline hover:underline-offset-4"
        >
          당첨 판매점 지역 통계 ›
        </Link>
      </div>
      <RoundList />
    </main>
  );
}
