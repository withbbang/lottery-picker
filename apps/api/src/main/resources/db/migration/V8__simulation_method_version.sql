-- 시뮬레이션 신뢰구간 계산 방식 버전. 기존 행은 줄 단위 이항 근사(1)로 계산된 결과다.
-- 코드의 현재 버전과 다르면 끝 회차가 같아도 다시 계산한다.
ALTER TABLE simulation_result ADD COLUMN method_version INT NOT NULL DEFAULT 1;
