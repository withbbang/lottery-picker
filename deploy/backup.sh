#!/usr/bin/env bash
# lottery DB를 gzip 덤프로 남기고 최근 KEEP개(기본 14)만 보관한다. 하루 1회 실행한다.
#   sudo bash deploy/backup.sh
# 환경변수(선택):
#   BACKUP_DIR    덤프 위치 (기본: 저장소 최상위의 backups/)
#   KEEP          보관 개수 (기본: 14)
#   ENV_FILE      compose용 .env (기본: deploy/.env)
#   COMPOSE_PROJECT  compose 프로젝트 이름 (기본: compose.yml의 name)
# 비밀번호는 명령행에 싣지 않는다. 컨테이너 안의 MYSQL_ROOT_PASSWORD를 MYSQL_PWD로 넘긴다.
set -euo pipefail

# DSM 작업 스케줄러는 PATH가 짧다. Synology의 docker는 /usr/local/bin에 있다.
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
KEEP="${KEEP:-14}"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"

compose=(docker compose -f "$SCRIPT_DIR/compose.yml" --env-file "$ENV_FILE")
if [[ -n "${COMPOSE_PROJECT:-}" ]]; then
	compose+=(-p "$COMPOSE_PROJECT")
fi

case "$KEEP" in
	'' | *[!0-9]* | 0) echo "KEEP은 1 이상의 정수여야 합니다: $KEEP" >&2; exit 1 ;;
esac

umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
target="$BACKUP_DIR/lottery-$stamp.sql.gz"
partial="$target.partial"
trap 'rm -f "$partial"' EXIT

# 작은따옴표 안의 $MYSQL_*는 컨테이너 안에서 풀린다. 호스트 ps에는 변수 이름만 보인다.
"${compose[@]}" exec -T mysql sh -c \
	'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --no-tablespaces --routines --triggers --hex-blob --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
	| gzip -9 > "$partial"

# 정상 종료한 mysqldump는 마지막 줄에 "-- Dump completed"를 남긴다.
if ! gzip -dc "$partial" | tail -n 1 | grep -q '^-- Dump completed'; then
	echo "덤프가 끝까지 기록되지 않았습니다: $partial" >&2
	exit 1
fi
mv "$partial" "$target"
trap - EXIT

# 이름에 시각이 들어 있어 사전순 = 시간순. 오래된 것부터 지운다.
dumps=()
while IFS= read -r f; do
	dumps+=("$f")
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'lottery-*.sql.gz' | sort)
excess=$(( ${#dumps[@]} - KEEP ))
for (( i = 0; i < excess; i++ )); do
	rm -f -- "${dumps[$i]}"
done

echo "백업 완료: $target ($(du -h "$target" | cut -f1)), 보관 $(( ${#dumps[@]} < KEEP ? ${#dumps[@]} : KEEP ))개"
