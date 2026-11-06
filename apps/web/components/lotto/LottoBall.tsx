export function LottoBall({ value }: { value: number }) {
  return (
    <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-current text-sm font-semibold tabular-nums">
      {value}
    </span>
  );
}
