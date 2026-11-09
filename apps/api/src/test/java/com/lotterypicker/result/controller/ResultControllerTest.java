package com.lotterypicker.result.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import com.lotterypicker.result.service.ResultService;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResultController.class)
@Import(ResultService.class)
class ResultControllerTest {

	private static final int ROUND = 1249;
	private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);
	private static final List<StrategyType> STRATEGIES = List.of(StrategyType.RECENT_HOT, StrategyType.LONG_COLD,
			StrategyType.ALL_TIME, StrategyType.PAIR, StrategyType.BALANCED);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LottoRoundRepository rounds;

	@MockitoBean
	private DailyRecommendationRepository recommendations;

	@MockitoBean
	private RecommendationResultRepository results;

	private static DailyRecommendation line(long id, LocalDate date, StrategyType strategy, int index) {
		DailyRecommendation line = DailyRecommendation.builder()
			.date(date)
			.roundNo(ROUND)
			.strategy(strategy)
			.lineIndex(index)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 30, 31)))
			.evidence(List.of("근거"))
			.relaxation(Relaxation.NONE)
			.fixedSnapshot("")
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(ROUND - 1)
			.createdAt(date.atTime(17, 0))
			.build();
		ReflectionTestUtils.setField(line, "id", id);
		return line;
	}

	private static RecommendationResult result(long recommendationId, int matchCount, PrizeRank rank, long amount) {
		return RecommendationResult.builder()
			.recommendationId(recommendationId)
			.roundNo(ROUND)
			.matchCount(matchCount)
			.prizeRank(rank)
			.prizeAmount(amount)
			.createdAt(LocalDateTime.of(2026, 11, 7, 20, 45))
			.build();
	}

	/** 7일 × (추천 5줄 + 대조군 5줄). 첫 추천 줄만 4등이고 첫 대조군 줄은 3등이다. */
	private void givenScoredWeek() {
		List<DailyRecommendation> lines = new ArrayList<>();
		List<RecommendationResult> scored = new ArrayList<>();
		long id = 1;
		for (int day = 0; day < 7; day++) {
			LocalDate date = SATURDAY.minusDays(6 - day);
			for (int i = 0; i < 10; i++, id++) {
				StrategyType strategy = i < 5 ? STRATEGIES.get(i) : StrategyType.CONTROL;
				lines.add(line(id, date, strategy, i));
				if (id == 1) {
					scored.add(result(id, 4, PrizeRank.FOURTH, 50_000));
				} else if (id == 6) {
					scored.add(result(id, 5, PrizeRank.THIRD, 1_500_000));
				} else {
					scored.add(result(id, 0, PrizeRank.NONE, 0));
				}
			}
		}
		given(rounds.existsById(ROUND)).willReturn(true);
		given(recommendations.findByRoundNoOrderByDateAscLineIndexAsc(ROUND)).willReturn(lines);
		given(results.findByRoundNo(ROUND)).willReturn(scored);
	}

	@Test
	void 대조군은_응답에_없다() throws Exception {
		// given
		givenScoredWeek();

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/1249/results"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.round").value(ROUND))
			.andExpect(jsonPath("$.data.lines.length()").value(35))
			.andExpect(jsonPath("$.data.lines[?(@.strategy == 'CONTROL')]").isEmpty())
			.andExpect(jsonPath("$.data.rankCounts.THIRD").value(0))
			.andExpect(jsonPath("$.data.rankCounts.FOURTH").value(1))
			.andExpect(jsonPath("$.data.rankCounts.NONE").value(34))
			.andExpect(jsonPath("$.data.prizeTotal").value(50_000));
	}

	@Test
	void 비용은_줄당_1000원이다() throws Exception {
		// given
		givenScoredWeek();

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/1249/results"));

		// then
		result.andExpect(jsonPath("$.data.cost").value(35_000))
			.andExpect(jsonPath("$.data.profit").value(15_000))
			.andExpect(jsonPath("$.data.lines[0].date").value("2026-11-01"))
			.andExpect(jsonPath("$.data.lines[0].strategy").value("RECENT_HOT"))
			.andExpect(jsonPath("$.data.lines[0].numbers[5]").value(31))
			.andExpect(jsonPath("$.data.lines[0].matchCount").value(4))
			.andExpect(jsonPath("$.data.lines[0].prizeRank").value("FOURTH"))
			.andExpect(jsonPath("$.data.lines[0].prizeAmount").value(50_000));
	}

	@Test
	void 추천이_없으면_빈_lines이다() throws Exception {
		// given
		given(rounds.existsById(ROUND)).willReturn(true);
		given(recommendations.findByRoundNoOrderByDateAscLineIndexAsc(ROUND)).willReturn(List.of());
		given(results.findByRoundNo(ROUND)).willReturn(List.of());

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/1249/results"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.lines").isEmpty())
			.andExpect(jsonPath("$.data.pending").value(false))
			.andExpect(jsonPath("$.data.cost").value(0))
			.andExpect(jsonPath("$.data.prizeTotal").value(0))
			.andExpect(jsonPath("$.data.profit").value(0));
	}

	@Test
	void 추천은_있는데_채점_전이면_pending이다() throws Exception {
		// given: 당첨번호는 저장됐지만 아직 채점하지 않았다
		given(rounds.existsById(ROUND)).willReturn(true);
		given(recommendations.findByRoundNoOrderByDateAscLineIndexAsc(ROUND))
			.willReturn(List.of(line(1, SATURDAY, StrategyType.RECENT_HOT, 0), line(2, SATURDAY, StrategyType.CONTROL, 5)));
		given(results.findByRoundNo(ROUND)).willReturn(List.of());

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/1249/results"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pending").value(true));
	}

	@Test
	void 대조군만_채점_전이면_pending이_아니다() throws Exception {
		// given
		given(rounds.existsById(ROUND)).willReturn(true);
		given(recommendations.findByRoundNoOrderByDateAscLineIndexAsc(ROUND))
			.willReturn(List.of(line(1, SATURDAY, StrategyType.RECENT_HOT, 0), line(2, SATURDAY, StrategyType.CONTROL, 5)));
		given(results.findByRoundNo(ROUND)).willReturn(List.of(result(1, 0, PrizeRank.NONE, 0)));

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/1249/results"));

		// then
		result.andExpect(jsonPath("$.data.pending").value(false))
			.andExpect(jsonPath("$.data.lines.length()").value(1));
	}

	@Test
	void 없는_회차는_404이다() throws Exception {
		// given
		given(rounds.existsById(9999)).willReturn(false);

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/9999/results"));

		// then
		result.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

}
