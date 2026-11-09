import { PerformanceTable } from "@/components/performance/PerformanceTable";
import { SimulationPanel } from "@/components/performance/SimulationPanel";

export default function PerformancePage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-10 px-4 pt-8 pb-10">
      <div className="flex flex-col gap-5">
        <h1 className="text-2xl font-bold tracking-tight">통계</h1>
        <section
          aria-labelledby="performance-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="performance-heading" className="text-lg font-semibold">
            추천 성적
          </h2>
          <PerformanceTable />
        </section>
      </div>
      <section
        aria-labelledby="simulation-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="simulation-heading" className="text-lg font-semibold">
          과거 회차 시뮬레이션
        </h2>
        <SimulationPanel />
      </section>
    </main>
  );
}
