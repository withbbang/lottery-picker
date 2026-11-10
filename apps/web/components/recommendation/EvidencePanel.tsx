import Link from "next/link";

/** 근거 펼침 버튼. 오늘 추천 카드와 지난 추천 기록이 같이 쓴다. */
export function EvidenceButton({
  open,
  controls,
  onToggle,
}: {
  open: boolean;
  controls: string;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      aria-expanded={open}
      aria-controls={controls}
      onClick={onToggle}
      className="flex items-center gap-1 rounded-md border border-rule px-2 py-1 text-xs font-medium text-muted hover:border-ink/40 hover:text-ink aria-expanded:border-slip/40 aria-expanded:text-slip"
    >
      근거
      <span
        aria-hidden="true"
        className={`transition-transform motion-reduce:transition-none ${open ? "rotate-180" : ""}`}
      >
        ▾
      </span>
    </button>
  );
}

/** 근거 문구와, 통계 지표가 있는 줄이면 번호 통계로 가는 링크. */
export function EvidencePanel({
  id,
  open,
  evidence,
  statsHref,
}: {
  id: string;
  open: boolean;
  evidence: string[];
  statsHref: string | null;
}) {
  return (
    <div
      id={id}
      hidden={!open}
      className="flex w-full flex-col gap-2 rounded-md bg-paper px-3 py-2 text-sm text-muted"
    >
      <ul className="flex flex-col gap-1">
        {evidence.map((text, i) => (
          <li key={i}>{text}</li>
        ))}
      </ul>
      {statsHref !== null && (
        <Link
          href={statsHref}
          className="self-start rounded-sm font-medium text-slip underline decoration-slip/40 underline-offset-4 hover:decoration-slip"
        >
          통계에서 보기
        </Link>
      )}
    </div>
  );
}
