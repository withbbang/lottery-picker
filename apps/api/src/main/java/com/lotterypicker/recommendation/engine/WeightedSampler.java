package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

/** 비복원 가중 추출. 고정 번호를 먼저 넣고 나머지를 채운다. */
final class WeightedSampler {

	static final Weight UNIFORM = (number, chosen) -> 1;

	private static final int MAX_EVIDENCE = 2;

	private WeightedSampler() {
	}

	/** {@code chosen}은 이미 들어간 번호(고정 포함). */
	interface Weight {
		int of(int number, List<Integer> chosen);
	}

	record Pick(int number, int weight) {
	}

	/** {@code picks}는 추출 순서대로이며 고정 번호를 뺀다. */
	record Sample(LottoNumbers numbers, List<Pick> picks) {

		/** 가중치가 높았던 번호 최대 2개의 근거. 가중치가 기본값 1인 번호는 수치가 0이라 쓰지 않는다. */
		List<String> evidence(IntFunction<String> text) {
			return topPicks().stream()
				.map(pick -> "번호 " + pick.number() + ": " + text.apply(pick.weight() - 1))
				.toList();
		}

		/** {@link #evidence}에 적히는 번호와 같은 번호를 같은 순서로. */
		List<Ingredient> ingredients() {
			List<Pick> top = topPicks();
			List<Ingredient> result = new ArrayList<>();
			for (int i = 0; i < top.size(); i++) {
				result.add(new Ingredient(List.of(top.get(i).number()), i));
			}
			return List.copyOf(result);
		}

		private List<Pick> topPicks() {
			return picks.stream()
				.sorted(Comparator.comparingInt(Pick::weight).reversed())
				.limit(MAX_EVIDENCE)
				.filter(pick -> pick.weight() > 1)
				.toList();
		}

	}

	static Sample sample(RecommendationConstraint c, Weight weight, Random random) {
		List<Integer> chosen = new ArrayList<>(c.fixed());
		List<Integer> candidates = new ArrayList<>();
		for (int number = LottoNumbers.MIN; number <= LottoNumbers.MAX; number++) {
			if (!c.fixed().contains(number) && !c.excluded().contains(number)) {
				candidates.add(number);
			}
		}

		List<Pick> picks = new ArrayList<>();
		int[] weights = new int[candidates.size()];
		while (chosen.size() < LottoNumbers.SIZE) {
			int total = 0;
			for (int i = 0; i < candidates.size(); i++) {
				weights[i] = weight.of(candidates.get(i), chosen);
				total += weights[i];
			}
			int index = total == 0 ? random.nextInt(candidates.size()) : indexAt(weights, random.nextInt(total));
			int number = candidates.remove(index);
			picks.add(new Pick(number, weights[index]));
			chosen.add(number);
		}
		return new Sample(new LottoNumbers(chosen), picks);
	}

	private static int indexAt(int[] weights, int target) {
		int cumulative = 0;
		int index = 0;
		while (true) {
			cumulative += weights[index];
			if (target < cumulative) {
				return index;
			}
			index++;
		}
	}

}
