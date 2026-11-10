# 텔레그램 봇 명령어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정된 텔레그램 채팅에서 `/today` `/result` `/performance` `/round N` `/stats` `/settings` `/help`를 보내면 봇이 웹과 같은 숫자로 답한다.

**Architecture:** `TelegramClient`에 `getUpdates`·`setMyCommands`를 더하고, 전용 스레드 하나(`TelegramPoller`)가 롱 폴링으로 메시지를 받아 `BotCommandService`에 넘긴다. 서비스는 웹이 쓰는 서비스 계층을 그대로 불러 DTO를 받고, 문구는 `BotMessageFormatter`가 만든다. 텔레그램 전용 계산은 없다.

**Tech Stack:** Spring Boot 4.1 / RestClient / Mockito / MockRestServiceServer.

**Spec:** `docs/superpowers/specs/2026-11-10-stats-share-stores-telegram-design.md` 4장

## Global Constraints

- 숫자 계산·집계는 기존 서비스 계층에서. 봇 코드는 받은 DTO를 문구로 바꾸기만 한다. 웹 API는 바꾸지 않는다.
- 설정된 `app.telegram.chat-id`에서 온 메시지에만 답한다. 그 외는 응답 없이 로그만(채팅 id는 남기되 본문은 남기지 않는다).
- 설정 변경 명령 없음. `/settings`는 보기만.
- 토큰이 로그·예외 메시지에 새지 않게 한다(기존 `TelegramException` 원칙: 원본 예외를 cause로 잇지 않고 종류·HTTP 코드만).
- 웹훅·인라인 버튼·여러 사용자 지원 없음.
- 토큰·채팅 id가 없거나 `app.scheduling.enabled=false`면 폴링을 시작하지 않는다.
- 폴링 실패가 추천 생성·알림 스케줄에 영향을 주지 않는다(스케줄러 풀이 아닌 전용 스레드, 실패 시 백오프).
- 문구 HTML(`parse_mode=HTML`). 고정 문구 밖 값(지역 이름 등)은 `HtmlUtils.htmlEscape`. 대조군은 `/performance`에만.
- "당첨 확률을 높인다" 류 표현 금지. 추천·통계·성적 메시지 끝에는 기존 고지문(`RecommendationSetResponse.DISCLAIMER`)을 붙인다.
- api 규칙: 생성자 주입, Service `@Transactional(readOnly = true)`, Mockito 단위 테스트, 테스트 이름 한국어 행동 서술.

## 이 계획이 정한 것

- **폴링 타임아웃**: `getUpdates`의 대기시간을 20초로 두어 기존 RestClient read-timeout(30초)보다 짧게 한다. 스펙의 "폴링 대기시간보다 긴 타임아웃"을 별도 클라이언트 없이 만족한다(별도 RequestFactory를 쓰면 `@RestClientTest`의 MockRestServiceServer가 가로채지 못한다).
- **수신 범위**: `allowed_updates=["message"]`. 편집·채널 글·콜백은 받지 않는다.
- **오래된 명령**: 보낸 지 10분 넘은 메시지는 답하지 않고 확인만 한다(재시작 직후 밀린 명령에 한꺼번에 답하지 않게).
- **중복 응답 방지**: 받은 묶음의 가장 큰 `update_id + 1`을 다음 `offset`으로 넘겨 확인한다. 같은 프로세스에서는 두 번 답하지 않는다. 처리 직후·다음 폴링 전에 프로세스가 죽으면 재시작 후 그 묶음이 다시 올 수 있는데, 10분 규칙으로 대부분 걸러진다(그 사이 재시작이면 한 번 더 답할 수 있음: 받아들임).
- **`/start`**는 `/help`와 같다. 명령이 아닌 글·모르는 명령은 짧은 안내.
- **`/today`**: 웹과 같은 `findLatest(today)` → 그 날짜의 `findSet` → 기존 일일 추천 알림 문구 그대로. 이월이면 앞에 한 줄 안내.
- **`/result`**: 채점 결과가 있는 가장 큰 회차를 추첨 종료 알림과 같은 문구(`MessageFormatter.drawFinished`)로.
- **`/round N`**: 당첨 번호·보너스, 등수별 당첨자 수·1인당 금액, 1등 판매점 지역별 건수(건수 내림차순 → 이름). 온라인은 기존 응답처럼 "온라인".
- **`/stats`**: 가장 최근 추천 기준 번호 통계(`NumberStatisticsService.find(null)`), 지표마다 서버 순서 앞 5개.
- **`/performance`**: 전략 + 대조군, 줄마다 `5등 이상 비율 · 평균 맞힌 개수 · 줄 수 · 라벨`(숫자를 앞에, 한글 라벨을 뒤에: 기존 알림의 열 정렬 원칙). 표본 부족이면 `(표본 부족)`.
- **패키지**: 새 도메인 `com.lotterypicker.bot`(`dto`, `service`). 문구는 기존 알림 문구와 헬퍼(`dateWithDay`, `numbers`, `won`, `label`, `rankLabel`)를 함께 쓰므로 `notification/BotMessageFormatter.java`에 두고, `MessageFormatter`의 해당 헬퍼를 package-private으로 연다.

## Review Focus

1. 텔레그램이 계속 실패(네트워크 끊김·HTTP 409 등) → 폴링이 바쁜 루프로 돌지 않고 5초부터 두 배씩 최대 5분까지 쉬었다 다시 시도한다(Task 6 백오프 테스트).
2. 다른 사람이 봇에게 말을 걺 → 답하지 않고 offset은 넘긴다(Task 6).
3. `/round` 뒤 인자가 없거나 `abc`·`0`·`-1`·`99999999999`(int 범위 밖) → 안내 문구, 예외 없음(Task 5).
4. 서비스가 예상 못 한 예외를 던짐 → 일반 오류 문구로 답하고 폴링은 계속(Task 5·6).
5. 그룹 채팅식 `/round@봇이름 1249`, 앞뒤 공백, 대문자 `/TODAY` → 같은 명령으로 해석(Task 2).

---

### Task 1: 텔레그램 수신·명령 메뉴 API

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/bot/dto/TelegramUpdate.java`
- Modify: `apps/api/src/main/java/com/lotterypicker/notification/TelegramClient.java`
- Test: `apps/api/src/test/java/com/lotterypicker/notification/TelegramClientTest.java` (기존 파일에 추가)

**Interfaces:**
- Produces:
  - `public record TelegramUpdate(long updateId, @Nullable Long chatId, @Nullable String text, @Nullable Instant sentAt)` — 메시지가 없는 업데이트는 `chatId`·`text`·`sentAt`이 null
  - `List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds)` — `POST /bot{token}/getUpdates` JSON `{offset, timeout, allowed_updates:["message"]}`. 응답 `{"ok":true,"result":[{update_id, message:{date(유닉스 초), chat:{id}, text}}]}`
  - `void setMyCommands(List<BotMenuItem> commands)` — `POST /bot{token}/setMyCommands` JSON `{commands:[{command, description}]}` (`BotMenuItem`은 Task 2)
  - `boolean isAllowedChat(long chatId)` — 설정된 `chat-id` 문자열과 같은지
  - 실패는 `send`와 같은 방식의 `TelegramException`(토큰 없음)

- [ ] **Step 1: 실패하는 테스트**
  - `getUpdates로_offset과_대기시간을_보내고_메시지를_읽는다`: 요청 URL `.../bot123:secret/getUpdates`, `$.offset == 7`, `$.timeout == 20`, `$.allowed_updates[0] == "message"`; 응답 두 건 → `TelegramUpdate(7, 42L, "/today", Instant.ofEpochSecond(1762750000))` 등
  - `메시지가_없는_업데이트는_본문이_비어_있다`: `{"update_id":8}` → `chatId`·`text`·`sentAt` null
  - `getUpdates_실패는_토큰_없는_예외다`: 서버 오류 → `TelegramException`, 메시지에 `secret` 없음
  - `명령_메뉴를_등록한다`: `$.commands[0].command == "today"`, `$.commands[0].description == "오늘 추천"`
  - `설정된_채팅만_허용한다`: `isAllowedChat(42)` 참, `isAllowedChat(43)` 거짓

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*TelegramClientTest'`(ctx_execute) → 컴파일 실패

- [ ] **Step 3: 구현** — 응답 JSON은 `TelegramClient` 안 private record(`@JsonProperty("update_id")` 등)로 받고 `TelegramUpdate`로 바꾼다. Jackson 어노테이션 패키지는 Gradle 캐시의 실제 jar로 확인. 공통 URI 생성은 `sendMessageUri`를 `methodUri(String method)`로 일반화.

- [ ] **Step 4: 통과 확인** — `TelegramClientTest` 전부 통과(기존 포함)

- [ ] **Step 5: 커밋** — `Feat(api) : 텔레그램 메시지 수신과 명령 메뉴 등록 추가`

### Task 2: 명령 해석

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/bot/BotCommand.java`, `apps/api/src/main/java/com/lotterypicker/bot/dto/BotMenuItem.java`
- Test: `apps/api/src/test/java/com/lotterypicker/bot/BotCommandTest.java`

**Interfaces:**
- Produces:
  - `public enum BotCommand { TODAY, RESULT, PERFORMANCE, ROUND, STATS, SETTINGS, HELP }` — 각자 `name()`의 소문자가 명령어, `description()`은 아래 표
  - `public record Parsed(BotCommand command, @Nullable String argument)` (BotCommand 안 중첩 record)
  - `static Optional<Parsed> parse(String text)` — `/`로 시작하지 않거나 모르는 명령이면 빈 값. `/start`는 HELP.
  - `static List<BotMenuItem> menu()` — 표 순서
  - `public record BotMenuItem(String command, String description)`

| 명령 | description |
|---|---|
| today | 오늘 추천 |
| result | 최근 회차 채점 결과 |
| performance | 전략별 성적표 |
| round | 회차 당첨 정보 (예: /round 1249) |
| stats | 번호 통계 상위 5개 |
| settings | 고정·제외 번호 보기 |
| help | 명령 목록 |

- [ ] **Step 1: 실패하는 테스트**
  - `명령과_인자를_나눈다`: `"/round 1249"` → `(ROUND, "1249")`; `"/today"` → `(TODAY, null)`
  - `봇_이름이_붙은_명령도_읽는다`: `"/round@lotto_bot 1249"` → `(ROUND, "1249")`
  - `대소문자와_공백을_가리지_않는다`: `"  /TODAY  "` → `(TODAY, null)`; `"/round   1249  "` → 인자 `"1249"`
  - `start는_help다`
  - `명령이_아니거나_모르는_명령은_빈_값`: `"안녕"`, `"/foo"`, `"/"`, `""`
  - `메뉴는_표_순서다`: `menu()`의 command 목록 == `[today, result, performance, round, stats, settings, help]`

- [ ] **Step 2~4** — 실패 확인 → 구현 → 6 passed

- [ ] **Step 5: 커밋** — `Feat(api) : 텔레그램 봇 명령 해석 추가`

### Task 3: 최근 채점 회차 요약

**Files:**
- Modify: `apps/api/src/main/java/com/lotterypicker/result/repository/RecommendationResultRepository.java`, `apps/api/src/main/java/com/lotterypicker/result/service/PostDrawService.java`
- Test: `apps/api/src/test/java/com/lotterypicker/result/service/PostDrawServiceTest.java` (기존 파일에 추가)

**Interfaces:**
- Produces:
  - `Optional<RecommendationResult> findFirstByOrderByRoundNoDesc()` (리포지토리)
  - `PostDrawService.latestSummary(): Optional<DrawSummary>` — 채점 결과가 있는 가장 큰 회차를 기존 `summarize`로. 결과가 없으면 빈 값. 읽기 전용(`@Transactional(readOnly = true)`), 알림을 보내지 않는다.

- [ ] **Step 1: 실패하는 테스트**
  - `최근_채점_회차를_알림과_같은_요약으로_준다`: 결과 회차 1249 → `round == 1249`, `tally.lineCount()`가 대조군 뺀 줄 수, 텔레그램 `send` 호출 없음
  - `채점한_회차가_없으면_빈_값`

- [ ] **Step 2~4** — 실패 확인 → 구현 → 2 passed, 기존 `PostDrawServiceTest` 통과

- [ ] **Step 5: 커밋** — `Feat(api) : 최근 채점 회차 요약 조회 추가`

### Task 4: 명령 응답 문구

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/notification/BotMessageFormatter.java`
- Modify: `apps/api/src/main/java/com/lotterypicker/notification/MessageFormatter.java` (`dateWithDay`, `numbers(LottoNumbers)`, `won`, `rankLabel`, `label`을 package-private으로. 동작 변화 없음)
- Modify: `docs/telegram-messages.md` ("## 명령 응답" 절 추가: 아래 문구와 예시)
- Test: `apps/api/src/test/java/com/lotterypicker/notification/BotMessageFormatterTest.java`

**Interfaces:**
- Consumes: `BotCommand.menu()`(Task 2), 기존 DTO `RecommendationSetResponse`, `RecommendationSet`, `DrawSummary`, `RoundDetailResponse`, `WinningStoreResponse`, `NumberStatisticsResponse`, `StrategyPerformancesResponse`, `RecommendationSettingsResponse`
- Produces (모두 `public static String`):
  - `help()`, `unknown()`, `error()`
  - `today(RecommendationSetResponse latest, RecommendationSet set)`, `noRecommendation()`
  - `result(DrawSummary)` = `MessageFormatter.drawFinished`, `noResult()`
  - `round(RoundDetailResponse, List<WinningStoreResponse> firstRankStores)`, `roundUsage()`, `roundNotFound(String argument)`
  - `stats(NumberStatisticsResponse)`, `noStats()`
  - `performance(StrategyPerformancesResponse)`, `noPerformance()`
  - `settings(RecommendationSettingsResponse)`

고정 문구:

| 함수 | 문구 |
|---|---|
| `help()` | `사용할 수 있는 명령` + 줄마다 `/{command} {description}` (메뉴 표 순서) |
| `unknown()` | `모르는 명령입니다. /help 로 명령 목록을 볼 수 있습니다.` |
| `error()` | `처리 중 오류가 났습니다. 잠시 후 다시 시도해 주세요.` |
| `noRecommendation()` | `아직 추천이 없습니다.` |
| `today` 이월 머리말 | `오늘 추천이 아직 없어 {M}월 {d}일 추천을 보여 드립니다.` + (`todayPending`이면) ` 오늘 추천은 17시에 나옵니다.` + 빈 줄, 그 뒤 `MessageFormatter.recommendation(set)` |
| `noResult()` | `아직 채점한 회차가 없습니다.` |
| `roundUsage()` | `회차 번호를 숫자로 적어 주세요. 예: /round 1249` |
| `roundNotFound(arg)` | `{arg}회 당첨 정보가 없습니다.` (arg는 이스케이프) |
| `round` | 1행 `{N}회 · {dateWithDay}` / 빈 줄 / `<pre>{numbers} + {bonus}</pre>` / 빈 줄 / 등수마다 `{k}등 {당첨자 수 %,d}명 · {won}` / 빈 줄 / `1등 판매점 지역` / `서울 3 · 경기 2 · 온라인 1`(판매점이 없으면 `1등 판매점 정보가 없습니다.`) |
| `noStats()` | `아직 추천이 없어 번호 통계가 없습니다.` |
| `stats` | 1행 `번호 통계 · {M}월 {d}일 추천 기준 ({from}~{to}회)` / 지표마다 빈 줄 + 지표명(`최근 {recentWindow}회 출현`, `미출현 회차`, `전체 출현`) + `<pre>` 안에 앞 5개를 줄마다 `{번호 %2d}  {값}  {순위}` (값: COLD는 0이면 `직전 회차 출현`, 아니면 `{v}회째`; 그 외 `{v}회`. 순위: `{r}위` 또는 `공동 {r}위`) / 빈 줄 / `과거에 자주·드물게 나온 정도일 뿐, 실제 당첨 확률과 무관합니다.` |
| `noPerformance()` | `아직 채점한 추천이 없습니다.` |
| `performance` | 1행 `전략별 성적표 (채점된 줄 기준)` / `5등 이상 비율 · 평균 맞힌 개수 · 줄 수` / `<pre>` 안 줄마다 `{rate %5.1f%%}  {avg %.2f}개  {lineCount}줄  {label}` + 표본 부족이면 ` (표본 부족)` / `이론값 {theory %.1f%%}` / 빈 줄 / 고지문 |
| `settings` | `고정 번호: 3, 7` / `제외 번호: 없음` / `바꾸기는 웹의 설정 화면에서 할 수 있습니다.` |

`performance`는 모든 전략의 `lineCount`가 0이면 `noPerformance()`. 판매점 지역 건수는 `WinningStoreResponse.region()`을 세어 건수 내림차순 → 이름 오름차순.

- [ ] **Step 1: 실패하는 테스트** (각 함수 한 개 이상, 정확한 문자열 비교)
  - `도움말은_메뉴_순서로_명령을_보여_준다`
  - `이월된_추천은_안내_한_줄을_앞에_붙인다`: `carriedOver=true, todayPending=true` → 첫 줄 `오늘 추천이 아직 없어 11월 9일 추천을 보여 드립니다. 오늘 추천은 17시에 나옵니다.`, 그 뒤 기존 추천 알림 문구
  - `오늘_추천은_알림과_같은_문구다`: `carriedOver=false` → `MessageFormatter.recommendation(set)`과 같다
  - `회차_정보는_번호_등수별_금액_판매점_지역을_보인다`: 지역 `[서울, 경기, 서울, 온라인]` → `서울 2 · 경기 1 · 온라인 1`
  - `판매점이_없으면_그렇게_말한다`
  - `회차_인자는_이스케이프한다`: `roundNotFound("<b>")` → `&lt;b&gt;회 당첨 정보가 없습니다.`
  - `번호_통계는_지표마다_앞_5개다`: 6개 준 지표에서 5개만, COLD 0 → `직전 회차 출현`, tied → `공동 3위`
  - `성적표는_숫자를_앞에_두고_표본_부족을_표시한다`
  - `채점한_줄이_없으면_성적표_대신_안내`
  - `설정이_비어_있으면_없음이다`

- [ ] **Step 2~4** — 실패 확인 → 구현 → 전부 통과, 기존 `MessageFormatterTest` 통과

- [ ] **Step 5: 커밋** — `Feat(api) : 텔레그램 명령 응답 문구 추가` (문서 포함)

### Task 5: 명령 처리 서비스

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/bot/service/BotCommandService.java`
- Test: `apps/api/src/test/java/com/lotterypicker/bot/service/BotCommandServiceTest.java` (Mockito)

**Interfaces:**
- Consumes: `BotCommand.parse`(Task 2), `PostDrawService.latestSummary`(Task 3), `BotMessageFormatter`(Task 4), 기존 `DailyRecommendationService.today()/findLatest/findSet`, `RoundService.detail/winningStores(round, 1, null)`, `NumberStatisticsService.find(null)`, `StrategyPerformanceService.performances()`, `RecommendationSettingsService.get()`
- Produces: `String reply(String text)` — 늘 답할 문구를 돌려준다(예외를 밖으로 던지지 않음)

- [ ] **Step 1: 실패하는 테스트**
  - `today는_최근_추천을_알림_문구로_답한다` / `추천이_없으면_없다고_답한다`
  - `result는_최근_채점_요약을_답한다` / `채점_전이면_없다고_답한다`
  - `round는_회차와_1등_판매점으로_답한다`: `RoundService.winningStores(1249, 1, null)` 호출
  - `round_인자가_잘못되면_사용법을_답한다`: `null`, `"abc"`, `"0"`, `"-1"`, `"99999999999"` → `roundUsage()`, `RoundService` 호출 없음
  - `없는_회차는_없다고_답한다`: `detail`이 `BusinessException(COMMON_NOT_FOUND)` → `roundNotFound("1300")`
  - `stats_performance_settings_help를_답한다` (각각 한 번씩)
  - `모르는_명령이나_일반_글은_안내한다`
  - `예상_못한_오류는_일반_오류_문구로_답한다`: `performances()`가 `IllegalStateException` → `error()`

- [ ] **Step 2~4** — 실패 확인 → 구현 → 전부 통과

- [ ] **Step 5: 커밋** — `Feat(api) : 텔레그램 명령 처리 서비스 추가`

### Task 6: 롱 폴링

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/bot/service/TelegramPoller.java`, `apps/api/src/main/java/com/lotterypicker/bot/service/PollBackoff.java`
- Test: `apps/api/src/test/java/com/lotterypicker/bot/service/TelegramPollerTest.java`, `PollBackoffTest.java`

**Interfaces:**
- Consumes: `TelegramClient.getUpdates/setMyCommands/isAllowedChat/send/isConfigured`(Task 1), `BotCommand.menu`(Task 2), `BotCommandService.reply`(Task 5), 기존 `Clock` 빈
- Produces:
  - `PollBackoff`: `Duration nextDelay()`(5초, 10초, 20초 … 최대 5분), `void reset()`
  - `TelegramPoller` (`SmartLifecycle`, `@ConditionalOnProperty("app.scheduling.enabled")`): `start()`은 `isConfigured()`일 때만 `setMyCommands(BotCommand.menu())`(실패는 경고 로그만) 후 데몬 스레드 `telegram-poller`에서 루프. `stop()`은 플래그를 내리고 스레드를 interrupt.
  - `void pollOnce()` (package-private, 테스트용): `getUpdates(offset, 20)` → 업데이트마다 `offset = updateId + 1`; 허용 안 된 채팅·본문 없음·`sentAt`이 지금(Clock)보다 10분 넘게 전이면 답하지 않음; 그 외 `send(reply(text))`. `send` 실패(`TelegramException`)는 경고 로그 후 다음 업데이트로.
  - 루프: `pollOnce` 성공이면 `reset()`, 예외면 `nextDelay()`만큼 쉬고 재시도. interrupt면 종료.

- [ ] **Step 1: 실패하는 테스트**
  - `PollBackoffTest.기다림은_5초부터_두_배씩_5분까지다`: 5,10,20,40,80,160,300,300초; `reset()` 뒤 다시 5초
  - `허용된_채팅의_명령에_답한다`: 업데이트 1건 → `send("응답")`
  - `다음_폴링은_마지막_업데이트_다음부터다`: 첫 `pollOnce`가 id 7·9 → 두 번째 호출 offset 10
  - `다른_채팅은_답하지_않고_넘긴다`: `isAllowedChat` 거짓 → `reply`·`send` 호출 없음, 다음 offset은 넘어감
  - `10분_넘은_명령은_답하지_않는다`: `sentAt = now - 11분` → 호출 없음; `now - 9분` → 답함
  - `본문_없는_업데이트는_건너뛴다`
  - `보내기_실패해도_다음_업데이트를_처리한다`: 첫 `send`가 `TelegramException` → 두 번째 업데이트도 `send`
  - `설정이_없으면_시작하지_않는다`: `isConfigured` 거짓 → `start()` 뒤 `isRunning()` 거짓, `getUpdates`·`setMyCommands` 호출 없음

- [ ] **Step 2~4** — 실패 확인 → 구현 → 전부 통과, API 전체 `./gradlew test` 통과(결과 XML로 셈)

- [ ] **Step 5: 커밋** — `Feat(api) : 텔레그램 명령 롱 폴링 추가`

### Task 7: 배포 후 실제 봇 확인 (사용자 결정: 배포 뒤 확인)

- 이 브랜치에서는 하지 않는다. 배포 후 확인 항목: `/` 메뉴 7개, 명령 7개 응답, `/round abc`, `/foo`, 다른 채팅에서 보낸 명령 무응답.
