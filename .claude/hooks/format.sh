#!/usr/bin/env bash
# PostToolUse(Edit|Write): apps/web 파일을 prettier 로 포맷.
# prettier 가 설치돼 있을 때만 동작하고, 실패해도 Claude 를 막지 않는다 (항상 exit 0).

input=$(cat)
file=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
[ -z "$file" ] && exit 0
[ -f "$file" ] || exit 0

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"

case "$file" in
  "$root"/apps/web/*)
    case "$file" in
      *.ts|*.tsx|*.js|*.jsx|*.mjs|*.css|*.json|*.md)
        prettier="$root/apps/web/node_modules/.bin/prettier"
        if [ -x "$prettier" ]; then
          (cd "$root/apps/web" && "$prettier" --write "$file" >/dev/null 2>&1) || true
        fi
        ;;
    esac
    ;;
esac
exit 0
