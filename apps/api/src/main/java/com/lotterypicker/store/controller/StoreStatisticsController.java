package com.lotterypicker.store.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.store.dto.StoreStatisticsResponse;
import com.lotterypicker.store.service.StoreStatisticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 당첨 판매점 지역 통계. 등수는 1·2등만, 판매점 정보가 하나도 없으면 204. */
@RestController
@RequestMapping("/api/v1/store-statistics")
@RequiredArgsConstructor
public class StoreStatisticsController {

	private final StoreStatisticsService service;

	@GetMapping
	public ResponseEntity<ApiResponse<StoreStatisticsResponse>> get(
			@RequestParam(defaultValue = "1") @Min(1) @Max(2) int rank,
			@RequestParam(required = false) String region,
			@RequestParam(required = false) String district) {
		return service.find(rank, blankToNull(region), blankToNull(district))
			.map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@Nullable
	private static String blankToNull(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
