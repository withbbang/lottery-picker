package com.lotterypicker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
		"app.scheduling.enabled=false",
		"app.auth.enabled=true",
		"app.auth.cookie-secure=true",
		"app.auth.totp-secret=" + AuthIntegrationTest.SECRET,
})
@AutoConfigureMockMvc
class AuthIntegrationTest {

	static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetAuth() {
		jdbcTemplate.update("DELETE FROM auth_session");
		jdbcTemplate.update("UPDATE auth_state SET failed_count = 0, locked_until = NULL, last_used_step = -1");
	}

	@Test
	void 코드_없이_API를_호출하면_401이다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/rounds"));

		// then
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 건강확인은_코드_없이_호출할_수_있다() throws Exception {
		// when
		var result = mockMvc.perform(get("/api/v1/health"));

		// then
		result.andExpect(status().isOk());
	}

	@Test
	void 로그인_쿠키는_HttpOnly_Secure_SameSite_Strict이고_30일이다() throws Exception {
		// when
		var result = login(currentCode());

		// then
		result.andExpect(status().isNoContent());
		assertThat(result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE))
				.startsWith("LP_SESSION=")
				.contains("Path=/")
				.contains("Max-Age=2592000")
				.contains("HttpOnly")
				.contains("Secure")
				.contains("SameSite=Strict");
	}

	@Test
	void 여섯자리_숫자가_아니면_COMMON_VALIDATION_FAILED이다() throws Exception {
		// when
		var result = login("12345a");

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0].field").value("code"));
	}

	@Test
	void 실패_횟수는_DB에_남아_잠금으로_이어진다() throws Exception {
		// given
		String wrong = wrongCode();
		for (int i = 0; i < 4; i++) {
			login(wrong).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CODE"));
		}

		// when, then
		login(wrong).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("AUTH_LOCKED"));
		login(currentCode()).andExpect(status().isTooManyRequests());
	}

	@Test
	void 사용한_코드는_DB_기록으로_다시_거부한다() throws Exception {
		// given
		String code = currentCode();
		login(code).andExpect(status().isNoContent());

		// when
		var result = login(code);

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CODE"));
	}

	@Test
	void 로그인_쿠키로_통과하고_로그아웃하면_다시_401이다() throws Exception {
		// given
		String setCookie = login(currentCode()).andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
		Cookie cookie = new Cookie("LP_SESSION", setCookie.substring("LP_SESSION=".length(), setCookie.indexOf(';')));

		// when, then
		mockMvc.perform(get("/api/v1/rounds/0").cookie(cookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));

		var logout = mockMvc.perform(delete("/api/v1/sessions").cookie(cookie))
				.andExpect(status().isNoContent());
		assertThat(logout.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

		mockMvc.perform(get("/api/v1/rounds").cookie(cookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 기동하면_시크릿_지문을_저장한다() {
		// when
		String stored = jdbcTemplate.queryForObject("SELECT secret_fingerprint FROM auth_state", String.class);

		// then
		assertThat(stored).isEqualTo(sha256(SECRET));
	}

	private ResultActions login(String code) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + code + "\"}"));
	}

	private static String currentCode() {
		return TotpVerifier.codeAt(SECRET, System.currentTimeMillis() / 1000 / 30);
	}

	private static String wrongCode() {
		long now = System.currentTimeMillis() / 1000;
		for (int candidate = 0; candidate < 1_000_000; candidate++) {
			String code = "%06d".formatted(candidate);
			if (TotpVerifier.matchStep(SECRET, code, now) == -1
					&& TotpVerifier.matchStep(SECRET, code, now + 60) == -1) {
				return code;
			}
		}
		throw new IllegalStateException("틀린 코드를 찾지 못했습니다.");
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
