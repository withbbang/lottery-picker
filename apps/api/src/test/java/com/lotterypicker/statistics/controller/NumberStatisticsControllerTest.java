package com.lotterypicker.statistics.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.dto.RankedNumberResponse;
import com.lotterypicker.statistics.service.NumberStatisticsService;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NumberStatisticsController.class)
class NumberStatisticsControllerTest {

	private static final LocalDate NOV_9 = LocalDate.of(2026, 11, 9);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NumberStatisticsService service;

	private static NumberStatisticsResponse response() {
		Map<NumberMetric, List<RankedNumberResponse>> metrics = new EnumMap<>(NumberMetric.class);
		for (NumberMetric metric : NumberMetric.values()) {
			metrics.put(metric, List.of(new RankedNumberResponse(7, 4, 1, false), new RankedNumberResponse(3, 3, 2, true)));
		}
		return new NumberStatisticsResponse(NOV_9, 1, 1198, 1198, 50, metrics);
	}

	@Test
	void 날짜를_주면_그날_추천_기준_통계를_준다() throws Exception {
		// given
		given(service.find(NOV_9)).willReturn(Optional.of(response()));

		// when
		var result = mockMvc.perform(get("/api/v1/number-statistics").param("date", "2026-11-09"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.date").value("2026-11-09"))
			.andExpect(jsonPath("$.data.historyToRound").value(1198))
			.andExpect(jsonPath("$.data.recentWindow").value(50))
			.andExpect(jsonPath("$.data.metrics.RECENT[0].number").value(7))
			.andExpect(jsonPath("$.data.metrics.RECENT[0].rank").value(1))
			.andExpect(jsonPath("$.data.metrics.COLD[1].tied").value(true));
	}

	@Test
	void 날짜를_생략하면_가장_최근_추천_기준이다() throws Exception {
		// given
		given(service.find(null)).willReturn(Optional.of(response()));

		// when
		var result = mockMvc.perform(get("/api/v1/number-statistics"));

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.data.date").value("2026-11-09"));
	}

	@Test
	void 추천이_하나도_없으면_204이다() throws Exception {
		// given
		given(service.find(null)).willReturn(Optional.empty());

		// when
		var result = mockMvc.perform(get("/api/v1/number-statistics"));

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
	}

	@Test
	void 그날_추천이_없으면_404이다() throws Exception {
		// given
		given(service.find(NOV_9)).willThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));

		// when
		var result = mockMvc.perform(get("/api/v1/number-statistics").param("date", "2026-11-09"));

		// then
		result.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void 날짜_형식이_틀리면_400이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/number-statistics").param("date", "abc"));

		// then
		result.andExpect(status().isBadRequest());
	}

}
