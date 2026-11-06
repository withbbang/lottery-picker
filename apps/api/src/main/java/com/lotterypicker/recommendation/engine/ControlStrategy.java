package com.lotterypicker.recommendation.engine;

import java.util.List;
import java.util.Random;

/** 대조군. 균등 추출이며 고정·제외만 적용한다. */
public class ControlStrategy implements LineStrategy {

	@Override
	public StrategyType type() {
		return StrategyType.CONTROL;
	}

	@Override
	public DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random) {
		return new DrawnLine(type(), WeightedSampler.sample(c, WeightedSampler.UNIFORM, random).numbers(), List.of());
	}

}
