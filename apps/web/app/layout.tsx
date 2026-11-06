import type { Metadata } from "next";

import { QueryProvider } from "@/components/providers/QueryProvider";

import "./globals.css";

export const metadata: Metadata = {
  title: "로또 번호 추첨",
  description: "역대 당첨번호 통계로 번호를 추첨합니다.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="flex min-h-full flex-col">
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
