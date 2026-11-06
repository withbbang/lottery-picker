import { Suspense } from "react";

import { ApiStatus } from "@/components/health/ApiStatus";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-4 px-4 py-16">
      <h1 className="text-3xl font-semibold">로또 번호 추첨</h1>
      <Suspense fallback={<p>API 상태 확인 중…</p>}>
        <ApiStatus />
      </Suspense>
    </main>
  );
}
