import type { MetadataRoute } from "next";

/** 홈 화면 설치용. 오프라인 캐시·푸시는 하지 않는다 (서비스 워커 없음). */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "로또 추첨",
    short_name: "로또 추첨",
    description:
      "역대 당첨번호 통계로 매일 추천 번호를 만들고, 추첨 결과와 맞춰 봅니다.",
    lang: "ko",
    start_url: "/",
    display: "standalone",
    // globals.css 라이트 토큰 --slip, --paper
    theme_color: "#c42f4f",
    background_color: "#f4f6f9",
    // 일반 아이콘은 배경이 투명해 실행 직후 화면(background_color) 위에 그림만 뜬다.
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      // 런처가 모양대로 잘라 쓰므로 남색 배경을 꽉 채운 버전. 그림은 가운데 80%(안전 영역) 안에 있다.
      {
        src: "/icons/icon-maskable.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
