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
    return <p>지역 정보 없음</p>;
  }

  return (
    <div className="flex flex-col">
      <p className="text-sm tabular-nums">총 {stores.length}곳</p>
      {groupByRegion(stores).map(([region, items]) => (
        <details key={region} className="border-t border-current/20 py-2">
          <summary className="flex cursor-pointer items-center justify-between">
            <span>{region}</span>
            <span className="tabular-nums">{items.length}건</span>
          </summary>
          <ul className="mt-2 flex flex-col gap-2 text-sm">
            {items.map((s, i) => (
              <li key={`${s.rank}-${s.name}-${s.address}-${i}`}>
                <p>
                  <span className="tabular-nums">{s.rank}등</span> {s.name}
                  <span className="opacity-70"> · {s.purchaseType}</span>
                </p>
                <p className="opacity-70">
                  {s.address}
                  {s.status ? ` (${s.status})` : ""}
                </p>
              </li>
            ))}
          </ul>
        </details>
      ))}
    </div>
  );
}
