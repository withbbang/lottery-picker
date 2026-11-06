package com.lotterypicker.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lotterypicker.recommendation.entity.RecommendationSettings;
import com.lotterypicker.recommendation.repository.RecommendationSettingsRepository;
import com.lotterypicker.recommendation.service.RecommendationSettingsService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(RecommendationSettingsController.class)
@Import(RecommendationSettingsService.class)
class RecommendationSettingsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RecommendationSettingsRepository repository;

	private final RecommendationSettings settings = RecommendationSettings.empty();

	@BeforeEach
	void setUp() {
		given(repository.findById(RecommendationSettings.SINGLE_ROW_ID)).willReturn(Optional.of(settings));
	}

	private ResultActions putSettings(String body) throws Exception {
		return mockMvc.perform(put("/api/v1/recommendation-settings")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	@Test
	void 기본값은_빈_목록이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/recommendation-settings"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.fixed").isEmpty())
			.andExpect(jsonPath("$.data.excluded").isEmpty());
	}

	@Test
	void 저장한_값을_돌려준다() throws Exception {
		// when
		var saved = putSettings("{\"fixed\":[7,3],\"excluded\":[45,1]}");
		var loaded = mockMvc.perform(get("/api/v1/recommendation-settings"));

		// then
		saved.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fixed[0]").value(3))
			.andExpect(jsonPath("$.data.fixed[1]").value(7));
		loaded.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fixed[0]").value(3))
			.andExpect(jsonPath("$.data.fixed[1]").value(7))
			.andExpect(jsonPath("$.data.excluded[0]").value(1))
			.andExpect(jsonPath("$.data.excluded[1]").value(45));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			{"fixed":[46],"excluded":[]}            | DRAW_NUMBER_OUT_OF_RANGE
			{"fixed":[7],"excluded":[7]}            | DRAW_FIXED_EXCLUDED_OVERLAP
			{"fixed":[1,2,3,4,5,6],"excluded":[]}   | RECOMMENDATION_TOO_MANY_FIXED
			{"fixed":[],"excluded":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40]} | DRAW_NOT_ENOUGH_CANDIDATES
			""")
	void 규칙_위반은_해당_에러코드와_400이다(String body, String code) throws Exception {
		// when
		var result = putSettings(body);

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value(code));
		assertThat(settings.getFixedNumbers()).isEmpty();
		assertThat(settings.getExcludedNumbers()).isEmpty();
	}

	@Test
	void 중복_숫자는_COMMON_VALIDATION_FAILED이다() throws Exception {
		// when
		var result = putSettings("{\"fixed\":[3,3],\"excluded\":[]}");

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
			.andExpect(jsonPath("$.error.fields[0].field").value("fixed"));
	}

	@Test
	void 목록이_없으면_COMMON_VALIDATION_FAILED이다() throws Exception {
		// when
		var result = putSettings("{\"fixed\":[3]}");

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
	}

}
