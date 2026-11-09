# 직전 추천 유지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 오늘 추천이 아직 없으면(0시~17시) 가장 최근 추천을 화면에 보여 준다.

**Architecture:** API가 "오늘 것, 없으면 `rec_date <= 오늘` 중 가장 최근 것"을 골라 주고, 응답에 `carriedOver`(오늘 것이 아님)와 `roundClosed`(그 추천의 회차가 이미 추첨됨) 불리언을 싣는다. 웹은 두 값에 따라 안내 한 줄만 덧붙인다.

**Tech Stack:** Spring Boot 4.1 / JPA, Next.js 16 / React Query / Vitest

**Spec:** `docs/superpowers/specs/2026-11-10-recommendation-carryover-combined-pwa-design.md` 1장

## Global Constraints

- 브랜치: `dev`에서 `feat/recommendation-carryover`를 딴다.
- `date` 파라미터를 명시한 호출은 지금처럼 그 날짜만 찾는다(오늘 이후 날짜 포함). 추천이 하나도 없으면 204.
- 추천은 생성 후 수정하지 않는다. 회차 번호는 저장된 값 그대로 응답한다.
- 판단은 서버가 한다. 웹은 날짜 비교로 "오늘 것인지"를 계산하지 않는다.
- "당첨 확률" 류 표현 금지.
- 커밋은 사용자가 요청할 때만, `.gitmessage.txt` 형식.

## Review Focus

- 서버가 며칠 꺼져 있어 직전 추천이 일주일 넘게 오래됨 → 기간 제한 없이 그 추천을 보여 준다 (Task 1 테스트 `오래된_추천도_돌려준다`).
- 오늘 추천은 있는데 더 앞선 날짜도 있음 → 반드시 오늘 것 (Task 1 `오늘_추천이_있으면_오늘_것이다`).
- 가장 최근 날짜에 대조군 줄만 있음(비정상 데이터) → 더 이전 날짜를 찾지 않고 204 (Task 1 `가장_최근_날짜에_추천_줄이_없으면_204이다`) — 단순성을 위한 [판단].
- 금요일 추천을 토요일 낮에 보여 줌 → 아직 판매 중이므로 `roundClosed=false` (Task 1 `같은_주_토요일까지는_마감이_아니다`).
- 일요일 아침 토요일 추천 → `roundClosed=true` (Task 1 `지난_토요일_추천은_마감이다`).

---

### Task 1: API — 가장 최근 추천 조회

**Files:**
- Modify: `apps/api/src/main/java/com/lotterypicker/recommendation/repository/DailyRecommendationRepository.java`
- Modify: `apps/api/src/main/java/com/lotterypicker/recommendation/dto/RecommendationSetResponse.java`
- Modify: `apps/api/src/main/java/com/lotterypicker/recommendation/service/DailyRecommendationService.java`
- Modify: `apps/api/src/main/java/com/lotterypicker/recommendation/controller/RecommendationController.java`
- Test: `apps/api/src/test/java/com/lotterypicker/recommendation/controller/RecommendationControllerTest.java`, `.../repository/DailyRecommendationRepositoryTest.java`

**Interfaces:**
- Produces (JSON): `RecommendationSetResponse`에 필드 2개 추가 — `carriedOver: boolean`, `roundClosed: boolean`. 기존 필드 순서 뒤에 붙인다.
- Produces (Java):
  - `Optional<DailyRecommendation> findFirstByDateLessThanEqualOrderByDateDesc(LocalDate date)` (repository)
  - `Optional<RecommendationSetResponse> findLatest(LocalDate today)` (service) — 오늘 줄이 있으면 오늘, 없으면 가장 최근 날짜. `carriedOver = !date.equals(today)`, `roundClosed = RoundCalendar.targetSaturday(date).isBefore(today)`.
  - 기존 `find(LocalDate date)`는 `carriedOver=false`, `roundClosed=false`로 응답(명시한 날짜 조회).
- Controller: `date == null` → `findLatest(service.today())`, 아니면 `find(date)`.

- [ ] **Step 1: 실패하는 테스트 작성** — `RecommendationControllerTest`에 추가 (기존 `storedSet`, `line` 헬퍼, `TODAY=2026-11-06`(금) 사용):
  - `오늘_추천이_없으면_가장_최근_추천이다`: `findByDateOrderByLineIndexAsc(TODAY)` → 빈 목록, `findFirstByDateLessThanEqualOrderByDateDesc(TODAY)` → 2026-11-05 줄, `findByDateOrderByLineIndexAsc(2026-11-05)` → `storedSet(2026-11-05)`. 기대: 200, `$.data.date == "2026-11-05"`, `$.data.carriedOver == true`, `$.data.roundClosed == false`.
  - `오늘_추천이_있으면_오늘_것이다`: 오늘 `storedSet(TODAY)`. 기대 `$.data.date == "2026-11-06"`, `$.data.carriedOver == false`.
  - `지난_토요일_추천은_마감이다`: 시계를 바꾸지 않고, 가장 최근이 2026-10-31(토). 기대 `$.data.roundClosed == true`, `$.data.round == 1249`(저장값 그대로).
  - `같은_주_토요일까지는_마감이_아니다` (`DailyRecommendationServiceTest`, Mockito 단위): `findLatest(2026-11-07)`(토), 오늘 줄 없음, 가장 최근 2026-11-06 → `roundClosed()==false`, `carriedOver()==true`.
  - `오래된_추천도_돌려준다`: 가장 최근이 2026-10-20. 기대 200, `$.data.date == "2026-10-20"`.
  - `추천이_하나도_없으면_204이다`: 둘 다 빈 값 → 204, 본문 빈 문자열.
  - `가장_최근_날짜에_추천_줄이_없으면_204이다`: 최근 날짜 조회는 값이 있지만 그 날짜 줄이 대조군뿐 → 204.
  - `날짜를_명시하면_그_날짜만_찾는다`: `param("date","2026-11-06")`, 그 날짜 빈 목록 → 204이고 `findFirstByDateLessThanEqualOrderByDateDesc`는 호출되지 않는다(`then(repository).should(never())`).
  - 기존 `날짜를_생략하면_오늘이다`는 `carriedOver == false` 단언을 추가해 유지.
- [ ] **Step 2: 실패 확인** — `cd apps/api && ./gradlew test --tests '*RecommendationControllerTest' --tests '*DailyRecommendationServiceTest'` → 새 테스트 FAIL(컴파일 실패 포함).
- [ ] **Step 3: repository 메서드 테스트** — `DailyRecommendationRepositoryTest`에 `가장_최근_날짜의_줄_하나를_찾는다`: 11-04, 11-05, 11-07 저장 후 `findFirstByDateLessThanEqualOrderByDateDesc(2026-11-06)` → 날짜 11-05.
- [ ] **Step 4: 구현** — 위 Interfaces 그대로. `find`와 `findLatest`가 줄→응답 변환을 공유하도록 private 메서드 `toResponse(List<DailyRecommendation> lines, LocalDate today)` 하나로 합친다(`find`는 today 자리에 날짜 자신을 넘기면 두 불리언이 false가 된다 — 이 방식이면 `find`의 응답 의미가 자연히 맞다).
- [ ] **Step 5: 통과 확인** — `./gradlew test` 전체 PASS.

### Task 2: 웹 — 직전 추천 안내

**Files:**
- Modify: `apps/web/types/api/recommendation.ts` (`RecommendationSetResponse`에 `carriedOver: boolean; roundClosed: boolean;`)
- Modify: `apps/web/components/recommendation/RecommendationCard.tsx`
- Test: `apps/web/components/recommendation/RecommendationCard.test.tsx` (기존 `set` 픽스처에 두 필드 `false` 추가)

**Interfaces:**
- Consumes: Task 1의 `carriedOver`, `roundClosed`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `it("explains that today's set comes at 5 pm when showing an earlier set")`: `{...set, date: "2026-11-05", carriedOver: true}` → `screen.getByText(/오늘 추천은 오후 5시에 나옵니다/)`와 `/11월 5일 추천/`이 보인다. 번호 5줄도 그대로 보인다.
  - `it("notes the numbers can still be bought when the round is closed")`: `carriedOver: true, roundClosed: true` → `/이번 회차에도 살 수 있습니다/`.
  - `it("shows no carry-over notice for today's set")`: 기본 `set` → `queryByText(/오후 5시에 나옵니다/)`가 null.
  - 204일 때 기존 빈 상태 문구 테스트는 그대로 유지.
- [ ] **Step 2: 실패 확인** — `cd apps/web && pnpm test -- RecommendationCard` → 새 테스트 FAIL.
- [ ] **Step 3: 구현** — 카드 위 안내 `<p>` 한 개. 문구:
  - `carriedOver`: `오늘 추천은 오후 5시에 나옵니다. 아래는 {M}월 {D}일 추천입니다.` (M/D는 `data.date` 문자열 `YYYY-MM-DD`를 잘라 숫자로; 날짜 계산은 하지 않는다)
  - 추가로 `roundClosed`: `{round}회는 추첨이 끝났지만, 번호는 이번 회차에도 살 수 있습니다.`
  - 스타일은 기존 알림 실패 배지(`rounded-md bg-slip-soft px-3 py-2 text-sm`) 계열을 따르되 색은 `text-muted` 톤으로 덜 강하게.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test` PASS.

### Task 3: 마무리 검증

- [ ] `apps/api` `./gradlew test`, `apps/web` `pnpm lint && pnpm test` 모두 PASS 확인 후 사용자에게 결과 보고. 커밋·리뷰·병합은 CLAUDE.md 절차대로 사용자 요청 시.
