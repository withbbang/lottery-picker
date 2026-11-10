"use client";

const PAGE_WINDOW = 5;
const PAGE_BUTTON =
  "flex size-9 items-center justify-center rounded-md font-medium hover:bg-surface disabled:opacity-30 disabled:hover:bg-transparent";

/** 현재 페이지가 속한 5개 묶음(1~5, 6~10…)의 0부터 센 페이지 번호. */
function pageWindow(page: number, totalPages: number) {
  const start = Math.floor(page / PAGE_WINDOW) * PAGE_WINDOW;
  const end = Math.min(start + PAGE_WINDOW, totalPages);
  return Array.from({ length: end - start }, (_, i) => start + i);
}

/**
 * 0부터 세는 페이지 이동 막대. 화면에는 1부터 보인다.
 * page는 응답의 페이지가 아니라 요청한 페이지(호출 쪽 상태)를 넘긴다. 다음 페이지를 불러오는 동안에도
 * 연달아 누른 이동이 쌓이고 현재 표시가 바로 바뀐다.
 */
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  return (
    <nav
      aria-label="페이지"
      className="flex items-center justify-center gap-0.5 text-sm tabular-nums"
    >
      <button
        type="button"
        aria-label="첫 페이지"
        className={PAGE_BUTTON}
        disabled={page === 0}
        onClick={() => onChange(0)}
      >
        <span aria-hidden="true">«</span>
      </button>
      <button
        type="button"
        aria-label="이전 페이지"
        className={PAGE_BUTTON}
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        <span aria-hidden="true">‹</span>
      </button>
      {pageWindow(page, totalPages).map((p) =>
        p === page ? (
          <span
            key={p}
            aria-current="page"
            className="flex size-9 items-center justify-center rounded-md bg-slip font-semibold text-on-slip"
          >
            {p + 1}
          </span>
        ) : (
          <button
            key={p}
            type="button"
            className={`${PAGE_BUTTON} text-muted hover:text-ink`}
            onClick={() => onChange(p)}
          >
            {p + 1}
          </button>
        ),
      )}
      <button
        type="button"
        aria-label="다음 페이지"
        className={PAGE_BUTTON}
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        <span aria-hidden="true">›</span>
      </button>
      <button
        type="button"
        aria-label="마지막 페이지"
        className={PAGE_BUTTON}
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(totalPages - 1)}
      >
        <span aria-hidden="true">»</span>
      </button>
    </nav>
  );
}
