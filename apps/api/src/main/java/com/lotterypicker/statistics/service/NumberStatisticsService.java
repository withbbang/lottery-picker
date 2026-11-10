package com.lotterypicker.statistics.service;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.recommendation.engine.NumberRanking;
import com.lotterypicker.recommendation.engine.NumberStatistics;
import com.lotterypicker.recommendation.engine.RecentHotStrategy;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.dto.RankedNumberResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 근거를 확인하는 번호 통계. 그 추천을 만들 때 쓴 회차 범위만으로 계산해 근거 문구의 숫자와 늘 같다.
 * 새 회차가 수집돼도 지난 추천의 통계는 바뀌지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NumberStatisticsService {

	private final DailyRecommendationRepository recommendations;
	private final LottoRoundRepository rounds;
	private final Clock clock;

	/**
	 * 회차 범위별 계산 결과. 지난 추천의 범위는 다시 바뀌지 않으므로 처음 계산한 값을 그대로 쓴다.
	 * 하루에 범위가 하나씩만 늘어 크기 제한은 두지 않는다.
	 */
	private final Map<RoundRange, Ranked> cache = new ConcurrentHashMap<>();

	private record RoundRange(int from, int to) {
	}

	private record Ranked(int totalRounds, Map<NumberMetric, List<RankedNumberResponse>> metrics) {
	}

	/**
	 * @param date 기준 추천 날짜. null이면 오늘 이하 가장 최근 추천.
	 * @return 날짜를 생략했는데 추천이 하나도 없으면 빈 값
	 * @throws BusinessException 지정한 날짜에 추천이 없으면 {@link ErrorCode#COMMON_NOT_FOUND}
	 */
	public Optional<NumberStatisticsResponse> find(@Nullable LocalDate date) {
		if (date == null) {
			return recommendations.findFirstByDateLessThanEqualOrderByDateDesc(LocalDate.now(clock))
				.flatMap(latest -> basis(latest.getDate()))
				.map(this::compute);
		}
		return Optional.of(compute(basis(date).orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND))));
	}

	/** 그날 추천 줄 하나. 대조군은 화면에 없는 줄이라 기준으로 쓰지 않는다. */
	private Optional<DailyRecommendation> basis(LocalDate date) {
		return recommendations.findByDateOrderByLineIndexAsc(date).stream().filter(line -> !line.isControl()).findFirst();
	}

	private NumberStatisticsResponse compute(DailyRecommendation basis) {
		RoundRange range = new RoundRange(basis.getHistoryFromRound(), basis.getHistoryToRound());
		Ranked ranked = cache.computeIfAbsent(range, this::rank);
		return new NumberStatisticsResponse(basis.getDate(), range.from(), range.to(), ranked.totalRounds(),
				RecentHotStrategy.WINDOW, ranked.metrics());
	}

	private Ranked rank(RoundRange range) {
		NumberStatistics stats = NumberStatistics.from(
				DailyRecommendationService.toHistory(rounds.findByRoundNoBetweenOrderByRoundNoAsc(range.from(), range.to())));
		Map<NumberMetric, List<RankedNumberResponse>> metrics = new EnumMap<>(NumberMetric.class);
		for (NumberMetric metric : NumberMetric.values()) {
			metrics.put(metric, NumberRanking.rank(stats, metric).stream().map(RankedNumberResponse::from).toList());
		}
		return new Ranked(stats.totalRounds(), Collections.unmodifiableMap(metrics));
	}

}
