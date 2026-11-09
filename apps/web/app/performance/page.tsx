import { PerformanceTable } from "@/components/performance/PerformanceTable";

export default function PerformancePage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">전략별 성적표</h1>
      <PerformanceTable />
    </main>
  );
}
