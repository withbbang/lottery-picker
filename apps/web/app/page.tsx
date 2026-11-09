import Link from "next/link";

import { LatestRoundSummary } from "@/components/recommendation/LatestRoundSummary";
import { RecommendationCard } from "@/components/recommendation/RecommendationCard";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-8 px-4 py-8">
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">오늘 추천</h1>
        <RecommendationCard />
      </section>
      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">최신 회차</h2>
        <LatestRoundSummary />
      </section>
      <nav className="flex gap-4 text-sm underline">
        <Link href="/rounds">역대 당첨번호</Link>
        <Link href="/settings">번호 설정</Link>
        <Link href="/performance">성적표</Link>
      </nav>
    </main>
  );
}
