package com.lotterypicker.result.dto;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.result.ResultTally;
import java.util.List;
import java.util.Map;

/** 한 회차 추천의 채점 결과. 대조군은 넣지 않는다. 금액은 줄당 1,000원에 샀다고 가정한 가상 값이다. */
public record RoundResultResponse(int round, List<RoundResultLineResponse> lines, Map<PrizeRank, Integer> rankCounts,
		long prizeTotal, long cost, long profit) {

	public static RoundResultResponse of(int round, List<RoundResultLineResponse> lines, ResultTally tally) {
		return new RoundResultResponse(round, lines, tally.rankCounts(), tally.prizeTotal(), tally.cost(),
				tally.profit());
	}

}
