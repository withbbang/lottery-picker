package com.lotterypicker.collector;

/** 동행복권 응답을 읽을 수 없거나 형식이 맞지 않을 때. API 응답으로 노출되지 않는다. */
public class CollectorException extends RuntimeException {

	public CollectorException(String message) {
		super(message);
	}

	public CollectorException(String message, Throwable cause) {
		super(message, cause);
	}

}
