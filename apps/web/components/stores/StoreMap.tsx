"use client";

import { useEffect, useRef } from "react";

import L from "leaflet";
import "leaflet/dist/leaflet.css";

import type { StoreResponse } from "@/types/api/store";

const TILE_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
const ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
// 대한민국 전체가 보이는 처음 위치. 핀이 오면 곧바로 핀 범위로 옮긴다.
const KOREA_CENTER: L.LatLngTuple = [36.3, 127.8];
const SINGLE_PIN_ZOOM = 16;

/** 팝업 내용. 판매점 이름·주소는 원본 데이터라 HTML로 넣지 않고 글자로만 넣는다. */
function popupContent(store: StoreResponse) {
  const root = document.createElement("div");
  const lines: [string, string][] = [
    [store.name, "font-semibold text-ink"],
    [store.address, "text-muted"],
    [
      `당첨 ${store.wins}회 · 최근 ${store.lastRound}회${store.status === "폐점" ? " · 폐점" : ""}`,
      "tabular-nums",
    ],
  ];
  root.className = "flex flex-col gap-0.5 text-sm";
  for (const [text, className] of lines) {
    const line = document.createElement("p");
    line.textContent = text;
    line.className = `m-0! ${className}`;
    root.append(line);
  }
  return root;
}

/** 숫자 핀. 두 번 이상 당첨된 판매점에만 쓴다. */
function countIcon(wins: number) {
  return L.divIcon({
    html: String(wins),
    className:
      "flex! items-center justify-center rounded-full border-2 border-surface bg-slip text-xs font-bold text-on-slip tabular-nums shadow",
    iconSize: [26, 26],
    iconAnchor: [13, 13],
    popupAnchor: [0, -13],
  });
}

export function StoreMap({ pins }: { pins: StoreResponse[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const layerRef = useRef<L.LayerGroup | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (container === null) {
      return;
    }
    const map = L.map(container, { preferCanvas: true }).setView(
      KOREA_CENTER,
      7,
    );
    L.tileLayer(TILE_URL, { attribution: ATTRIBUTION, maxZoom: 19 }).addTo(map);
    const layer = L.layerGroup().addTo(map);
    mapRef.current = map;
    layerRef.current = layer;
    return () => {
      // 핀을 먼저 지운다. 캔버스 렌더러가 먼저 지워지면 남은 원이 지워지며 없는 캔버스에 다시 그리려 한다.
      layer.clearLayers();
      map.remove();
      mapRef.current = null;
      layerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const layer = layerRef.current;
    if (map === null || layer === null) {
      return;
    }
    layer.clearLayers();
    const slip = getComputedStyle(document.documentElement)
      .getPropertyValue("--slip")
      .trim();
    // 많이 당첨된 판매점이 위에 오도록 1회 판매점부터 그린다.
    const placed = [...pins].reverse().flatMap((store) =>
      store.latitude === null || store.longitude === null
        ? []
        : [
            {
              store,
              point: [store.latitude, store.longitude] as L.LatLngTuple,
            },
          ],
    );
    // 시야를 먼저 맞추고 핀을 얹는다. 핀을 먼저 얹으면 시야를 옮길 때 캔버스가 바로 다시 그리면서
    // 앞서 걸어 둔 다시 그리기 예약을 잃어버려, 지도를 지운 뒤에 그 예약이 실행되며 오류가 난다.
    if (placed.length === 1) {
      map.setView(placed[0].point, SINGLE_PIN_ZOOM, { animate: false });
    } else if (placed.length > 1) {
      map.fitBounds(L.latLngBounds(placed.map(({ point }) => point)), {
        padding: [24, 24],
        animate: false,
      });
    }
    for (const { store, point } of placed) {
      const pin =
        store.wins >= 2
          ? L.marker(point, {
              icon: countIcon(store.wins),
              title: store.name,
              zIndexOffset: store.wins * 100,
            })
          : L.circleMarker(point, {
              radius: 5,
              color: slip,
              weight: 1,
              fillColor: slip,
              fillOpacity: 0.55,
            });
      pin.bindPopup(() => popupContent(store)).addTo(layer);
    }
  }, [pins]);

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label="당첨 판매점 지도"
      className="isolate z-0 h-80 w-full overflow-hidden rounded-lg border border-rule bg-surface"
    />
  );
}
