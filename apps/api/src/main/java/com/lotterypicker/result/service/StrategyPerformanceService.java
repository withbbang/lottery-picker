package com.lotterypicker.result.service;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.dto.StrategyPerformanceResponse;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrategyPerformanceService {

	static final double THEORY_AT_LEAST_FIFTH_RATE = PrizeRank.AT_LEAST_FIFTH_RATE;

	/** 표본 기준은 작성자 판단이다. 5등 이상 기대치가 약 24건이 되는 줄 수다. */
	static final int SUFFICIENT_LINE_COUNT = 1000;

	private final RecommendationResultRepository results;
	private final DailyRecommendationRepository recommendations;

	/** 결과가 저장된 줄만 센다. 결과와 추천을 각각 한 번에 읽어 줄마다 조회하지 않는다. */
	public StrategyPerformancesResponse performances() {
		List<RecommendationResult> scored = results.findAll();
		List<Long> ids = scored.stream().map(RecommendationResult::getRecommendationId).toList();
		Map<Long, DailyRecommendation> lineById = recommendations.findAllById(ids).stream()
			.collect(Collectors.toMap(DailyRecommendation::getId, Function.identity()));

		Map<StrategyType, List<Scored>> byStrategy = new EnumMap<>(StrategyType.class);
		for (RecommendationResult result : scored) {
			DailyRecommendation line = lineById.get(result.getRecommendationId());
			if (line != null) {
				byStrategy.computeIfAbsent(line.getStrategy(), k -> new ArrayList<>())
					.add(new Scored(result, line.hasSettings()));
			}
		}

		List<StrategyPerformanceResponse> rows = new ArrayList<>();
		for (StrategyType type : StrategyType.values()) {
			rows.add(summarize(type, byStrategy.getOrDefault(type, List.of())));
		}
		return new StrategyPerformancesResponse(THEORY_AT_LEAST_FIFTH_RATE, rows);
	}

	private static StrategyPerformanceResponse summarize(StrategyType type, List<Scored> lines) {
		Map<PrizeRank, Integer> rankCounts = new EnumMap<>(PrizeRank.class);
		for (PrizeRank rank : PrizeRank.values()) {
			rankCounts.put(rank, 0);
		}
		int matchSum = 0;
		int hits = 0;
		int noSettings = 0;
		int noSettingsHits = 0;
		for (Scored s : lines) {
			PrizeRank rank = s.result().getPrizeRank();
			rankCounts.merge(rank, 1, Integer::sum);
			matchSum += s.result().getMatchCount();
			boolean hit = rank != PrizeRank.NONE;
			if (hit) {
				hits++;
			}
			if (!s.hasSettings()) {
				noSettings++;
				if (hit) {
					noSettingsHits++;
				}
			}
		}
		int count = lines.size();
		return new StrategyPerformanceResponse(type, count, ratio(matchSum, count), rankCounts, ratio(hits, count),
				noSettings, ratio(noSettingsHits, noSettings), count >= SUFFICIENT_LINE_COUNT);
	}

	/** 분모가 0이면 0. 줄이 없는 전략을 0으로 표시하고 나눗셈 오류를 피한다. */
	private static double ratio(int numerator, int denominator) {
		return denominator == 0 ? 0 : (double) numerator / denominator;
	}

	private record Scored(RecommendationResult result, boolean hasSettings) {
	}

}
