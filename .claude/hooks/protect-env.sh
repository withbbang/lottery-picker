#!/usr/bin/env bash
# PreToolUse(Edit|Write): .env* 파일 수정 차단.
# exit 2 → 도구 호출 차단, stderr 내용이 Claude 에게 전달됨.
# .env.example 은 허용.

input=$(cat)
file=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
[ -z "$file" ] && exit 0

base=$(basename "$file")
case "$base" in
  .env.example) exit 0 ;;
  .env|.env.*)
    echo "차단: '$file' 은 환경변수 파일입니다. 값은 사용자가 직접 넣고, 키 목록만 .env.example 에 적으세요." >&2
    exit 2
    ;;
esac
exit 0
