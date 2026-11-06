---
paths:
  - "apps/web/**"
---
# apps/web — Next.js 16 / React 19 / TypeScript

- **App Router**. 서버 컴포넌트가 기본, 상호작용(이벤트·훅)이 필요한 파일에만 `"use client"`.
- 데이터: 서버 컴포넌트는 `fetch` 직접, 클라이언트는 React Query. API 베이스 URL은 `NEXT_PUBLIC_API_URL` 하나로만.
- API 호출은 `lib/api.ts`의 래퍼를 통해서만. 컴포넌트에서 `fetch` 직접 호출 금지.
- 상태: 서버 상태는 React Query. 전역 UI 상태가 정말 필요할 때만 Redux Toolkit 도입, 그 전엔 로컬 state.
- 디렉토리: `app/`(라우트) · `components/ui/`(범용) · `components/<feature>/`(도메인) · `hooks/` · `lib/` · `types/api/`.
- 파일명: 컴포넌트 `PascalCase.tsx`, 훅 `use-xxx.ts`, 그 외 `kebab-case.ts`.
- 타입: `any` 금지. API 응답 타입은 `types/api/`에 두고 백엔드 DTO 이름(`DrawResponse` 등)과 일치시킨다.
- 스타일: Tailwind만. 인라인 `style={}` 금지. 공통 변형은 `cva` 또는 유틸 함수로.
- import 순서: react → next → 외부 패키지 → `@/` 절대경로 → 상대경로. 절대경로는 `@/`만 사용.
- 번호 표시 컴포넌트(`LottoBall` 등)는 받은 숫자를 그리기만 한다. 정렬·검증·추첨 계산은 하지 않는다 → `lotto-domain.md`.
- 접근성: 버튼은 `<button>`, 링크는 `<Link>`. `div onClick` 금지.
