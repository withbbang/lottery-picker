# 로또 6/45 개인 서비스 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 역대 데이터 조회, 매일 17:00 통계 기반 추천 5줄과 텔레그램 알림, 추첨 후 비교·성적표·시뮬레이션을 갖춘 개인 전용 서비스를 만든다.

**Architecture:** `apps/api`가 수집, 번호 생성, 비교, 스케줄, 인증을 모두 맡고 자체 DB만 읽어 응답한다. `apps/web`은 같은 주소의 `/api/*`를 Next.js rewrites로 API에 전달하고 표시만 한다. 웹, API, MySQL은 집 서버에서 함께 실행한다.

**Tech Stack:** Spring Boot 4.1.2, Java 17, JPA, Flyway, RestClient, MySQL(운영), H2(로컬·테스트), Next.js 16.4, React 19, React Query, Tailwind, Docker Compose

**Spec:** `docs/superpowers/specs/2026-11-06-lotto-blueprint-design.md`

## Global Constraints

- 추천 문구 금지: "당첨 확률을 높인다", "이번에 나올 번호". 고지 문구는 정확히 `실제 당첨 확률과 무관합니다.` (2026-11-09 변경)
- 번호 생성, 등수 계산, 통계는 `apps/api`에만 둔다. 웹은 받은 값을 표시만 한다.
- 추천과 대조군은 생성 후 수정 경로를 두지 않는다.
- 시각 계산은 모두 `Asia/Seoul`. `LocalDate.now()` 금지, 주입한 `Clock`을 쓴다.
- 난수는 주입한 `Random`. 시뮬레이션만 회차별 고정 시드 `Random`을 만든다.
- 비밀값은 환경변수만 사용하고 `.env.example`에 키 이름만 적는다. `.env*` 원본은 읽거나 고치지 않는다.
- 응답 래퍼, 페이징, 에러 코드는 `api-conventions`, 코드 규약은 `.claude/rules/*`를 따른다.
- 테스트 이름은 행동 서술(Java는 한국어 스네이크), given/when/then 주석, 실제 네트워크 호출 금지.
- Boot 4 모듈 사용 전 Gradle 캐시의 실제 소스를, Next API 사용 전 `apps/web/node_modules/next/dist/docs/`를 확인한다.

### 브랜치와 커밋 (각 태스크 공통)

CLAUDE.md가 이 계획의 커밋 지시보다 우선한다.

1. 태스크마다 `dev`에서 `feat/<이름>` 브랜치를 딴다.
2. 태스크 끝에 바뀐 앱의 검증을 통과시킨다. API는 `cd apps/api && ./gradlew test`, 웹은 `cd apps/web && pnpm lint && pnpm test`.
3. 커밋은 사용자가 요청했을 때만 한다. 메시지는 `.gitmessage.txt` 정책을 따른다.
4. 병합 전 `/code-review low <브랜치>`, 병합·푸시도 요청 시에만 CLAUDE.md 절차대로 한다.

### 검증 단계 표기

- **실패 확인:** 해당 테스트만 실행해 없는 클래스나 단언 실패로 FAIL. 예) `./gradlew test --tests '*LottoNumbersTest'`
- **통과 확인:** 같은 테스트와 앱 전체 테스트가 PASS.

## Review Focus

사용자가 실제로 겪기 쉬운 상황이다. 각 항목의 테스트는 담당 태스크에 포함했다.

1. 동행복권이 200으로 HTML을 주거나 범위 밖 번호를 줌 → 아무것도 저장하지 않고 실패 알림, 낙첨 처리 없음. (Task 1, 8)
2. 초기 적재 도중 재시작 → 중복 없이 이어서 수집, 오래된 회차 알림 폭탄 없음. (Task 2, 8)
3. 서버 시간대가 UTC → 추천 시각과 회차 계산은 여전히 한국 시간 기준. (Task 6)
4. 제외 번호를 극단적으로 설정(후보 정확히 6개, 32~45 전부 제외) → 멈추지 않고 5줄 생성, 완화 사실 표시. (Task 5)
5. 세션 만료 상태로 화면 열기, 또는 텔레그램 장애 → 로그인 화면으로 한 번만 이동, 추천은 유지되고 토큰은 로그에 남지 않음. (Task 3, 7)

## 파일 구조

```
apps/api/src/main/java/com/lotterypicker/
  common/       ClockConfig, PageResponse, SchedulingConfig
  lotto/        LottoNumbers, PrizeRank
  round/        LottoRound, WinningStore, 리포지토리, RoundService, RoundController, dto/
  collector/    DhLotteryClient, CollectorService, CollectionJob, PollPolicy, CollectorException
  auth/         TotpVerifier, Base32, TotpSetup, AuthService, AuthState, AuthSession, SessionAuthFilter, AuthFilterConfig, SessionController
  recommendation/
    engine/     NumberStatistics, DrawHistory, WeightedSampler, LineStrategy, 전략 5종, PopularityFilter, RecommendationConstraint, RecommendationEngine
    (루트)       DailyRecommendation, RoundCalendar, CatchUpPolicy, DailyRecommendationService, RecommendationJob, Settings, 컨트롤러, dto/
  result/       RecommendationResult, PostDrawService, ResultController, StrategyPerformanceService, dto/
  simulation/   SimulationService, SimulationResult, SimulationController, dto/
  notification/ TelegramClient, NotificationLog, NotificationService, 메시지 포매터
apps/api/src/main/resources/db/migration/  V1~V7
apps/api/src/test/resources/fixtures/dhlottery/
apps/web/  app/{login,rounds,rounds/[round],performance,settings}, components/{auth,lotto,rounds,recommendation,results,performance}, types/api/
deploy/    compose.yml, apps/api/Dockerfile, apps/web/Dockerfile, backup.sh
```

스펙의 `recommendation`에 있던 `LottoNumbers`는 회차도 함께 쓰므로 `lotto` 패키지에 둔다. 이 경로는 `lotto-domain.md` 자동 적용 대상이다.

---

### Task 1: 데이터 기반

**Files:**
- Modify: `apps/api/build.gradle`, `src/main/resources/application.yml`, `application-local.yml`, `ErrorCode.java`
- Create: `common/ClockConfig.java`, `lotto/LottoNumbers.java`, `round/LottoRound.java`, `round/WinningStore.java`, 리포지토리 2개, `collector/DhLotteryClient.java`, `collector/CollectorException.java`, `db/migration/V1__round.sql`
- Test: `lotto/LottoNumbersTest.java`, `collector/DhLotteryClientTest.java`, `round/LottoRoundRepositoryTest.java`, 픽스처 4개

**Interfaces:**
- Produces:
  - `record LottoNumbers(List<Integer> values)`: 불변, 오름차순. 위반 시 `IllegalArgumentException`. `boolean contains(int)`, `int countMatches(LottoNumbers)`
  - `DhLotteryClient`: `List<RoundPeriod> fetchRoundList()`, `List<RoundPayload> fetchRoundPage(int round)`, `List<StorePayload> fetchStores(int round)`. 형식 오류는 `CollectorException`
  - `record RoundPeriod(int round, LocalDate drawDate, LocalDate salesStart, LocalDate salesEnd)`
  - `record RoundPayload(int round, LottoNumbers numbers, int bonus, LocalDate drawDate, List<PrizeTier> tiers)`, `record PrizeTier(int rank, long winnerCount, long amountPerWinner)`
  - `record StorePayload(int rank, String region, String name, String address, String purchaseType, Double latitude, Double longitude, String status)`
  - `LottoRound`: `roundNo`, `numbers()`, `bonus`, `drawDate`, 등수별 인원·금액, `source`, `fetchedAt`, `storeFetchedAt`. `Optional<LottoRound> findTopByOrderByRoundNoDesc()`
  - `Clock` 빈(`Asia/Seoul`)

- [ ] **Step 1: 의존성과 설정을 추가한다**

`build.gradle`: `spring-boot-starter-flyway`, `spring-boot-starter-restclient`, `runtimeOnly org.flywaydb:flyway-mysql`, `testImplementation spring-boot-starter-restclient-test`. 테스트 태스크에 `systemProperty 'app.scheduling.enabled', 'false'`.

`application.yml`: `app.dhlottery.base-url: https://www.dhlottery.co.kr`, `app.dhlottery.min-interval-ms: 500`, `app.scheduling.enabled: ${SCHEDULING_ENABLED:true}`.

`application-local.yml`: `ddl-auto: validate`, H2를 파일 DB `jdbc:h2:file:./.data/lottery;MODE=MySQL`로 바꾼다(재시작마다 재적재 방지). `apps/api/.gitignore`에 `.data/` 추가. 테스트가 이 파일 DB를 건드리지 않도록 `build.gradle` 테스트 시스템 프로퍼티로 `spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1`를 지정한다.

- [ ] **Step 2: 픽스처를 저장한다**

실제 응답을 `fixtures/dhlottery/`에 저장한다: `round-list.json`(상위 5건으로 축소), `round-page-1248.json`, `stores-1248.json`(1·2등 각 2건으로 축소), `stores-empty.json`(`srchLtEpsd=1`). 요청 헤더는 `User-Agent: lottery-picker/1.0 (personal use)`, `Accept: application/json`이다. 2026-11-06에 이 헤더로 200 JSON을 확인했다.

- [ ] **Step 3: 실패하는 테스트를 쓴다**

`LottoNumbersTest`
- `값은_오름차순으로_정렬된다`: `[45,1,20,3,7,9]` → `[1,3,7,9,20,45]`
- `1과_45는_허용된다`
- `0_46_5개_7개_중복은_거부된다`: 각각 `IllegalArgumentException`
- `일치_개수를_센다`: `[1,2,3,4,5,6]` vs `[4,5,6,7,8,9]` → 3

`DhLotteryClientTest` (`@RestClientTest` + `MockRestServiceServer`, `min-interval-ms=0`)
- `회차_목록을_읽는다`: 첫 항목 1248, 추첨일 `2026-10-31`, 판매 시작 `2026-10-25`
- `회차_상세를_읽는다`: 1248의 번호 `[16,18,27,36,38,42]`, 보너스 22, 1등 15명 `1933658450`원, 2등 91명, 5등 금액 5000
- `요청_경로와_파라미터가_맞다`: `/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1248`, `/wnprchsplcsrch/selectLtWnShp.do?srchWnShpRnk=all&srchLtEpsd=1248&srchShpLctn=`
- `판매점의_등수는_응답의_wnShpRnk로_판단한다`
- `판매점이_없으면_빈_목록을_돌려준다`
- `HTML_응답은_CollectorException이다`: 200 `text/html`
- `범위_밖_번호는_CollectorException이다`: `tm1WnNo=46`
- `필수_필드가_없으면_CollectorException이다`: `tm6WnNo` 누락

`LottoRoundRepositoryTest` (`@DataJpaTest`)
- `최신_회차를_찾는다`, `번호와_금액을_저장하고_복원한다`

- [ ] **Step 4: 실패 확인**

- [ ] **Step 5: 구현한다**

- `V1__round.sql`: `lotto_round`(`round_no` PK, `n1`~`n6`, `bonus`, `draw_date`, `rank1_count`~`rank5_count`, `rank1_amount`~`rank5_amount` BIGINT, `source`, `fetched_at`, `store_fetched_at` NULL), `winning_store`(`id`, `round_no` FK, `win_rank`, `region`, `name`, `address`, `purchase_type`, `latitude`, `longitude`, `status`, 인덱스 `round_no, win_rank`). `rank`는 MySQL 예약어이므로 쓰지 않는다. `region`은 VARCHAR.
- `rnk*SumWnAmt`, `winType*`는 읽지 않는다. DTO는 `@JsonIgnoreProperties(ignoreUnknown = true)`.
- 요청 간격은 클라이언트 내부에서 `min-interval-ms`로 맞춘다. `source` 값은 base URL이다.

- [ ] **Step 6: 통과 확인**

- [ ] **Step 7: MySQL에서 마이그레이션을 확인한다**

```bash
docker run -d --name lp-mysql -e MYSQL_ROOT_PASSWORD=dev -e MYSQL_DATABASE=lottery -p 3307:3306 mysql:8
cd apps/api && DB_URL=jdbc:mysql://localhost:3307/lottery DB_USERNAME=root DB_PASSWORD=dev SCHEDULING_ENABLED=false ./gradlew bootRun --args='--spring.profiles.active=prod'
```
Expected: Flyway 적용, `ddl-auto: validate` 통과, 기동 성공. 확인 후 컨테이너를 삭제한다. 새 마이그레이션을 추가하는 태스크마다 반복한다.

---

### Task 2: 초기 적재

**Files:**
- Create: `collector/CollectorService.java`, `common/SchedulingConfig.java`, `collector/CollectorStartup.java`
- Modify: `round/LottoRound.java`
- Test: `collector/CollectorServiceTest.java`

**Interfaces:**
- Consumes: Task 1 전체
- Produces:
  - `List<Integer> CollectorService.syncMissingRounds()`: 이번에 저장한 회차. 동시 실행이면 빈 목록(`ReentrantLock.tryLock`)
  - `void CollectorService.syncStores()`
  - `SchedulingConfig`: `@EnableScheduling @EnableAsync`, `@ConditionalOnProperty("app.scheduling.enabled")`

- [ ] **Step 1: 실패하는 테스트를 쓴다** (클라이언트 Mockito, Clock 고정 `2026-11-06T12:00+09:00`)

- `없는_회차만_저장한다`: 목록 1~12, DB에 1~5 → 6~12만 저장, 반환값도 동일
- `페이지에_다른_회차가_있어도_중복_저장하지_않는다`
- `재실행하면_이어서_수집한다`: 첫 실행 중 `CollectorException` → 앞서 저장한 회차 유지, 두 번째 실행은 남은 회차만 요청
- `요청한_회차가_페이지에_없으면_CollectorException이다`: 무한 반복 방지
- `판매점이_있으면_저장하고_완료로_표시한다`: `storeFetchedAt` 설정
- `추첨후_7일_이내_빈_결과는_재시도_대상으로_남긴다`: 추첨일 `2026-10-31`, 오늘 11-06 → null 유지
- `추첨후_7일이_넘은_빈_결과는_완료로_표시한다`: 추첨일 `2026-10-30` 기준 11-06은 정확히 7일 → 유지, 추첨일 `2026-10-29`는 완료
- `이미_완료된_회차는_다시_요청하지_않는다`
- `동시_실행이면_두번째는_아무것도_하지_않는다`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현한다**

`syncMissingRounds`: 목록에서 누락 집합을 구하고, 비면 끝. 누락 최댓값으로 페이지를 요청해 누락 회차만 저장하고 집합에서 뺀다. 회차 단위 트랜잭션이다. `CollectorStartup`은 `ApplicationReadyEvent`에서 `@Async`로 `syncMissingRounds()` → `syncStores()`를 호출한다. 전체 약 10분 걸려도 기동을 막지 않는다.

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 실제로 적재한다**

`cd apps/api && ./gradlew bootRun` 후 H2 콘솔에서 확인한다. Expected: 회차 수가 사이트 목록과 같고, 1248회는 판매점 106건. 도중에 종료하고 재시작해 이어지는지도 본다.

---

### Task 3: OTP 인증

**Files:**
- Modify: `application.yml`, `application-local.yml`, `ErrorCode.java`, `apps/api/.env.example`, `apps/web/.env.example`, `apps/web/next.config.ts`, `apps/web/lib/api.ts`, `apps/web/lib/api.test.ts`, `apps/web/components/providers/QueryProvider.tsx`, `apps/web/components/health/ApiStatus.tsx`, `.claude/rules/web.md`, `.claude/skills/api-conventions/SKILL.md`, `HealthControllerTest.java`
- Delete: `common/config/CorsConfig.java`
- Create: `auth/*`, `db/migration/V2__auth.sql`, `apps/web/app/login/page.tsx`, `apps/web/components/auth/LoginForm.tsx`, `apps/web/types/api/auth.ts`
- Test: `auth/TotpVerifierTest.java`, `auth/AuthServiceTest.java`, `auth/SessionAuthFilterTest.java`, `auth/AuthIntegrationTest.java`, `apps/web/components/auth/LoginForm.test.tsx`, `QueryProvider.test.tsx`

**Interfaces:**
- Consumes: `Clock`, `ErrorCode`
- Produces:
  - `long TotpVerifier.matchStep(String base32Secret, String code, long epochSeconds)`: 일치한 30초 구간 번호, 없으면 `-1`. 앞뒤 1구간 허용
  - `String AuthService.login(String code)`: 원본 토큰 반환. `boolean isValid(String token)`, `void logout(String token)`
  - `POST /api/v1/sessions` `{code}` → 204 + `Set-Cookie: LP_SESSION`, `DELETE /api/v1/sessions` → 204
  - 웹 `apiFetch<T>(path, init)`는 상대 경로만 호출, 쿠키는 브라우저가 자동 전송

- [ ] **Step 1: 실패하는 API 테스트를 쓴다**

`TotpVerifierTest` (RFC 6238 SHA-1 시크릿 `12345678901234567890`의 Base32 `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`)
- `시각_59초의_코드는_287082이다`
- `시각_1111111109초의_코드는_081804이다`: 앞자리 0 유지
- `앞뒤_30초_구간까지_허용한다`, `60초_벗어나면_거부한다`

`AuthServiceTest` (Clock 가변, 리포지토리 Mock)
- `올바른_코드는_세션을_만들고_해시만_저장한다`: 저장값 ≠ 원본 토큰, 값 = 토큰 SHA-256
- `틀린_코드는_AUTH_INVALID_CODE이다`
- `5회_연속_실패하면_AUTH_LOCKED이다`: 5번째 호출부터 잠금
- `잠금_중에는_올바른_코드도_거부한다`
- `15분_후에는_다시_시도할_수_있다`: 14:59 거부, 15:00 허용
- `성공하면_실패_횟수를_초기화한다`
- `이미_사용한_코드는_거부한다`: 같은 구간 재사용 → `AUTH_INVALID_CODE`
- `30일이_지난_세션은_무효이다`: 경계값 29일 23:59 유효, 30일 무효
- `로그아웃한_세션은_무효이다`
- `시크릿이_바뀌면_기존_세션을_모두_폐기한다`: 저장된 지문과 현재 시크릿 SHA-256 비교, 기동 시 호출

`SessionAuthFilterTest` (`MockHttpServletRequest`)
- `쿠키가_없으면_401과_AUTH_REQUIRED_래퍼를_돌려준다`
- `건강확인과_로그인은_통과시킨다`: `GET /api/v1/health`, `POST /api/v1/sessions`
- `로그아웃은_세션이_필요하다`: `DELETE /api/v1/sessions`
- `유효한_쿠키는_통과시킨다`

`AuthIntegrationTest` (`@SpringBootTest` + MockMvc, `app.auth.enabled=true`)
- `코드_없이_API를_호출하면_401이다`
- `로그인_쿠키는_HttpOnly_Secure_SameSite_Strict이고_30일이다`
- `여섯자리_숫자가_아니면_COMMON_VALIDATION_FAILED이다`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: API를 구현한다**

- `V2__auth.sql`: `auth_session`(`token_hash` PK, `created_at`, `expires_at`), `auth_state`(단일 행: `failed_count`, `locked_until`, `last_used_step`, `secret_fingerprint`).
- 설정: `app.auth.enabled: true`, `app.auth.totp-secret: ${TOTP_SECRET:}`, `app.auth.cookie-secure: true`. `application-local.yml`만 `enabled: false`, `cookie-secure: false`. 활성 상태에서 시크릿이 비면 기동 실패.
- `AuthFilterConfig`: 일반 `@Configuration` + `FilterRegistrationBean`. `@WebMvcTest` 슬라이스에 필터가 끼지 않게 `@Component`로 만들지 않는다.
- 필터는 예외를 던지지 않고 `ApiResponse.failure(AUTH_REQUIRED)`를 직접 JSON으로 쓴다.
- `ErrorCode`: `AUTH_REQUIRED(401)`, `AUTH_INVALID_CODE(400)`, `AUTH_LOCKED(429)`.
- `TotpSetup`: `main`에서 랜덤 20바이트 Base32 시크릿과 `otpauth://totp/lottery-picker?secret=...&issuer=lottery-picker`를 출력한다. Gradle `totpSetup` 태스크(`JavaExec`). QR은 `qrencode -t ansiutf8 '<uri>'`로 표시한다.
- CORS 설정과 `HealthControllerTest`의 CORS 테스트, `app.cors`, `CORS_ALLOWED_ORIGINS`를 삭제한다. 브라우저는 같은 주소만 호출한다.
- 운영 프로파일에 `server.address`를 두지 말고 Compose에서 API 포트를 외부에 열지 않는다(Task 9).

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 실패하는 웹 테스트를 쓴다**

`api.test.ts`를 수정한다: 베이스 URL 환경변수 테스트 삭제. `calls the relative path`: `fetch`가 `"/api/v1/health"`로 호출됨.

`LoginForm.test.tsx`
- `submits the six-digit code and moves to home`
- `shows the server message when the code is wrong`
- `disables submit until six digits are entered`: `inputMode="numeric"`, `autoComplete="one-time-code"`

`QueryProvider.test.tsx`
- `redirects to /login once on AUTH_REQUIRED`: 쿼리 2개가 실패해도 이동 1회
- `does not redirect when already on /login`

- [ ] **Step 6: 실패 확인**

- [ ] **Step 7: 웹을 구현한다**

- 먼저 `node_modules/next/dist/docs/01-app/03-api-reference/05-config/01-next-config-js/rewrites.md`를 읽는다.
- `next.config.ts`: `/api/:path*` → `${API_INTERNAL_URL}/api/:path*`. 값은 `next build` 시점에 필요하다.
- `QueryProvider`: `QueryCache.onError`에서 `ApiError.code === "AUTH_REQUIRED"`이면 `/login`으로 이동. mutation도 동일.
- `ApiStatus`는 쿠키 전달이 필요 없도록 `"use client"` + React Query로 바꾼다.
- `web.md` 수정: "데이터는 클라이언트 React Query로 조회. 호출은 같은 주소의 `/api/*` 상대 경로만. 서버 컴포넌트는 데이터 API를 호출하지 않는다." `NEXT_PUBLIC_API_URL` 규칙 삭제.
- `api-conventions`: 상태 코드에 401, 429 추가, `AUTH_*` 표 추가, 웹 구현 절 갱신.
- `.env.example`: 웹 `API_INTERNAL_URL=http://localhost:8080`, API `TOTP_SECRET=`.

- [ ] **Step 8: 통과 확인** (API, 웹 전체 + `pnpm build`)

---

### Task 4: 역대 조회

**Files:**
- Create: `common/PageResponse.java`, `round/RoundService.java`, `round/RoundController.java`, `round/dto/*`, `apps/web/types/api/round.ts`, `apps/web/app/rounds/page.tsx`, `apps/web/app/rounds/[round]/page.tsx`, `apps/web/components/lotto/LottoBall.tsx`, `apps/web/components/rounds/*`
- Test: `round/RoundServiceTest.java`, `round/RoundControllerTest.java`, `apps/web/components/rounds/RoundDetail.test.tsx`

**Interfaces:**
- Consumes: `LottoRound`, `WinningStore`
- Produces:
  - `record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages)`
  - `GET /rounds` → `RoundSummaryResponse(round, numbers, bonus, drawDate)`
  - `GET /rounds/{round}` → `RoundDetailResponse(round, numbers, bonus, drawDate, prizes: List<PrizeTierResponse(rank, winnerCount, amountPerWinner)>)`
  - `GET /rounds/{round}/winning-stores?rank&region` → `List<WinningStoreResponse(rank, region, name, address, purchaseType, status)>`
  - 웹 `LottoBall({ value })`: 숫자만 그린다

- [ ] **Step 1: 실패하는 API 테스트를 쓴다**

- `회차_목록은_최신순이다`
- `size가_100을_넘으면_COMMON_VALIDATION_FAILED이다`: 100 통과, 101 거부
- `상세는_1등부터_5등까지_다섯_항목을_돌려준다`
- `없는_회차는_404_COMMON_NOT_FOUND이다`
- `판매점은_등수와_지역으로_거른다`
- `3등_이상_숫자를_rank로_주면_빈_목록이다`: 지역 데이터가 1·2등뿐
- `판매점이_없는_회차는_빈_목록이다`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: API를 구현한다**

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 실패하는 웹 테스트를 쓴다**

`RoundDetail.test.tsx`
- `shows winner count and amount for ranks 1 to 5`
- `labels the region section as 당첨 판매점 지역`
- `shows 지역 정보 없음 when no stores exist`
- `states that ranks 3 to 5 have no region data`
- `shows a not-found message for a missing round`

- [ ] **Step 6: 실패 확인**

- [ ] **Step 7: 웹을 구현한다**

쿼리 키 `['rounds', {page}]`, `['rounds', round]`, `['rounds', round, 'stores']`. 지역 화면은 지역별 건수를 먼저 보이고 펼치면 판매점 목록을 보인다. 모바일 우선, 숫자는 `tabular-nums`. 금액 쉼표 표시는 화면 포맷이라 웹에서 처리해도 된다.

- [ ] **Step 8: 통과 확인** + `pnpm dev`로 `/rounds`, `/rounds/1248`, `/rounds/1` 표시 확인

---

### Task 5: 추천 엔진

**Files:**
- Create: `recommendation/engine/*`, `recommendation/RecommendationSettings.java`, `recommendation/RecommendationSettingsService.java`, `recommendation/RecommendationSettingsController.java`, `recommendation/dto/*`, `db/migration/V3__settings.sql`
- Modify: `ErrorCode.java`, `.claude/rules/lotto-domain.md`, `.claude/skills/api-conventions/SKILL.md`
- Test: `recommendation/engine/*Test.java`, `recommendation/RecommendationSettingsControllerTest.java`

**Interfaces:**
- Consumes: `LottoNumbers`, `RandomConfig`의 `Random`
- Produces:
  - `record DrawHistory(List<HistoricalDraw> draws)`, `record HistoricalDraw(int round, LottoNumbers numbers)`. `DrawHistory before(int round)`: 해당 회차 미만만 반환
  - `NumberStatistics.from(DrawHistory)`: `int frequency(n)`, `int recentFrequency(n, window)`, `int roundsSinceLast(n)`, `int pairCount(a, b)`, `IntRange oddCountRange()`, `lowCountRange()`, `sumRange()`. `record IntRange(int min, int max)`는 양 끝 포함
  - `enum StrategyType { RECENT_HOT, LONG_COLD, ALL_TIME, PAIR, BALANCED, CONTROL }`
  - `interface LineStrategy { StrategyType type(); DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random); }`
  - `record DrawnLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence)`
  - `record RecommendationConstraint(Set<Integer> fixed, Set<Integer> excluded)`: `static of(...)`, 상수 `NONE`
  - `enum Relaxation { NONE, OVERLAP, OVERLAP_AND_POPULARITY }`
  - `record GeneratedLine(DrawnLine line, Relaxation relaxation)`
  - `record GeneratedSet(List<GeneratedLine> lines, List<DrawnLine> control)`
  - `GeneratedSet RecommendationEngine.generate(DrawHistory history, RecommendationConstraint c, Random random)`
  - `boolean PopularityFilter.isPopular(LottoNumbers n, Set<LottoNumbers> pastWinners)`
  - `GET·PUT /recommendation-settings` → `RecommendationSettingsResponse(fixed, excluded)`

- [ ] **Step 1: 실패하는 테스트를 쓴다** (모두 시드 고정)

`NumberStatisticsTest` (회차 3~5개 합성 데이터)
- `전체_빈도를_센다`, `최근_N회_빈도는_창_밖을_세지_않는다`, `미출현_번호의_경과는_전체_회차수이다`, `쌍_빈도는_순서와_무관하다`
- `합계_범위는_nearest_rank_25에서_75백분위이다`

`WeightedSamplerTest`
- `제외_번호는_나오지_않는다`, `고정_번호는_항상_포함된다`, `가중치_0인_번호만_남아도_6개를_채운다`

`PopularityFilterTest`
- `전부_31이하면_인기_조합이다`: `[3,7,11,19,25,31]` true, `[3,7,11,19,25,32]` false
- `네개_연속이면_인기_조합이다`: `[1,2,3,4,20,40]` true, `[1,2,3,20,33,40]` false
- `등차수열이면_인기_조합이다`: `[1,8,15,22,29,36]` true
- `과거_1등_조합과_같으면_인기_조합이다`

`RecommendationConstraintTest`
- `범위_밖이면_DRAW_NUMBER_OUT_OF_RANGE이다`, `겹치면_DRAW_FIXED_EXCLUDED_OVERLAP이다`
- `제외_39개는_허용하고_40개는_DRAW_NOT_ENOUGH_CANDIDATES이다`
- `고정_5개는_허용하고_6개는_RECOMMENDATION_TOO_MANY_FIXED이다`

`RecommendationEngineTest`
- `전략별로_한_줄씩_다섯_줄을_만든다`: 순서 RECENT_HOT, LONG_COLD, ALL_TIME, PAIR, BALANCED
- `고정_번호를_모든_줄과_대조군에_넣는다`, `제외_번호를_모든_줄과_대조군에서_뺀다`
- `기본_설정에서_고정을_뺀_줄간_겹침은_2개_이하이다`
- `기본_설정에서_인기_조합은_나오지_않는다`
- `대조군은_5줄이고_CONTROL_전략이다`
- `대조군에는_인기_필터를_적용하지_않는다`: 인기 조합을 뽑도록 한 Random 스텁으로 확인
- `후보가_정확히_6개면_완화하고_5줄을_만든다`: 후보 `[1,10,20,30,40,45]`만 남도록 제외 39개 → 5줄 모두 이 번호, 첫 줄 `NONE`, 나머지 4줄 `OVERLAP`
- `32에서_45를_모두_제외하면_생일_필터를_완화한다`: 예외 없음, `OVERLAP_AND_POPULARITY`, 근거에 완화 문구
- `고정_번호_규칙은_완화하지_않는다`
- `시드가_같으면_결과가_같다`
- `근거_문구_형식이_맞다`: `최근 50회 중 11번 출현`, `23회째 미출현`, `전체 1248회 중 190번 출현`, `12·34 쌍이 8번 함께 출현`, `홀3:짝3, 합계 138, 과거 분포 중앙 구간`
- `균형_전략은_합계와_홀짝과_저고를_범위_안에_둔다`
- `균형_조건을_못_맞추면_미충족을_근거에_적는다`

`RecommendationSettingsControllerTest`
- `기본값은_빈_목록이다`, `저장한_값을_돌려준다`, `규칙_위반은_해당_에러코드와_400이다`, `중복_숫자는_COMMON_VALIDATION_FAILED이다`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현한다**

가중치(모두 비복원 가중 추출, 고정 번호를 먼저 넣고 나머지를 채운다):
| 전략 | 번호 n의 가중치 |
|---|---|
| RECENT_HOT | `recentFrequency(n, 50) + 1` |
| LONG_COLD | `roundsSinceLast(n) + 1` |
| ALL_TIME | `frequency(n) + 1` |
| PAIR | 이미 뽑은 번호 p마다 `pairCount(n, p)`의 합 `+ 1`. 첫 번호는 `frequency(n) + 1` |
| BALANCED | 균등. 세 범위 안에 들 때까지 최대 1,000회 재추출, 실패 시 마지막 줄에 `균형 조건 미충족` |
| CONTROL | 균등. 고정·제외만 적용 |

- 범위는 IQR 포함 범위다(nearest-rank 25·75 백분위). 저번호는 1~22.
- 줄 생성: 전략 순서대로 인기 필터와 겹침 조건으로 최대 1,000회 재추출 → 실패하면 겹침 해제 후 1,000회 → 실패하면 인기 필터도 해제. 완화 시 근거에 `중복 제한 완화` 또는 `중복·인기 조합 제한 완화`를 넣는다.
- 겹침 계산은 고정 번호를 뺀다.
- 추천 근거는 줄당 최대 2개다. 번호를 정할 때 가중치가 가장 높았던 번호를 쓴다.
- 새 에러 코드: `RECOMMENDATION_TOO_MANY_FIXED(400)`, `RECOMMENDATION_NO_HISTORY(409)`.
- `V3__settings.sql`: `recommendation_settings` 단일 행 `fixed_numbers`, `excluded_numbers` VARCHAR(쉼표 구분).
- `lotto-domain.md`: `LottoDrawService` 관련 문단을 `recommendation` 패키지 기준으로 바꾸고, 고정 번호 상한 5개와 추천 줄의 `bonus` 없음을 적는다. `testing.md`의 "고정 번호 6개" 경계도 5·6개로 고친다. `api-conventions`에 `RECOMMENDATION_*` 추가.

- [ ] **Step 4: 통과 확인**

---

### Task 6: 일일 추천

**Files:**
- Create: `recommendation/DailyRecommendation.java`, 리포지토리, `recommendation/RoundCalendar.java`, `recommendation/CatchUpPolicy.java`, `recommendation/DailyRecommendationService.java`, `recommendation/RecommendationJob.java`, `recommendation/RecommendationController.java`, `recommendation/dto/RecommendationSetResponse.java`, `db/migration/V4__daily_recommendation.sql`, `apps/web/types/api/recommendation.ts`, `apps/web/components/recommendation/*`, `apps/web/app/settings/page.tsx`
- Modify: `apps/web/app/page.tsx`, `round/LottoRound` 리포지토리
- Test: `RoundCalendarTest.java`, `CatchUpPolicyTest.java`, `DailyRecommendationServiceTest.java`, `RecommendationControllerTest.java`, `apps/web/components/recommendation/RecommendationCard.test.tsx`, `SettingsForm.test.tsx`

**Interfaces:**
- Consumes: `RecommendationEngine`, `RecommendationSettingsService`, `LottoRoundRepository`
- Produces:
  - `LocalDate RoundCalendar.targetSaturday(LocalDate date)`, `int RoundCalendar.roundFor(LocalDate date, int latestRound, LocalDate latestDrawDate)`
  - `boolean CatchUpPolicy.shouldGenerate(ZonedDateTime now, boolean alreadyGenerated)`
  - `Optional<RecommendationSet> DailyRecommendationService.generateIfAbsent(LocalDate date)`: 새로 만들면 값, 이미 있으면 빈 값
  - `record RecommendationSet(LocalDate date, int round, List<StoredLine> lines)`, 대조군 미포함. `record StoredLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence, Relaxation relaxation)`
  - `DailyRecommendation`: `date`, `roundNo`, `strategy`, `lineIndex`, `numbers()`, `evidence`, `relaxation`, `fixedSnapshot`, `excludedSnapshot`, `historyFromRound`, `historyToRound`, `control`, `hasSettings()`
  - `void RecommendationJob.run()`: 생성 후 Task 7에서 발송 연결
  - `GET /recommendations?date` → `RecommendationSetResponse(date, round, lines, historyFromRound, historyToRound, disclaimer)`, 없으면 204

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`RoundCalendarTest`
- `토요일의_대상은_그날이다`: `2026-11-07` → `2026-11-07`
- `일요일의_대상은_다음_토요일이다`: `2026-11-08` → `2026-11-14`
- `회차를_계산한다`: 최신 1248(`10-31`) 기준 `11-06` → 1249, `11-07` → 1249, `11-08` → 1250
- `직전_수집이_실패해도_회차가_맞다`: 최신 1247(`10-24`), `11-06` → 1249

`CatchUpPolicyTest`
- `16시59분은_false`, `17시는_true`, `이미_있으면_false`
- `토요일_19시59분은_true`, `토요일_20시는_false`, `일요일_20시는_true`

`DailyRecommendationServiceTest`
- `추천과_대조군_10줄을_저장한다`
- `같은_날_두번_호출해도_한_벌만_만든다`: 두 번째 빈 값
- `동시_생성으로_유니크_위반이_나면_빈_값이다`
- `회차가_없으면_RECOMMENDATION_NO_HISTORY이다`
- `설정과_입력_회차_범위를_함께_저장한다`
- `통계는_저장된_회차만_쓴다`
- `설정을_바꿔도_지난_추천은_바뀌지_않는다`
- `서버_시간대가_UTC여도_한국_날짜로_저장한다`: `TimeZone.setDefault(UTC)`, Clock `2026-11-06T17:00+09:00`, 복구는 `@AfterEach`

`RecommendationControllerTest`
- `대조군은_응답에_없다`: lines 5개, `CONTROL` 없음
- `날짜를_생략하면_오늘이다`, `없으면_204이다`, `고지_문구를_포함한다`

`RecommendationCard.test.tsx`
- `shows five lines with strategy and evidence`, `shows the disclaimer`, `shows an empty message on 204`

`SettingsForm.test.tsx`
- `saves fixed and excluded numbers`, `shows the server message on conflict`, `says changes apply from the next generation`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: API를 구현한다**

- `V4__daily_recommendation.sql`: `daily_recommendation`, 유니크 `(rec_date, strategy, line_index)`, `evidence`는 TEXT(JSON 배열).
- `RecommendationJob`: `@Scheduled(cron = "0 0 17 * * *", zone = "Asia/Seoul")`. `app.scheduling.enabled`일 때만 등록.
- 기동 보충: `CollectorStartup`의 `syncMissingRounds()` 뒤에 `CatchUpPolicy`로 판단해 호출한다. 회차 적재가 먼저 끝나야 통계가 맞다.

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 웹을 구현한다**

`/`: 오늘 추천, 최신 회차 요약. `/settings`: 고정·제외 번호 입력. 저장 후 `['recommendation-settings']`를 무효화한다.

- [ ] **Step 6: 통과 확인** (API, 웹 전체)

---

### Task 7: 텔레그램

**Files:**
- Create: `notification/*`, `db/migration/V5__notification.sql`
- Modify: `recommendation/RecommendationJob.java`, `RecommendationSetResponse`, `apps/api/.env.example`, `apps/web/components/recommendation/RecommendationCard.tsx`
- Test: `notification/TelegramClientTest.java`, `notification/NotificationServiceTest.java`, `notification/MessageFormatterTest.java`, `recommendation/RecommendationJobTest.java`

**Interfaces:**
- Consumes: `RecommendationSet`, `RecommendationJob`
- Produces:
  - `enum NotificationKind { DAILY_RECOMMENDATION, RECOMMENDATION_FAILED, DRAW_FINISHED, COLLECTION_FAILED, COLLECTION_ERROR }`
  - `NotificationStatus NotificationService.sendOnce(NotificationKind kind, String targetKey, String text)`: `SENT`, `FAILED`, `SKIPPED`
  - `NotificationStatus NotificationService.statusOf(NotificationKind kind, String targetKey)`
  - `String MessageFormatter.recommendation(RecommendationSet set)`
  - `RecommendationSetResponse`에 `notificationStatus` 추가

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`TelegramClientTest` (`MockRestServiceServer`)
- `sendMessage로_chat_id와_text를_보낸다`: `POST .../bot{token}/sendMessage`
- `실패_예외에_토큰이_포함되지_않는다`: 500 응답 후 예외 메시지에 토큰 문자열 없음

`NotificationServiceTest`
- `발송_전에_기록을_남긴다`: 전송 호출 시점에 `PENDING` 기록 존재
- `같은_알림은_한번만_보낸다`
- `3회_실패하면_FAILED이고_예외를_던지지_않는다`: 전송 3회 호출
- `두번째_시도에_성공하면_SENT이다`
- `설정이_비어_있으면_SKIPPED이다`
- `FAILED_기록은_다음_호출에서_다시_보낸다`

`RecommendationJobTest`
- `생성하면_알림을_보낸다`: 키 `2026-11-06`
- `발송이_실패해도_추천은_유지된다`
- `이미_생성됐고_미발송이면_다시_보낸다`
- `추천_생성_실패는_RECOMMENDATION_FAILED_알림을_보낸다`

`MessageFormatterTest`
- `날짜와_회차_5줄_전략과_고지문을_포함한다`
- `대조군은_포함하지_않는다`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현한다**

- 설정: `app.telegram.base-url: https://api.telegram.org`, `bot-token: ${TELEGRAM_BOT_TOKEN:}`, `chat-id: ${TELEGRAM_CHAT_ID:}`, `retry-delay-ms: 5000`(테스트 0).
- `V5__notification.sql`: `notification_log`(`kind`, `target_key`, `status`, `attempts`, `updated_at`), 유니크 `(kind, target_key)`.
- 로그와 예외에는 HTTP 상태만 남긴다. URL에 토큰이 들어가므로 원본 예외 메시지를 기록하지 않는다.
- 카드에 `notificationStatus === "FAILED"`이면 `텔레그램 발송 실패` 표시.

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 실제 봇으로 확인한다**

사용자가 BotFather로 봇을 만들고 환경변수를 설정한다. `/recommendations` 생성을 로컬 17:00 또는 기동 보충으로 일으켜 메시지 수신을 사용자가 확인한다.

---

### Task 8: 추첨 후 처리

**Files:**
- Create: `lotto/PrizeRank.java`, `result/RecommendationResult.java`, 리포지토리, `result/PostDrawService.java`, `result/ResultController.java`, `result/dto/*`, `collector/PollPolicy.java`, `collector/CollectionJob.java`, `db/migration/V6__result.sql`, `apps/web/types/api/result.ts`, `apps/web/components/results/RoundResults.tsx`
- Modify: `notification/MessageFormatter.java`, `collector/CollectorStartup.java`, `apps/web/app/rounds/[round]/page.tsx`
- Test: `lotto/PrizeRankTest.java`, `collector/PollPolicyTest.java`, `collector/CollectionJobTest.java`, `result/PostDrawServiceTest.java`, `result/ResultControllerTest.java`, `RoundResults.test.tsx`

**Interfaces:**
- Consumes: `CollectorService`, `NotificationService`, `DailyRecommendation`, `LottoRound`
- Produces:
  - `enum PrizeRank { FIRST, SECOND, THIRD, FOURTH, FIFTH, NONE }`, `static PrizeRank of(LottoNumbers line, LottoNumbers winning, int bonus)`
  - `void PostDrawService.process(int round)`: 멱등
  - `boolean PollPolicy.isDue(ZonedDateTime now, LocalDate latestDrawDate)`, `boolean PollPolicy.isOverdue(ZonedDateTime now, LocalDate latestDrawDate)`, `Optional<LocalDate> PollPolicy.expectedDraw(ZonedDateTime now, LocalDate latestDrawDate)`
  - `boolean PostDrawService.shouldNotify(LottoRound round, boolean hasRecommendations, LocalDate today)`
  - `RecommendationResult`: `recommendationId`, `roundNo`, `matchCount`, `prizeRank`, `prizeAmount`
  - `GET /rounds/{round}/results` → `RoundResultResponse(round, lines, rankCounts, prizeTotal, cost, profit)`, 대조군 미포함

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`PrizeRankTest` (당첨 `[1,2,3,4,5,6]`, 보너스 7)
- 6개 FIRST / `[1,2,3,4,5,7]` SECOND / `[1,2,3,4,5,8]` THIRD / 4개 FOURTH / 3개 FIFTH / 2개 NONE
- `보너스와_4개_일치는_4등이다`: `[1,2,3,4,7,8]` FOURTH

`PollPolicyTest` (최신 회차 `2026-10-31`)
- `토요일_20시39분은_대상이_아니다`: `11-07T20:39` expectedDraw 빈 값
- `토요일_20시40분부터_5분마다_확인한다`: `20:40`, `20:45` true
- `자정_이후에는_정시에만_확인한다`: `11-08T00:05` false, `01:00` true
- `수집이_끝나면_확인하지_않는다`: 최신 `11-07`, `11-07T21:00` false
- `일요일_12시부터_지연이다`: `11:59` false, `12:00` true
- `월요일에도_정시마다_계속_확인한다`: `11-09T10:00` true

`CollectionJobTest`
- `새_회차를_처리한다`, `수집기_예외는_COLLECTION_ERROR_알림을_보낸다`: 키 = 기대 추첨일
- `HTML_응답_같은_형식_오류여도_결과를_만들지_않는다`
- `지연이면_COLLECTION_FAILED_알림을_한번만_보내고_계속_확인한다`
- `여러_회차가_빠졌으면_모두_처리한다`

`PostDrawServiceTest`
- `회차의_추천과_대조군_전부를_채점한다`: 35+35줄
- `두번_실행해도_결과와_알림이_한번이다`
- `알림은_대조군을_제외하고_요약한다`: 회차, 번호, 줄별 최고 등수, 등수별 줄 수, 가상 당첨금
- `지연_수집이면_그_사실을_적는다`
- `추천이_없는_오래된_회차는_알리지_않는다`: 초기 적재
- `추천이_없어도_7일_내_회차는_알린다`: 경계 7일 true, 8일 false
- `해당_회차_추천이_없으면_결과를_낙첨으로_만들지_않는다`

`ResultControllerTest`
- `대조군은_응답에_없다`, `비용은_줄당_1000원이다`: 35줄 → 35000, `추천이_없으면_빈_lines이다`, `없는_회차는_404이다`

`RoundResults.test.tsx`
- `shows rank counts and prize total`, `labels amounts as 구매했다고 가정`, `shows an empty message without recommendations`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현한다**

- `CollectionJob`: `@Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")`. 매번 `PollPolicy`로 판단해 빈 호출을 막는다. 추가로 `0 0 6 * * *`에 `syncStores()`.
- 흐름: 지연이면 `COLLECTION_FAILED` 발송 → `syncMissingRounds()` → 추천이 있는 미처리 회차 모두 `process`. 예외는 `COLLECTION_ERROR`로 알린다. 저장은 파싱 성공 후에만 일어난다.
- 기동 시 `CollectorStartup`도 수집 뒤 미처리 회차를 처리한다.
- `V6__result.sql`: `recommendation_result`, `recommendation_id` 유니크. 금액은 해당 회차의 1인당 금액.

- [ ] **Step 4: 통과 확인** (API, 웹 전체)

---

### Task 9: 집 서버 배포

**Files:**
- Create: `apps/api/Dockerfile`, `apps/web/Dockerfile`, `deploy/compose.yml`, `deploy/backup.sh`, `deploy/README.md`, `deploy/.env.example`

**Interfaces:**
- Consumes: 모든 앱 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TOTP_SECRET`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `API_INTERNAL_URL`
- Produces: 재부팅 후 자동 시작되는 운영 환경

- [ ] **Step 1: 사용자와 서버 정보를 확인한다**

필요 정보: 서버 종류와 OS, CPU 아키텍처, Docker 사용 가능 여부, 사용할 도메인 유무. 기본 경로는 Docker Compose + Cloudflare Tunnel이다. 서버나 도메인 조건이 맞지 않으면 이 태스크만 조정하고 사용자 승인 후 진행한다.

- [ ] **Step 2: 서버에서 동행복권 접속을 확인한다**

서버에서 3장의 세 경로를 호출한다. Expected: 모두 200 `application/json`. 실패하면 멈추고 사용자에게 알린다. 수집 설계에 영향을 준다.

- [ ] **Step 3: Compose를 작성하고 로컬에서 확인한다**

- `mysql:8`(볼륨 유지, 포트 비공개), `api`(`SPRING_PROFILES_ACTIVE=prod`, 포트 비공개), `web`(`127.0.0.1:3000`만 공개), 전부 `restart: unless-stopped`, `TZ=Asia/Seoul`.
- 웹 이미지는 `API_INTERNAL_URL=http://api:8080`을 빌드 인자로 받는다.
- 시크릿은 서버의 `deploy/.env`에 두고 Git에 넣지 않는다. 키 이름만 `deploy/.env.example`에 적고 `.gitignore`를 확인한다.

Run: `docker compose -f deploy/compose.yml up --build` 후 `curl -s localhost:3000/api/v1/health`. Expected: `"status":"UP"`, `curl localhost:3000/api/v1/rounds`는 401, API 8080은 호스트에서 접속 불가.

- [ ] **Step 4: 서버에 배포하고 OTP를 등록한다**

`master`를 체크아웃해 실행하고, `totpSetup` 출력으로 시크릿을 넣고 재시작한다. 사용자가 OTP 앱에 등록한 뒤 로그인을 확인한다.

- [ ] **Step 5: HTTPS 경로를 연결한다**

터널을 `127.0.0.1:3000`으로 연결한다. Expected: 집 밖 네트워크의 휴대폰에서 HTTPS 주소로 OTP 로그인 성공, 공유기에 새로 연 포트 없음.

- [ ] **Step 6: 백업과 복구를 확인한다**

`deploy/backup.sh`: `mysqldump`를 일 1회, 최근 14개 보관. 추천 기록은 다시 만들 수 없으므로 빈 DB에 복구해 행 수가 같은지 확인한다.

- [ ] **Step 7: 재부팅과 1주 운영을 확인한다**

재부팅 후 자동 시작을 본다. 7일 동안 17:00 알림과 토요일 종료 알림, 비교 화면을 사용자가 확인한다. 이 항목으로 Task 7·8의 실제 수신 검증도 끝난다.

---

### Task 10: 성적표

**Files:**
- Create: `result/StrategyPerformanceService.java`, `result/StrategyPerformanceController.java`, `result/dto/StrategyPerformanceResponse.java`, `apps/web/types/api/performance.ts`, `apps/web/app/performance/page.tsx`, `apps/web/components/performance/PerformanceTable.tsx`
- Test: `result/StrategyPerformanceServiceTest.java`, `PerformanceTable.test.tsx`

**Interfaces:**
- Consumes: `RecommendationResult`, `DailyRecommendation.hasSettings()`
- Produces: `GET /strategy-performances` → `List<StrategyPerformanceResponse(strategy, lineCount, averageMatches, rankCounts, atLeastFifthRate, noSettingsLineCount, noSettingsAtLeastFifthRate, sampleSufficient)>`, `theoryAtLeastFifthRate`는 응답 상위 필드

- [ ] **Step 1: 실패하는 테스트를 쓴다**

- `전략별과_대조군을_각각_집계한다`
- `평균_일치와_5등_이상_비율을_계산한다`: 합성 값으로 정확한 수치 단언
- `이론값은_194130_나누기_8145060이다`: `isCloseTo(0.023834, within(0.000001))`
- `이론_비교는_설정_없는_줄만_쓴다`: 설정 있는 줄을 `noSettings*`에서 제외, 전체 집계에는 포함
- `천줄_미만이면_표본이_부족하다`: 999 false, 1000 true
- `결과가_없는_줄은_세지_않는다`: 미추첨 회차 제외
- 웹: `shows the control group`, `shows 아직 차이를 판단하기 이르다 when the sample is small`, `shows theory only with its line count`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현한다**

표본 기준 1,000줄은 작성자 판단이다. 5등 이상 기대치가 약 24건이 되는 규모이며 약 29주 분량이다.

- [ ] **Step 4: 통과 확인**

---

### Task 11: 시뮬레이션

**Files:**
- Create: `simulation/*`, `db/migration/V7__simulation.sql`, `apps/web/types/api/simulation.ts`, `apps/web/components/performance/SimulationPanel.tsx`
- Modify: `result/PostDrawService.java`, `collector/CollectorStartup.java`, `apps/web/app/performance/page.tsx`
- Test: `simulation/SimulationServiceTest.java`, `simulation/SimulationControllerTest.java`, `SimulationPanel.test.tsx`

**Interfaces:**
- Consumes: `DrawHistory.before`, `NumberStatistics.from`, 전략 `LineStrategy.draw`(`RecommendationConstraint.NONE`), `PrizeRank.of`
- Produces:
  - `SimulationReport SimulationService.run(DrawHistory history, int startRound, int repetitions, long baseSeed)`: 순수 계산
  - `void SimulationService.refresh()`: 전체 실행 후 최신 결과 교체, `@Async`
  - `record StrategySimulation(StrategyType strategy, long lineCount, double averageMatches, Map<PrizeRank, Long> rankCounts, double atLeastFifthRate, double ciLow, double ciHigh, boolean differsFromControl)`
  - `GET /simulations/latest` → 결과, `fromRound`, `toRound`, `repetitions`, `computedAt`, `scopeNote`. 없으면 204

- [ ] **Step 1: 실패하는 테스트를 쓴다**

- `시작_회차_이전_데이터만_쓴다`: 회차 r 결과를 고정하고 r 이후 번호를 바꿔도 r의 추출이 같다
- `101회부터_시작한다`: `startRound=101`, 100회 이하 집계 없음
- `전략당_회차별_N회_추출한다`: 회차 3개 × 200 = 600줄, CONTROL 포함
- `시드가_같으면_결과가_같다`
- `신뢰구간은_정규근사이다`: `p ± 1.96√(p(1-p)/n)` 정확값
- `구간이_겹치면_유의한_차이가_없다`: 겹침 false, 안 겹침 true, 경계 접점은 겹침
- `인기_필터와_중복_제약을_적용하지_않는다`: 인기 조합이 집계에서 빠지지 않음
- `최신_결과만_저장한다`
- `새_회차_처리_후_다시_계산한다`, `결과가_없으면_기동_시_계산한다`
- 컨트롤러: `결과가_없으면_204이다`
- 웹: `shows control and theory`, `shows 유의한 차이 없음`, `states that only the number selection rules are evaluated`

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현한다**

- 기본값: `startRound=101`, `repetitions=200`, `baseSeed` 상수. 회차·전략별 시드는 `baseSeed ^ (round * 1_000_003L) ^ strategy.ordinal()`.
- 요청 시점에 계산하지 않는다. `V7__simulation.sql`: 단일 행 `simulation_result`(JSON TEXT, 범위, `computed_at`).
- `scopeNote`: `전략의 번호 추출 규칙만 평가합니다. 고정·제외 번호와 인기 조합·중복 제약은 적용하지 않습니다.`
- 차이가 나도 "효과 있음"이라고 쓰지 않는다. 표시는 `무작위와 차이 관측` / `유의한 차이 없음`이며 여러 전략을 동시에 비교한 결과임을 함께 적는다.

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 실제 데이터로 실행 시간을 확인한다**

로컬에 적재된 전체 회차로 `refresh()`를 실행한다. Expected: 5분 이내, 웹과 스케줄이 계속 응답. 초과하면 통계 증분 계산으로 고친다.
