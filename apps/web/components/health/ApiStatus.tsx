"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { HealthResponse } from "@/types/api/health";

export function ApiStatus() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["health"],
    queryFn: () => apiFetch<HealthResponse>("/api/v1/health"),
  });

  if (isPending) {
    return <p>API 상태 확인 중…</p>;
  }
  if (isError) {
    return <p role="alert">API에 연결할 수 없습니다.</p>;
  }
  return <p>API 상태: {data.status}</p>;
}
