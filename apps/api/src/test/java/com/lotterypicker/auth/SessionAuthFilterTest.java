package com.lotterypicker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lotterypicker.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class SessionAuthFilterTest {

	private final AuthService authService = mock(AuthService.class);
	private final SessionAuthFilter filter = new SessionAuthFilter(authService, JsonMapper.builder().build());
	private final MockHttpServletResponse response = new MockHttpServletResponse();
	private final MockFilterChain chain = new MockFilterChain();

	@Test
	void 쿠키가_없으면_401과_AUTH_REQUIRED_래퍼를_돌려준다() throws ServletException, IOException {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rounds");

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8))
				.contains("\"success\":false")
				.contains("\"code\":\"AUTH_REQUIRED\"")
				.contains("로그인이 필요합니다.");
	}

	@Test
	void 건강확인과_로그인은_통과시킨다() throws ServletException, IOException {
		// given
		MockHttpServletRequest health = new MockHttpServletRequest("GET", "/api/v1/health");
		MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/v1/sessions");
		MockFilterChain loginChain = new MockFilterChain();

		// when
		filter.doFilter(health, response, chain);
		filter.doFilter(login, new MockHttpServletResponse(), loginChain);

		// then
		assertThat(chain.getRequest()).isNotNull();
		assertThat(loginChain.getRequest()).isNotNull();
	}

	@Test
	void 로그아웃은_세션이_필요하다() throws ServletException, IOException {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/sessions");

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void 공개_경로와_메서드가_다르면_세션이_필요하다() throws ServletException, IOException {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/health");

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void 유효한_쿠키는_통과시킨다() throws ServletException, IOException {
		// given
		when(authService.isValid("good-token")).thenReturn(true);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rounds");
		request.setCookies(new Cookie(SessionAuthFilter.COOKIE_NAME, "good-token"));

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void 세션_확인이_실패하면_500_래퍼를_쓰고_토큰을_로그에_남기지_않는다(CapturedOutput output)
			throws ServletException, IOException {
		// given
		when(authService.isValid("good-token")).thenThrow(new IllegalStateException("db down"));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rounds");
		request.setCookies(new Cookie(SessionAuthFilter.COOKIE_NAME, "good-token"));

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(500);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8))
				.contains("\"success\":false")
				.contains("\"code\":\"COMMON_INTERNAL_ERROR\"");
		assertThat(output.getAll()).doesNotContain("good-token");
	}

	@Test
	void 무효한_쿠키는_401이다() throws ServletException, IOException {
		// given
		when(authService.isValid("bad-token")).thenReturn(false);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rounds");
		request.setCookies(new Cookie(SessionAuthFilter.COOKIE_NAME, "bad-token"));

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(401);
	}

}
