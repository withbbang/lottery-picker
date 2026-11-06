package com.lotterypicker.health.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 상태_조회는_성공_래퍼로_UP을_반환한다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/health"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void 허용된_출처의_CORS_사전요청을_통과시킨다() throws Exception {
		// when
		var result = mockMvc.perform(options("/api/v1/health")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET"));

		// then
		result.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}

}
