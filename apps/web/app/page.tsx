import { LatestRoundSummary } from "@/components/recommendation/LatestRoundSummary";
import { RecommendationCard } from "@/components/recommendation/RecommendationCard";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-10 px-4 pt-8 pb-10">
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold tracking-tight">오늘 추천</h1>
        <RecommendationCard />
      </section>
      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold">최근 당첨번호</h2>
        <LatestRoundSummary />
      </section>
    </main>
  );
}
