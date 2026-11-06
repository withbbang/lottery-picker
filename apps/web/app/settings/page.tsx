import Link from "next/link";

import { SettingsForm } from "@/components/recommendation/SettingsForm";

export default function SettingsPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-4 px-4 py-8">
      <Link href="/" className="text-sm underline">
        오늘 추천으로
      </Link>
      <h1 className="text-2xl font-semibold">번호 설정</h1>
      <SettingsForm />
    </main>
  );
}
