package com.lotterypicker.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> failure(ErrorCode errorCode) {
		return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), errorCode.getMessage(), null));
	}

	public static ApiResponse<Void> failure(ErrorCode errorCode, List<FieldReason> fields) {
		return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), errorCode.getMessage(), fields));
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorBody(String code, String message, List<FieldReason> fields) {
	}

	public record FieldReason(String field, String reason) {
	}

}
