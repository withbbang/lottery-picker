import { Suspense } from "react";

import Link from "next/link";

import { RoundDetailView } from "@/components/rounds/RoundDetailView";

export default function RoundPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 pt-6 pb-10">
      <Link
        href="/rounds"
        className="self-start text-sm text-muted hover:text-ink"
      >
        ‹ 회차 목록
      </Link>
      {/* cacheComponents에서 useParams는 동적 경로에 Suspense가 필요하다. */}
      <Suspense
        fallback={<p className="text-muted">회차 정보를 불러오는 중…</p>}
      >
        <RoundDetailView />
      </Suspense>
    </main>
  );
}
