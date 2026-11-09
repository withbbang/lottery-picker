package com.lotterypicker.simulation;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.util.Map;

/**
 * 전략(또는 대조군) 하나의 시뮬레이션 집계. 비율은 0~1, 신뢰구간은 5등 이상 비율의 95% 정규근사 구간이다.
 * {@code differsFromControl}은 구간이 대조군 구간과 겹치지 않을 때만 true이며, 대조군 자신은 false다.
 */
public record StrategySimulation(StrategyType strategy, long lineCount, double averageMatches,
		Map<PrizeRank, Long> rankCounts, double atLeastFifthRate, double ciLow, double ciHigh,
		boolean differsFromControl) {

	public StrategySimulation {
		rankCounts = Map.copyOf(rankCounts);
	}

}
