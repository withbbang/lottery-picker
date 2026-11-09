package com.lotterypicker.notification;

/** 요청 URL에 봇 토큰이 들어가므로 원본 예외를 cause로 잇지 않는다. */
public class TelegramException extends RuntimeException {

	public TelegramException(String message) {
		super(message);
	}

}
