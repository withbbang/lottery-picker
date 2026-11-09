# 근거 조합 전략 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 다섯 전략의 근거 번호를 모아 만든 "근거 조합"(`COMBINED`) 줄을 하루 최대 5줄 생성·저장·표시·알림·채점·시뮬레이션한다.

**Architecture:** 각 전략이 근거로 꼽은 번호를 문자열이 아닌 구조(`Ingredient`)로 `DrawnLine`에 함께 싣는다. 새 순수 함수 `CombinedLines.combine`이 재료 묶음의 부분집합을 전수 조사해 6개짜리 조합을 만들고 점수순으로 최대 5개 고른다. 엔진은 결과를 `GeneratedSet.combined`로 따로 돌려주고, 저장은 기존 테이블에 `line_index` 5~9로 넣는다.

**Tech Stack:** Spring Boot 4.1 / JPA / JUnit 5 + AssertJ, Next.js 16 / Vitest

**Spec:** `docs/superpowers/specs/2026-11-10-recommendation-carryover-combined-pwa-design.md` 2장

## Global Constraints

- 브랜치: `dev`에서 `feat/combined-strategy`. 이 기능은 "직전 추천 유지" 병합 뒤에 시작한다(같은 `RecommendationCard`를 고침).
- 하루 최대 5줄. 재료로 만들 수 있는 조합이 적으면 그만큼만, 0이면 없음. 다른 번호로 보충하지 않는다.
- 재료: `RECENT_HOT`·`LONG_COLD`·`ALL_TIME`은 근거 문구에 나온 번호(최대 2개)를 각각 단독 재료로. `PAIR`는 근거 첫 쌍 하나만, 두 번호를 늘 함께 넣는 묶음. `BALANCED`는 재료 없음. 고정 번호는 모든 조합에 들어가는 필수 묶음.
- 재료 번호는 근거 문구와 정확히 일치해야 한다(같은 선택 로직에서 나와야 함).
- 결정적: 같은 입력이면 같은 결과. 난수를 쓰지 않는다.
- 실제 추천: 인기 조합 필터(`PopularityFilter.isPopular`, 과거 1등 포함)에 걸린 조합은 건너뛴다. 기존 5줄과의 겹침 제한은 적용하지 않는다.
- 시뮬레이션: 필터 없이(기존 `SCOPE_NOTE` 원칙). `METHOD_VERSION` 2 → 3.
- 표시 이름 "근거 조합", 설명 "각 전략 근거 번호를 모은 조합".
- "당첨 가능성이 높다" 류 표현 금지.
- 커밋은 사용자가 요청할 때만, `.gitmessage.txt` 형식.

## 스펙과 다르게 정한 것 (사용자 확인 대상)

- `line_index`: 스펙은 0~4였으나 5~9로 저장한다. `findByDateOrderByLineIndexAsc`가 날짜 단위로 정렬하므로 0~4면 기존 5줄과 섞여 정렬된다.
- "재료가 모자라 조합을 만들지 못했습니다" 안내는 넣지 않는다. 기능 이전 날짜의 추천과 구별할 수 없어 옛 추천에도 잘못 뜬다. 근거 조합 줄이 없으면 그 용지를 그리지 않는다.

## Review Focus

- 고정 번호 5개 설정 → 재료 묶음 중 크기 1인 것 하나만 더하면 6개, 쌍(크기 2)은 들어갈 수 없음 → 단독 재료 수만큼 줄 (Task 2 `고정_5개면_단독_재료마다_한_줄이다`).
- 같은 번호가 두 전략 근거에 동시에 나옴, 또는 PAIR 쌍의 한 번호가 다른 전략의 단독 재료와 같음 → 중복 번호로 6개 미만 조합이 생기지 않고, 근거에 출처가 모두 적힌다 (Task 2 `겹치는_재료는_한_번만_세고_출처를_모두_적는다`).
- 재료가 6개 미만(예: 근거 가중치가 모두 1이라 근거가 비어 있음) → 0줄, 예외 없음 (Task 2 `재료가_6개_미만이면_빈_목록이다`).
- 인기 조합 필터에 모든 후보가 걸림 → 0줄 (Task 2 `모두_인기_조합이면_빈_목록이다`).
- 시뮬레이션에서 어떤 회차의 근거 조합 줄이 0개 → 집계가 0으로 나누지 않음 (Task 5 `조합이_없는_회차가_있어도_집계한다`).

---

### Task 1: 전략이 재료를 구조로 내놓는다

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/engine/Ingredient.java`
- Modify: `.../engine/DrawnLine.java`, `.../engine/WeightedSampler.java`, `.../engine/RecentHotStrategy.java`, `LongColdStrategy.java`, `AllTimeStrategy.java`, `PairStrategy.java`
- Test: `apps/api/src/test/java/com/lotterypicker/recommendation/engine/WeightedSamplerTest.java`, `RecommendationEngineTest.java`(또는 새 `IngredientTest.java`)

**Interfaces:**
- Produces:
  - `public record Ingredient(List<Integer> numbers, int rank)` — `numbers` 크기 1 또는 2(오름차순), `rank`는 그 전략 안 근거 순위(0이 가장 강함).
  - `DrawnLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence, List<Ingredient> ingredients)` + 기존 3인자 생성자 유지(재료 빈 목록). `BALANCED`, `CONTROL`은 3인자 그대로.
  - `WeightedSampler.Sample.ingredients()` — `evidence(...)`와 **같은** 정렬·limit·필터(가중치 내림차순, 최대 2개, 가중치 > 1)로 고른 pick을 순서대로 `new Ingredient(List.of(number), index)`. 두 메서드가 private 헬퍼 `topPicks()` 하나를 공유한다.
  - `PairStrategy`: 근거 문구로 쓰인 첫 `PairFact`(정렬·limit·count>0 필터 후 첫 원소)를 `new Ingredient(List.of(min, max), 0)` 하나로. 근거가 비면 빈 목록.

- [ ] **Step 1: 실패하는 테스트**
  - `재료는_근거_문구의_번호와_같다`: 시드 고정(`TestHistories.seeded(…)`, `new Random(7)`)으로 RECENT_HOT·LONG_COLD·ALL_TIME 각각 `draw` → `ingredients()`의 번호들 == 근거 문구 `"번호 (\\d+):"`에서 뽑은 번호들(같은 순서), `rank`는 0,1….
  - `쌍_재료는_첫_근거_쌍이다`: PAIR `draw` → `ingredients()` 크기 1, 번호 2개 == 첫 근거 문구 `"(\\d+)·(\\d+) 쌍"`.
  - `균형_전략은_재료가_없다`: BALANCED → 빈 목록.
  - `고정_번호는_재료에_없다`: `fixed={7}` → 어떤 전략의 재료에도 7 없음(고정은 근거에서 빠지는 기존 규칙과 일치).
- [ ] **Step 2: 실패 확인** — `cd apps/api && ./gradlew test --tests '*engine*'` → FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces 대로. 기존 근거 문구는 바뀌지 않아야 한다(기존 테스트 `근거_문구_형식이_맞다` 등 그대로 통과).
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 2: 조합 생성 `CombinedLines`

**Files:**
- Create: `apps/api/src/main/java/com/lotterypicker/recommendation/engine/CombinedLines.java`
- Modify: `.../engine/StrategyType.java` — `COMBINED`를 enum **맨 끝**(`CONTROL` 뒤)에 추가. 시뮬레이션 시드가 `ordinal()`을 쓰므로 기존 전략의 ordinal을 바꾸지 않기 위함(Task 5).
- Test: `apps/api/src/test/java/com/lotterypicker/recommendation/engine/CombinedLinesTest.java`

**Interfaces:**
- Consumes: Task 1 `Ingredient`, `DrawnLine.ingredients()`.
- Produces: `public static List<DrawnLine> combine(List<DrawnLine> sources, Set<Integer> fixed, Predicate<LottoNumbers> rejected)` — 결과 각 줄은 `strategy = COMBINED`, 재료 빈 목록, 최대 5개. 실제 추천은 `rejected = n -> PopularityFilter.isPopular(n, winners)`, 시뮬레이션은 `n -> false`.
- `public static final int MAX_LINES = 5;`

**알고리즘 (테스트가 정하지 못하는 부분):**
1. 묶음 만들기: `sources` 순서대로 각 재료를 묶음으로. 같은 번호 집합의 묶음은 하나로 합치고 출처를 모은다. 묶음 점수 = 출처마다 `(2 - rank)`를 더한 값을 묶음 안 번호 수로 곱한 것(단독 rank0=2, rank1=1; 쌍 rank0=번호당 2 → 4). 고정 번호는 필수 묶음, 점수 0.
2. 후보: 필수 묶음을 포함하는 모든 묶음 부분집합(묶음 최대 7개 → 128가지)의 번호 합집합이 정확히 6개이면 후보. 같은 번호 집합은 한 번만(점수는 그 집합을 만드는 방법 중 최대). 후보 점수 = 고른 묶음 점수 합.
3. `rejected` 후보 제거.
4. 선택: 최대 5번 반복, 남은 후보 중 (점수 내림차순, 이미 고른 줄과의 최대 겹침 오름차순, 번호 목록 사전순)으로 첫 번째를 고른다.
5. 근거 문구 한 줄: 번호 묶음별 `"7(최근 빈도)"`, 쌍은 `"12·34(번호 쌍)"`, 출처 여러 개면 `"7(최근 빈도·전체 빈도)"`, 고정 번호는 `"5(고정)"`, 오름차순(묶음의 최소 번호 기준)으로 `" · "` 연결. 라벨은 `CombinedLines` 안 private 맵(`MessageFormatter.label`과 같은 문자열).

- [ ] **Step 1: 실패하는 테스트** (입력 `DrawnLine`을 직접 만들어 넣는다):
  - `재료_7개면_최대_5줄이다`: 단독 6개 + 쌍 0 + 단독 1 → 7개 번호, `hasSize(5)`, 모두 `COMBINED`, 각 줄 번호 6개.
  - `재료가_정확히_6개면_한_줄이다`.
  - `재료가_6개_미만이면_빈_목록이다`.
  - `쌍은_두_번호가_함께_들어간다`: 결과 모든 줄에서 쌍 번호는 둘 다 있거나 둘 다 없다.
  - `점수가_높은_조합이_먼저다`: rank0 재료를 모두 포함한 조합이 첫 줄.
  - `같은_입력이면_같은_결과다`.
  - `고정_번호는_모든_줄에_있다`.
  - `고정_5개면_단독_재료마다_한_줄이다`: 고정 5 + 단독 3 + 쌍 1 → 3줄.
  - `겹치는_재료는_한_번만_세고_출처를_모두_적는다`: RECENT_HOT과 ALL_TIME 모두 7 → 근거에 `"7(최근 빈도·전체 빈도)"`.
  - `거부된_조합은_건너뛴다`, `모두_인기_조합이면_빈_목록이다`.
  - `근거_문구_형식이_맞다`: 예시 입력에 대해 정확한 문자열 단언.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*CombinedLinesTest'` → FAIL.
- [ ] **Step 3: 구현.** `StrategyType`에 값을 추가하면 `MessageFormatter.label`의 switch가 컴파일 오류가 난다 → 이 Task에서 `case COMBINED -> "근거 조합";`만 추가해 컴파일을 맞춘다(알림 본문 변경은 Task 3).
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체. `SimulationService` 테스트가 `StrategyType.values()` 순회 때문에 깨지면 Task 5 전까지 `COMBINED`를 순회에서 건너뛰는 최소 수정만 하고 그 사실을 보고에 적는다.

### Task 3: 엔진·저장·알림 연결

**Files:**
- Modify: `.../engine/GeneratedSet.java` → `record GeneratedSet(List<GeneratedLine> lines, List<DrawnLine> combined, List<DrawnLine> control)`
- Modify: `.../engine/RecommendationEngine.java` — 5줄 확정 후 `CombinedLines.combine(최종 5줄의 DrawnLine, c.fixed(), n -> PopularityFilter.isPopular(n, winners))`.
- Modify: `.../service/DailyRecommendationService.java` — `toRows`에서 근거 조합 줄은 `lineIndex = 5 + i`, `Relaxation.NONE`, 대조군 아님. 반환 `RecommendationSet`에도 포함(기존 5줄 뒤).
- Modify: `apps/api/src/main/java/com/lotterypicker/notification/MessageFormatter.java` — `recommendation`: 기존 `<pre>` 블록 뒤에 근거 조합 줄이 있으면 `"\n\n근거 조합\n<pre>…</pre>"`(같은 `line` 포맷). 기존 블록에서는 `COMBINED`를 뺀다.
- Test: `RecommendationEngineTest`, `DailyRecommendationServiceTest`, `MessageFormatterTest`(존재하는 테스트 파일에 추가), `RecommendationControllerTest`

- [ ] **Step 1: 실패하는 테스트**
  - 엔진 `근거_조합은_최종_다섯_줄의_재료로_만든다`: 시드 고정 → `set.combined()`의 모든 번호 ⊆ (다섯 줄 재료 번호 ∪ 고정).
  - 엔진 `근거_조합에는_인기_조합이_없다`.
  - 엔진 기존 `전략별로_한_줄씩_다섯_줄을_만든다`는 `set.lines()` 5줄 그대로 통과해야 함.
  - 서비스 `근거_조합_줄은_line_index_5부터_저장한다`: `saveAll` 인자 캡처 → `COMBINED` 줄의 `lineIndex`가 5,6,…, `isControl()==false`.
  - 포매터 `근거_조합은_별도_블록이다`: 본문에 `"근거 조합\n<pre>"` 포함, 첫 `<pre>` 블록 안에는 근거 조합 줄 번호가 없음.
  - 포매터 `근거_조합이_없으면_블록을_넣지_않는다`.
  - 컨트롤러 `근거_조합_줄을_응답에_담는다`: 저장 줄에 COMBINED 2줄(lineIndex 5,6) 포함 → `$.data.lines` 7개, 마지막 두 개 `strategy == "COMBINED"`.
- [ ] **Step 2: 실패 확인** — `./gradlew test` → 새 테스트 FAIL.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS. 성적표(`StrategyPerformanceService`)는 `StrategyType.values()` 순회로 `COMBINED` 행이 자동으로 생긴다 — `StrategyPerformanceServiceTest`에 `근거_조합_행이_있다` 한 개 추가.

### Task 4: 웹 표시

**Files:**
- Modify: `apps/web/types/api/recommendation.ts` — `StrategyType`에 `"COMBINED"`.
- Modify: `apps/web/components/recommendation/RecommendationCard.tsx` — `STRATEGY_LABELS.COMBINED = "근거 조합"`, `STRATEGY_DESCRIPTIONS.COMBINED = "각 전략 근거 번호를 모은 조합"`. `lines`를 `COMBINED`와 나머지로 나눠, 기존 용지 아래 근거 조합 용지(머리 "근거 조합", 칸 A~)를 하나 더 그린다(0줄이면 그리지 않음). `SlipLine`의 `key`를 `` `${line.strategy}-${index}` ``로.
- Modify: `apps/web/components/performance/PerformanceTable.tsx`, `SimulationPanel.tsx` — 라벨 맵을 `STRATEGY_LABELS`에서 가져오므로 타입만 맞으면 된다. API 행 순서는 enum 순서라 `COMBINED`가 대조군 뒤에 온다 → 화면에서는 대조군 행을 맨 아래로 정렬한다(대조군이 항상 마지막이라는 단언을 테스트에 추가).
- Test: `RecommendationCard.test.tsx`, `PerformanceTable.test.tsx`, `SimulationPanel.test.tsx`

- [ ] **Step 1: 실패하는 테스트**
  - `it("shows combined lines on a separate slip")`: 픽스처에 COMBINED 2줄 추가 → `getByRole("heading", { name: /근거 조합/ })` 아래 2줄, 첫 용지는 여전히 5줄.
  - `it("hides the combined slip when there are no combined lines")`.
  - `it("lists the combined strategy in the performance table")`, 시뮬레이션 패널도 같은 형식으로 한 개.
- [ ] **Step 2: 실패 확인** — `cd apps/web && pnpm test` → FAIL.
- [ ] **Step 3: 구현.**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test` PASS.

### Task 5: 시뮬레이션

**Files:**
- Modify: `apps/api/src/main/java/com/lotterypicker/simulation/service/SimulationService.java`
- Test: `apps/api/src/test/java/com/lotterypicker/simulation/service/SimulationServiceTest.java`

**Interfaces:**
- `METHOD_VERSION = 3` (주석에 "3: 근거 조합 전략 추가" 한 줄).
- `run(...)` 회차 루프: `COMBINED`를 뺀 각 전략의 `DrawnLine` 목록(반복 수만큼)을 먼저 뽑아 집계하고, 반복 i마다 다섯 소스 전략의 i번째 줄로 `CombinedLines.combine(sources, Set.of(), n -> false)` → 나온 줄(0~5개)을 `COMBINED` 집계에 더한다. 회차 끝에 `endRound()`.
- 기존 `draw`는 `List<DrawnLine>`을 돌려주게 바꾸고 `linesFor`는 `.numbers()`로 매핑해 시그니처 유지.
- 시드: 기존 전략의 시드·줄은 바뀌지 않아야 한다(`COMBINED`가 enum 맨 끝이라 기존 ordinal 불변).
- `SCOPE_NOTE` 끝에 `" 근거 조합도 같은 조건에서 다섯 전략 줄의 근거로 만들었습니다."` 추가.

- [ ] **Step 1: 실패하는 테스트**
  - `근거_조합_결과가_있다`: 작은 이력(`TestHistories.seeded`)·반복 3 → 리포트에 `COMBINED` 행, `lines > 0`.
  - `근거_조합은_소스_전략의_같은_반복_줄로_만든다`: 한 회차에 대해 `linesFor`로 얻은 소스 줄들에서 직접 `combine`한 결과 줄 수 합 == `COMBINED.lines`.
  - `기존_전략_결과는_바뀌지_않는다`: 같은 시드로 `RECENT_HOT`·`CONTROL`의 `linesFor` 결과가 변경 전 기대값과 같음(변경 전에 값을 뽑아 테스트에 고정).
  - `조합이_없는_회차가_있어도_집계한다`.
  - `계산_방식_버전은_3이다`, 저장 결과 버전이 2면 다시 계산한다(기존 재계산 테스트 패턴 따라).
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*Simulation*'` → FAIL.
- [ ] **Step 3: 구현.** 성능: 기존 대비 전략 하나 추가 + 반복마다 조합 계산(최대 128 부분집합). 로컬에서 실제 이력 전체로 한 번 돌려 걸린 시간을 보고에 적는다.
- [ ] **Step 4: 통과 확인** — `./gradlew test` PASS.

### Task 6: 마무리 검증

- [ ] `apps/api` `./gradlew test`, `apps/web` `pnpm lint && pnpm test` 모두 PASS. 로컬에서 `./gradlew bootRun` + `pnpm dev`로 추천 생성 후 화면에 근거 조합 용지가 보이는지 확인(가능하면 스크린샷). 결과 보고. 커밋·리뷰·병합은 CLAUDE.md 절차대로 사용자 요청 시.
