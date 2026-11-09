package com.lotterypicker.simulation.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.simulation.dto.SimulationResponse;
import com.lotterypicker.simulation.dto.StrategySimulationResponse;
import com.lotterypicker.simulation.service.SimulationService;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SimulationService service;

	@Test
	void 결과가_없으면_204이다() throws Exception {
		// given
		given(service.findLatest()).willReturn(Optional.empty());

		// when
		var result = mockMvc.perform(get("/api/v1/simulations/latest"));

		// then
		result.andExpect(status().isNoContent()).andExpect(content().string(""));
	}

	@Test
	void 결과가_있으면_래퍼에_담아_돌려준다() throws Exception {
		// given
		Map<PrizeRank, Long> ranks = new EnumMap<>(PrizeRank.class);
		for (PrizeRank rank : PrizeRank.values()) {
			ranks.put(rank, 0L);
		}
		ranks.put(PrizeRank.FIFTH, 24L);
		given(service.findLatest()).willReturn(Optional.of(new SimulationResponse(101, 1249, 200,
				OffsetDateTime.parse("2026-11-09T10:00:00+09:00"), 0.0238, "범위 안내",
				List.of(new StrategySimulationResponse(StrategyType.CONTROL, 1000, 0.8, ranks, 0.024, 0.0145, 0.0335,
						false)))));

		// when
		var result = mockMvc.perform(get("/api/v1/simulations/latest"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.fromRound").value(101))
			.andExpect(jsonPath("$.data.toRound").value(1249))
			.andExpect(jsonPath("$.data.repetitions").value(200))
			.andExpect(jsonPath("$.data.computedAt").value("2026-11-09T10:00:00+09:00"))
			.andExpect(jsonPath("$.data.scopeNote").value("범위 안내"))
			.andExpect(jsonPath("$.data.strategies[0].strategy").value("CONTROL"))
			.andExpect(jsonPath("$.data.strategies[0].rankCounts.FIFTH").value(24))
			.andExpect(jsonPath("$.data.strategies[0].ciLow").value(0.0145))
			.andExpect(jsonPath("$.data.strategies[0].differsFromControl").value(false));
	}

}
