package com.lotterypicker.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SessionCreateRequest(
		@NotNull(message = "OTP 코드를 입력해 주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "OTP 코드는 숫자 6자리입니다.")
		String code) {
}
