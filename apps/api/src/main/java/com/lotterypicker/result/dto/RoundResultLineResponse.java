package com.lotterypicker.result.dto;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.time.LocalDate;
import java.util.List;

/** 추천 한 줄의 채점 결과. {@code prizeAmount}는 구매했다고 가정한 1인당 금액이다. */
public record RoundResultLineResponse(LocalDate date, StrategyType strategy, List<Integer> numbers, int matchCount,
		PrizeRank prizeRank, long prizeAmount) {
}
