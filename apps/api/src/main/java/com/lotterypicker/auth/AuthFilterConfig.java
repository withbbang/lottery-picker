package com.lotterypicker.auth;

import com.lotterypicker.auth.service.AuthService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty("app.auth.enabled")
public class AuthFilterConfig {

	@Bean
	public FilterRegistrationBean<SessionAuthFilter> sessionAuthFilter(AuthService authService,
			ObjectMapper objectMapper) {
		return new FilterRegistrationBean<>(new SessionAuthFilter(authService, objectMapper));
	}

	/** 웹 서버가 요청을 받기 전에 시크릿 변경을 확인해, 폐기 대상 세션이 통과할 틈을 없앤다. */
	@Bean
	public SmartInitializingSingleton authSecretCheck(AuthService authService) {
		return authService::revokeSessionsIfSecretChanged;
	}

}
