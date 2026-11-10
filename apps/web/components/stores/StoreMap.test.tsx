import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { StoreMap } from "@/components/stores/StoreMap";
import type { StoreResponse } from "@/types/api/store";

// jsdom에는 지도를 그릴 크기·캔버스가 없다. 지도에 건네는 시야 요청만 본다.
const fakeMap = vi.hoisted(() => ({
  setView: vi.fn(),
  fitBounds: vi.fn(),
  remove: vi.fn(),
}));

vi.mock("leaflet/dist/leaflet.css", () => ({}));
vi.mock("leaflet", () => {
  const layer = () => ({
    addTo: vi.fn().mockReturnThis(),
    bindPopup: vi.fn().mockReturnThis(),
    clearLayers: vi.fn(),
  });
  fakeMap.setView.mockReturnValue(fakeMap);
  return {
    default: {
      map: () => fakeMap,
      tileLayer: layer,
      layerGroup: layer,
      marker: layer,
      circleMarker: layer,
      divIcon: () => ({}),
      latLngBounds: (points: unknown) => ({ points }),
    },
  };
});

function store(name: string): StoreResponse {
  return {
    name,
    address: "서울 중랑구 망우로 1",
    region: "서울",
    district: "중랑구",
    wins: 1,
    lastRound: 1249,
    latitude: 37.5,
    longitude: 127.1,
    status: "정상",
  };
}

describe("StoreMap", () => {
  it("does not zoom past the single pin zoom when pins share a spot", () => {
    // given: 같은 건물의 판매점 두 곳
    const pins = [store("1층점"), store("2층점")];

    // when
    render(<StoreMap pins={pins} />);

    // then
    expect(fakeMap.fitBounds).toHaveBeenLastCalledWith(
      expect.anything(),
      expect.objectContaining({ maxZoom: 16 }),
    );
  });
});
