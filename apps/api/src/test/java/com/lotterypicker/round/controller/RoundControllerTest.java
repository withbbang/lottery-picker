package com.lotterypicker.round.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.common.PageResponse;
import com.lotterypicker.round.dto.RoundSummaryResponse;
import com.lotterypicker.round.service.RoundService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoundController.class)
class RoundControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RoundService service;

	private PageResponse<RoundSummaryResponse> page(int size) {
		return new PageResponse<>(
			List.of(new RoundSummaryResponse(1248, List.of(16, 18, 27, 36, 38, 42), 22, LocalDate.of(2026, 10, 31))),
			0, size, 1, 1);
	}

	@Test
	void 회차_목록은_래퍼와_페이징_형태로_돌려준다() throws Exception {
		// given
		given(service.list(0, 20)).willReturn(page(20));

		// when
		var result = mockMvc.perform(get("/api/v1/rounds"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items[0].round").value(1248))
			.andExpect(jsonPath("$.data.items[0].numbers[5]").value(42))
			.andExpect(jsonPath("$.data.items[0].drawDate").value("2026-10-31"))
			.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void size_100은_통과한다() throws Exception {
		// given
		given(service.list(0, 100)).willReturn(page(100));

		// when
		var result = mockMvc.perform(get("/api/v1/rounds").param("size", "100"));

		// then
		result.andExpect(status().isOk());
	}

	@Test
	void size가_100을_넘으면_COMMON_VALIDATION_FAILED이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/rounds").param("size", "101"));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
			.andExpect(jsonPath("$.error.fields[0].field").value("size"));
	}

	@Test
	void 음수_page나_0_size는_COMMON_VALIDATION_FAILED이다() throws Exception {
		mockMvc.perform(get("/api/v1/rounds").param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/rounds").param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 없는_회차는_404_COMMON_NOT_FOUND이다() throws Exception {
		// given
		given(service.detail(9999)).willThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/9999"));

		// then
		result.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void 판매점_조회는_rank와_region을_서비스에_넘긴다() throws Exception {
		// given
		given(service.winningStores(eq(1248), eq(1), eq("온라인"))).willReturn(List.of());

		// when
		var result = mockMvc.perform(get("/api/v1/rounds/1248/winning-stores")
			.param("rank", "1").param("region", "온라인"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data").isEmpty());
		verify(service).winningStores(1248, 1, "온라인");
	}

	@Test
	void 범위_밖_rank는_COMMON_VALIDATION_FAILED이다() throws Exception {
		mockMvc.perform(get("/api/v1/rounds/1248/winning-stores").param("rank", "6"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

}
