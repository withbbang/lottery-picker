import { Suspense } from "react";

import type { Metadata } from "next";
import { IBM_Plex_Sans_KR } from "next/font/google";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { TabBar } from "@/components/ui/TabBar";

import "./globals.css";

const plex = IBM_Plex_Sans_KR({
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
  variable: "--font-plex",
  display: "swap",
});

export const metadata: Metadata = {
  title: "로또 번호 추첨",
  description:
    "역대 당첨번호 통계로 매일 추천 번호 5줄을 만들고, 추첨 뒤 채점합니다.",
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
