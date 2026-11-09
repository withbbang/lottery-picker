package com.lotterypicker.simulation.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.simulation.dto.SimulationResponse;
import com.lotterypicker.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 저장된 마지막 결과만 읽는다. 요청 시점에 계산하지 않는다. */
@RestController
@RequestMapping("/api/v1/simulations")
@RequiredArgsConstructor
public class SimulationController {

	private final SimulationService service;

	@GetMapping("/latest")
	public ResponseEntity<ApiResponse<SimulationResponse>> latest() {
		return service.findLatest()
			.map(result -> ResponseEntity.ok(ApiResponse.success(result)))
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

}
