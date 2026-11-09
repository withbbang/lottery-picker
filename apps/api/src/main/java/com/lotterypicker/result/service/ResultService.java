package com.lotterypicker.result.service;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.ResultTally;
import com.lotterypicker.result.dto.RoundResultLineResponse;
import com.lotterypicker.result.dto.RoundResultResponse;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.util.ArrayList;
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
public class ResultService {

	private final LottoRoundRepository rounds;
	private final DailyRecommendationRepository recommendations;
	private final RecommendationResultRepository results;

	/** 채점된 추천 줄만 돌려준다. 대조군은 성적표 전용이라 뺀다. 채점 전인 추천 줄이 있으면 pending이다. */
	public RoundResultResponse roundResults(int round) {
		if (!rounds.existsById(round)) {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}
		Map<Long, RecommendationResult> byLine = results.findByRoundNo(round).stream()
			.collect(Collectors.toMap(RecommendationResult::getRecommendationId, Function.identity()));

		List<RoundResultLineResponse> lines = new ArrayList<>();
		List<RecommendationResult> scored = new ArrayList<>();
		boolean pending = false;
		for (DailyRecommendation line : recommendations.findByRoundNoOrderByDateAscLineIndexAsc(round)) {
			if (line.isControl()) {
				continue;
			}
			RecommendationResult result = byLine.get(line.getId());
			if (result == null) {
				pending = true;
				continue;
			}
			scored.add(result);
			lines.add(new RoundResultLineResponse(line.getDate(), line.getStrategy(), line.numbers().values(),
					result.getMatchCount(), result.getPrizeRank(), result.getPrizeAmount()));
		}
		return RoundResultResponse.of(round, lines, ResultTally.of(scored), pending);
	}

}
