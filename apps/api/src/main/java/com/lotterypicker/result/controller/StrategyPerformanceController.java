package com.lotterypicker.result.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.result.service.StrategyPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/strategy-performances")
@RequiredArgsConstructor
public class StrategyPerformanceController {

	private final StrategyPerformanceService service;

	@GetMapping
	public ApiResponse<StrategyPerformancesResponse> performances() {
		return ApiResponse.success(service.performances());
	}

}
