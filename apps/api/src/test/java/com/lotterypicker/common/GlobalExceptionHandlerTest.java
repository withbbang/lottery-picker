package com.lotterypicker.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 비즈니스_예외는_ErrorCode의_상태와_코드로_변환된다() throws Exception {
		// when
		var result = mockMvc.perform(get("/test/business"));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void 검증_실패는_필드별_사유를_포함한다() throws Exception {
		// when
		var result = mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("name"))
				.andExpect(jsonPath("$.error.fields[0].reason").value("이름은 필수입니다."));
	}

	@Test
	void 잘못된_JSON은_검증_실패로_응답한다() throws Exception {
		// when
		var result = mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

	@Test
	void 없는_경로는_404_래퍼로_응답한다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/none"));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void 지원하지_않는_메서드는_404_래퍼로_응답한다() throws Exception {
		// when
		var result = mockMvc.perform(post("/test/business"));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void 예상못한_예외는_내부_메시지를_숨기고_500으로_응답한다() throws Exception {
		// when
		var result = mockMvc.perform(get("/test/unexpected"));

		// then
		result.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value(ErrorCode.COMMON_INTERNAL_ERROR.getMessage()));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/business")
		String business() {
			throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
		}

		@PostMapping("/test/validation")
		String validation(@Valid @RequestBody TestRequest request) {
			return request.name();
		}

		@GetMapping("/test/unexpected")
		String unexpected() {
			throw new IllegalStateException("내부 정보");
		}

	}

	record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
	}

}
