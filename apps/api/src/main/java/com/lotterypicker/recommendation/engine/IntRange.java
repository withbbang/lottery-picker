package com.lotterypicker.recommendation.engine;

/** 양 끝을 포함하는 정수 범위. */
public record IntRange(int min, int max) {

	public boolean contains(int value) {
		return value >= min && value <= max;
	}

}
