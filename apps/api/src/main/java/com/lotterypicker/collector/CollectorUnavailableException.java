package com.lotterypicker.collector;

/** 요청 자체가 실패했거나 JSON이 아닌 응답이 온 경우. 특정 회차 데이터가 아니라 사이트 상태 문제로 본다. */
public class CollectorUnavailableException extends CollectorException {

	public CollectorUnavailableException(String message) {
		super(message);
	}

	public CollectorUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
