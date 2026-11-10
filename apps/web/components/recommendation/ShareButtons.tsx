"use client";

import { useEffect, useRef, useState } from "react";

import { copyText, shareOrCopy, type ShareResult } from "@/lib/share";
import { formatLineNumbers } from "@/lib/share-text";

const FEEDBACK_MS = 2000;

const MESSAGES: Partial<Record<ShareResult, string>> = {
  copied: "복사됨",
  failed: "복사 실패",
};

/** 복사 결과 문구를 2초 보여 준다. 새 결과가 오면 2초를 다시 세고, 언마운트 뒤에는 아무것도 바꾸지 않는다. */
function useFeedback() {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      if (timer.current !== null) clearTimeout(timer.current);
    };
  }, []);

  function show(result: ShareResult) {
    if (!mounted.current) return;
    if (timer.current !== null) clearTimeout(timer.current);
    const text = MESSAGES[result];
    if (text === undefined) {
      setMessage(null);
      timer.current = null;
      return;
    }
    setMessage(text);
    timer.current = setTimeout(() => {
      timer.current = null;
      setMessage(null);
    }, FEEDBACK_MS);
  }

  return { message, show };
}

/** 화면 낭독기에 결과를 알리는 숨은 영역. 버튼 이름이 바뀌어도 읽히지 않을 수 있어 따로 둔다. */
function Status({ message }: { message: string | null }) {
  return (
    <span role="status" aria-live="polite" className="sr-only">
      {message}
    </span>
  );
}

/** 한 줄 번호 복사. 판매점에서 보고 마킹하거나 메모에 붙이는 용도. */
export function CopyLineButton({
  numbers,
  label,
}: {
  numbers: number[];
  label: string;
}) {
  const { message, show } = useFeedback();

  async function handleClick() {
    show(await copyText(formatLineNumbers(numbers)));
  }

  return (
    <>
      <button
        type="button"
        aria-label={`${label} 번호 복사`}
        onClick={handleClick}
        className="rounded-md border border-rule px-2 py-1 text-xs font-medium text-muted hover:border-ink/40 hover:text-ink"
      >
        {message ?? "복사"}
      </button>
      <Status message={message} />
    </>
  );
}

/** 추천 전체를 기기 공유 시트로, 없으면 클립보드로 보낸다. */
export function ShareSetButton({ text }: { text: string }) {
  const { message, show } = useFeedback();

  async function handleClick() {
    show(await shareOrCopy(text));
  }

  return (
    <>
      <button
        type="button"
        onClick={handleClick}
        className="w-full rounded-md border border-slip px-3 py-2 text-sm font-medium text-slip hover:bg-slip/5"
      >
        {message ?? "번호 공유"}
      </button>
      <Status message={message} />
    </>
  );
}
