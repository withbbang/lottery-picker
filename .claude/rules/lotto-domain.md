---
paths:
  - "**/lotto/**"
  - "**/draw/**"
  - "**/lottery/**"
  - "**/recommendation/**"
---
# 로또 6/45 도메인 규칙

## 불변 조건
- 본번호 `numbers`: 1~45 정수 **6개**, 중복 없음, **오름차순 정렬**해서 저장·반환.
- 보너스 `bonus`: 본번호와 겹치지 않는 1개. 본번호 배열에 섞지 말고 별도 필드로.
- 위 조건을 깨는 값은 저장 전에 반드시 걸러진다 (`LottoNumbers` 값 객체 생성자에서 검증).

## 번호 추천
- 번호 생성은 `apps/api`의 `recommendation` 패키지(`recommendation/engine`) **한 곳**에만. 프론트는 호출·표시만.
- 난수는 주입받은 `Random`(운영: `SecureRandom`)을 쓴다. 테스트는 시드 고정 `Random`을 넣어 결정적으로.
- 추천 줄에는 `bonus`가 없다. 보너스는 당첨 번호에만 있다.
- 설정: 고정 번호 `fixed`(≤ 5개), 제외 번호 `excluded`. 규칙(`RecommendationConstraint`):
  - `fixed ∩ excluded = ∅`, 아니면 400 `DRAW_FIXED_EXCLUDED_OVERLAP`
  - 후보(1~45 − excluded) 가 6개 미만이면 400 `DRAW_NOT_ENOUGH_CANDIDATES`
  - 범위 밖 숫자가 있으면 400 `DRAW_NUMBER_OUT_OF_RANGE`
  - 고정 번호가 6개 이상이면 400 `RECOMMENDATION_TOO_MANY_FIXED` (6개면 5줄이 모두 같아진다)

## 당첨 비교 (나중에 붙일 때)
- 등수: 1등 6개 일치 / 2등 5개 + 보너스 / 3등 5개 / 4등 4개 / 5등 3개 / 그 외 낙첨.
- 회차 `round`는 nullable. 당첨 번호 원본 출처와 조회 시각을 함께 저장한다.
