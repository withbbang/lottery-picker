package com.lotterypicker.store.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.store.dto.AreaCountResponse;
import com.lotterypicker.store.dto.StoreResponse;
import com.lotterypicker.store.dto.StoreStatisticsResponse;
import com.lotterypicker.store.service.StoreStatisticsService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreStatisticsController.class)
class StoreStatisticsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StoreStatisticsService service;

	private static StoreStatisticsResponse response(int rank) {
		StoreResponse store = new StoreResponse("망우점", "서울 중랑구 망우로 1", "서울", "중랑구", 3, 1249, 37.5, 127.1,
			"정상");
		return new StoreStatisticsResponse(rank, 262, 1249, List.of(new AreaCountResponse("경기", 2100)), null,
			List.of(store), List.of());
	}

	@Test
	void 기본은_1등_전국이다() throws Exception {
		// given
		given(service.find(1, null, null)).willReturn(Optional.of(response(1)));

		// when
		var result = mockMvc.perform(get("/api/v1/store-statistics"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.rank").value(1))
			.andExpect(jsonPath("$.data.fromRound").value(262))
			.andExpect(jsonPath("$.data.regions[0].name").value("경기"))
			.andExpect(jsonPath("$.data.repeatStores[0].wins").value(3));
	}

	@Test
	void 등수와_지역을_넘긴다() throws Exception {
		// given
		given(service.find(2, "서울", "중랑구")).willReturn(Optional.of(response(2)));

		// when
		var result = mockMvc.perform(get("/api/v1/store-statistics")
			.param("rank", "2").param("region", "서울").param("district", "중랑구"));

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.data.rank").value(2));
	}

	@Test
	void 등수가_1_2가_아니면_400() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/store-statistics").param("rank", "3"));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 데이터가_없으면_204() throws Exception {
		// given
		given(service.find(1, null, null)).willReturn(Optional.empty());

		// when
		var result = mockMvc.perform(get("/api/v1/store-statistics"));

		// then
		result.andExpect(status().isNoContent());
	}

	@Test
	void 없는_지역이면_404() throws Exception {
		// given
		given(service.find(1, "화성", null)).willThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));

		// when
		var result = mockMvc.perform(get("/api/v1/store-statistics").param("region", "화성"));

		// then
		result.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

}
