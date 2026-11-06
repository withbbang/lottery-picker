package com.lotterypicker.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_INVALID_CODE(HttpStatus.BAD_REQUEST, "OTP 코드가 올바르지 않습니다."),
	AUTH_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 15분 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

}
