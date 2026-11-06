package com.lotterypicker.recommendation.dto;

import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import java.util.List;

public record RecommendationLineResponse(StrategyType strategy, List<Integer> numbers, List<String> evidence,
		Relaxation relaxation) {

	public static RecommendationLineResponse from(DailyRecommendation line) {
		return new RecommendationLineResponse(line.getStrategy(), line.numbers().values(), line.getEvidence(),
				line.getRelaxation());
	}

}
