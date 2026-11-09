# PWA 홈 화면 설치 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 화면에 추가하면 아이콘으로 standalone 실행되게 한다. 그 이상(오프라인·푸시·캐싱)은 하지 않는다.

**Architecture:** Next.js 메타데이터 파일 규약만 쓴다 — `app/manifest.ts`, 아이콘은 `next/og`의 `ImageResponse`로 코드에서 그린다(바이너리 PNG를 저장소에 두지 않음). 서비스 워커 없음.

**Tech Stack:** Next.js 16.4 App Router (`MetadataRoute.Manifest`, `ImageResponse`), Vitest

**Spec:** `docs/superpowers/specs/2026-11-10-recommendation-carryover-combined-pwa-design.md` 3장

## Global Constraints

- 브랜치: `dev`에서 `feat/pwa-install`.
- **코드 쓰기 전에** `apps/web/node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/01-metadata/manifest.md`, `app-icons.md`, 그리고 `functions/image-response` 문서를 읽는다(`apps/web/AGENTS.md`: 이 Next는 학습 데이터와 다르다). `next.config.ts`의 `cacheComponents: true`와 충돌하지 않는지 문서로 확인.
- 색: 라이트 토큰 값 사용 — `theme_color` / `viewport.themeColor` = `#c42f4f`(`--slip`), `background_color` = `#f4f6f9`(`--paper`). 다크 모드 `themeColor`는 `{ media: "(prefers-color-scheme: dark)", color: "#10151f" }` 추가.
- 이름: `name: "로또 번호 추첨"`(기존 `metadata.title`과 같게), `short_name: "로또 추첨"`, `start_url: "/"`, `display: "standalone"`, `lang: "ko"`.
- 서비스 워커·오프라인 캐시·푸시 추가 금지.
- 응답 헤더 크기 주의: `layout.tsx` 주석대로 nginx 헤더 버퍼 4KB. 아이콘·매니페스트 추가로 `Link` 헤더가 늘지 않는지 Task 2에서 확인.
- 커밋은 사용자가 요청할 때만, `.gitmessage.txt` 형식.

## Review Focus

- iOS Safari "홈 화면에 추가" → `apple-touch-icon`(180×180)이 없으면 스크린샷 썸네일이 아이콘이 된다 → `app/apple-icon.tsx` 필수 (Task 1 테스트 `exports a 180px apple icon`).
- Android 원형 마스크 → `purpose: "maskable"` 아이콘의 안전 영역(가운데 80%) 밖에 그림이 잘림 → maskable은 여백을 넉넉히 (Task 2 수동 확인).
- 로그인 안 된 상태에서 매니페스트·아이콘 요청 → 인증과 무관하게 200 (Task 2 curl 확인). 현재 웹에 미들웨어/`proxy.ts`가 없어 막힐 경로는 없지만 확인한다.
- 다크 모드에서 상태 표시줄 색 → 다크 `themeColor` 적용 (Task 1 테스트).

---

### Task 1: 매니페스트와 아이콘

**Files:**
- Create: `apps/web/app/manifest.ts`
- Create: `apps/web/components/brand/AppIconArt.tsx` — 아이콘 그림 JSX 하나(용지 빨강 배경 `#c42f4f` 위 흰 공 하나, 공 안에 숫자 없이 또는 "6/45" 정도. 세부는 재량). `ImageResponse`가 지원하는 CSS(flex만, Tailwind 불가)만 쓴다 — 이 파일은 `ImageResponse` 전용이라 web.md의 "인라인 style 금지" 예외임을 파일 주석에 한 줄로 적는다.
- Create: `apps/web/app/icons/[variant]/route.tsx` — `variant` ∈ `"192" | "512" | "maskable"`; `generateStaticParams`로 세 값 고정, 그 밖은 404. `maskable`은 512px에 그림을 70% 크기로.
- Create: `apps/web/app/apple-icon.tsx` — `size = { width: 180, height: 180 }`, `contentType = "image/png"`.
- Modify: `apps/web/app/layout.tsx` — `export const viewport: Viewport = { themeColor: [...] }`, `metadata.appleWebApp = { capable: true, title: "로또 추첨", statusBarStyle: "default" }`.
- Test: `apps/web/app/manifest.test.ts`, `apps/web/app/layout.test.ts`(viewport·metadata 값만 import해서 단언)

**Interfaces:**
- Produces: URL `/manifest.webmanifest`, `/icons/192`, `/icons/512`, `/icons/maskable`, `/apple-icon`(Next가 붙이는 쿼리 포함).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `manifest.test.ts`:
    - `it("opens standalone at the home page")`: `manifest()` → `display === "standalone"`, `start_url === "/"`, `name === "로또 번호 추첨"`, `short_name === "로또 추첨"`.
    - `it("uses the slip red theme and paper background")`: `theme_color === "#c42f4f"`, `background_color === "#f4f6f9"`.
    - `it("lists 192, 512 and maskable png icons")`: `icons`에 `{src:"/icons/192", sizes:"192x192", type:"image/png"}`, `{src:"/icons/512", sizes:"512x512", type:"image/png"}`, `{src:"/icons/maskable", sizes:"512x512", type:"image/png", purpose:"maskable"}`.
  - `layout.test.ts`:
    - `it("sets light and dark theme colors")`: `viewport.themeColor`에 `#c42f4f`와 다크 `#10151f`.
    - `it("exports a 180px apple icon")`: `import { size, contentType } from "@/app/apple-icon"` → `180×180`, `"image/png"`.
  - `layout.tsx`를 import할 때 `next/font/google`이 vitest에서 깨지면 `vi.mock("next/font/google", ...)`로 막는다.
- [ ] **Step 2: 실패 확인** — `cd apps/web && pnpm test -- manifest layout` → FAIL.
- [ ] **Step 3: 구현** — 위 Files 대로.
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test` PASS.

### Task 2: 빌드와 실제 응답 확인

- [ ] **Step 1:** `cd apps/web && API_INTERNAL_URL=http://localhost:8080 pnpm build` → 성공, 빌드 출력에 `/manifest.webmanifest`, `/icons/[variant]`, `/apple-icon` 경로가 보인다.
- [ ] **Step 2:** 빌드 결과로 서버를 띄우고(`node .next/standalone/server.js` 또는 `pnpm start`) 다음을 curl:
  - `/manifest.webmanifest` → 200, `application/manifest+json`, 본문에 `"display":"standalone"`.
  - `/icons/192`, `/icons/512`, `/icons/maskable` → 200 `image/png`. `/icons/999` → 404.
  - `/` → HTML `<head>`에 `rel="manifest"`, `rel="apple-touch-icon"`, `name="theme-color"` 두 개.
  - `curl -sI /` 의 응답 헤더 전체 크기가 4096바이트 미만.
- [ ] **Step 3:** 아이콘 세 개를 PNG로 받아 Read 도구로 눈으로 확인(maskable은 가운데 원 안에 그림이 다 들어오는지). 사용자에게 이미지 경로를 알려 준다.
- [ ] **Step 4:** Chrome DevTools(Playwright MCP 가능) Application → Manifest에서 "Installability" 오류가 없는지 확인. 오류가 SW 부재 때문이면 스펙대로 아무것도 캐싱하지 않는 최소 SW를 넣을지 사용자에게 먼저 묻는다.
- [ ] **Step 5:** 결과 보고. 커밋·리뷰·병합은 CLAUDE.md 절차대로 사용자 요청 시.
