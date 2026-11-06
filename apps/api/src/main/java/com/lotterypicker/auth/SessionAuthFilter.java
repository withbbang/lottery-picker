package com.lotterypicker.auth;

import com.lotterypicker.auth.service.AuthService;
import com.lotterypicker.common.ApiResponse;
import com.lotterypicker.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** 건강 확인과 로그인 외의 모든 요청에 세션을 요구한다. 경로는 정확히 일치할 때만 공개한다. */
public class SessionAuthFilter extends OncePerRequestFilter {

	public static final String COOKIE_NAME = "LP_SESSION";

	private final AuthService authService;
	private final ObjectMapper objectMapper;

	public SessionAuthFilter(AuthService authService, ObjectMapper objectMapper) {
		this.authService = authService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isPublic(request) || authService.isValid(sessionToken(request))) {
			chain.doFilter(request, response);
			return;
		}
		writeAuthRequired(response);
	}

	private static boolean isPublic(HttpServletRequest request) {
		String method = request.getMethod();
		String path = request.getRequestURI();
		return ("GET".equals(method) && "/api/v1/health".equals(path))
				|| ("POST".equals(method) && "/api/v1/sessions".equals(path));
	}

	private static String sessionToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void writeAuthRequired(HttpServletResponse response) throws IOException {
		ErrorCode code = ErrorCode.AUTH_REQUIRED;
		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getOutputStream().write(objectMapper.writeValueAsBytes(ApiResponse.failure(code)));
	}

}
