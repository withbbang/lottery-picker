import { RecommendationHistory } from "@/components/history/RecommendationHistory";

export default function HistoryPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 pt-8 pb-10">
      <h1 className="text-2xl font-bold tracking-tight">지난 추천</h1>
      <RecommendationHistory />
    </main>
  );
}
