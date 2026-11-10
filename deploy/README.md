# 집 서버 배포 (Synology NAS)

웹·API·MySQL을 NAS의 Docker Compose로 실행하고, HTTPS는 DSM 역방향 프록시가 받는다. 이미지는 GitHub Actions가 빌드해 GHCR에 올리고, NAS는 받아서 실행만 한다 (`chat-app`과 같은 방식).

```
휴대폰 ──HTTPS 443──▶ 공유기 ──▶ DSM 역방향 프록시(Let's Encrypt)
                                      │ http://127.0.0.1:13000
                                      ▼
                         web (127.0.0.1:13000 → 3000) ──/api/*──▶ api:8080 ──▶ mysql:3306
                                                       (Compose 내부 네트워크, 호스트 포트 없음)

master push ──▶ GitHub Actions ──build/push──▶ ghcr.io/withbbang/lottery-picker-{api,web}:<SHA>
                      │ SSH (NAS_SSH_PORT)
                      ▼
                NAS: deploy/ 복사 → deploy/.env 에 IMAGE_TAG=<SHA> → compose pull · up -d
```

- 공유기에서 NAS로 넘기는 포트는 **80, 443, 배포용 SSH 포트(`NAS_SSH_PORT`)** 세 개다. SSH 포트는 `chat-app` 배포 때문에 이미 열려 있는 것을 같이 쓴다. 13000, 3306, 8080, DSM 관리 포트(5000/5001)는 인터넷에 열지 않는다.
- web은 NAS의 루프백(`127.0.0.1:13000`)에만 묶여 네트워크 쪽으로는 공개되지 않는다. api, mysql은 호스트 포트가 아예 없다.
- 모든 컨테이너는 `restart: unless-stopped`, `TZ=Asia/Seoul`. web은 api가 healthy가 된 뒤에 뜬다.
- NAS에는 소스 코드가 가지 않는다. `deploy/` 폴더만 복사된다.

| 파일 | 역할 |
|---|---|
| `compose.yml` | mysql:8.4, api, web 구성 (프로젝트 이름 `lottery-picker`). api·web은 GHCR 이미지 `:${IMAGE_TAG:-latest}` |
| `compose.build.yml` | 로컬 확인용으로 소스에서 빌드할 때만 겹치는 파일 (8장). NAS에서는 쓰지 않는다 |
| `.env.example` | 키 목록. 실제 값은 서버의 `deploy/.env`(Git 제외) |
| `backup.sh` | 일 1회 `mysqldump` → `backups/lottery-*.sql.gz`, 최근 14개 보관 |
| `restore-check.sh` | 덤프를 임시 MySQL에 복구해 운영 DB와 테이블별 행 수 비교 |
| `../.github/workflows/deploy.yml` | 빌드·푸시·NAS 배포 워크플로 |

NAS의 배치 (`/volume1/docker/lottery-picker`, 워크플로 변수 `NAS_DEPLOY_DIR`로 바꿀 수 있다):

```
/volume1/docker/lottery-picker/
├── deploy/          ← 워크플로가 매번 덮어쓴다 (Git에 있는 파일만)
│   ├── compose.yml, backup.sh, ...
│   └── .env         ← 직접 만든다. 워크플로는 IMAGE_TAG 줄만 고친다
└── backups/         ← backup.sh가 만든다. 워크플로는 건드리지 않는다
```

아래 명령은 NAS에서 이 폴더에 있다고 가정한다. DSM에서는 docker 명령에 `sudo`가 필요하다. 반복해서 쓰니 별칭을 둔다.

```bash
cd /volume1/docker/lottery-picker
alias dc='sudo docker compose -f deploy/compose.yml --env-file deploy/.env'
```

---

## 1. 처음 배포

### 1-1. NAS 준비 확인 (chat-app과 같은 배포 계정이면 이미 되어 있다)

`<배포계정>`은 chat-app의 `NAS_SSH_USER`와 같은 계정이다. 아래 세 가지를 확인만 하고, 안 되어 있는 것만 한다.

1. **SSH 키 로그인**: Mac에서 `ssh -p <NAS_SSH_PORT> <배포계정>@<NAS_HOST>`가 비밀번호 없이 들어가지는지. 안 되면 DSM 제어판 → 터미널 및 SNMP에서 SSH를 켜고, 배포용 공개키를 `~/.ssh/authorized_keys`에 넣는다.
2. **docker를 비밀번호 없이 sudo로**: Synology는 `docker.sock`이 `root:root 660`이라 sudo가 필요하다. 확인:
   ```bash
   sudo -n /usr/local/bin/docker ps      # 목록이 나오면 이미 되어 있다
   ```
   `a password is required`가 나오면 (visudo 없음):
   ```bash
   sudo sh -c 'echo "<배포계정> ALL=(root) NOPASSWD: /usr/local/bin/docker" > /etc/sudoers.d/<배포계정> && chmod 440 /etc/sudoers.d/<배포계정>'
   ```
3. **GHCR 로그인**: 워크플로가 처음 만든 패키지는 비공개로 시작한다. chat-app이 같은 소유자(`withbbang`)의 이미지를 받고 있으면 로그인이 이미 되어 있다. 확인:
   ```bash
   sudo cat /root/.docker/config.json | grep ghcr    # "ghcr.io" 항목이 있으면 된다
   ```
   없으면 GitHub → Settings → Developer settings → Personal access tokens (classic)에서 `read:packages`만 준 토큰을 만들어:
   ```bash
   sudo docker login ghcr.io -u withbbang     # 비밀번호 자리에 토큰
   ```
   확실한 확인은 1-4의 `dc pull api`가 성공하는지다 (`denied`/`unauthorized`면 로그인 문제).

### 1-2. 배포 폴더와 `deploy/.env`

폴더는 배포 계정이 쓸 수 있어야 한다 (워크플로가 `deploy/`를 풀고 `.env`의 `IMAGE_TAG`를 고친다).

```bash
# NAS
sudo mkdir -p /volume1/docker/lottery-picker/deploy
sudo chown <배포계정> /volume1/docker/lottery-picker
sudo chown -R <배포계정> /volume1/docker/lottery-picker/deploy
```

`.env`의 틀은 Mac에서 예시 파일을 그대로 보낸다.

```bash
# Mac, 저장소 루트
scp -O -P <NAS_SSH_PORT> deploy/.env.example <배포계정>@<NAS_HOST>:/volume1/docker/lottery-picker/deploy/.env
```

```bash
# NAS
cd /volume1/docker/lottery-picker
chmod 600 deploy/.env
vi deploy/.env
```

| 키 | 값 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 새로 만든 긴 비밀번호 (백업·복구에만 씀) |
| `DB_USERNAME` | `lottery` 그대로 (root 불가) |
| `DB_PASSWORD` | 새로 만든 긴 비밀번호 |
| `DB_URL` | 비워 둔다 (`jdbc:mysql://mysql:3306/lottery`) |
| `TOTP_SECRET` | 1-4에서 채운다. 지금은 비워 둔다 |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | 텔레그램 봇 값 |
| `TELEGRAM_FAMILY_CHAT_ID` | 선택. 가족방 id. 비우면 가족방을 쓰지 않는다. 그룹 id는 음수라 `-`까지 넣는다. 가족방에서 명령을 보내면 api 로그의 `허용되지 않은 채팅의 메시지를 무시합니다: chat=…`에 id가 찍힌다. 그룹 설정(권한·느린 모드 등)을 바꾸면 슈퍼그룹으로 전환되며 id가 `-100…`으로 바뀌니, 봇이 갑자기 답하지 않으면 로그에서 새 id를 확인해 바꾸고 `up -d` 한다 |
| `SCHEDULING_ENABLED` | 비워 둔다 (운영은 기본 `true`) |
| `IMAGE_TAG` | 비워 둔다. 워크플로가 배포할 때마다 커밋 SHA를 써 넣는다 |

- 비밀번호는 영문·숫자로만 만든다 (따옴표, `$`, 공백은 Compose 치환과 충돌한다). 예: `openssl rand -hex 24`.
- `MYSQL_*`, `DB_PASSWORD`는 **mysql 볼륨을 처음 만들 때만** 적용된다. 나중에 `.env`만 바꾸면 DB 비밀번호는 그대로라 api가 접속에 실패한다 (9장 참고).
- `SPRING_PROFILES_ACTIVE=prod`는 compose에 고정되어 있다. 프로파일이 빠지면 앱이 `local`(H2, 인증 꺼짐)로 뜨므로 지우지 않는다.
- 예전 방식(`git archive`를 풀어 둔 상태)에서 넘어오는 경우: `deploy/.env`와 `backups/`만 남기고 `apps/` 등 소스는 지워도 된다. 위 `chown`은 `backups/`를 건드리지 않는다 (root 전용으로 둔다).

### 1-3. GitHub 설정

이 저장소(`withbbang/lottery-picker`)의 Settings → Secrets and variables → Actions → **Secrets 탭 → Repository secrets**에 네 개를 등록한다. 값은 chat-app에 넣은 것과 같다. GitHub은 등록된 시크릿을 다시 보여 주지 않으므로 원래 값(키 파일 등)에서 다시 입력한다.

| 이름 | 값 |
|---|---|
| `NAS_HOST` | NAS 공인 주소 (도메인 또는 IP) |
| `NAS_SSH_PORT` | 배포용 SSH 포트 |
| `NAS_SSH_USER` | `<배포계정>` |
| `NAS_SSH_KEY` | 배포용 개인키 전체 (`-----BEGIN ... END ...-----` 포함) |

CLI로 넣어도 된다 (값은 프롬프트나 파일로 넘긴다):

```bash
gh secret set NAS_HOST
gh secret set NAS_SSH_PORT
gh secret set NAS_SSH_USER
gh secret set NAS_SSH_KEY < ~/.ssh/<배포용 개인키>
```

- Environment secrets에 넣으면 이 워크플로가 읽지 못한다. 비어 있으면 deploy 잡이 **이름만** 출력하고 멈춘다.
- 배포 폴더를 바꿀 때만 Variables 탭에 `NAS_DEPLOY_DIR`를 둔다 (기본 `/volume1/docker/lottery-picker`).

### 1-4. 첫 실행과 OTP 등록

`TOTP_SECRET`이 비어 있으면 api가 기동하지 않으므로, 워크플로는 `.env`의 `TOTP_SECRET`이 비어 있으면 **배포 전에 멈춘다**. 첫 실행은 이미지를 GHCR에 올리고 `deploy/`를 NAS에 복사한 뒤 여기서 멈추는 것이 정상이다.

이 배포 구성을 `master`에 처음 푸시하는 순간에도 워크플로가 자동으로 한 번 돈다. 시크릿이나 `.env`가 아직 없으면 그 실행은 해당 확인 단계에서 실패하고 NAS는 건드리지 않으니 무시해도 된다.

1. GitHub → Actions → **deploy** → Run workflow (브랜치 `master`). build 잡 2개가 성공하고, deploy 잡이 `TOTP_SECRET 이 비어 있습니다`로 실패한다.
2. NAS에서 방금 올라간 api 이미지를 받아 일회성 시크릿 생성기(`com.lotterypicker.auth.TotpSetup`, Gradle의 `totpSetup` 태스크와 같은 클래스)를 실행한다. DB에는 접속하지 않는다.
   ```bash
   cd /volume1/docker/lottery-picker
   dc pull api                 # IMAGE_TAG가 비어 있으면 :latest
   dc run --rm --no-deps --entrypoint java api -cp app.jar com.lotterypicker.auth.TotpSetup
   ```
   출력:
   ```
   Picked up JAVA_TOOL_OPTIONS: ...        ← JVM 안내 문구, 무시
   TOTP_SECRET=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
   otpauth://totp/lottery-picker?secret=XXXX...&issuer=lottery-picker
   ```
3. `TOTP_SECRET=...` 줄의 값을 `deploy/.env`의 `TOTP_SECRET`에 넣는다.
4. OTP 앱(Google Authenticator 등)에 등록한다. 둘 중 하나:
   - **설정 키 직접 입력**: 계정 이름 `lottery-picker`, 키는 위 시크릿, 유형 "시간 기준".
   - QR로 등록하려면 Mac에서 `brew install qrencode` 후 `qrencode -t ansiutf8 'otpauth://...'`로 터미널에 QR을 띄운다. 시크릿을 온라인 QR 생성 사이트에 넣지 않는다.
5. 출력이 남은 터미널은 닫는다. 시크릿은 `deploy/.env`와 OTP 앱에만 있어야 한다.
6. GitHub Actions의 실패한 실행에서 **Re-run failed jobs** (또는 Run workflow를 다시). 이번에는 pull → up까지 간다.

- 1번 전에 시크릿을 만들어 두고 싶으면 Mac에서 `cd apps/api && ./gradlew -q totpSetup`으로 같은 출력을 얻을 수 있다. 그러면 첫 실행이 바로 배포까지 간다.
- 시크릿을 잃어버렸거나 바꿀 때는 2~5 후 `dc up -d`를 하면 된다. 시크릿이 바뀌면 기동 시 이전 세션과 잠금이 모두 지워진다.

### 1-5. 기동 확인

첫 배포는 HDD에서 mysql 초기화(수 분) → api 기동 → web 순서라 deploy 잡이 몇 분 걸린다. 끝나면 NAS에서:

```bash
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
| TCP `NAS_SSH_PORT` | NAS 내부 IP:`NAS_SSH_PORT` | GitHub Actions 배포 (chat-app과 같이 씀, 이미 있으면 그대로) |

이 세 개 외에는 추가하지 않는다. GitHub Actions 러너는 매번 다른 IP에서 접속하므로 SSH 포트는 소스 IP로 좁힐 수 없다. 기존에 5000/5001 등을 열어 둔 규칙이 있으면 이번 기회에 닫는 것을 권한다.

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
- 바깥 망에서 아래가 **열리지 않아야** 한다: `http://<공인 IP>:13000`, `:3306`, `:8080`, `:5000`, `:5001` (그리고 `NAS_SSH_PORT`가 22가 아니면 `:22`).
- 바깥 망에서 SSH 포트는 열려 있되 **비밀번호 로그인은 거절돼야** 한다 (2-7의 6번):
  ```bash
  ssh -o PubkeyAuthentication=no -o PreferredAuthentications=password -p <NAS_SSH_PORT> <배포계정>@<NAS_HOST>
  # Permission denied (publickey) 류로 끝나면 된다. 비밀번호를 물으면 2-7의 5번을 본다.
  ```
- 바깥 망에서 아래 주소에 **DSM 로그인 화면이나 DSM 관련 페이지가 보이지 않아야** 한다 (2-7).
  - `http://<공인 IP>/`, `https://<공인 IP>/` (도메인 없이 IP로)
  - `http://lottery-picker.o-r.kr/` (80, 인증서 발급용으로 열어 둔 포트)

### 2-7. DSM 보호 (반드시)

80·443을 NAS로 넘기면, Host가 `lottery-picker.o-r.kr`이 아닌 요청(IP 직접 접속, 스캐너 등)은 모두 DSM의 기본 nginx가 받는다. 80과 443 둘 다 해당한다. 배포용 SSH 포트도 인터넷 전체에 열려 있다. 그래서 아래는 조건 없이 모두 해 둔다.

1. **관리자 계정 2단계 인증**: 관리자 권한이 있는 모든 계정에서 개인 → 보안 → 2단계 인증을 켠다.
2. **자동 차단**: 제어판 → 보안 → 보호 → 자동 차단 사용 (예: 5분 안에 로그인 10회 실패 시 차단). SSH 로그인 실패에도 적용된다.
3. **기본 `admin` 계정 비활성화**: 제어판 → 사용자 및 그룹 → `admin` → 비활성화. 이미 비활성이면 그대로 둔다.
4. **방화벽**: 제어판 → 보안 → 방화벽 → 방화벽 사용, 프로필 규칙을 위에서부터 이 순서로 만든다.

   | 순서 | 포트 | 소스 IP | 동작 |
   |---|---|---|---|
   | 1 | 모두 | 집 LAN 서브넷 (예: `192.168.0.0/24`, 공유기 설정에서 확인) | 허용 |
   | 2 | TCP 80, 443 | 모두 | 허용 |
   | 3 | TCP `NAS_SSH_PORT` | 모두 | 허용 (GitHub Actions 배포, IP가 매번 바뀜) |
   | — | 규칙에 없는 요청 | | 거부 |

   - 결과적으로 DSM 5000/5001, NAS의 다른 서비스(3000, 8080 등)는 LAN에서만 열린다. SSH는 `NAS_SSH_PORT`만 바깥에서 열린다.
   - SSH 포트를 LAN 전용으로 막으면 이 저장소와 chat-app의 자동 배포가 모두 실패한다.
   - chat-app 배포로 이미 같은 규칙이 있으면 그대로 둔다.
   - 서브넷을 잘못 넣으면 NAS에 못 들어갈 수 있다. LAN에서 DSM 창을 하나 열어 둔 채로 적용하고, 새 창으로 접속되는지 확인한 뒤 닫는다.
5. **DSM 로그인 페이지가 80/443에 나오는지 확인**: 제어판 → 로그인 포털 → DSM 탭(웹 서비스 포트, 사용자 지정 도메인, HTTP→HTTPS 자동 리디렉션)과 고급 탭을 확인한다. DSM 버전·설치 패키지(Web Station 등)에 따라 다르므로 동작을 짐작하지 말고 2-6의 바깥 망 확인으로 판단한다.
   - IP로 접속했을 때 DSM 로그인 화면이 뜨거나 `:5000`/`:5001`로 리디렉션되면, DSM 탭의 사용자 지정 도메인·리디렉션 설정을 끄고 다시 확인한다.
   - 그래도 DSM 페이지가 보이면 1~4가 켜져 있는지 다시 확인한다. 방화벽 4번이 있으면 리디렉션돼도 5000/5001은 바깥에서 열리지 않는다.
6. **SSH는 키로만**: 바깥에 열린 SSH 포트로 비밀번호 추측이 들어온다. 배포 계정은 키 로그인만 쓰므로 비밀번호 로그인은 막혀 있는 편이 좋다. 2-6의 `ssh -o PubkeyAuthentication=no ...`로 확인한다.
   - 비밀번호를 묻는다면, DSM 버전에 따라 설정 위치가 다르므로 짐작하지 말고 확인한다: DSM 화면(터미널 및 SNMP 등)에 해당 옵션이 있는지, 없으면 `/etc/ssh/sshd_config`의 `PasswordAuthentication no` 후 SSH 서비스 재시작을 검토한다. 이 파일은 DSM 업데이트 때 되돌아갈 수 있으므로 업데이트 뒤에 2-6의 확인을 다시 한다.
   - 바꾸기 전에 다른 터미널에서 키 로그인 세션을 하나 열어 둔다. 키 로그인이 안 되는 상태에서 막으면 NAS에 SSH로 못 들어간다 (DSM 웹은 LAN에서 계속 쓸 수 있다).
   - 비밀번호 로그인을 막지 못하면 배포 계정의 비밀번호를 길게 두고 2(자동 차단)를 반드시 켜 둔다.


## 3. 다시 배포 (업데이트)

평소 흐름(피처 → `dev` → `master` 병합, `git push origin dev master`)대로 `master`에 푸시하면 끝이다. `apps/**`, `deploy/**`, 워크플로 파일이 바뀐 푸시만 배포한다. 그 안의 `.md`만 바뀐 푸시는 배포하지 않는다. 수동 실행(Run workflow)은 `master`에서만 돈다.

- 진행은 GitHub → Actions → deploy에서 본다. 끝나면 NAS에서 `dc ps`로 확인.
- 다시 돌리고 싶을 때(실패 후 NAS 쪽을 고쳤을 때 등): Actions → deploy → **Run workflow** (`master`).
- DB 스키마는 api 기동 시 Flyway가 올린다. 큰 변경 전에는 NAS에서 `sudo bash deploy/backup.sh`를 먼저 돌린다.

### 되돌리기

배포된 커밋 SHA는 `deploy/.env`의 `IMAGE_TAG`에 있고, 이전 SHA는 GitHub Actions 실행 목록이나 `git log master`에서 찾는다. GHCR에 SHA 태그가 남아 있으므로 NAS에 이미지가 없어도 받아 온다.

```bash
vi deploy/.env             # IMAGE_TAG=<되돌릴 커밋 SHA 전체>
dc up -d                   # 없는 이미지는 받아 와서 재생성
```

- 다음 `master` 배포가 `IMAGE_TAG`를 다시 덮어쓴다. 고친 커밋을 푸시하면 자연스럽게 앞으로 돌아온다.
- Flyway 마이그레이션은 앞으로만 간다. 스키마를 바꾼 커밋보다 이전으로 되돌리면 api가 기동하지 않을 수 있다. 그때는 4장의 덤프로 DB를 되돌린다.

### 오래된 이미지 정리

배포마다 `:<SHA>` 태그 이미지가 하나씩 남는다 (`image prune -f`는 태그 없는 이미지만 지운다). 가끔 확인해서 지금 쓰는 것과 되돌릴 후보만 남긴다.

```bash
sudo docker images 'ghcr.io/withbbang/lottery-picker-*'
sudo docker rmi ghcr.io/withbbang/lottery-picker-api:<오래된 SHA> ghcr.io/withbbang/lottery-picker-web:<오래된 SHA>
```

---

## 4. 백업 (Step 6)

`deploy/backup.sh`, `deploy/restore-check.sh`는 따로 보낼 필요 없다. 배포 워크플로가 `deploy/`를 복사할 때 같이 NAS에 들어가고, 매번 Git의 최신 내용으로 덮어쓴다. `backups/`는 워크플로가 건드리지 않는다.

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
- 재부팅 때는 Docker가 컨테이너를 한꺼번에 다시 띄우므로 `depends_on`의 `service_healthy`(mysql 준비 후 api 기동)가 지켜지지 않는다. api가 mysql보다 먼저 준비되면 DB 접속에 실패해 종료하고, 재시작 정책으로 다시 뜬다. web도 api보다 먼저 떠서 잠시 `/api/*`가 502를 낼 수 있다. **재부팅 직후 api가 한두 번 재시작되는 것은 정상이다.** 몇 분 뒤 `dc ps`에서 api가 `(healthy)`면 된다. 계속 재시작되면 `dc logs --tail 100 api`로 원인을 본다.
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

Mac에서 확인할 때는 프로젝트 이름과 `.env`를 따로 쓰고, 동행복권 호출을 막는다. GHCR 이미지 대신 소스에서 빌드하도록 `compose.build.yml`을 겹친다.

```bash
# 저장소 밖의 임시 파일에 키를 채운다. SCHEDULING_ENABLED=false,
# TOTP_SECRET=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ (RFC 6238 테스트 값)
alias ldc='docker compose -p lp-deploy-test -f deploy/compose.yml -f deploy/compose.build.yml --env-file <임시 env>'
ldc up -d --build
curl -s localhost:13000/api/v1/health
ldc down -v --rmi local
# 빌드한 이미지는 ghcr.io/... 이름으로 태그된다 (푸시하지 않는다). 남아 있으면 직접 지운다:
docker images 'ghcr.io/withbbang/lottery-picker-*'
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
