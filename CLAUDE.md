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
API_INTERNAL_URL=http://localhost:8080 pnpm dev   # 개발 서버 (이 변수 없으면 바로 종료)
pnpm lint       # ESLint + Prettier 서식 검사
pnpm format     # Prettier로 서식 정리 (.prettierrc.json)
pnpm test       # Vitest (watch 아님, vitest run)
API_INTERNAL_URL=http://localhost:8080 pnpm build   # 이 변수 없으면 빌드 실패

# apps/api
./gradlew bootRun
./gradlew test
./gradlew build
# 마이그레이션 확인: .data/lottery.mv.db 복사본으로 기동 (원본 DB 보존, 스케줄러 끔)
SPRING_PROFILES_ACTIVE=local SCHEDULING_ENABLED=false SPRING_DATASOURCE_URL="jdbc:h2:file:<복사본경로>/lottery;MODE=MySQL" ./gradlew bootRun
```

## 항상 지킬 것

- 응답은 한국어로. `/code-review`·`/claude-md-management:*` 등 스킬·서브에이전트 결과도 한국어로 작성하고, 영어로 받은 결과는 한국어로 옮겨 보고한다.
- 패키지 매니저는 **pnpm**. npm / yarn 명령 쓰지 않는다.
- 브랜치: `master`(배포용) · `dev` · 피처 브랜치. 피처 브랜치는 항상 `dev`에서 딴다. 피처 브랜치는 PR하지 않고 로컬에서 머지만 한다. origin에 푸시하는 건 `dev`, `master`만.
- 병합·푸시(요청 시): 피처 → `dev` → `master` 순서로 병합, `git push origin dev master`, 끝나면 로컬 피처 브랜치 삭제. fast-forward가 안 되면 `-m "Merge : <기능명> → dev"`로 병합한다 (기본 메시지는 `commit-msg` 훅이 거부, 제목 50자 이내).
- `dev` 병합 전 피처 브랜치에 `/code-review <강도> <브랜치>` 필수. 단, 바뀐 파일이 모두 문서(`.md` `.txt` `.rst` `.adoc`, `apps/` 안은 앱 최상위 `README.md`·`AGENTS.md`만 문서로 봄)면 생략한다. 강도는 기본 `low`. 리뷰 전에 작업 중요도·코드 변경량·버그 위험을 파악해, 높일 필요가 있으면 강도와 이유를 제안하고 승인받은 뒤 실행한다. 실제 동작 버그만 병합을 막고, 나머지 지적은 결과만 보고한 뒤 병합한다. 버그를 고친 뒤에는 테스트 통과로 확인하고 다시 리뷰하지 않는다 (강도를 높여 리뷰한 중요한 작업은 재리뷰를 제안할 수 있음). 버그가 없거나 고쳤으면 `git rev-parse <브랜치> >> "$(git rev-parse --git-common-dir)/code-review-passed"`로 기록한다. 기록 없는 코드가 `dev`로 들어오는 경로는 `.githooks/reference-transaction`이, `dev`·`master` 푸시는 `.githooks/pre-push`가 막는다 (실수 방지용, 의도적 우회는 못 막음). `/code-review`는 `master`와 비교하므로 `dev`가 앞서 있으면 범위가 더 넓게 잡힌다.
- 리뷰 통과를 기록한 뒤 `dev` 병합 전에 `/claude-md-management:revise-claude-md`를 돌린다. 제안은 승인받은 것만 반영해 피처 브랜치에 `Docs` 커밋으로 넣는다. 넣을 게 없으면 커밋 없이 넘어간다. 병합은 이 단계 뒤에도 요청받았을 때만 한다.
- 커밋 전 바뀐 앱의 `lint`·`test` 통과. 커밋은 요청받았을 때만, 메시지는 **커밋 전에 `.gitmessage.txt`를 읽고** 그 정책대로 쓴다. 형식 `Type(scope) : 제목`, 타입 첫 글자 대문자 (예: `Feat : 로또 번호 추첨 추가`). `-m`/`-F`는 템플릿을 거치지 않으니 직접 맞춘다. 위반 시 `.githooks/commit-msg`가 거부한다 (클론 후 1회: `git config --local commit.template ./.gitmessage.txt && git config --local core.hooksPath .githooks`).
- **추첨 로직은 `apps/api` 한 곳에만.** 프론트는 호출·표시만 한다. (세부: `.claude/rules/lotto-domain.md`)
- `.env*` 파일은 읽지도 고치지도 않는다. 예시는 `.env.example`에만 쓴다. (훅으로 차단됨)
- API 환경변수를 추가하면 `application.yml`·`apps/api/.env.example`·`deploy/.env.example`·`deploy/compose.yml`(environment)·`deploy/README.md` 표를 함께 고친다.
- API 응답 포맷·에러 코드는 `api-conventions` 스킬을 따른다. 임의로 새 포맷 만들지 않는다.
- 모르는 라이브러리 API는 추측하지 말고 `node_modules` / Gradle 캐시의 실제 타입·소스를 읽는다.

## 더 읽을 곳 (필요할 때만 로드됨)

- 경로별 컨벤션 → `.claude/rules/` : `web.md`, `api.md`, `testing.md`, `lotto-domain.md`
- 절차 → `/new-feature` (API→웹 기능 구현), `/review` (diff 점검), `/release` (배포 전 체크)
- 참조 → `api-conventions` 스킬 (URL · 응답 래퍼 · 에러 코드 · 페이징)
- 격리 작업 → `code-reviewer`, `test-runner` 서브에이전트 (`.claude/agents/`)
- 텔레그램 알림·봇 명령 문구와 동작 → `docs/telegram-messages.md` (문구·동작을 바꾸면 함께 고침)
- 개인 메모 → `CLAUDE.local.md` (gitignore 됨)
