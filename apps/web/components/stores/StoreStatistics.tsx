"use client";

import dynamic from "next/dynamic";
import Link from "next/link";

import { useQuery } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import {
  storeStatsApiPath,
  storeStatsHref,
  type StoreRank,
  type StoreStatsParams,
} from "@/lib/store-stats-params";
import type {
  AreaCountResponse,
  StoreResponse,
  StoreStatisticsResponse,
} from "@/types/api/store";

// 지도는 브라우저에서만 그린다(Leaflet은 window가 필요하다).
const StoreMap = dynamic(
  () => import("@/components/stores/StoreMap").then((m) => m.StoreMap),
  {
    ssr: false,
    loading: () => (
      <div className="h-80 w-full animate-pulse rounded-lg border border-rule bg-surface motion-reduce:animate-none" />
    ),
  },
);

const RANKS: StoreRank[] = [1, 2];
const CLOSED = "폐점";

// 204면 apiFetch가 undefined를 주는데, React Query는 undefined 결과를 오류로 본다.
async function fetchStatistics(
  params: StoreStatsParams,
): Promise<StoreStatisticsResponse | null> {
  const stats = await apiFetch<StoreStatisticsResponse | undefined>(
    storeStatsApiPath(params),
  );
  return stats ?? null;
}

function RankSwitch({ params }: { params: StoreStatsParams }) {
  return (
    <nav
      aria-label="당첨 등수"
      className="grid grid-cols-2 gap-1 rounded-lg border border-rule bg-surface p-1"
    >
      {RANKS.map((rank) => (
        <Link
          key={rank}
          href={storeStatsHref({ ...params, rank })}
          aria-current={rank === params.rank ? "page" : undefined}
          className="rounded-md px-2 py-2 text-center text-sm font-medium text-muted hover:text-ink aria-[current=page]:bg-ink aria-[current=page]:text-surface"
        >
          {rank}등
        </Link>
      ))}
    </nav>
  );
}

function AreaTrail({ params }: { params: StoreStatsParams }) {
  const { rank, region, district } = params;
  // key는 단계 이름. 세종처럼 시·도와 시·군·구 이름이 같을 수 있다.
  const steps: {
    key: "all" | "region" | "district";
    label: string;
    href: string | null;
  }[] = [
    {
      key: "all",
      label: "전국",
      href:
        region === null
          ? null
          : storeStatsHref({ rank, region: null, district: null }),
    },
  ];
  if (region !== null) {
    steps.push({
      key: "region",
      label: region,
      href:
        district === null
          ? null
          : storeStatsHref({ rank, region, district: null }),
    });
  }
  if (district !== null) {
    steps.push({ key: "district", label: district, href: null });
  }
  return (
    <nav aria-label="지역 경로">
      <ol className="flex flex-wrap items-baseline gap-x-1.5 text-lg font-semibold">
        {steps.map((step, i) => (
          <li key={step.key} className="flex items-baseline gap-x-1.5">
            {i > 0 && (
              <span aria-hidden="true" className="text-muted">
                ›
              </span>
            )}
            {step.href === null ? (
              <span aria-current="location">{step.label}</span>
            ) : (
              <Link
                href={step.href}
                className="text-muted underline decoration-rule underline-offset-4 hover:text-ink"
              >
                {step.label}
              </Link>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

function AreaRanking({
  label,
  areas,
  hrefOf,
  current,
}: {
  label: string;
  areas: AreaCountResponse[];
  hrefOf: (name: string) => string;
  current: string | null;
}) {
  // 막대 길이의 기준. 서버 정렬 순서에 기대지 않고 가장 큰 값을 쓴다.
  const scale = Math.max(1, ...areas.map((area) => area.count));
  return (
    <ol
      aria-label={label}
      className="flex flex-col overflow-hidden rounded-lg border border-rule bg-surface"
    >
      {areas.map((area, i) => {
        const selected = area.name === current;
        return (
          <li
            key={area.name}
            className="border-t border-rule/60 first:border-t-0"
          >
            <Link
              href={hrefOf(area.name)}
              aria-current={selected ? "page" : undefined}
              className="grid grid-cols-[1.5rem_5.5rem_1fr_auto] items-center gap-x-3 px-4 py-2 hover:bg-paper aria-[current=page]:bg-slip-soft"
            >
              <span className="text-sm text-muted tabular-nums">{i + 1}</span>
              <span
                className={`truncate ${selected ? "font-semibold text-slip" : "font-medium"}`}
              >
                {area.name}
              </span>
              <meter
                aria-hidden="true"
                min={0}
                max={scale}
                value={area.count}
                className={`h-2 w-full appearance-none bg-none [&::-moz-meter-bar]:rounded-full [&::-moz-meter-bar]:bg-none [&::-webkit-meter-bar]:border-0 [&::-webkit-meter-bar]:bg-transparent [&::-webkit-meter-bar]:bg-none [&::-webkit-meter-optimum-value]:rounded-full [&::-webkit-meter-optimum-value]:bg-none ${
                  selected
                    ? "[&::-moz-meter-bar]:bg-slip [&::-webkit-meter-optimum-value]:bg-slip"
                    : "[&::-moz-meter-bar]:bg-muted/40 [&::-webkit-meter-optimum-value]:bg-muted/40"
                }`}
              />
              <span className="text-sm text-muted tabular-nums">
                {area.count.toLocaleString("ko-KR")}건
              </span>
            </Link>
          </li>
        );
      })}
    </ol>
  );
}

function RepeatStores({ stores }: { stores: StoreResponse[] }) {
  if (stores.length === 0) {
    return (
      <p className="text-sm text-muted">
        이 범위에는 두 번 이상 당첨된 판매점이 없습니다.
      </p>
    );
  }
  return (
    <ol
      aria-label="두 번 이상 당첨된 판매점"
      className="flex flex-col overflow-hidden rounded-lg border border-rule bg-surface"
    >
      {stores.map((store) => (
        <li
          key={`${store.name}\n${store.address}`}
          className="grid grid-cols-[1fr_auto] gap-x-3 border-t border-rule/60 px-4 py-3 first:border-t-0"
        >
          <div className="flex min-w-0 flex-col gap-0.5">
            <p className="flex items-baseline gap-2">
              <span className="font-semibold">{store.name}</span>
              {store.status === CLOSED && (
                <span className="rounded-sm border border-rule px-1 text-xs text-muted">
                  {CLOSED}
                </span>
              )}
            </p>
            <p className="text-sm text-muted">{store.address}</p>
          </div>
          <div className="flex flex-col items-end gap-0.5 tabular-nums">
            <span className="text-lg font-bold text-slip">{store.wins}회</span>
            <span className="text-xs text-muted">
              최근{" "}
              <Link
                href={`/rounds/${store.lastRound}`}
                className="underline decoration-rule underline-offset-2 hover:text-ink"
              >
                {store.lastRound}회
              </Link>
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}

export function StoreStatistics({ params }: { params: StoreStatsParams }) {
  const { rank, region, district } = params;
  const { data, isPending, error } = useQuery({
    queryKey: ["store-statistics", rank, region, district],
    queryFn: () => fetchStatistics(params),
  });

  const body = (() => {
    if (isPending) {
      return <p className="text-muted">판매점 통계를 불러오는 중…</p>;
    }
    if (error) {
      const notFound =
        error instanceof ApiError && error.code === "COMMON_NOT_FOUND";
      return (
        <div className="flex flex-col items-start gap-2">
          <p role="alert" className="text-danger">
            {notFound
              ? "그 지역의 당첨 판매점 정보가 없습니다."
              : "판매점 통계를 불러오지 못했습니다. 잠시 후 새로고침해 주세요."}
          </p>
          {notFound && (
            <Link
              href={storeStatsHref({ rank, region: null, district: null })}
              className="text-sm font-medium text-slip underline underline-offset-4"
            >
              전국 보기
            </Link>
          )}
        </div>
      );
    }
    if (data === null) {
      return (
        <p className="rounded-lg border border-dashed border-slip/50 px-4 py-6 text-muted">
          아직 판매점 정보가 없습니다.
        </p>
      );
    }

    // 전국이면 시·도 순위, 시·도면 그 안 시·군·구 순위. 온라인은 시·군·구가 없어 순위를 두지 않는다.
    const areas =
      region === null
        ? {
            title: "시·도별 당첨",
            label: "시·도 순위",
            items: data.regions,
            hrefOf: (name: string) =>
              storeStatsHref({ rank, region: name, district: null }),
          }
        : data.districts !== null && data.districts.length > 0
          ? {
              title: `${region} 시·군·구별 당첨`,
              label: `${region} 시·군·구 순위`,
              items: data.districts,
              hrefOf: (name: string) =>
                storeStatsHref({ rank, region, district: name }),
            }
          : null;

    return (
      <>
        <div className="flex flex-col gap-1">
          <p className="text-sm text-muted tabular-nums">
            판매점 정보가 있는 {data.fromRound}회~{data.toRound}회 기준
          </p>
          <p className="text-sm text-muted">
            판매점이 많은 지역일수록 당첨도 많이 나옵니다.
          </p>
        </div>

        {region !== null && data.pins.length > 0 && (
          <StoreMap pins={data.pins} />
        )}

        {areas !== null && (
          <section className="flex flex-col gap-2">
            <h2 className="font-semibold">{areas.title}</h2>
            <AreaRanking
              label={areas.label}
              areas={areas.items}
              hrefOf={areas.hrefOf}
              current={district}
            />
          </section>
        )}

        <section className="flex flex-col gap-2">
          <h2 className="font-semibold">두 번 이상 당첨된 판매점</h2>
          <RepeatStores stores={data.repeatStores} />
        </section>
      </>
    );
  })();

  return (
    <div className="flex flex-col gap-4">
      <RankSwitch params={params} />
      <AreaTrail params={params} />
      {body}
    </div>
  );
}
