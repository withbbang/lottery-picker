import { RoundList } from "@/components/rounds/RoundList";

export default function RoundsPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 pt-8 pb-10">
      <h1 className="text-2xl font-bold tracking-tight">회차별 당첨번호</h1>
      <RoundList />
    </main>
  );
}
