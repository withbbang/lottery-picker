import { appIconResponse } from "@/components/brand/AppIconArt";

/** 안드로이드가 원·둥근 사각형으로 잘라도 공이 다 보이도록 안전 영역(가운데 80%) 안에 그린다. */
export function GET() {
  return appIconResponse(512, 0.56);
}
