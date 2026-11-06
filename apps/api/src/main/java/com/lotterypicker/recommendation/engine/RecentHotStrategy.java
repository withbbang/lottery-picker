package com.lotterypicker.recommendation.engine;

import java.util.Random;

public class RecentHotStrategy implements LineStrategy {

	private static final int WINDOW = 50;

	@Override
	public StrategyType type() {
		return StrategyType.RECENT_HOT;
	}

	@Override
	public DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random) {
		WeightedSampler.Sample sample = WeightedSampler.sample(c,
				(number, chosen) -> stats.recentFrequency(number, WINDOW) + 1, random);
		return new DrawnLine(type(), sample.numbers(),
				sample.evidence(count -> "최근 " + WINDOW + "회 중 " + count + "회 출현"));
	}

}
