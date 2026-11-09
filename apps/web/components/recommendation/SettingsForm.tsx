"use client";

import { useId, useState, type FormEvent } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import type {
  RecommendationSettingsRequest,
  RecommendationSettingsResponse,
} from "@/types/api/recommendation";

const SETTINGS_PATH = "/api/v1/recommendation-settings";
const SETTINGS_KEY = ["recommendation-settings"];
const FALLBACK_ERROR = "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const INPUT_CLASS =
  "rounded-md border border-rule bg-surface px-3 py-2.5 tabular-nums placeholder:text-muted/70";
const INVALID_INPUT = "번호는 쉼표나 공백으로 구분한 숫자로 입력해 주세요.";

function formatNumbers(numbers: number[]): string {
  return numbers.join(", ");
}

/** 입력 형식만 본다. 범위·겹침 같은 규칙은 서버가 검증한다. 숫자가 아니면 null. */
function parseNumbers(text: string): number[] | null {
  const tokens = text.split(/[\s,]+/).filter(Boolean);
  return tokens.every((token) => /^\d+$/.test(token))
    ? tokens.map(Number)
    : null;
}

function saveSettings(
  request: RecommendationSettingsRequest,
): Promise<RecommendationSettingsResponse> {
  return apiFetch<RecommendationSettingsResponse>(SETTINGS_PATH, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

export function SettingsForm() {
  const { data, isPending } = useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: () => apiFetch<RecommendationSettingsResponse>(SETTINGS_PATH),
  });

  if (isPending) {
    return <p className="text-muted">설정을 불러오는 중…</p>;
  }
  // 백그라운드 재조회 실패는 데이터가 남아 있다. 폼을 내리면 입력 중인 값이 사라진다.
  if (!data) {
    return (
      <p role="alert" className="text-danger">
        설정을 불러오지 못했습니다. 잠시 후 새로고침해 주세요.
      </p>
    );
  }
  return <SettingsFields initial={data} />;
}

function SettingsFields({
  initial,
}: {
  initial: RecommendationSettingsResponse;
}) {
  const queryClient = useQueryClient();
  const fixedHint = useId();
  const excludedHint = useId();
  const [fixed, setFixed] = useState(formatNumbers(initial.fixed));
  const [excluded, setExcluded] = useState(formatNumbers(initial.excluded));
  const [inputError, setInputError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: saveSettings,
    onSuccess: (saved) => {
      setFixed(formatNumbers(saved.fixed));
      setExcluded(formatNumbers(saved.excluded));
      return queryClient.invalidateQueries({ queryKey: SETTINGS_KEY });
    },
  });

  const clearMessages = () => {
    setInputError(null);
    save.reset();
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fixedNumbers = parseNumbers(fixed);
    const excludedNumbers = parseNumbers(excluded);
    if (fixedNumbers === null || excludedNumbers === null) {
      save.reset();
      setInputError(INVALID_INPUT);
      return;
    }
    setInputError(null);
    save.mutate({ fixed: fixedNumbers, excluded: excludedNumbers });
  };

  const errorMessage =
    inputError ??
    (save.isError
      ? save.error instanceof ApiError
        ? save.error.message
        : FALLBACK_ERROR
      : null);

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <label htmlFor={`${fixedHint}-input`} className="font-semibold">
          고정 번호
        </label>
        <p id={fixedHint} className="text-sm text-muted">
          모든 줄에 넣을 번호입니다. 5개까지 지정할 수 있습니다.
        </p>
        <input
          id={`${fixedHint}-input`}
          type="text"
          inputMode="numeric"
          aria-describedby={fixedHint}
          placeholder="예: 7, 23"
          value={fixed}
          onChange={(event) => {
            setFixed(event.target.value);
            clearMessages();
          }}
          className={INPUT_CLASS}
        />
      </div>
      <div className="flex flex-col gap-2">
        <label htmlFor={`${excludedHint}-input`} className="font-semibold">
          제외 번호
        </label>
        <p id={excludedHint} className="text-sm text-muted">
          추천에서 제외할 번호입니다.
        </p>
        <input
          id={`${excludedHint}-input`}
          type="text"
          inputMode="numeric"
          aria-describedby={excludedHint}
          placeholder="예: 1, 45"
          value={excluded}
          onChange={(event) => {
            setExcluded(event.target.value);
            clearMessages();
          }}
          className={INPUT_CLASS}
        />
      </div>
      <p className="text-sm text-muted">
        번호는 쉼표나 공백으로 구분합니다. 바꾼 설정은 다음 추천부터 적용됩니다.{" "}
        <span className="whitespace-nowrap">
          (예시:{" "}
          <span className="font-medium text-ink tabular-nums">3, 17, 42</span>{" "}
          또는{" "}
          <span className="font-medium text-ink tabular-nums">3 17 42</span>)
        </span>
      </p>
      <button
        type="submit"
        disabled={save.isPending}
        className="rounded-md bg-slip px-4 py-2.5 font-semibold text-on-slip hover:bg-slip/90 disabled:opacity-40"
      >
        {save.isPending ? "저장하는 중…" : "설정 저장"}
      </button>
      {errorMessage !== null && (
        <p role="alert" className="text-sm text-danger">
          {errorMessage}
        </p>
      )}
      {save.isSuccess && (
        <p role="status" className="text-sm font-medium text-slip">
          설정을 저장했습니다.
        </p>
      )}
    </form>
  );
}
