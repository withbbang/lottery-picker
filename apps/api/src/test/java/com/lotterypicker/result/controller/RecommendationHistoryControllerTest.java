package com.lotterypicker.result.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.common.PageResponse;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.result.dto.RecommendationHistoryLineResponse;
import com.lotterypicker.result.dto.RecommendationHistoryResponse;
import com.lotterypicker.result.service.RecommendationHistoryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationHistoryController.class)
class RecommendationHistoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RecommendationHistoryService service;

	@Test
	void 기본_페이지는_0_크기는_10이고_채점_전_줄은_null로_준다() throws Exception {
		// given
		var scored = new RecommendationHistoryLineResponse(StrategyType.RECENT_HOT, List.of(1, 2, 3, 4, 30, 45),
				List.of("근거"), Relaxation.NONE, List.of(30), NumberMetric.RECENT, 4, PrizeRank.FOURTH,
				List.of(1, 2, 3, 4), 45);
		var pending = new RecommendationHistoryLineResponse(StrategyType.PAIR, List.of(1, 2, 3, 4, 30, 45),
				List.of("근거"), Relaxation.NONE, List.of(), null, null, null, null, null);
		given(service.history(0, 10)).willReturn(new PageResponse<>(
				List.of(new RecommendationHistoryResponse(LocalDate.of(2026, 11, 7), 1249, List.of(scored, pending))),
				0, 10, 1, 1));

		// when
		var result = mockMvc.perform(get("/api/v1/recommendations/history"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items[0].date").value("2026-11-07"))
			.andExpect(jsonPath("$.data.items[0].round").value(1249))
			.andExpect(jsonPath("$.data.items[0].lines[0].matchCount").value(4))
			.andExpect(jsonPath("$.data.items[0].lines[0].prizeRank").value("FOURTH"))
			.andExpect(jsonPath("$.data.items[0].lines[0].matchedNumbers[3]").value(4))
			.andExpect(jsonPath("$.data.items[0].lines[0].matchedBonus").value(45))
			.andExpect(jsonPath("$.data.items[0].lines[0].evidenceNumbers[0]").value(30))
			.andExpect(jsonPath("$.data.items[0].lines[0].statsMetric").value("RECENT"))
			.andExpect(jsonPath("$.data.items[0].lines[1].matchCount").isEmpty())
			.andExpect(jsonPath("$.data.items[0].lines[1].matchedNumbers").isEmpty())
			.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void 크기가_100을_넘으면_400이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/recommendations/history").param("size", "101"));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

}
