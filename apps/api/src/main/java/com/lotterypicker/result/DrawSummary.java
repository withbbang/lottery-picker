package com.lotterypicker.result;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 추첨 종료 알림에 담는 한 회차 요약. {@code tally}와 {@code lines}는 대조군을 뺀 추천 줄만 담는다.
 * {@code lines}는 추천 날짜, 줄 순서대로다. {@code lateCollectedAt}은 늦게 수집했을 때만 값이 있다.
 */
public record DrawSummary(int round, LocalDate drawDate, LottoNumbers numbers, int bonus,
		LocalDateTime lateCollectedAt, ResultTally tally, List<Line> lines) {

	public DrawSummary {
		lines = List.copyOf(lines);
	}

	/** 추천 한 줄의 채점 결과. */
	public record Line(LocalDate date, LottoNumbers numbers, StrategyType strategy, int matchCount, PrizeRank rank) {
	}

}
