package com.lotterypicker.result.dto;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.util.List;

/**
 * 지난 추천 한 줄. 채점 전(추첨 전이거나 아직 채점하지 않음)이면 {@code matchCount}, {@code prizeRank},
 * {@code matchedNumbers}가 null이다. {@code matchedNumbers}는 당첨 본번호와 겹친 번호(오름차순)이고 보너스는 넣지 않는다.
 * {@code matchedBonus}는 이 줄에 보너스 번호가 있으면 그 번호, 없거나 채점 전이면 null이다.
 */
public record RecommendationHistoryLineResponse(StrategyType strategy, List<Integer> numbers, List<String> evidence,
		Relaxation relaxation, Integer matchCount, PrizeRank prizeRank, List<Integer> matchedNumbers,
		Integer matchedBonus) {
}
