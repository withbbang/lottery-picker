package com.lotterypicker.result.dto;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.util.Map;

/**
 * 전략(또는 대조군) 하나의 누적 성적. 채점된 줄만 센다. 비율은 0~1이고 줄이 없으면 0이다.
 * {@code noSettings*}는 고정·제외 번호가 없는 줄만의 값으로, 이론값과 비교할 때 쓴다.
 */
public record StrategyPerformanceResponse(StrategyType strategy, int lineCount, double averageMatches,
		Map<PrizeRank, Integer> rankCounts, double atLeastFifthRate, int noSettingsLineCount,
		double noSettingsAtLeastFifthRate, boolean sampleSufficient) {
}
