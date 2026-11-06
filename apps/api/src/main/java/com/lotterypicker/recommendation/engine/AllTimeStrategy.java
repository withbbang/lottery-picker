package com.lotterypicker.recommendation.engine;

import java.util.Random;

public class AllTimeStrategy implements LineStrategy {

	@Override
	public StrategyType type() {
		return StrategyType.ALL_TIME;
	}

	@Override
	public DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random) {
		WeightedSampler.Sample sample = WeightedSampler.sample(c,
				(number, chosen) -> stats.frequency(number) + 1, random);
		return new DrawnLine(type(), sample.numbers(),
				sample.evidence(count -> "전체 " + stats.totalRounds() + "회 중 " + count + "회 출현"));
	}

}
