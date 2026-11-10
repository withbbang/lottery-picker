# 번호 통계 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 추천 근거 번호가 45개 번호 중 몇 위인지, 그 추천을 만든 시점의 회차 범위 기준으로 보여 주는 "번호 통계" 화면과 진입 링크를 만든다.

**Architecture:** 엔진에 지표(`NumberMetric`)와 순위 계산(`NumberRanking`)을 두고 `NumberStatistics`를 그대로 쓴다. 추천 줄마다 근거 번호를 새 컬럼에 저장하고(기존 행은 Flyway Java 마이그레이션으로 1회 채움), 새 `statistics` 도메인이 `GET /api/v1/number-statistics`로 지표별 순위를 준다. 웹은 `/numbers` 페이지에서 받은 순서대로 그리기만 하고, 추천 카드·기록 화면의 근거 영역(공용 컴포넌트)에서 링크한다.

**Tech Stack:** Spring Boot 4.1 / Java 17 / JPA / Flyway(H2 MySQL 모드 테스트), Next.js 16 / React 19 / React Query / Tailwind / Vitest.

**Spec:** `docs/superpowers/specs/2026-11-10-stats-share-stores-telegram-design.md` §1

**Branch:** `dev`에서 `feat/number-statistics` 분기.

## Global Constraints

- 계산·순위·정렬은 `apps/api`에서만. 웹은 받은 배열 순서대로 그린다. 웹은 근거 문구를 파싱하지 않는다.
- 통계는 추천 행의 `history_from_round`~`history_to_round` 범위의 회차만으로 계산한다. 최신 회차를 쓰지 않는다.
- 지표 3개만: 최근 50회 출현(`RECENT_HOT`), 미출현 회차(`LONG_COLD`), 전체 출현(`ALL_TIME`). `PAIR`·`BALANCED`·`COMBINED`·`CONTROL`은 통계 링크 없음.
- 윈도 50은 `RecentHotStrategy.WINDOW` 하나만 쓴다.
- 순위는 공동 순위(1,2,2,4). 값이 클수록 위, 같은 값은 번호 오름차순.
- 응답 래퍼·에러 코드는 `api-conventions`: 지정한 날짜에 추천 없음 → 404 `COMMON_NOT_FOUND`, 날짜 생략인데 추천이 하나도 없음 → 204.
- 웹: Tailwind만(인라인 `style={}` 금지), `lib/api.ts`의 `apiFetch`만, `<button>`/`<Link>`, 문구에 "당첨 확률" 류 표현 금지.
- 테스트 이름은 행동 서술, given/when/then, AssertJ.
- 커밋 메시지 `Type(scope) : 제목`(50자 이내), 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **기존 행의 근거에 "번호 N:"이 아닌 줄이 섞임** — 완화 메모(`RecommendationEngine.withNote`)나 근거가 0~1개인 줄. 그런 줄은 건너뛰고 있는 번호만 채운다. → Task 2 테스트.
2. **값이 같은 번호가 많음**(예: 최근 50회 0회가 여럿) — 공동 순위와 `tied` 표시가 맞아야 한다. → Task 1 테스트.
3. **추천 뒤 새 회차가 수집됨** — 범위 밖 회차(> `historyToRound`)가 통계에 섞이면 안 된다. → Task 3 테스트.
4. **URL 파라미터가 깨짐**(`highlight=abc,99`, `metric=PAIR`, `date` 없음) — 화면이 죽지 않고 유효한 값만 쓰며 기본 탭(최근)으로 연다. → Task 4 테스트.
5. **링크가 없어야 할 줄**(번호 쌍·균형·근거 조합) — 링크를 그리지 않는다. → Task 5 테스트.

---

### Task 1: 엔진 — 지표와 순위 계산

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/engine/NumberMetric.java`
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/engine/NumberRanking.java`
- Modify: `apps/api/src/main/java/com/lotterypicker/recommendation/engine/RecentHotStrategy.java` (`WINDOW`를 `public static final`로)
- Modify: `.gitignore` (`.superpowers/` 추가)
- Test: `apps/api/src/test/java/com/lotterypicker/recommendation/engine/NumberRankingTest.java`

**Interfaces:**
- Produces:
  - `public enum NumberMetric { RECENT, COLD, ALL_TIME; public int valueOf(NumberStatistics stats, int number); public static Optional<NumberMetric> forStrategy(StrategyType type); }` — RECENT=`recentFrequency(n, RecentHotStrategy.WINDOW)`, COLD=`roundsSinceLast(n)`, ALL_TIME=`frequency(n)`. 매핑: RECENT_HOT→RECENT, LONG_COLD→COLD, ALL_TIME→ALL_TIME, 나머지 empty.
  - `public record RankedNumber(int number, int value, int rank, boolean tied)` (같은 파일 또는 별도 파일, `engine` 패키지)
  - `public final class NumberRanking { public static List<RankedNumber> rank(NumberStatistics stats, NumberMetric metric); }` — 45개, 정렬 = 값 내림차순 → 번호 오름차순.

- [ ] **Step 1: 실패하는 테스트 작성** (`NumberRankingTest`, `TestHistories.draw`로 합성 회차)
  - `순위는_값_내림차순이고_같은_값은_번호_오름차순이다()` — 결과 크기 45, 인접 쌍마다 `value` 비증가, 같은 값이면 `number` 증가.
  - `같은_값은_공동_순위이고_다음_순위는_건너뛴다()` — 회차 2개 `draw(1, 1,2,3,4,5,6)`, `draw(2, 1,2,3,7,8,9)`, ALL_TIME: 1·2·3은 rank 1·tied true, 4~9는 rank 4·tied true, 값 0인 번호들은 rank 10.
  - `혼자인_값은_tied가_false다()` — `draw(1, 1..6)`, `draw(2, 1,7..11)`: 번호 1은 rank 1, tied false.
  - `미출현_지표는_오래_안_나온_번호가_위다()` — COLD 첫 항목의 `value`가 `stats.roundsSinceLast`의 최댓값.
  - `근거_문구의_숫자와_순위_값이_같다()` — `TestHistories.seeded(7, 120)`, `new Random(42)`로 `RecentHotStrategy`/`LongColdStrategy`/`AllTimeStrategy`를 각각 `draw` → `line.ingredients()`의 번호마다 `NumberRanking.rank(stats, metric)`에서 찾은 `value`가 근거 문구 속 숫자와 같다(테스트 안에서 문구의 숫자를 정규식으로 읽어 비교).
  - `통계_지표가_없는_전략은_빈_값이다()` — `forStrategy(PAIR|BALANCED|COMBINED|CONTROL)` empty.

- [ ] **Step 2: 실패 확인** — `cd apps/api && ./gradlew test --tests '*NumberRankingTest'` → 컴파일 실패.
- [ ] **Step 3: 구현** — 위 시그니처대로. `rank`는 정렬 후 앞 항목과 값이 같으면 같은 rank, 다르면 `index + 1`; `tied`는 같은 값의 항목이 둘 이상인지.
- [ ] **Step 4: 통과 확인** — 같은 명령 PASS, 이어서 `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `Feat(api) : 번호 통계 지표별 순위 계산 추가`

---

### Task 2: 추천 줄의 근거 번호 저장 · 응답 노출

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V9__evidence_numbers.sql` — `ALTER TABLE daily_recommendation ADD COLUMN evidence_numbers VARCHAR(20) NOT NULL DEFAULT '';`
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/EvidenceNumbers.java`
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/entity/EvidenceNumbersConverter.java`
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/LegacyEvidenceBackfill.java`
- Create: `apps/api/src/main/java/db/migration/V10__Backfill_evidence_numbers.java`
- Modify: `recommendation/entity/DailyRecommendation.java` (필드·빌더 파라미터 `List<Integer> evidenceNumbers`)
- Modify: `recommendation/service/DailyRecommendationService.java` (`row(...)`에서 저장)
- Modify: `recommendation/dto/RecommendationLineResponse.java`, `result/dto/RecommendationHistoryLineResponse.java`, `result/service/RecommendationHistoryService.java`
- Test: `recommendation/EvidenceNumbersTest.java`, `recommendation/repository/LegacyEvidenceBackfillTest.java`(`@DataJpaTest`, `Replace.NONE`), 기존 `DailyRecommendationServiceTest`·`RecommendationControllerTest`·기록 테스트 보강

**Interfaces:**
- Consumes: `NumberMetric.forStrategy`, `DrawnLine.ingredients()`
- Produces:
  - `public final class EvidenceNumbers { public static List<Integer> of(DrawnLine line); public static List<Integer> fromLegacyEvidence(List<String> evidence); }` — `of`: 지표 있는 전략이면 ingredients 번호를 rank 순으로 펼침, 아니면 `List.of()`. `fromLegacyEvidence`: 정규식 `^번호 (\d+): `에 맞는 줄의 번호만 순서대로.
  - 엔티티 `public List<Integer> getEvidenceNumbers()`(Lombok `@Getter`), 컬럼 형식 `"7,12"`(빈 목록은 `""`).
  - `RecommendationLineResponse(StrategyType strategy, List<Integer> numbers, List<String> evidence, Relaxation relaxation, List<Integer> evidenceNumbers, NumberMetric statsMetric)` — `statsMetric`은 `forStrategy(...).orElse(null)`.
  - `RecommendationHistoryLineResponse`에도 같은 두 필드를 `relaxation` 뒤에 추가.
  - `public final class LegacyEvidenceBackfill { public static int run(Connection connection) throws SQLException; }` — `strategy IN ('RECENT_HOT','LONG_COLD','ALL_TIME') AND evidence_numbers = ''`인 행을 읽어 `evidence` JSON을 파싱(`EvidenceConverter`와 같은 Jackson)하고 채운다. 바꾼 행 수 반환. V10은 `run(context.getConnection())`만 호출.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `EvidenceNumbersTest`
    - `근거_번호는_전략_근거_순서대로_펼친다()` — `DrawnLine(RECENT_HOT, …, ingredients=[Ingredient([12],0), Ingredient([7],1)])` → `[12, 7]`.
    - `통계_지표가_없는_전략은_근거_번호를_저장하지_않는다()` — PAIR 줄(ingredient 쌍 있음) → `[]`.
    - `옛_근거_문구에서_번호_접두어만_읽는다()` — `["번호 7: 최근 50회 중 4회 출현", "번호 12: 최근 50회 중 3회 출현", "겹침 제한을 완화함"]` → `[7, 12]`.
    - `번호_접두어가_없으면_빈_목록이다()` — `["균형 조건 미충족"]` → `[]`.
  - `LegacyEvidenceBackfillTest` — JdbcTemplate으로 V9 이후 상태 행 3개 삽입(RECENT_HOT 근거 2개, LONG_COLD 근거 1개+완화 메모, PAIR) 후 `run` → 반환 2, 앞 두 행 `"7,12"`·`"30"`, PAIR는 `""`. 두 번 실행하면 0(재실행 안전).
  - `DailyRecommendationServiceTest` — 생성 저장 시 RECENT_HOT 행의 `evidenceNumbers`가 엔진 줄의 ingredients 번호와 같다.
  - `RecommendationControllerTest` — 응답 `lines[0].evidenceNumbers`, `lines[0].statsMetric == "RECENT"`, PAIR 줄 `statsMetric`은 null.
  - 기록 API 테스트 — 기록 줄에도 `evidenceNumbers`, `statsMetric`.
- [ ] **Step 2: 실패 확인** — `./gradlew test` → 해당 테스트 실패/컴파일 실패.
- [ ] **Step 3: 구현** — 엔티티는 `@Immutable` 그대로(갱신은 V10 JDBC로만). 기존 테스트의 레코드 생성자 호출은 새 필드에 맞게 고친다.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS. `./gradlew bootRun`(local, H2)로 기동해 Flyway V9·V10 적용 로그 확인.
- [ ] **Step 5: 커밋** — `Feat(api) : 추천 줄 근거 번호 저장과 응답 추가`

---

### Task 3: 번호 통계 API

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/statistics/controller/NumberStatisticsController.java`
- Create: `apps/api/src/main/java/com/lotterypicker/statistics/service/NumberStatisticsService.java`
- Create: `apps/api/src/main/java/com/lotterypicker/statistics/dto/NumberStatisticsResponse.java`, `RankedNumberResponse.java`
- Modify: `round/repository/LottoRoundRepository.java` (`List<LottoRound> findByRoundNoBetweenOrderByRoundNoAsc(int from, int to)`)
- Test: `statistics/service/NumberStatisticsServiceTest.java`(Mockito), `statistics/controller/NumberStatisticsControllerTest.java`(`@WebMvcTest`), `round/repository` 기존 테스트에 범위 조회 1건

**Interfaces:**
- Consumes: `NumberRanking.rank`, `NumberMetric`, `NumberStatistics.from`, `DailyRecommendationService.toHistory(List<LottoRound>)`, `DailyRecommendationRepository.findByDateOrderByLineIndexAsc` / `findFirstByDateLessThanEqualOrderByDateDesc`
- Produces:
  - `GET /api/v1/number-statistics?date=YYYY-MM-DD` (`date` 선택)
  - `public record RankedNumberResponse(int number, int value, int rank, boolean tied)`
  - `public record NumberStatisticsResponse(LocalDate date, int historyFromRound, int historyToRound, int totalRounds, int recentWindow, Map<NumberMetric, List<RankedNumberResponse>> metrics)` — `metrics`는 `EnumMap`, 키 3개 모두, 각 45개 순위 순.
  - `public Optional<NumberStatisticsResponse> find(@Nullable LocalDate date)` — date 있음: 그날 추천 없으면 `BusinessException(COMMON_NOT_FOUND)`. date 없음: 오늘(`Clock`) 이하 가장 최근 추천, 없으면 `Optional.empty()`(→204). 범위는 그날 첫 비대조군 행의 from/to.

- [ ] **Step 1: 실패하는 테스트 작성**
  - 서비스: `추천에_저장된_회차_범위만으로_계산한다()` — 저장소가 1~3회를 주고 추천 범위가 1~2 → `findByRoundNoBetweenOrderByRoundNoAsc(1, 2)`로 호출, `totalRounds == 2`, 3회에만 나온 번호의 ALL_TIME 값 0.
  - 서비스: `날짜를_주면_그날_추천_기준이다()`, `날짜가_없으면_가장_최근_추천_기준이다()`, `지정한_날짜에_추천이_없으면_COMMON_NOT_FOUND다()`, `추천이_하나도_없으면_빈_값이다()`.
  - 서비스: `세_지표_모두_45개씩_준다()` — `metrics.keySet() == {RECENT, COLD, ALL_TIME}`, 각 크기 45, `recentWindow == 50`.
  - 컨트롤러: 200 + `$.data.metrics.RECENT[0].rank == 1`; 빈 값 → 204; 404 → `$.error.code == "COMMON_NOT_FOUND"`; `date=abc` → 400.
  - 리포지토리: 범위 조회가 양끝 포함, 오름차순.
- [ ] **Step 2: 실패 확인** — `./gradlew test` 실패.
- [ ] **Step 3: 구현** — 서비스 클래스 `@Transactional(readOnly = true)`. 컨트롤러는 `RecommendationController`와 같은 패턴(`Optional` → 200/204).
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `Feat(api) : 추천 기준 번호 통계 조회 API 추가`

---

### Task 4: 웹 — 번호 통계 페이지

**Files:**
- Create: `apps/web/types/api/statistics.ts`
- Modify: `apps/web/types/api/recommendation.ts`, `apps/web/types/api/result.ts` (`evidenceNumbers: number[]`, `statsMetric: NumberMetric | null`)
- Create: `apps/web/app/numbers/page.tsx` (서버 컴포넌트, 제목 "번호 통계", 클라이언트 컴포넌트를 `Suspense`로 감쌈 — `cacheComponents`에서 `useSearchParams`에 필요)
- Create: `apps/web/components/statistics/NumberStatistics.tsx` (`"use client"`)
- Create: `apps/web/lib/number-stats-link.ts`
- Test: `apps/web/components/statistics/NumberStatistics.test.tsx`, `apps/web/lib/number-stats-link.test.ts`

**Interfaces:**
- Consumes: `GET /api/v1/number-statistics?date=`
- Produces:
  - `types/api/statistics.ts`: `NumberMetric = "RECENT" | "COLD" | "ALL_TIME"`, `RankedNumberResponse`, `NumberStatisticsResponse`(API와 같은 필드).
  - `lib/number-stats-link.ts`: `numberStatsHref(date: string, metric: NumberMetric, highlight: number[]): string` → `/numbers?date=2026-11-10&metric=RECENT&highlight=7,12`; `parseNumberStatsParams(params: URLSearchParams): { date: string | null; metric: NumberMetric; highlight: number[] }` — metric이 셋 중 아니면 `"RECENT"`, highlight는 1~45 정수만.
  - React Query 키 `["number-statistics", date ?? "latest"]`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `number-stats-link.test.ts`: `it("builds a link with date, metric and highlighted numbers")`; `it("falls back to RECENT for an unknown metric")`(`metric=PAIR`); `it("keeps only whole numbers between 1 and 45 in highlight")`(`highlight=abc,99,7,0,12` → `[7, 12]`); `it("returns null date when date is missing")`.
  - `NumberStatistics.test.tsx`(fetch 모킹, `next/navigation`의 `useSearchParams` 모킹):
    - `it("shows rows in the order the server sent")` — RECENT 배열 순서대로 번호가 나온다.
    - `it("opens the tab given by the metric parameter")` — `metric=COLD`면 미출현 탭이 선택(`aria-selected`/`aria-pressed`)되고 COLD 배열이 보인다.
    - `it("switches metric when another tab is clicked")`.
    - `it("marks highlighted numbers with their rank")` — highlight 7, rank 2 tied false → "2위", tied true → "공동 2위".
    - `it("shows the basis date and round range")` — "11월 10일 추천 기준 · 1~1199회".
    - `it("labels the recent tab with the window from the server")` — "최근 50회 출현".
    - `it("explains when the recommendation for the date does not exist")` — 404 → "그날 추천이 없습니다." 류 문구.
    - `it("explains when there is no recommendation yet")` — 204.
- [ ] **Step 2: 실패 확인** — `cd apps/web && pnpm test` 실패.
- [ ] **Step 3: 구현**
  - 탭 3개(라벨: `최근 ${recentWindow}회 출현`, `미출현 회차`, `전체 출현`), 행: `LottoBall size="sm"`, 막대, 값(RECENT·ALL_TIME `N회`, COLD `N회째`), 강조 행에만 순위. 막대는 인라인 스타일 없이 `<meter min={0} max={그 지표의 첫 값(최댓값)} value={value}>`를 Tailwind 의사요소 변형으로 꾸민다. 강조는 기존 주황 계열 토큰(`slip` 등 `globals.css` 확인).
  - 하단 탭바는 건드리지 않는다.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test` PASS.
- [ ] **Step 5: 커밋** — `Feat(web) : 번호 통계 화면 추가`

---

### Task 5: 웹 — 근거 영역 공용화와 "통계에서 보기" 링크

**Files:**
- Create: `apps/web/components/recommendation/EvidencePanel.tsx`
- Modify: `apps/web/components/recommendation/RecommendationCard.tsx` (`SlipLine`의 근거 `<ul>`을 `EvidencePanel`로)
- Modify: `apps/web/components/history/RecommendationHistory.tsx` (`HistoryLine`에 근거 토글 버튼 + `EvidencePanel`)
- Test: `RecommendationCard.test.tsx`, `RecommendationHistory.test.tsx` 보강, `EvidencePanel.test.tsx`

**Interfaces:**
- Consumes: `numberStatsHref`(Task 4), 응답의 `evidenceNumbers`·`statsMetric`(Task 2)
- Produces: `EvidencePanel({ id, open, evidence, statsHref }: { id: string; open: boolean; evidence: string[]; statsHref: string | null })` — `hidden={!open}`, `statsHref`가 있으면 끝에 `<Link href={statsHref}>통계에서 보기</Link>`.
  - 링크 조건: `line.statsMetric !== null && line.evidenceNumbers.length > 0`일 때 `numberStatsHref(date, line.statsMetric, line.evidenceNumbers)`, 아니면 null. date는 추천 카드는 `data.date`, 기록은 그 날짜.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `EvidencePanel.test.tsx`: `it("renders evidence and a stats link when href is given")`, `it("renders no link when href is null")`.
  - `RecommendationCard.test.tsx`: `it("links recent-hot evidence to the number statistics of that day")` — 근거 펼침 후 링크 href가 `/numbers?date=…&metric=RECENT&highlight=…`; `it("shows no stats link for pair, balanced and combined lines")`.
  - `RecommendationHistory.test.tsx`: `it("toggles evidence on a past line")` — "근거" 버튼 클릭 시 문구 노출; `it("links past evidence to the statistics of that date")`.
- [ ] **Step 2: 실패 확인** — `pnpm test` 실패.
- [ ] **Step 3: 구현** — 기존 `SlipLine` 버튼·스타일 유지. 기록 줄 토글은 같은 버튼 모양.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test && pnpm build` PASS. 이어서 `/run`(로컬 API+웹)으로 추천 카드 → 근거 → 통계에서 보기 → 강조 번호·탭 확인.
- [ ] **Step 5: 커밋** — `Feat(web) : 추천 근거에서 번호 통계로 가는 링크 추가`

---

### 마무리 (CLAUDE.md 절차)
- `/code-review low feat/number-statistics` — DB 마이그레이션(V9·V10)이 운영 데이터를 바꾸므로 강도를 올릴지 리뷰 전에 사용자에게 제안.
- 동작 버그 없으면 기록 후 `dev` 병합.
