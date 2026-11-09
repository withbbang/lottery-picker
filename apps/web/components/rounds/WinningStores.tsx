import type { WinningStoreResponse } from "@/types/api/round";

function groupByRegion(stores: WinningStoreResponse[]) {
  const groups = new Map<string, WinningStoreResponse[]>();
  for (const store of stores) {
    const group = groups.get(store.region);
    if (group) {
      group.push(store);
    } else {
      groups.set(store.region, [store]);
    }
  }
  return [...groups.entries()];
}

export function WinningStores({ stores }: { stores: WinningStoreResponse[] }) {
  if (stores.length === 0) {
    return <p className="text-muted">판매점 정보가 없습니다.</p>;
  }

  return (
    <div className="flex flex-col">
      <p className="pb-2 text-sm text-muted tabular-nums">
        총 {stores.length}곳
      </p>
      <ul className="flex flex-col">
        {groupByRegion(stores).map(([region, items]) => (
          <li
            key={region}
            aria-label={region}
            className="flex flex-col gap-2 border-t border-rule py-3"
          >
            <p className="flex items-baseline justify-between">
              <span className="font-semibold">{region}</span>
              <span className="text-sm text-muted tabular-nums">
                {items.length}건
              </span>
            </p>
            <ul className="flex flex-col gap-2.5 text-sm">
              {items.map((s, i) => (
                <li key={`${s.rank}-${s.name}-${s.address}-${i}`}>
                  <p>
                    <span className="font-semibold text-slip tabular-nums">
                      {s.rank}등
                    </span>{" "}
                    {s.name}
                    {s.purchaseType ? (
                      <span className="text-muted"> · {s.purchaseType}</span>
                    ) : null}
                  </p>
                  <p className="text-muted">
                    {s.address}
                    {s.status ? ` (${s.status})` : ""}
                  </p>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}
