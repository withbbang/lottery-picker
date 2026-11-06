package com.lotterypicker.health.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@GetMapping
	public ApiResponse<HealthResponse> health() {
		return ApiResponse.success(new HealthResponse("UP"));
	}

}
