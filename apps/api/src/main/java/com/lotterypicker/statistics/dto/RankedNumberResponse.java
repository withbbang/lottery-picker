package com.lotterypicker.statistics.dto;

import com.lotterypicker.recommendation.engine.RankedNumber;

/**
 * 한 지표에서 번호 하나.
 * @param rank 공동 순위(1, 2, 2, 4…)
 * @param tied 같은 값의 번호가 또 있으면 true("공동 N위")
 */
public record RankedNumberResponse(int number, int value, int rank, boolean tied) {

	public static RankedNumberResponse from(RankedNumber ranked) {
		return new RankedNumberResponse(ranked.number(), ranked.value(), ranked.rank(), ranked.tied());
	}

}
