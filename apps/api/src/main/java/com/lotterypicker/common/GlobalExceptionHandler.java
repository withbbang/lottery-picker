package com.lotterypicker.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		return respond(e.getErrorCode());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<ApiResponse.FieldReason> fields = e.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiResponse.FieldReason(error.getField(), error.getDefaultMessage()))
				.toList();
		ErrorCode code = ErrorCode.COMMON_VALIDATION_FAILED;
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(code, fields));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception e) {
		return respond(ErrorCode.COMMON_VALIDATION_FAILED);
	}

	@ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
		return respond(ErrorCode.COMMON_NOT_FOUND);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return respond(ErrorCode.COMMON_INTERNAL_ERROR);
	}

	private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code) {
		return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(code));
	}

}
