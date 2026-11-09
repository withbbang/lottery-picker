package com.lotterypicker.recommendation.engine;

import java.util.Random;

public class LongColdStrategy implements LineStrategy {

	@Override
	public StrategyType type() {
		return StrategyType.LONG_COLD;
	}

	@Override
	public DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random) {
		WeightedSampler.Sample sample = WeightedSampler.sample(c,
				(number, chosen) -> stats.roundsSinceLast(number) + 1, random);
		return new DrawnLine(type(), sample.numbers(), sample.evidence(rounds -> rounds + "회째 미출현"),
				sample.ingredients());
	}

}
