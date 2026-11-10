import { Suspense } from "react";

import Link from "next/link";

import { StoreStatisticsView } from "@/components/stores/StoreStatisticsView";

export default function StoresPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 pt-6 pb-10">
      <Link
        href="/rounds"
        className="self-start text-sm text-muted hover:text-ink"
      >
        ‹ 회차 목록
      </Link>
      <h1 className="text-2xl font-bold tracking-tight">당첨 판매점</h1>
      {/* cacheComponents에서 useSearchParams는 Suspense가 필요하다. */}
      <Suspense
        fallback={<p className="text-muted">판매점 통계를 불러오는 중…</p>}
      >
        <StoreStatisticsView />
      </Suspense>
    </main>
  );
}
