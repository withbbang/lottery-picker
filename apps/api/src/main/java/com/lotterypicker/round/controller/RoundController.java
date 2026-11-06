package com.lotterypicker.round.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.common.PageResponse;
import com.lotterypicker.round.dto.RoundDetailResponse;
import com.lotterypicker.round.dto.RoundSummaryResponse;
import com.lotterypicker.round.dto.WinningStoreResponse;
import com.lotterypicker.round.service.RoundService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rounds")
@RequiredArgsConstructor
public class RoundController {

	private final RoundService service;

	@GetMapping
	public ApiResponse<PageResponse<RoundSummaryResponse>> list(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.success(service.list(page, size));
	}

	@GetMapping("/{round}")
	public ApiResponse<RoundDetailResponse> detail(@PathVariable int round) {
		return ApiResponse.success(service.detail(round));
	}

	@GetMapping("/{round}/winning-stores")
	public ApiResponse<List<WinningStoreResponse>> winningStores(@PathVariable int round,
			@RequestParam(required = false) @Min(1) @Max(5) Integer rank,
			@RequestParam(required = false) String region) {
		return ApiResponse.success(service.winningStores(round, rank, region));
	}

}
