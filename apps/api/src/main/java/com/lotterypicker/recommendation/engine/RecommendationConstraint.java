package com.lotterypicker.recommendation.engine;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.lotto.LottoNumbers;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** 고정·제외 번호. 규칙을 어긴 값은 만들어지지 않으며, 번호 순서로 순회한다. */
public record RecommendationConstraint(Set<Integer> fixed, Set<Integer> excluded) {

	public static final int MAX_FIXED = 5;
	public static final RecommendationConstraint NONE = new RecommendationConstraint(Set.of(), Set.of());

	public RecommendationConstraint {
		fixed = Collections.unmodifiableSet(new TreeSet<>(fixed));
		excluded = Collections.unmodifiableSet(new TreeSet<>(excluded));

		if (!inRange(fixed) || !inRange(excluded)) {
			throw new BusinessException(ErrorCode.DRAW_NUMBER_OUT_OF_RANGE);
		}
		if (!Collections.disjoint(fixed, excluded)) {
			throw new BusinessException(ErrorCode.DRAW_FIXED_EXCLUDED_OVERLAP);
		}
		if (fixed.size() > MAX_FIXED) {
			throw new BusinessException(ErrorCode.RECOMMENDATION_TOO_MANY_FIXED);
		}
		if (LottoNumbers.MAX - excluded.size() < LottoNumbers.SIZE) {
			throw new BusinessException(ErrorCode.DRAW_NOT_ENOUGH_CANDIDATES);
		}
	}

	public static RecommendationConstraint of(Set<Integer> fixed, Set<Integer> excluded) {
		return new RecommendationConstraint(fixed, excluded);
	}

	private static boolean inRange(Set<Integer> numbers) {
		return numbers.stream().allMatch(n -> n >= LottoNumbers.MIN && n <= LottoNumbers.MAX);
	}

}
