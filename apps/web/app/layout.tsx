import { Suspense } from "react";

import type { Metadata, Viewport } from "next";
import { IBM_Plex_Sans_KR } from "next/font/google";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { TabBar } from "@/components/ui/TabBar";

import "./globals.css";

const plex = IBM_Plex_Sans_KR({
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
  variable: "--font-plex",
  display: "swap",
  // 한글 글꼴은 파일이 수십 개로 쪼개져, 미리 불러오기를 켜면 응답의 Link 헤더가 6KB 가까이 된다.
  // DSM 역방향 프록시(nginx)의 기본 헤더 버퍼(4KB)를 넘으면 모든 페이지가 502가 된다.
  preload: false,
});

export const metadata: Metadata = {
  title: "로또 추첨",
  description:
    "역대 당첨번호 통계로 매일 추천 번호 5줄을 만들고, 추첨 결과와 맞춰 봅니다.",
  appleWebApp: {
    capable: true,
    title: "로또 추첨",
    statusBarStyle: "default",
  },
};

// globals.css 의 --slip(라이트), --paper(다크) 값.
export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#c42f4f" },
    { media: "(prefers-color-scheme: dark)", color: "#10151f" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${plex.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col font-sans">
        <QueryProvider>
          <div className="flex flex-1 flex-col pb-20">{children}</div>
          {/* cacheComponents에서 usePathname은 동적 경로에 Suspense가 필요하다. */}
          <Suspense fallback={null}>
            <TabBar />
          </Suspense>
        </QueryProvider>
      </body>
    </html>
  );
}
