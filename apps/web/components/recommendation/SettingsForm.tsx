"use client";

import { useState, type FormEvent } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import type {
  RecommendationSettingsRequest,
  RecommendationSettingsResponse,
} from "@/types/api/recommendation";

const SETTINGS_PATH = "/api/v1/recommendation-settings";
const SETTINGS_KEY = ["recommendation-settings"];
const FALLBACK_ERROR = "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
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
    return <p>설정을 불러오는 중…</p>;
  }
  // 백그라운드 재조회 실패는 데이터가 남아 있다. 폼을 내리면 입력 중인 값이 사라진다.
  if (!data) {
    return <p role="alert">설정을 불러오지 못했습니다.</p>;
  }
  return <SettingsFields initial={data} />;
}

function SettingsFields({
  initial,
}: {
  initial: RecommendationSettingsResponse;
}) {
  const queryClient = useQueryClient();
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <label className="flex flex-col gap-2">
        <span className="font-medium">고정 번호</span>
        <input
          type="text"
          value={fixed}
          onChange={(event) => {
            setFixed(event.target.value);
            clearMessages();
          }}
          className="rounded-md border border-current/40 bg-transparent px-3 py-2 tabular-nums"
        />
      </label>
      <label className="flex flex-col gap-2">
        <span className="font-medium">제외 번호</span>
        <input
          type="text"
          value={excluded}
          onChange={(event) => {
            setExcluded(event.target.value);
            clearMessages();
          }}
          className="rounded-md border border-current/40 bg-transparent px-3 py-2 tabular-nums"
        />
      </label>
      <p className="text-sm opacity-70">
        쉼표나 공백으로 구분합니다. 고정 번호는 5개까지 지정할 수 있습니다.
      </p>
      <p className="text-sm font-medium">
        변경 내용은 다음 추천 생성부터 적용됩니다.
      </p>
      <button
        type="submit"
        disabled={save.isPending}
        className="rounded-md border border-current px-4 py-2 font-medium disabled:opacity-50"
      >
        저장
      </button>
      {errorMessage !== null && (
        <p role="alert" className="text-red-700 dark:text-red-400">
          {errorMessage}
        </p>
      )}
      {save.isSuccess && <p role="status">저장했습니다.</p>}
    </form>
  );
}
