package com.lotterypicker.result.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.common.PageResponse;
import com.lotterypicker.result.dto.RecommendationHistoryResponse;
import com.lotterypicker.result.service.RecommendationHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 지난 추천 목록. 한 항목이 하루치 추천 한 벌이고 최근 날짜부터 준다. */
@RestController
@RequestMapping("/api/v1/recommendations/history")
@RequiredArgsConstructor
public class RecommendationHistoryController {

	private final RecommendationHistoryService service;

	@GetMapping
	public ApiResponse<PageResponse<RecommendationHistoryResponse>> history(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
		return ApiResponse.success(service.history(page, size));
	}

}
