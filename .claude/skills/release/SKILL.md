---
name: release
description: 배포 전 점검 — 양쪽 앱 빌드·테스트, 변경 요약 작성, 태그 명령 준비. 사용자가 /release 로 직접 호출할 때만.
disable-model-invocation: true
---
# /release [version]

순서대로 실행하고, 하나라도 실패하면 **거기서 멈추고** 실패 요약만 보고한다.

1. `git status --porcelain` 이 비어 있는지 확인. 아니면 중단하고 커밋/스태시를 요청.
2. `apps/web`:
   ```bash
   pnpm install --frozen-lockfile && pnpm lint && pnpm test && pnpm build
   ```
3. `apps/api`:
   ```bash
   ./gradlew clean test build
   ```
4. 직전 태그 찾기: `git describe --tags --abbrev=0` (없으면 첫 커밋부터).
5. `git log <직전 태그>..HEAD --oneline` 을 `feat` / `fix` / `chore` / 기타 로 묶어 변경 요약 작성.
6. 태그·푸시는 **하지 않는다**. 아래 명령만 출력하고 끝낸다:
   ```bash
   git tag v<version> && git push origin v<version>
   ```
