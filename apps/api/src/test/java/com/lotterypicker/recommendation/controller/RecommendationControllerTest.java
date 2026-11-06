package com.lotterypicker.recommendation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.RecommendationEngine;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import com.lotterypicker.recommendation.service.RecommendationSettingsService;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionOperations;

@WebMvcTest(RecommendationController.class)
@Import({ DailyRecommendationService.class, RecommendationControllerTest.Fixtures.class })
class RecommendationControllerTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
	private static final List<StrategyType> STRATEGIES = List.of(StrategyType.RECENT_HOT, StrategyType.LONG_COLD,
			StrategyType.ALL_TIME, StrategyType.PAIR, StrategyType.BALANCED);

	@TestConfiguration
	static class Fixtures {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneId.of("Asia/Seoul"));
		}

		@Bean
		Random random() {
			return new Random(1);
		}

		@Bean
		TransactionOperations transactions() {
			return TransactionOperations.withoutTransaction();
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RecommendationEngine engine;

	@MockitoBean
	private RecommendationSettingsService settings;

	@MockitoBean
	private LottoRoundRepository rounds;

	@MockitoBean
	private DailyRecommendationRepository repository;

	private static DailyRecommendation line(LocalDate date, StrategyType strategy, int lineIndex) {
		return DailyRecommendation.builder()
			.date(date)
			.roundNo(1249)
			.strategy(strategy)
			.lineIndex(lineIndex)
			.numbers(new LottoNumbers(List.of(3, 11, 19, 27, 38, 44)))
			.evidence(List.of("최근 50회 중 11번 출현"))
			.relaxation(Relaxation.NONE)
			.fixedSnapshot("")
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(1248)
			.createdAt(LocalDateTime.of(2026, 11, 6, 17, 0))
			.build();
	}

	/** 저장된 그대로 추천 5줄과 대조군 5줄. */
	private static List<DailyRecommendation> storedSet(LocalDate date) {
		List<DailyRecommendation> lines = new ArrayList<>();
		for (int i = 0; i < STRATEGIES.size(); i++) {
			lines.add(line(date, STRATEGIES.get(i), i));
			lines.add(line(date, StrategyType.CONTROL, i));
		}
		return lines;
	}

	@Test
	void 대조군은_응답에_없다() throws Exception {
		// given
		given(repository.findByDateOrderByLineIndexAsc(TODAY)).willReturn(storedSet(TODAY));

		// when
		var result = mockMvc.perform(get("/api/v1/recommendations").param("date", "2026-11-06"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.date").value("2026-11-06"))
			.andExpect(jsonPath("$.data.round").value(1249))
			.andExpect(jsonPath("$.data.historyFromRound").value(1))
			.andExpect(jsonPath("$.data.historyToRound").value(1248))
			.andExpect(jsonPath("$.data.lines", hasSize(5)))
			.andExpect(jsonPath("$.data.lines[*].strategy", not(hasItem("CONTROL"))))
			.andExpect(jsonPath("$.data.lines[0].strategy").value("RECENT_HOT"))
			.andExpect(jsonPath("$.data.lines[0].numbers", hasSize(6)))
			.andExpect(jsonPath("$.data.lines[0].evidence[0]").value("최근 50회 중 11번 출현"))
			.andExpect(jsonPath("$.data.lines[0].relaxation").value("NONE"));
	}

	@Test
	void 날짜를_생략하면_오늘이다() throws Exception {
		// given
		given(repository.findByDateOrderByLineIndexAsc(TODAY)).willReturn(storedSet(TODAY));

		// when
		var result = mockMvc.perform(get("/api/v1/recommendations"));

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.data.date").value("2026-11-06"));
	}

	@Test
	void 없으면_204이다() throws Exception {
		// given
		given(repository.findByDateOrderByLineIndexAsc(TODAY)).willReturn(List.of());

		// when
		var result = mockMvc.perform(get("/api/v1/recommendations").param("date", "2026-11-06"));

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
	}

	@Test
	void 고지_문구를_포함한다() throws Exception {
		// given
		given(repository.findByDateOrderByLineIndexAsc(TODAY)).willReturn(storedSet(TODAY));

		// when
		var result = mockMvc.perform(get("/api/v1/recommendations"));

		// then
		result.andExpect(jsonPath("$.data.disclaimer").value("번호 선택 방식 설명이며 당첨 확률과 무관합니다."));
	}

	@Test
	void 날짜_형식이_틀리면_400이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/recommendations").param("date", "11/06"));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 쓰기_요청_경로가_없다() throws Exception {
		// when, then: 지원하지 않는 메서드는 공통 처리기가 404로 응답한다
		mockMvc.perform(post("/api/v1/recommendations")).andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/recommendations")).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/recommendations")).andExpect(status().isNotFound());
	}

}
