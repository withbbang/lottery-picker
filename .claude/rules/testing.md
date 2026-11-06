---
paths:
  - "**/*.test.ts"
  - "**/*.test.tsx"
  - "**/*.spec.ts"
  - "**/*Test.java"
  - "**/*Tests.java"
  - "apps/web/e2e/**"
---
# 테스트 규칙

- 테스트 이름은 행동을 서술한다. Java: `추첨_결과는_중복없는_6개_오름차순이다()`, TS: `it("returns 6 unique numbers in ascending order")`.
- 추첨 로직 테스트는 **시드 고정**(Random 주입)으로 결정적으로 만든다. "여러 번 돌려서 대체로 맞다" 식의 확률적 단언 금지.
- 경계값 필수: `1`과 `45`가 나올 수 있는지, 보너스가 본번호와 겹치지 않는지, 고정 번호 5개(허용)와 6개(400), 제외 번호로 후보가 6개 미만일 때(400).
- 외부 I/O(DB, HTTP, 시계)는 모킹. 테스트에서 실제 네트워크 호출 금지.
- web: Vitest + Testing Library. 컴포넌트는 사용자 관점(`getByRole`, `getByText`)으로 쿼리, `data-testid`는 최후 수단.
- api: JUnit 5 + AssertJ. `assertThat(...)` 체인 사용, `assertEquals` 금지.
- 테스트 하나에 검증 주제 하나. given / when / then 주석으로 구획.
- 실패하는 테스트를 `skip` / `@Disabled` 로 덮지 않는다. 고치거나 이유를 적고 사용자에게 알린다.
