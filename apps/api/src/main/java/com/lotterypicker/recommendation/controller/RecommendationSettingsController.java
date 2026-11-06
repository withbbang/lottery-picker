package com.lotterypicker.recommendation.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.recommendation.dto.RecommendationSettingsRequest;
import com.lotterypicker.recommendation.dto.RecommendationSettingsResponse;
import com.lotterypicker.recommendation.service.RecommendationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendation-settings")
@RequiredArgsConstructor
public class RecommendationSettingsController {

	private final RecommendationSettingsService service;

	@GetMapping
	public ApiResponse<RecommendationSettingsResponse> get() {
		return ApiResponse.success(service.get());
	}

	@PutMapping
	public ApiResponse<RecommendationSettingsResponse> update(@Valid @RequestBody RecommendationSettingsRequest request) {
		return ApiResponse.success(service.update(request));
	}

}
