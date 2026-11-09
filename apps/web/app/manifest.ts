import type { MetadataRoute } from "next";

/** 홈 화면 설치용. 오프라인 캐시·푸시는 하지 않는다 (서비스 워커 없음). */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "로또 번호 추첨",
    short_name: "로또 추첨",
    description:
      "역대 당첨번호 통계로 매일 추천 번호를 만들고, 추첨 뒤 채점합니다.",
    lang: "ko",
    start_url: "/",
    display: "standalone",
    // globals.css 라이트 토큰 --slip, --paper
    theme_color: "#c42f4f",
    background_color: "#f4f6f9",
    icons: [
      { src: "/icons/192", sizes: "192x192", type: "image/png" },
      { src: "/icons/512", sizes: "512x512", type: "image/png" },
      {
        src: "/icons/maskable",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
