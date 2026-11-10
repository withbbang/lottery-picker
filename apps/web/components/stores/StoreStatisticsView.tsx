"use client";

import { useSearchParams } from "next/navigation";

import { StoreStatistics } from "@/components/stores/StoreStatistics";
import { parseStoreStatsParams } from "@/lib/store-stats-params";

export function StoreStatisticsView() {
  const searchParams = useSearchParams();
  const params = parseStoreStatsParams(searchParams);
  return <StoreStatistics params={params} />;
}
