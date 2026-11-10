package com.lotterypicker.recommendation.dto;

import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import java.util.List;

/**
 * @param evidenceNumbers 근거 문구에 적힌 번호(번호 통계에서 강조할 번호). 지표 없는 전략은 빈 목록.
 * @param statsMetric 이 줄의 근거를 확인할 번호 통계 지표. 없으면 null.
 */
public record RecommendationLineResponse(StrategyType strategy, List<Integer> numbers, List<String> evidence,
		Relaxation relaxation, List<Integer> evidenceNumbers, NumberMetric statsMetric) {

	public static RecommendationLineResponse from(DailyRecommendation line) {
		return new RecommendationLineResponse(line.getStrategy(), line.numbers().values(), line.getEvidence(),
				line.getRelaxation(), line.getEvidenceNumbers(),
				NumberMetric.forStrategy(line.getStrategy()).orElse(null));
	}

}
