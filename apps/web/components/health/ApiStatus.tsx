import { connection } from "next/server";

import { apiFetch } from "@/lib/api";
import type { HealthResponse } from "@/types/api/health";

async function fetchApiStatus(): Promise<string | null> {
  try {
    const health = await apiFetch<HealthResponse>("/api/v1/health");
    return health.status;
  } catch (error) {
    console.error("API 상태 조회 실패", error);
    return null;
  }
}

export async function ApiStatus() {
  await connection();
  const status = await fetchApiStatus();

  if (status === null) {
    return <p role="alert">API에 연결할 수 없습니다.</p>;
  }
  return <p>API 상태: {status}</p>;
}
