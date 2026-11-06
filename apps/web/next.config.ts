import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // 값은 next build 시점에 routes manifest로 고정된다.
  async rewrites() {
    const apiInternalUrl = process.env.API_INTERNAL_URL;
    if (!apiInternalUrl) {
      throw new Error(
        "API_INTERNAL_URL 이 설정되지 않았습니다. 예: API_INTERNAL_URL=http://localhost:8080",
      );
    }
    return [
      {
        source: "/api/:path*",
        destination: `${apiInternalUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
