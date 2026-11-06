"use client";

import { useState, type ReactNode } from "react";

import { useRouter } from "next/navigation";

import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";

import { ApiError } from "@/lib/api";

const LOGIN_PATH = "/login";
const MAX_QUERY_RETRIES = 3;

function isAuthRequired(error: unknown): boolean {
  return error instanceof ApiError && error.code === "AUTH_REQUIRED";
}

function createQueryClient(moveToLogin: () => void): QueryClient {
  let redirecting = false;

  const redirectIfAuthRequired = (error: unknown) => {
    if (
      !isAuthRequired(error) ||
      redirecting ||
      window.location.pathname === LOGIN_PATH
    ) {
      return;
    }
    redirecting = true;
    moveToLogin();
  };

  return new QueryClient({
    queryCache: new QueryCache({ onError: redirectIfAuthRequired }),
    mutationCache: new MutationCache({
      onError: redirectIfAuthRequired,
      // 다시 로그인한 뒤 세션이 또 만료되면 다시 이동할 수 있게 한다.
      onSuccess: () => {
        redirecting = false;
      },
    }),
    defaultOptions: {
      queries: {
        retry: (failureCount, error) =>
          !isAuthRequired(error) && failureCount < MAX_QUERY_RETRIES,
      },
    },
  });
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [queryClient] = useState(() =>
    createQueryClient(() => router.replace(LOGIN_PATH)),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
