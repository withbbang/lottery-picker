"use client";

import { useState, type FormEvent } from "react";

import { useRouter } from "next/navigation";

import { useMutation } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import type { SessionCreateRequest } from "@/types/api/auth";

const CODE_LENGTH = 6;
const FALLBACK_ERROR = "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.";

function createSession(request: SessionCreateRequest): Promise<void> {
  return apiFetch<void>("/api/v1/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

export function LoginForm() {
  const router = useRouter();
  const [code, setCode] = useState("");
  const login = useMutation({
    mutationFn: createSession,
    onSuccess: () => router.replace("/"),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    login.mutate({ code });
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">인증 코드</span>
        <input
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          value={code}
          onChange={(event) =>
            setCode(event.target.value.replace(/\D/g, "").slice(0, CODE_LENGTH))
          }
          className="rounded-md border border-rule bg-surface px-3 py-2.5 text-center text-2xl font-semibold tracking-[0.5em] tabular-nums"
        />
      </label>
      <button
        type="submit"
        disabled={code.length !== CODE_LENGTH || login.isPending}
        className="rounded-md bg-slip px-4 py-2.5 font-semibold text-on-slip hover:bg-slip/90 disabled:opacity-40"
      >
        로그인
      </button>
      {login.isError && (
        <p role="alert" className="text-sm text-danger">
          {login.error instanceof ApiError
            ? login.error.message
            : FALLBACK_ERROR}
        </p>
      )}
    </form>
  );
}
