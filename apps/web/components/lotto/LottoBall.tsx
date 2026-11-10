/** 동행복권 공 색 구간. 표시용 매핑일 뿐 번호를 검증·계산하지 않는다. */
function ballColor(value: number) {
  if (value <= 10) return "bg-ball-yellow";
  if (value <= 20) return "bg-ball-blue";
  if (value <= 30) return "bg-ball-red";
  if (value <= 40) return "bg-ball-gray";
  return "bg-ball-green";
}

const SIZES = {
  sm: "size-7 text-xs",
  md: "size-9 text-sm",
  lg: "size-9 text-sm sm:size-11 sm:text-base",
} as const;

/**
 * missed면 채점한 용지처럼 색을 빼고 테두리만 남긴다. bonus면 색을 두고 용지 빨강 테두리를 두른다.
 * 맞았는지, 보너스인지는 서버가 알려 준 대로만 쓴다.
 */
export function LottoBall({
  value,
  size = "md",
  missed = false,
  bonus = false,
}: {
  value: number;
  size?: keyof typeof SIZES;
  missed?: boolean;
  bonus?: boolean;
}) {
  const tone = missed
    ? "border border-rule font-medium text-muted"
    : `font-bold text-ball-ink ${ballColor(value)}`;
  const ring = bonus
    ? "ring-2 ring-slip ring-offset-2 ring-offset-surface"
    : "";
  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-full tabular-nums ${tone} ${ring} ${SIZES[size]}`}
    >
      {value}
    </span>
  );
}

/** 당첨번호 6개와 보너스 번호를 한 줄로 그린다. fill이면 가로 폭을 꽉 채워 고르게 벌린다. */
export function DrawnBalls({
  numbers,
  bonus,
  size = "md",
  fill = false,
}: {
  numbers: number[];
  bonus: number;
  size?: keyof typeof SIZES;
  fill?: boolean;
}) {
  return (
    <span
      className={`flex items-center ${fill ? "w-full justify-between" : "flex-wrap gap-1.5"}`}
    >
      {numbers.map((n) => (
        <LottoBall key={n} value={n} size={size} />
      ))}
      <span className="px-0.5 text-muted">
        <span aria-hidden="true">+</span>
        <span className="sr-only">보너스</span>
      </span>
      <LottoBall value={bonus} size={size} />
    </span>
  );
}
