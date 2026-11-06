package com.lotterypicker.recommendation.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecommendationConstraintTest {

	private static Set<Integer> range(int from, int to) {
		return IntStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
	}

	private static void assertRejected(Set<Integer> fixed, Set<Integer> excluded, ErrorCode code) {
		assertThatThrownBy(() -> RecommendationConstraint.of(fixed, excluded))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(code);
	}

	@Test
	void 범위_밖이면_DRAW_NUMBER_OUT_OF_RANGE이다() {
		// then
		assertRejected(Set.of(0), Set.of(), ErrorCode.DRAW_NUMBER_OUT_OF_RANGE);
		assertRejected(Set.of(46), Set.of(), ErrorCode.DRAW_NUMBER_OUT_OF_RANGE);
		assertRejected(Set.of(), Set.of(0), ErrorCode.DRAW_NUMBER_OUT_OF_RANGE);
		assertRejected(Set.of(), Set.of(46), ErrorCode.DRAW_NUMBER_OUT_OF_RANGE);
	}

	@Test
	void 경계값_1과_45는_허용한다() {
		// when
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(1), Set.of(45));

		// then
		assertThat(constraint.fixed()).containsExactly(1);
		assertThat(constraint.excluded()).containsExactly(45);
	}

	@Test
	void 겹치면_DRAW_FIXED_EXCLUDED_OVERLAP이다() {
		// then
		assertRejected(Set.of(7), Set.of(7), ErrorCode.DRAW_FIXED_EXCLUDED_OVERLAP);
	}

	@Test
	void 제외_39개는_허용하고_40개는_DRAW_NOT_ENOUGH_CANDIDATES이다() {
		// then
		assertThat(RecommendationConstraint.of(Set.of(), range(1, 39)).excluded()).hasSize(39);
		assertRejected(Set.of(), range(1, 40), ErrorCode.DRAW_NOT_ENOUGH_CANDIDATES);
	}

	@Test
	void 고정_5개는_허용하고_6개는_RECOMMENDATION_TOO_MANY_FIXED이다() {
		// then
		assertThat(RecommendationConstraint.of(range(1, 5), Set.of()).fixed()).hasSize(5);
		assertRejected(range(1, 6), Set.of(), ErrorCode.RECOMMENDATION_TOO_MANY_FIXED);
	}

	@Test
	void 기본_상수는_비어_있다() {
		// then
		assertThat(RecommendationConstraint.NONE.fixed()).isEmpty();
		assertThat(RecommendationConstraint.NONE.excluded()).isEmpty();
	}

}
