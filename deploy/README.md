# 집 서버 배포 (Synology NAS)

웹·API·MySQL을 NAS의 Docker Compose로 실행하고, HTTPS는 DSM 역방향 프록시가 받는다.

```
휴대폰 ──HTTPS 443──▶ 공유기 ──▶ DSM 역방향 프록시(Let's Encrypt)
                                      │ http://127.0.0.1:13000
                                      ▼
                         web (127.0.0.1:13000 → 3000) ──/api/*──▶ api:8080 ──▶ mysql:3306
                                                       (Compose 내부 네트워크, 호스트 포트 없음)
```

- 공유기에서 NAS로 넘기는 포트는 **80, 443 두 개뿐**이다. 13000, 3306, 8080, DSM 관리 포트(5000/5001), SSH(22)는 인터넷에 열지 않는다.
- web은 NAS의 루프백(`127.0.0.1:13000`)에만 묶여 네트워크 쪽으로는 공개되지 않는다. api, mysql은 호스트 포트가 아예 없다.
- 모든 컨테이너는 `restart: unless-stopped`, `TZ=Asia/Seoul`.

| 파일 | 역할 |
|---|---|
| `compose.yml` | mysql:8.4, api, web 구성 (프로젝트 이름 `lottery-picker`) |
| `.env.example` | 키 목록. 실제 값은 서버의 `deploy/.env`(Git 제외) |
| `backup.sh` | 일 1회 `mysqldump` → `backups/lottery-*.sql.gz`, 최근 14개 보관 |
| `restore-check.sh` | 덤프를 임시 MySQL에 복구해 운영 DB와 테이블별 행 수 비교 |

아래 명령은 NAS에서 `/volume1/docker/lottery-picker`에 있다고 가정한다. DSM에서는 docker 명령에 `sudo`가 필요하다. 반복해서 쓰니 별칭을 둔다.

```bash
cd /volume1/docker/lottery-picker
alias dc='sudo docker compose -f deploy/compose.yml --env-file deploy/.env'
```

---

## 1. 처음 배포

### 1-1. 코드 보내기 (Mac에서)

NAS에는 git이 없다. 배포는 항상 `master`를 묶어서 보낸다. 피처 → `dev` → `master` 병합이 끝난 뒤에 한다.

```bash
# Mac, 저장소 루트
git archive --format=tar.gz -o lottery-picker.tar.gz master
scp -O lottery-picker.tar.gz <NAS계정>@<NAS 내부 IP>:~/
```

- DSM 제어판 → 터미널 및 SNMP에서 SSH를 켜야 한다. `-O`는 DSM에서 SFTP가 꺼져 있어도 되는 예전 scp 방식이다.

### 1-2. 풀기 (NAS에서)

```bash
ssh <NAS계정>@<NAS 내부 IP>
sudo mkdir -p /volume1/docker/lottery-picker
sudo tar -xzf ~/lottery-picker.tar.gz -C /volume1/docker/lottery-picker
rm ~/lottery-picker.tar.gz
cd /volume1/docker/lottery-picker
```

### 1-3. `deploy/.env` 만들기

```bash
sudo cp deploy/.env.example deploy/.env
sudo chmod 600 deploy/.env
sudo vi deploy/.env
```

| 키 | 값 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 새로 만든 긴 비밀번호 (백업·복구에만 씀) |
| `DB_USERNAME` | `lottery` 그대로 (root 불가) |
| `DB_PASSWORD` | 새로 만든 긴 비밀번호 |
| `DB_URL` | 비워 둔다 (`jdbc:mysql://mysql:3306/lottery`) |
| `TOTP_SECRET` | 1-4에서 채운다. 지금은 비워 둔다 |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | 텔레그램 봇 값 |
| `SCHEDULING_ENABLED` | 비워 둔다 (운영은 기본 `true`) |

- 비밀번호는 영문·숫자로만 만든다 (따옴표, `$`, 공백은 Compose 치환과 충돌한다). 예: `openssl rand -hex 24`.
- `MYSQL_*`, `DB_PASSWORD`는 **mysql 볼륨을 처음 만들 때만** 적용된다. 나중에 `.env`만 바꾸면 DB 비밀번호는 그대로라 api가 접속에 실패한다 (9장 참고).
- `SPRING_PROFILES_ACTIVE=prod`는 compose에 고정되어 있다. 프로파일이 빠지면 앱이 `local`(H2, 인증 꺼짐)로 뜨므로 지우지 않는다.

### 1-4. OTP 첫 등록 (기동 전에)

`TOTP_SECRET`이 비어 있으면 api가 기동하지 않는다. 먼저 이미지를 빌드하고, 같은 이미지로 일회성 시크릿 생성기(`com.lotterypicker.auth.TotpSetup`, Gradle의 `totpSetup` 태스크와 같은 클래스)를 실행한다. DB에는 접속하지 않는다.

```bash
dc build
dc run --rm --no-deps --entrypoint java api -cp app.jar com.lotterypicker.auth.TotpSetup
```

출력:

```
Picked up JAVA_TOOL_OPTIONS: ...        ← JVM 안내 문구, 무시
TOTP_SECRET=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
otpauth://totp/lottery-picker?secret=XXXX...&issuer=lottery-picker
```

1. `TOTP_SECRET=...` 줄의 값을 `deploy/.env`의 `TOTP_SECRET`에 넣는다.
2. OTP 앱(Google Authenticator 등)에 등록한다. 둘 중 하나:
   - **설정 키 직접 입력**: 계정 이름 `lottery-picker`, 키는 위 시크릿, 유형 "시간 기준".
   - QR로 등록하려면 Mac에서 `brew install qrencode` 후 `qrencode -t ansiutf8 'otpauth://...'`로 터미널에 QR을 띄운다. 시크릿을 온라인 QR 생성 사이트에 넣지 않는다.
3. 출력이 남은 터미널은 닫는다. 시크릿은 `deploy/.env`와 OTP 앱에만 있어야 한다.

시크릿을 잃어버렸거나 바꿀 때도 같은 절차 후 `dc up -d`를 하면 된다. 시크릿이 바뀌면 기동 시 이전 세션과 잠금이 모두 지워진다.

### 1-5. 기동

```bash
dc up -d --build
dc ps                              # mysql, api가 (healthy), web이 Up
curl -s localhost:13000/api/v1/health    # {"success":true,"data":{"status":"UP"}}
curl -s -o /dev/null -w '%{http_code}\n' localhost:13000/api/v1/rounds   # 401
```

### 1-6. 첫 수집 (약 1시간)

운영 DB는 빈 상태로 시작한다. 로컬 H2 데이터는 옮기지 않는다. api가 기동하면 전 회차를 동행복권에서 받아 온다 (요청 간격 2초, 전 회차가 1,200회가 넘어 1시간 정도).

```bash
dc logs -f api          # Ctrl+C로 빠져나와도 컨테이너는 계속 돈다
```

- `기동 수집 시작` → `회차 수집: 남은 N건`이 줄어듦 → `기동 수집 완료`.
- 수집 중에도 로그인과 화면은 쓸 수 있다. 통계·시뮬레이션은 수집이 끝난 뒤에 채워진다.
- 중간에 재시작돼도 남은 회차부터 이어서 받는다.

---

## 2. HTTPS 연결 (DSM 역방향 프록시)

순서대로 한다. 인증서 발급에 80 포트가 필요하므로 공유기 설정이 먼저다.

### 2-1. DNS

도메인 관리 화면에서 A 레코드 `lottery-picker.o-r.kr` → 집의 고정 공인 IP. 확인:

```bash
dig +short lottery-picker.o-r.kr     # 공인 IP가 나와야 한다
```

### 2-2. 공유기 포트 포워딩

| 외부 포트 | 내부 | 용도 |
|---|---|---|
| TCP 80 | NAS 내부 IP:80 | Let's Encrypt 발급·갱신 (HTTP-01) |
| TCP 443 | NAS 내부 IP:443 | 서비스 |

이 두 개 외에는 추가하지 않는다. 기존에 5000/5001/22 등을 열어 둔 규칙이 있으면 이번 기회에 닫는 것을 권한다.

### 2-3. 인증서

DSM 제어판 → 보안 → 인증서 → 추가 → 새 인증서 추가 → **Let's Encrypt에서 인증서 얻기**

- 도메인 이름 `lottery-picker.o-r.kr`, 이메일 입력. 기본 인증서로 지정하지 않아도 된다.
- 발급되면 DSM이 만료 전에 자동 갱신한다 (그래서 80 포워딩을 유지한다).

### 2-4. 역방향 프록시 규칙

DSM 제어판 → 로그인 포털 → 고급 → 역방향 프록시 → 생성

| 항목 | 값 |
|---|---|
| 설명 | `lottery-picker` |
| 소스 프로토콜 / 호스트 이름 / 포트 | HTTPS / `lottery-picker.o-r.kr` / 443 |
| HSTS 활성화 | 켜도 된다 (선택) |
| 대상 프로토콜 / 호스트 이름 / 포트 | HTTP / `127.0.0.1` / `13000` |

- **사용자 지정 머리글은 추가하지 않는다.** 이 앱은 WebSocket을 쓰지 않는다.
- DSM nginx는 대상에 `X-Forwarded-For`, `X-Real-IP` 같은 전달 헤더를 붙인다. 앱은 이 값을 쓰지 않으므로 따로 설정할 것이 없다.

그다음 제어판 → 보안 → 인증서 → **설정**에서 `lottery-picker.o-r.kr` 역방향 프록시 항목의 인증서를 2-3에서 발급한 것으로 바꾼다.

### 2-5. 세션 쿠키와 프록시

- 로그인 쿠키 `LP_SESSION`은 `Secure; HttpOnly; SameSite=Strict`로 내려간다. `Secure`는 요청이 HTTPS인지 보고 정하는 것이 아니라 설정(`app.auth.cookie-secure: true`)으로 항상 켜져 있다. 그래서 프록시 뒤의 api가 평문 HTTP를 받아도 쿠키는 그대로 `Secure`로 발급되고, 브라우저는 HTTPS 주소에서 받으므로 정상 저장한다. `X-Forwarded-Proto` 처리는 필요 없다.
- 반대로 `http://`로 접속하면 쿠키가 저장·전송되지 않아 로그인이 유지되지 않는다. 항상 `https://lottery-picker.o-r.kr`로 들어간다.

### 2-6. 확인 (Step 5)

- 휴대폰 **와이파이를 끄고** LTE/5G로 `https://lottery-picker.o-r.kr` 접속 → 자물쇠 표시, OTP 로그인 성공, 새로고침 후에도 로그인 유지.
  - 집 와이파이에서는 공유기가 NAT 루프백을 지원하지 않으면 도메인 접속이 안 될 수 있다. 바깥 망으로 확인한다.
- 바깥 망에서 아래가 **열리지 않아야** 한다: `http://<공인 IP>:13000`, `:3306`, `:8080`, `:5000`, `:5001`, `:22`.
- 바깥 망에서 아래 주소에 **DSM 로그인 화면이나 DSM 관련 페이지가 보이지 않아야** 한다 (2-7).
  - `http://<공인 IP>/`, `https://<공인 IP>/` (도메인 없이 IP로)
  - `http://lottery-picker.o-r.kr/` (80, 인증서 발급용으로 열어 둔 포트)

### 2-7. DSM 보호 (반드시)

80·443을 NAS로 넘기면, Host가 `lottery-picker.o-r.kr`이 아닌 요청(IP 직접 접속, 스캐너 등)은 모두 DSM의 기본 nginx가 받는다. 80과 443 둘 다 해당한다. 그래서 아래는 조건 없이 모두 해 둔다.

1. **관리자 계정 2단계 인증**: 관리자 권한이 있는 모든 계정에서 개인 → 보안 → 2단계 인증을 켠다.
2. **자동 차단**: 제어판 → 보안 → 보호 → 자동 차단 사용 (예: 5분 안에 로그인 10회 실패 시 차단).
3. **기본 `admin` 계정 비활성화**: 제어판 → 사용자 및 그룹 → `admin` → 비활성화. 이미 비활성이면 그대로 둔다.
4. **방화벽**: 제어판 → 보안 → 방화벽 → 방화벽 사용, 프로필 규칙을 위에서부터 이 순서로 만든다.

   | 순서 | 포트 | 소스 IP | 동작 |
   |---|---|---|---|
   | 1 | 모두 | 집 LAN 서브넷 (예: `192.168.0.0/24`, 공유기 설정에서 확인) | 허용 |
   | 2 | TCP 80, 443 | 모두 | 허용 |
   | — | 규칙에 없는 요청 | | 거부 |

   - 결과적으로 DSM 5000/5001, SSH 22, NAS의 다른 서비스(3000, 8080 등)는 LAN에서만 열린다.
   - 서브넷을 잘못 넣으면 NAS에 못 들어갈 수 있다. LAN에서 DSM 창을 하나 열어 둔 채로 적용하고, 새 창으로 접속되는지 확인한 뒤 닫는다.
5. **DSM 로그인 페이지가 80/443에 나오는지 확인**: 제어판 → 로그인 포털 → DSM 탭(웹 서비스 포트, 사용자 지정 도메인, HTTP→HTTPS 자동 리디렉션)과 고급 탭을 확인한다. DSM 버전·설치 패키지(Web Station 등)에 따라 다르므로 동작을 짐작하지 말고 2-6의 바깥 망 확인으로 판단한다.
   - IP로 접속했을 때 DSM 로그인 화면이 뜨거나 `:5000`/`:5001`로 리디렉션되면, DSM 탭의 사용자 지정 도메인·리디렉션 설정을 끄고 다시 확인한다.
   - 그래도 DSM 페이지가 보이면 1~4가 켜져 있는지 다시 확인한다. 방화벽 4번이 있으면 리디렉션돼도 5000/5001은 바깥에서 열리지 않는다.

---

## 3. 다시 배포 (업데이트)

```bash
# Mac
git archive --format=tar.gz -o lottery-picker.tar.gz master
scp -O lottery-picker.tar.gz <NAS계정>@<NAS 내부 IP>:~/

# NAS
cd /volume1/docker/lottery-picker
sudo tar -xzf ~/lottery-picker.tar.gz -C /volume1/docker/lottery-picker && rm ~/lottery-picker.tar.gz
dc up -d --build
sudo docker image prune -f         # 이전 빌드의 이름 없는 이미지 정리
```

- 압축 해제는 Git에 있는 파일만 덮어쓴다. `deploy/.env`와 `backups/`는 그대로 남는다.
- Git에서 지운 파일은 NAS에 남는다. 문제가 되면 `.env`·`backups/`를 제외하고 지운 뒤 다시 푼다.
- DB 스키마는 api 기동 시 Flyway가 올린다. 큰 변경 전에는 `sudo bash deploy/backup.sh`를 먼저 돌린다.

---

## 4. 백업 (Step 6)

`deploy/backup.sh`가 하는 일:

- `docker compose exec`로 mysql 컨테이너 안에서 `mysqldump --single-transaction`(운영 중에도 일관된 스냅숏) → gzip.
- 비밀번호는 명령행에 싣지 않는다. 컨테이너의 `MYSQL_ROOT_PASSWORD`를 컨테이너 안에서 `MYSQL_PWD`로 넘기므로 호스트 `ps`에는 값이 보이지 않는다.
- `backups/lottery-YYYYMMDD-HHMMSS.sql.gz`로 저장. 전체 데이터가 들어 있으므로 폴더는 root 전용(700), 파일은 600이다. 보거나 읽을 때는 `sudo`를 붙인다. 끝까지 기록된 덤프(`-- Dump completed`)만 남기고, 최근 14개를 넘는 오래된 파일은 지운다.
- 실패하면 0이 아닌 코드로 끝나고 반쯤 쓴 파일은 지운다.

수동 실행:

```bash
sudo bash deploy/backup.sh
sudo ls -l backups/
```

### 매일 자동 실행 (DSM 작업 스케줄러)

DSM 제어판 → 작업 스케줄러 → 생성 → 예약된 작업 → 사용자 정의 스크립트

| 탭 | 값 |
|---|---|
| 일반 | 작업 `lottery-picker backup`, 사용자 **root** |
| 일정 | 매일, 04:30 (06:02 수집 작업과 겹치지 않게) |
| 작업 설정 | 스크립트가 비정상 종료될 때만 메일 보내기(선택) / 사용자 정의 스크립트: `bash /volume1/docker/lottery-picker/deploy/backup.sh` |

- 보관 위치·개수는 환경변수로 바꿀 수 있다: `BACKUP_DIR=/volume1/backup/lottery KEEP=30 bash .../backup.sh`.
- 덤프가 NAS 한 곳에만 있으면 디스크 고장 때 같이 잃는다. `backups/`를 Hyper Backup이나 다른 기기로 한 번 더 복사하는 것을 권한다.

### 복구 확인 (빈 DB에 복구해 행 수 비교)

추천 기록은 다시 만들 수 없으니, 백업이 실제로 복구되는지 한 번은 확인한다. 수집(1-6)이 끝난 뒤에 한다.

```bash
sudo bash deploy/backup.sh
sudo bash deploy/restore-check.sh            # 인자가 없으면 backups/의 가장 최근 덤프
```

특정 덤프를 확인하려면 `sudo ls backups/`로 이름을 본 뒤 `sudo bash deploy/restore-check.sh backups/lottery-<시각>.sql.gz`.
(`backups/`는 root 전용이라 `"$(ls backups/...)"`처럼 호출하는 셸에서 glob을 풀면 실패한다.)

`restore-check.sh`는 네트워크가 없는 임시 `mysql:8.4` 컨테이너를 띄워 덤프를 복구하고, 운영 DB와 테이블별 `COUNT(*)`를 나란히 출력한 뒤 임시 컨테이너를 지운다. 운영 DB는 읽기만 한다.

```
TABLE                              LIVE   RESTORED
auth_session                          1          1
...
lotto_round                        <회차 수>  <회차 수>
결과: 모든 테이블의 행 수가 같습니다.
```

덤프와 확인 사이에 로그인·수집·17:00 추천이 일어나면 그 테이블만 다를 수 있다. 다르면 바로 다시 `backup.sh` → `restore-check.sh`를 실행한다.

### 실제로 운영 DB를 덤프로 되돌릴 때

```bash
sudo bash deploy/backup.sh                       # 현재 상태도 남겨 둔다
sudo ls backups/                                 # 되돌릴 덤프 이름 확인
sudo -v                                          # 파이프 양쪽의 sudo가 비밀번호를 동시에 묻지 않게 미리 인증
dc stop api web
sudo gzip -dc backups/lottery-<복구할 시각>.sql.gz \
  | dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'
dc start api web
```

덤프에는 테이블별 `DROP TABLE IF EXISTS`가 들어 있어 같은 DB에 그대로 덮어쓴다.

---

## 5. 재부팅과 1주 운영 (Step 7)

- DSM 패키지 센터에서 Container Manager가 실행 중이면, 재부팅 후 Docker가 올라오면서 `unless-stopped` 컨테이너가 자동으로 다시 뜬다. `dc stop`으로 멈춘 컨테이너는 재부팅 후에도 멈춘 상태로 남는다.
- 재부팅 때는 Docker가 컨테이너를 한꺼번에 다시 띄우므로 `depends_on`의 `service_healthy`(mysql 준비 후 api 기동)가 지켜지지 않는다. api가 mysql보다 먼저 준비되면 DB 접속에 실패해 종료하고, 재시작 정책으로 다시 뜬다. **재부팅 직후 api가 한두 번 재시작되는 것은 정상이다.** 몇 분 뒤 `dc ps`에서 api가 `(healthy)`면 된다. 계속 재시작되면 `dc logs --tail 100 api`로 원인을 본다.
- 재부팅 후 확인: `dc ps`, `curl -s localhost:13000/api/v1/health`, 휴대폰에서 HTTPS 접속.
- 7일 동안 확인: 매일 17:00 텔레그램 추천 알림, 토요일 추첨 후 결과 알림, 비교 화면.

---

## 6. 자주 쓰는 명령

```bash
dc ps                      # 상태 (health 포함)
dc logs -f --tail 200 api  # api 로그
dc restart api             # .env를 바꾼 뒤에는 restart 말고 `dc up -d` (환경변수는 컨테이너 재생성 때 반영)
dc down                    # 정지·삭제 (볼륨은 남는다)
sudo docker stats --no-stream   # 메모리 사용량
```

**`dc down -v`는 DB 볼륨까지 지운다. 운영에서는 쓰지 않는다.**

---

## 7. 메모리

NAS 여유 메모리(약 4 GiB)를 고려해 상한을 두었다.

| 서비스 | 설정 | 로컬 측정(빈 DB, 기동 직후) |
|---|---|---|
| mysql | `innodb-buffer-pool-size=128M`, `performance-schema=OFF`, `mem_limit 768m` | 약 276 MiB |
| api | `-Xmx512m -XX:+UseSerialGC`, `mem_limit 768m` | 약 379 MiB |
| web | `mem_limit 384m` | 약 52 MiB |

커널이 메모리 제한을 지원하지 않으면 Docker가 경고만 내고 제한 없이 실행한다.

---

## 8. 로컬에서 같은 구성 확인 (개발용)

Mac에서 확인할 때는 프로젝트 이름과 `.env`를 따로 쓰고, 동행복권 호출을 막는다.

```bash
# 저장소 밖의 임시 파일에 키를 채운다. SCHEDULING_ENABLED=false,
# TOTP_SECRET=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ (RFC 6238 테스트 값)
docker compose -p lp-deploy-test -f deploy/compose.yml --env-file <임시 env> up -d --build
curl -s localhost:13000/api/v1/health
docker compose -p lp-deploy-test -f deploy/compose.yml --env-file <임시 env> down -v --rmi local
```

`backup.sh`, `restore-check.sh`도 `ENV_FILE=<임시 env> COMPOSE_PROJECT=lp-deploy-test BACKUP_DIR=<임시 폴더>`로 같은 스택에 돌릴 수 있다.

---

## 9. DB 비밀번호를 바꿀 때

`.env`만 바꾸면 이미 만들어진 DB 계정은 그대로다. DB에서 먼저 바꾼 뒤 `.env`를 맞춘다.

```bash
dc exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
# mysql> ALTER USER 'lottery'@'%' IDENTIFIED BY '<새 비밀번호>';
# root는: ALTER USER 'root'@'%' IDENTIFIED BY '...'; ALTER USER 'root'@'localhost' IDENTIFIED BY '...';
sudo vi deploy/.env        # DB_PASSWORD / MYSQL_ROOT_PASSWORD 수정
dc up -d                   # 바뀐 환경변수로 컨테이너 재생성
```
