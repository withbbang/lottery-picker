package com.lotterypicker.recommendation.engine;

public enum StrategyType {
	RECENT_HOT, LONG_COLD, ALL_TIME, PAIR, BALANCED, CONTROL,
	/** 근거 조합. 시뮬레이션 시드가 ordinal을 쓰므로 기존 값 뒤에만 추가한다. */
	COMBINED
}
