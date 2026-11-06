# lottery-picker

로또 6/45 번호 추첨 서비스. Next.js(웹) + Spring Boot(API) 모노레포, 1인 개발.

## 구조

| 경로 | 역할 | 스택 | 도구 |
|---|---|---|---|
| `apps/web` | 프론트엔드 | Next.js 16 / React 19 / TypeScript / Tailwind | pnpm |
| `apps/api` | REST API | Spring Boot 4.1 / Java 17 / JPA | Gradle |
| `.claude/` | Claude Code 설정 (rules · skills · agents · hooks) | — | — |

## 명령

```bash
# apps/web
pnpm dev        # 개발 서버
pnpm lint       # ESLint
pnpm test       # Vitest (watch 아님, vitest run)
pnpm build

# apps/api
./gradlew bootRun
./gradlew test
./gradlew build
```

## 항상 지킬 것

- 패키지 매니저는 **pnpm**. npm / yarn 명령 쓰지 않는다.
- 브랜치: `master`(배포용) · `dev` · 피처 브랜치. 피처 브랜치는 항상 `dev`에서 딴다. 피처 브랜치는 PR하지 않고 로컬에서 머지만 한다. origin에 푸시하는 건 `dev`, `master`만.
- 커밋 전 바뀐 앱의 `lint`·`test` 통과. 커밋은 요청받았을 때만, 메시지는 **커밋 전에 `.gitmessage.txt`를 읽고** 그 정책대로 쓴다. 형식 `Type(scope) : 제목`, 타입 첫 글자 대문자 (예: `Feat : 로또 번호 추첨 추가`). `-m`/`-F`는 템플릿을 거치지 않으니 직접 맞춘다. 위반 시 `.githooks/commit-msg`가 거부한다 (클론 후 1회: `git config --local commit.template ./.gitmessage.txt && git config --local core.hooksPath .githooks`).
- **추첨 로직은 `apps/api` 한 곳에만.** 프론트는 호출·표시만 한다. (세부: `.claude/rules/lotto-domain.md`)
- `.env*` 파일은 읽지도 고치지도 않는다. 예시는 `.env.example`에만 쓴다. (훅으로 차단됨)
- API 응답 포맷·에러 코드는 `api-conventions` 스킬을 따른다. 임의로 새 포맷 만들지 않는다.
- 모르는 라이브러리 API는 추측하지 말고 `node_modules` / Gradle 캐시의 실제 타입·소스를 읽는다.

## 더 읽을 곳 (필요할 때만 로드됨)

- 경로별 컨벤션 → `.claude/rules/` : `web.md`, `api.md`, `testing.md`, `lotto-domain.md`
- 절차 → `/new-feature` (API→웹 기능 구현), `/review` (diff 점검), `/release` (배포 전 체크)
- 참조 → `api-conventions` 스킬 (URL · 응답 래퍼 · 에러 코드 · 페이징)
- 격리 작업 → `code-reviewer`, `test-runner` 서브에이전트 (`.claude/agents/`)
- 개인 메모 → `CLAUDE.local.md` (gitignore 됨)
