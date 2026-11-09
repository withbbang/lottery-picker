package com.lotterypicker.recommendation.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class PairStrategy implements LineStrategy {

	private static final int MAX_EVIDENCE = 2;

	@Override
	public StrategyType type() {
		return StrategyType.PAIR;
	}

	@Override
	public DrawnLine draw(NumberStatistics stats, RecommendationConstraint c, Random random) {
		WeightedSampler.Sample sample = WeightedSampler.sample(c, (number, chosen) -> weight(stats, number, chosen),
				random);
		List<PairFact> facts = evidenceFacts(stats, c, sample);
		List<Ingredient> ingredients = facts.isEmpty() ? List.of()
				: List.of(new Ingredient(List.of(facts.get(0).number(), facts.get(0).partner()), 0));
		return new DrawnLine(type(), sample.numbers(), facts.stream().map(PairFact::text).toList(), ingredients);
	}

	private static int weight(NumberStatistics stats, int number, List<Integer> chosen) {
		if (chosen.isEmpty()) {
			return stats.frequency(number) + 1;
		}
		int sum = 1;
		for (int other : chosen) {
			sum += stats.pairCount(number, other);
		}
		return sum;
	}

	/**
	 * 가중치가 높았던 번호와, 그 번호를 뽑을 때 이미 있던 번호 중 가장 자주 함께 나온 번호의 쌍을 쓴다. 근거 문구와
	 * 근거 조합 재료가 같은 목록에서 나온다.
	 */
	private static List<PairFact> evidenceFacts(NumberStatistics stats, RecommendationConstraint c,
			WeightedSampler.Sample sample) {
		List<PairFact> facts = new ArrayList<>();
		List<Integer> before = new ArrayList<>(c.fixed());
		for (WeightedSampler.Pick pick : sample.picks()) {
			if (!before.isEmpty()) {
				int partner = before.stream()
					.max(Comparator.comparingInt(other -> stats.pairCount(pick.number(), other)))
					.orElseThrow();
				facts.add(new PairFact(pick.number(), partner, stats.pairCount(pick.number(), partner), pick.weight()));
			}
			before.add(pick.number());
		}
		return facts.stream()
			.sorted(Comparator.comparingInt(PairFact::weight).reversed())
			.limit(MAX_EVIDENCE)
			.filter(fact -> fact.count() > 0)
			.toList();
	}

	private record PairFact(int number, int partner, int count, int weight) {

		String text() {
			return Math.min(number, partner) + "·" + Math.max(number, partner) + " 쌍이 " + count + "회 함께 출현";
		}

	}

}
