package com.lotterypicker.recommendation.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조회만 있다. 추천은 생성 후 수정 경로를 두지 않는다. 날짜를 생략하면 오늘 추천, 없으면 가장 최근 추천을 준다.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

	private final DailyRecommendationService service;

	@GetMapping
	public ResponseEntity<ApiResponse<RecommendationSetResponse>> get(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return (date != null ? service.find(date) : service.findLatest(service.today()))
			.map(set -> ResponseEntity.ok(ApiResponse.success(set)))
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

}
