import { SettingsForm } from "@/components/recommendation/SettingsForm";

export default function SettingsPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-6 px-4 pt-8 pb-10">
      <h1 className="text-2xl font-bold tracking-tight">번호 설정</h1>
      <SettingsForm />
    </main>
  );
}
