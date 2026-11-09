package com.lotterypicker.result.dto;

import java.util.List;

/** 성적표 전체. 이론값(5등 이상 확률)은 전략과 무관한 상수라 상위에 둔다. */
public record StrategyPerformancesResponse(double theoryAtLeastFifthRate,
		List<StrategyPerformanceResponse> strategies) {
}
