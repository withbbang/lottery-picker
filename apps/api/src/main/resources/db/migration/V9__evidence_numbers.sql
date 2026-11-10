-- 근거 문구에 적힌 번호를 구조로 저장한다. 번호 통계 화면이 강조할 번호다. 예: '7,12'
-- 통계 지표가 있는 전략(RECENT_HOT, LONG_COLD, ALL_TIME)만 채우고 나머지는 ''.
-- 이전 행은 V10이 근거 문구에서 한 번 채운다.
ALTER TABLE daily_recommendation ADD COLUMN evidence_numbers VARCHAR(20) NOT NULL DEFAULT '';
