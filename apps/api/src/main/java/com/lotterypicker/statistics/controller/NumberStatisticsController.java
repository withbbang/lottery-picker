package com.lotterypicker.statistics.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.service.NumberStatisticsService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 추천 기준 번호 통계. 날짜를 생략하면 가장 최근 추천 기준이고, 추천이 하나도 없으면 204. */
@RestController
@RequestMapping("/api/v1/number-statistics")
@RequiredArgsConstructor
public class NumberStatisticsController {

	private final NumberStatisticsService service;

	@GetMapping
	public ResponseEntity<ApiResponse<NumberStatisticsResponse>> get(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.find(date)
			.map(stats -> ResponseEntity.ok(ApiResponse.success(stats)))
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

}
