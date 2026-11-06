package com.lotterypicker.recommendation.engine;

import static com.lotterypicker.recommendation.engine.TestHistories.numbers;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PopularityFilterTest {

	@Test
	void 전부_31이하면_인기_조합이다() {
		// then
		assertThat(PopularityFilter.isPopular(numbers(3, 7, 11, 19, 25, 31), Set.of())).isTrue();
		assertThat(PopularityFilter.isPopular(numbers(3, 7, 11, 19, 25, 32), Set.of())).isFalse();
	}

	@Test
	void 네개_연속이면_인기_조합이다() {
		// then
		assertThat(PopularityFilter.isPopular(numbers(1, 2, 3, 4, 20, 40), Set.of())).isTrue();
		assertThat(PopularityFilter.isPopular(numbers(1, 2, 3, 20, 33, 40), Set.of())).isFalse();
	}

	@Test
	void 등차수열이면_인기_조합이다() {
		// then
		assertThat(PopularityFilter.isPopular(numbers(1, 8, 15, 22, 29, 36), Set.of())).isTrue();
		assertThat(PopularityFilter.isPopular(numbers(5, 10, 15, 20, 25, 45), Set.of())).isFalse();
	}

	@Test
	void 과거_1등_조합과_같으면_인기_조합이다() {
		// given
		var winner = numbers(16, 18, 27, 36, 38, 42);

		// then
		assertThat(PopularityFilter.isPopular(winner, Set.of(winner))).isTrue();
		assertThat(PopularityFilter.isPopular(winner, Set.of(numbers(16, 18, 27, 36, 38, 43)))).isFalse();
	}

}
