package com.lotterypicker.simulation.dto;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.simulation.StrategySimulation;
import java.util.Map;

/** 전략 하나의 시뮬레이션 집계. 비율과 구간은 0~1이며 구간은 5등 이상 비율의 95% 정규근사다. */
public record StrategySimulationResponse(StrategyType strategy, long lineCount, double averageMatches,
		Map<PrizeRank, Long> rankCounts, double atLeastFifthRate, double ciLow, double ciHigh,
		boolean differsFromControl) {

	public static StrategySimulationResponse from(StrategySimulation s) {
		return new StrategySimulationResponse(s.strategy(), s.lineCount(), s.averageMatches(), s.rankCounts(),
				s.atLeastFifthRate(), s.ciLow(), s.ciHigh(), s.differsFromControl());
	}

}
