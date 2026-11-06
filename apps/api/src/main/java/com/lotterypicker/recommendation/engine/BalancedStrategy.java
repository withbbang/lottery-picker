package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.List;
import java.util.Random;

public class BalancedStrategy implements LineStrategy {

	static final String UNMET = "균형 조건 미충족";

	private static final int MAX_ATTEMPTS = 1_000;

	@Override
	public StrategyType type() {
		return StrategyType.BALANCED;
	}

	@Override
	public DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random) {
		LottoNumbers numbers = null;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			numbers = WeightedSampler.sample(c, WeightedSampler.UNIFORM, random).numbers();
			if (isBalanced(stats, numbers)) {
				return new DrawnLine(type(), numbers, List.of(summary(numbers) + ", 과거 분포 중앙 구간"));
			}
		}
		return new DrawnLine(type(), numbers, List.of(summary(numbers), UNMET));
	}

	private static boolean isBalanced(NumberStatistics stats, LottoNumbers numbers) {
		return stats.sumRange().contains(NumberStatistics.sum(numbers))
				&& stats.oddCountRange().contains(NumberStatistics.oddCount(numbers))
				&& stats.lowCountRange().contains(NumberStatistics.lowCount(numbers));
	}

	private static String summary(LottoNumbers numbers) {
		int odd = NumberStatistics.oddCount(numbers);
		return "홀" + odd + ":짝" + (LottoNumbers.SIZE - odd) + ", 합계 " + NumberStatistics.sum(numbers);
	}

}
