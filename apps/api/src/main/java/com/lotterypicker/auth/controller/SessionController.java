package com.lotterypicker.auth.controller;

import com.lotterypicker.auth.SessionAuthFilter;
import com.lotterypicker.auth.dto.SessionCreateRequest;
import com.lotterypicker.auth.service.AuthService;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@ConditionalOnProperty("app.auth.enabled")
public class SessionController {

	private static final Duration COOKIE_LIFETIME = Duration.ofDays(30);

	private final AuthService authService;
	private final boolean cookieSecure;

	public SessionController(AuthService authService, @Value("${app.auth.cookie-secure}") boolean cookieSecure) {
		this.authService = authService;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping
	public ResponseEntity<Void> create(@Valid @RequestBody SessionCreateRequest request) {
		String token = authService.login(request.code());
		return withCookie(token, COOKIE_LIFETIME);
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(
			@CookieValue(name = SessionAuthFilter.COOKIE_NAME, required = false) String token) {
		authService.logout(token);
		return withCookie("", Duration.ZERO);
	}

	private ResponseEntity<Void> withCookie(String value, Duration maxAge) {
		ResponseCookie cookie = ResponseCookie.from(SessionAuthFilter.COOKIE_NAME, value)
				.httpOnly(true)
				.secure(cookieSecure)
				.sameSite("Strict")
				.path("/")
				.maxAge(maxAge)
				.build();
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}

}
