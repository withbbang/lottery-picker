import { Suspense } from "react";

import { RoundDetailView } from "@/components/rounds/RoundDetailView";

export default function RoundPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 py-8">
      {/* cacheComponents에서 useParams는 동적 경로에 Suspense가 필요하다. */}
      <Suspense fallback={<p>회차 정보를 불러오는 중…</p>}>
        <RoundDetailView />
      </Suspense>
    </main>
  );
}
