#!/usr/bin/env bash
# 백업 덤프를 임시 MySQL 컨테이너(포트 비공개)에 복구하고, 운영 DB와 테이블별 행 수를 비교한다.
# 운영 DB는 읽기만 한다. 끝나면 임시 컨테이너를 지운다.
#   sudo bash deploy/restore-check.sh backups/lottery-YYYYMMDD-HHMMSS.sql.gz
# 덤프 직후에 실행한다. 덤프 뒤에 운영 DB가 바뀌면(수집, 로그인 등) 그만큼 행 수가 달라진다.
# 환경변수(선택): ENV_FILE (기본 deploy/.env), COMPOSE_PROJECT, MYSQL_IMAGE (기본 mysql:8.4)
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"

if [[ $# -ne 1 || ! -f "$1" ]]; then
	echo "사용법: $0 <lottery-*.sql.gz>" >&2
	exit 2
fi
dump="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4}"
compose=(docker compose -f "$SCRIPT_DIR/compose.yml" --env-file "$ENV_FILE")
if [[ -n "${COMPOSE_PROJECT:-}" ]]; then
	compose+=(-p "$COMPOSE_PROJECT")
fi

scratch="lp-restore-check-$$"
work="$(mktemp -d)"
cleanup() {
	docker rm -f "$scratch" > /dev/null 2>&1 || true
	rm -rf "$work"
}
trap cleanup EXIT

# 테이블별 "이름<TAB>행 수". 비밀번호는 컨테이너 환경변수로만 다룬다.
# shellcheck disable=SC2016
count_sql='export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
for t in $(mysql -uroot -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema = \"$MYSQL_DATABASE\" ORDER BY 1"); do
	printf "%s\t%s\n" "$t" "$(mysql -uroot -N -e "SELECT COUNT(*) FROM \`$t\`" "$MYSQL_DATABASE")"
done'

# 임시 비밀번호는 이 컨테이너 안에서만 쓰이고 함께 사라진다.
docker run -d --name "$scratch" --network none \
	-e MYSQL_ROOT_PASSWORD="restore-check-$$" -e MYSQL_DATABASE=lottery \
	"$MYSQL_IMAGE" --skip-name-resolve --performance-schema=OFF > /dev/null

echo "임시 MySQL 준비 중..."
# 초기화 중에는 소켓 전용 임시 서버가 뜨므로 TCP(127.0.0.1) 응답을 기다린다.
for _ in $(seq 1 60); do
	if docker exec "$scratch" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h 127.0.0.1 -N -e "SELECT 1"' > /dev/null 2>&1; then
		ready=1
		break
	fi
	sleep 3
done
if [[ -z "${ready:-}" ]]; then
	echo "임시 MySQL이 준비되지 않았습니다." >&2
	exit 1
fi

echo "복구 중: $dump"
gzip -dc "$dump" | docker exec -i "$scratch" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'

export LC_ALL=C
"${compose[@]}" exec -T mysql sh -c "$count_sql" | sort > "$work/live.tsv"
docker exec "$scratch" sh -c "$count_sql" | sort > "$work/restored.tsv"

# join 대신 awk만 쓴다 (DSM 기본 도구로 충분하도록).
awk -F '\t' '
	NR == FNR { live[$1] = $2; names[$1] = 1; next }
	{ restored[$1] = $2; names[$1] = 1 }
	END {
		printf "%-28s %10s %10s\n", "TABLE", "LIVE", "RESTORED"
		for (t in names) {
			l = (t in live) ? live[t] : "-"
			r = (t in restored) ? restored[t] : "-"
			printf "%-28s %10s %10s%s\n", t, l, r, (l == r ? "" : "  <- 다름")
		}
	}' "$work/live.tsv" "$work/restored.tsv" | { IFS= read -r header; echo "$header"; sort; }

if [[ "$(cat "$work/live.tsv")" == "$(cat "$work/restored.tsv")" ]]; then
	echo "결과: 모든 테이블의 행 수가 같습니다."
else
	echo "결과: 행 수가 다른 테이블이 있습니다. 덤프 이후 운영 DB 변경인지 확인하세요." >&2
	exit 1
fi
