package com.lotterypicker.simulation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 마지막 시뮬레이션 결과. 이론값은 5등 이상 조합의 비율이고, {@code scopeNote}는 평가 범위 안내다. */
public record SimulationResponse(int fromRound, int toRound, int repetitions, OffsetDateTime computedAt,
		double theoryAtLeastFifthRate, String scopeNote, List<StrategySimulationResponse> strategies) {
}
