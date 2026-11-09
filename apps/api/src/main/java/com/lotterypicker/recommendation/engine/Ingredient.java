package com.lotterypicker.recommendation.engine;

import java.util.List;

/**
 * 근거 조합의 재료. 전략이 근거로 꼽은 번호 묶음이다.
 * @param numbers 번호 1개, 또는 늘 함께 넣는 쌍 2개 (오름차순)
 * @param rank 그 전략 안에서의 근거 순위. 0이 가장 강하다.
 */
public record Ingredient(List<Integer> numbers, int rank) {

	public Ingredient {
		numbers = numbers.stream().sorted().toList();
	}

}
