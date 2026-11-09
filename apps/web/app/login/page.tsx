import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-6 px-4 py-16">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">로그인</h1>
        <p className="text-sm text-muted">
          인증 앱에 표시된 6자리 코드를 입력하세요.
        </p>
      </div>
      <LoginForm />
    </main>
  );
}
