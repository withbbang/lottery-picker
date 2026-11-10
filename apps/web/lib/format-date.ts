/** "2026-11-05" → "11월 5일". 날짜 계산 없이 문자열만 나눈다. */
export function monthDay(date: string) {
  const [, month, day] = date.split("-");
  return `${Number(month)}월 ${Number(day)}일`;
}
