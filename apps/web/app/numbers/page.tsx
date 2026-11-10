import { Suspense } from "react";

import Link from "next/link";

import { NumberStatisticsView } from "@/components/statistics/NumberStatisticsView";

export default function NumbersPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 pt-6 pb-10">
      <Link href="/" className="self-start text-sm text-muted hover:text-ink">
        ‹ 오늘 추천
      </Link>
      <h1 className="text-2xl font-bold tracking-tight">번호 통계</h1>
      {/* cacheComponents에서 useSearchParams는 Suspense가 필요하다. */}
      <Suspense
        fallback={<p className="text-muted">번호 통계를 불러오는 중…</p>}
      >
        <NumberStatisticsView />
      </Suspense>
    </main>
  );
}
