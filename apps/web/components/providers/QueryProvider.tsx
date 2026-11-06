"use client";

import { Suspense, useEffect, useState, type ReactNode } from "react";

import { usePathname, useRouter } from "next/navigation";

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

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === "COMMON_NOT_FOUND";
}

function createAuthAwareClient(moveToLogin: () => void) {
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

  const queryClient = new QueryClient({
    queryCache: new QueryCache({ onError: redirectIfAuthRequired }),
    mutationCache: new MutationCache({ onError: redirectIfAuthRequired }),
    defaultOptions: {
      queries: {
        retry: (failureCount, error) =>
          !isAuthRequired(error) &&
          !isNotFound(error) &&
          failureCount < MAX_QUERY_RETRIES,
      },
    },
  });

  return {
    queryClient,
    clearRedirect: () => {
      redirecting = false;
    },
  };
}

function ClearRedirectOnNavigation({ onClear }: { onClear: () => void }) {
  const pathname = usePathname();

  useEffect(() => {
    onClear();
  }, [pathname, onClear]);

  return null;
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [{ queryClient, clearRedirect }] = useState(() =>
    createAuthAwareClient(() => router.replace(LOGIN_PATH)),
  );

  return (
    <QueryClientProvider client={queryClient}>
      {/* cacheComponents에서 usePathname은 동적 경로에 Suspense가 필요하다. */}
      <Suspense fallback={null}>
        <ClearRedirectOnNavigation onClear={clearRedirect} />
      </Suspense>
      {children}
    </QueryClientProvider>
  );
}
