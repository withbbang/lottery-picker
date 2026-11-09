import { ImageResponse } from "next/og";

// ImageResponse(Satori) 전용 그림이다. Tailwind를 쓸 수 없어 인라인 style을 쓴다 (web.md 예외).

const SLIP = "#c42f4f";
const BALL = "#fdfdfe";
const INK = "#1c2541";

/**
 * 로또 용지 빨강 위에 흰 공 하나. {@code ballRatio}는 한 변 대비 공 지름 비율.
 * maskable 아이콘은 가운데 80%만 보장되므로 공을 작게 그린다.
 */
export function appIconResponse(size: number, ballRatio: number) {
  const ball = Math.round(size * ballRatio);
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: SLIP,
        }}
      >
        <div
          style={{
            width: ball,
            height: ball,
            borderRadius: "50%",
            background: BALL,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: INK,
            fontSize: Math.round(ball * 0.34),
            fontWeight: 700,
            letterSpacing: -1,
          }}
        >
          6/45
        </div>
      </div>
    ),
    {
      width: size,
      height: size,
      headers: { "Cache-Control": "public, max-age=86400" },
    },
  );
}
