import { PerformanceTable } from "@/components/performance/PerformanceTable";
import { SimulationPanel } from "@/components/performance/SimulationPanel";

export default function PerformancePage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">전략별 성적표</h1>
      <PerformanceTable />
      <section aria-labelledby="simulation-heading" className="flex flex-col gap-3">
        <h2 id="simulation-heading" className="text-xl font-semibold">
          과거 회차 시뮬레이션
        </h2>
        <SimulationPanel />
      </section>
    </main>
  );
}
