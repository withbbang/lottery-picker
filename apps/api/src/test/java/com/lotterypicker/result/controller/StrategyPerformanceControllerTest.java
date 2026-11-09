package com.lotterypicker.result.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.result.dto.StrategyPerformanceResponse;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.result.service.StrategyPerformanceService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StrategyPerformanceController.class)
class StrategyPerformanceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StrategyPerformanceService service;

	@Test
	void 성적표는_이론값과_전략_목록을_래퍼에_담아_돌려준다() throws Exception {
		// given
		Map<PrizeRank, Integer> ranks = new EnumMap<>(PrizeRank.class);
		for (PrizeRank rank : PrizeRank.values()) {
			ranks.put(rank, 0);
		}
		ranks.put(PrizeRank.FIFTH, 2);
		given(service.performances()).willReturn(new StrategyPerformancesResponse(0.0238,
				List.of(new StrategyPerformanceResponse(StrategyType.CONTROL, 10, 1.5, ranks, 0.2, 8, 0.25, false))));

		// when
		var result = mockMvc.perform(get("/api/v1/strategy-performances"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.theoryAtLeastFifthRate").value(0.0238))
			.andExpect(jsonPath("$.data.strategies[0].strategy").value("CONTROL"))
			.andExpect(jsonPath("$.data.strategies[0].lineCount").value(10))
			.andExpect(jsonPath("$.data.strategies[0].rankCounts.FIFTH").value(2))
			.andExpect(jsonPath("$.data.strategies[0].noSettingsLineCount").value(8))
			.andExpect(jsonPath("$.data.strategies[0].sampleSufficient").value(false));
	}

}
