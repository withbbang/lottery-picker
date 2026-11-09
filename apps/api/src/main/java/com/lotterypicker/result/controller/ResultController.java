package com.lotterypicker.result.controller;

import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.result.dto.RoundResultResponse;
import com.lotterypicker.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rounds")
@RequiredArgsConstructor
public class ResultController {

	private final ResultService service;

	@GetMapping("/{round}/results")
	public ApiResponse<RoundResultResponse> roundResults(@PathVariable int round) {
		return ApiResponse.success(service.roundResults(round));
	}

}
