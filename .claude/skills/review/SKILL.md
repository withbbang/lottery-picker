---
name: review
description: 현재 변경분(git diff)을 프로젝트 체크리스트로 점검하고 심각도순으로 보고. 파일은 수정하지 않는다.
disable-model-invocation: true
---
# /review

`git diff HEAD` (스테이징 포함) 를 읽고 아래 순서로 점검한다. **코드를 고치지 않는다.**
변경이 없으면 "변경 없음" 한 줄로 끝낸다.

## 체크리스트
1. **정확성** — 로직 오류, 경계값(1 / 45, 빈 배열, null), 예외 처리 누락.
2. **도메인 규칙** — `lotto-domain.md` 위반: 프론트에서 추첨 계산, 중복 번호 허용, 보너스를 본번호에 섞음, 정렬 안 함.
3. **레이어 위반** — Controller 안 비즈니스 로직, Entity 외부 노출, `@Data` 사용, 서버 컴포넌트에 불필요한 `"use client"`.
4. **테스트** — 새 로직에 테스트가 있는가, 확률적 단언은 없는가, 외부 I/O를 실제로 호출하지 않는가.
5. **보안** — 입력 검증 누락, 비밀값 하드코딩, JPQL/SQL 문자열 결합, `NEXT_PUBLIC_`에 비밀값.
6. **규약** — `api-conventions` 응답 포맷·에러 코드 준수, 네이밍(`XxxResponse`, camelCase JSON).
7. **스타일** — `any`, 인라인 style, import 순서 등 rules 위반. 사소한 건 마지막에 한 줄로 묶는다.

## 출력 형식
심각도순(🔴 치명 / 🟠 중요 / 🟡 사소) 으로:
```
🔴 apps/api/.../LottoDrawService.java:42 — 제외 번호 검증 없이 후보 생성 — excluded 필터 후 size < 6 이면 DRAW_NOT_ENOUGH_CANDIDATES
```
마지막 줄에 판정: **머지 가능** / **수정 후 머지** / **재작업**.
