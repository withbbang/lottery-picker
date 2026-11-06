---
name: api-conventions
description: apps/api 와 apps/web 이 공유하는 REST 규약 — URL 설계, 응답 래퍼, 에러 코드, 페이징, 네이밍. 엔드포인트를 만들거나 호출 코드를 쓸 때 참조.
---
# API 규약

## URL
- `/api/v1/<복수 명사>`: `GET /api/v1/draws` · `POST /api/v1/draws` · `GET /api/v1/draws/{id}`
- URL에 동사 금지. 행위는 HTTP 메서드로. 리소스로 표현이 안 되는 액션만 `POST /api/v1/draws/{id}/check`.
- 쿼리 파라미터는 camelCase: `?round=1150&size=20`.

## 응답 래퍼
성공:
```json
{ "success": true, "data": { "id": 1, "numbers": [3, 11, 19, 27, 38, 44], "bonus": 7, "round": null, "createdAt": "2026-11-06T13:00:00+09:00" } }
```
실패:
```json
{ "success": false, "error": { "code": "DRAW_NOT_ENOUGH_CANDIDATES", "message": "제외 번호를 뺀 후보가 6개 미만입니다." } }
```
- `data` 는 객체 또는 배열. `null` 응답은 204 로.
- HTTP 상태: 200 조회/수정 · 201 생성 · 204 삭제 · 400 검증 실패 · 401 인증 필요 · 404 없음 · 409 충돌 · 429 시도 제한 · 500 서버 오류.

## 에러 코드
- 형식 `<DOMAIN>_<REASON>` 대문자 스네이크. `ErrorCode` enum 한 곳에서 관리 (`apps/api/.../common/ErrorCode.java`).
- `message` 는 사용자에게 그대로 보여줘도 되는 **한국어 완성 문장**. 스택트레이스·클래스명 금지.
- 검증 실패(`@Valid`)는 `COMMON_VALIDATION_FAILED` + `error.fields: [{ field, reason }]`.

| 코드 | 상태 | 뜻 |
|---|---|---|
| `COMMON_VALIDATION_FAILED` | 400 | 요청 필드 검증 실패 |
| `COMMON_NOT_FOUND` | 404 | 리소스 없음 |
| `COMMON_INTERNAL_ERROR` | 500 | 처리되지 않은 서버 오류 |
| `DRAW_NUMBER_OUT_OF_RANGE` | 400 | 1~45 밖 숫자 |
| `DRAW_FIXED_EXCLUDED_OVERLAP` | 400 | 고정·제외 번호 겹침 |
| `DRAW_NOT_ENOUGH_CANDIDATES` | 400 | 후보 6개 미만 |
| `AUTH_REQUIRED` | 401 | 세션 없음 또는 만료 |
| `AUTH_INVALID_CODE` | 400 | OTP 코드 틀림 또는 재사용 |
| `AUTH_LOCKED` | 429 | 연속 5회 실패로 15분 잠금 |

## 페이징
- 요청 `?page=0&size=20` (0-based, size 최대 100).
- 응답 `data: { items: [...], page, size, totalElements, totalPages }`.

## 네이밍
- JSON 키 camelCase. 날짜·시각은 ISO-8601 오프셋 포함 문자열.
- 추첨 결과 객체: `{ id, numbers: number[6], bonus, round | null, createdAt }` — `numbers` 는 항상 오름차순.
- DTO 클래스: `DrawCreateRequest` / `DrawResponse`. 웹 타입도 같은 이름으로 `types/api/draw.ts`.

## 웹 쪽 구현
- `lib/api.ts` 의 `apiFetch<T>()` 하나로 호출. 경로는 같은 주소의 `/api/*` 상대 경로만 쓰고, 쿠키는 브라우저가 자동으로 보낸다. `success: false` 면 `ApiError(code, message)` 를 throw.
- Next.js rewrites 가 `/api/*` 를 `API_INTERNAL_URL` 로 전달한다. 값은 `next build` 시점에 필요하다.
- 데이터 조회는 클라이언트 React Query로 한다. 서버 컴포넌트는 데이터 API를 호출하지 않는다.
- `AUTH_REQUIRED` 는 `QueryProvider` 가 한 곳에서 받아 `/login` 으로 이동시킨다.
- React Query 키: `['draws']`, `['draws', id]`. 변이 후 `['draws']` invalidate.
