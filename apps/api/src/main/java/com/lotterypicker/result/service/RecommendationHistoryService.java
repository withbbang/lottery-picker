package com.lotterypicker.result.service;

import com.lotterypicker.common.PageResponse;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.dto.RecommendationHistoryLineResponse;
import com.lotterypicker.result.dto.RecommendationHistoryResponse;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지난 추천을 날짜 단위로 나눠 보여 준다. 채점 결과를 붙여야 해서 추천 도메인이 아니라 결과 도메인에 둔다
 * (결과 → 추천 방향 의존만 둔다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationHistoryService {

	private final DailyRecommendationRepository recommendations;
	private final RecommendationResultRepository results;
	private final LottoRoundRepository rounds;

	public PageResponse<RecommendationHistoryResponse> history(int page, int size) {
		Page<LocalDate> dates = recommendations.findRecommendationDates(PageRequest.of(page, size));
		// 빈 페이지면 IN 조회를 건너뛴다. map은 빈 페이지에서 함수를 부르지 않는다.
		List<DailyRecommendation> lines = dates.isEmpty() ? List.of()
				: recommendations.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(dates.getContent());
		Map<Long, RecommendationResult> byLine = lines.isEmpty() ? Map.of()
				: results.findByRecommendationIdIn(lines.stream().map(DailyRecommendation::getId).toList())
					.stream()
					.collect(Collectors.toMap(RecommendationResult::getRecommendationId, Function.identity()));

		// 채점된 줄의 회차만 당첨번호를 읽는다. 채점됐다면 그 회차 당첨번호는 저장돼 있다.
		List<Integer> scoredRounds = lines.stream()
			.filter(line -> byLine.containsKey(line.getId()))
			.map(DailyRecommendation::getRoundNo)
			.distinct()
			.toList();
		Map<Integer, LottoRound> winning = scoredRounds.isEmpty() ? Map.of()
				: rounds.findAllById(scoredRounds)
					.stream()
					.collect(Collectors.toMap(LottoRound::getRoundNo, Function.identity()));

		Map<LocalDate, List<DailyRecommendation>> byDate = lines.stream()
			.collect(Collectors.groupingBy(DailyRecommendation::getDate, LinkedHashMap::new, Collectors.toList()));
		return PageResponse.from(dates.map(date -> toResponse(date, byDate.get(date), byLine, winning)));
	}

	private static RecommendationHistoryResponse toResponse(LocalDate date, List<DailyRecommendation> lines,
			Map<Long, RecommendationResult> byLine, Map<Integer, LottoRound> winning) {
		return new RecommendationHistoryResponse(date, lines.get(0).getRoundNo(), lines.stream()
			.map(line -> toLine(line, byLine.get(line.getId()), winning.get(line.getRoundNo())))
			.toList());
	}

	private static RecommendationHistoryLineResponse toLine(DailyRecommendation line, RecommendationResult result,
			LottoRound winning) {
		LottoNumbers numbers = line.numbers();
		if (result == null || winning == null) {
			return new RecommendationHistoryLineResponse(line.getStrategy(), numbers.values(), line.getEvidence(),
					line.getRelaxation(), null, null, null, null);
		}
		LottoNumbers drawn = winning.numbers();
		Integer bonus = numbers.contains(winning.getBonus()) ? winning.getBonus() : null;
		return new RecommendationHistoryLineResponse(line.getStrategy(), numbers.values(), line.getEvidence(),
				line.getRelaxation(), result.getMatchCount(), result.getPrizeRank(),
				numbers.values().stream().filter(drawn::contains).toList(), bonus);
	}

}
