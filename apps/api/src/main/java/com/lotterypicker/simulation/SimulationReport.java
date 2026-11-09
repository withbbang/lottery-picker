package com.lotterypicker.simulation;

import java.util.List;

/** 시뮬레이션 한 번의 결과. {@code fromRound}~{@code toRound} 회차마다 전략별로 {@code repetitions}줄을 뽑았다. */
public record SimulationReport(int fromRound, int toRound, int repetitions, List<StrategySimulation> strategies) {

	public SimulationReport {
		strategies = List.copyOf(strategies);
	}

}
