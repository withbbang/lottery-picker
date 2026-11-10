package com.lotterypicker.recommendation.engine;

import java.util.Optional;

/** 번호 통계 화면의 지표. 각각 한 전략의 근거와 같은 계산식을 쓴다. */
public enum NumberMetric {

	/** 최근 {@link RecentHotStrategy#WINDOW}회 출현 횟수. 최근 빈도 전략의 근거. */
	RECENT,
	/** 마지막 출현 뒤 지난 회차 수. 장기 미출현 전략의 근거. */
	COLD,
	/** 전체 출현 횟수. 전체 빈도 전략의 근거. */
	ALL_TIME;

	public int valueOf(NumberStatistics stats, int number) {
		return switch (this) {
			case RECENT -> stats.recentFrequency(number, RecentHotStrategy.WINDOW);
			case COLD -> stats.roundsSinceLast(number);
			case ALL_TIME -> stats.frequency(number);
		};
	}

	/** 근거를 이 지표로 확인할 수 있는 전략이면 그 지표. 번호 쌍·균형·근거 조합·대조군은 없다. */
	public static Optional<NumberMetric> forStrategy(StrategyType type) {
		return switch (type) {
			case RECENT_HOT -> Optional.of(RECENT);
			case LONG_COLD -> Optional.of(COLD);
			case ALL_TIME -> Optional.of(ALL_TIME);
			default -> Optional.empty();
		};
	}

}
