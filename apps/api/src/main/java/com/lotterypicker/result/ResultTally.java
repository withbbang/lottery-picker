package com.lotterypicker.result;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.result.entity.RecommendationResult;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * 한 회차 줄들의 등수별 줄 수와 가상 금액. 줄당 1,000원에 샀다고 가정한다.
 * 등수별 줄 수는 없는 등수도 0으로 채운다.
 */
public record ResultTally(int lineCount, Map<PrizeRank, Integer> rankCounts, long prizeTotal) {

	public static final long COST_PER_LINE = 1_000;

	public ResultTally {
		Map<PrizeRank, Integer> filled = new EnumMap<>(PrizeRank.class);
		for (PrizeRank rank : PrizeRank.values()) {
			filled.put(rank, rankCounts.getOrDefault(rank, 0));
		}
		rankCounts = filled;
	}

	public static ResultTally of(Collection<RecommendationResult> results) {
		Map<PrizeRank, Integer> counts = new EnumMap<>(PrizeRank.class);
		long prizeTotal = 0;
		for (RecommendationResult result : results) {
			counts.merge(result.getPrizeRank(), 1, Integer::sum);
			prizeTotal += result.getPrizeAmount();
		}
		return new ResultTally(results.size(), counts, prizeTotal);
	}

	public long cost() {
		return lineCount * COST_PER_LINE;
	}

	public long profit() {
		return prizeTotal - cost();
	}

	/** 줄 중 가장 높은 등수. 줄이 없거나 모두 낙첨이면 낙첨. */
	public PrizeRank bestRank() {
		for (PrizeRank rank : PrizeRank.values()) {
			if (rankCounts.get(rank) > 0) {
				return rank;
			}
		}
		return PrizeRank.NONE;
	}

}
