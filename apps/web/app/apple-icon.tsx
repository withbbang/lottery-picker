import { appIconResponse } from "@/components/brand/AppIconArt";

export const size = { width: 180, height: 180 };
export const contentType = "image/png";

/** iOS는 아이콘 모서리를 직접 둥글게 자르므로 배경을 꽉 채운다. */
export default function AppleIcon() {
  return appIconResponse(size.width, 0.74);
}
