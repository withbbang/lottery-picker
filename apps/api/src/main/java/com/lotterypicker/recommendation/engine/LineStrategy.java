package com.lotterypicker.recommendation.engine;

import java.util.Random;

public interface LineStrategy {

	StrategyType type();

	DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random);

}
