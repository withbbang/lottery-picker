package com.lotterypicker.recommendation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WeightedSamplerTest {

	private static final WeightedSampler.Weight UNIFORM = (number, chosen) -> 1;

	@Test
	void 제외_번호는_나오지_않는다() {
		// given: 1~39 제외. 후보는 40~45 뿐이다
		Set<Integer> excluded = IntStream.rangeClosed(1, 39).boxed().collect(Collectors.toSet());
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), excluded);

		// when
		WeightedSampler.Sample sample = WeightedSampler.sample(constraint, UNIFORM, new Random(1));

		// then
		assertThat(sample.numbers().values()).containsExactly(40, 41, 42, 43, 44, 45);
	}

	@Test
	void 고정_번호는_항상_포함된다() {
		// given
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(1, 45), Set.of());

		// when
		WeightedSampler.Sample sample = WeightedSampler.sample(constraint, UNIFORM, new Random(2));

		// then
		assertThat(sample.numbers().values()).contains(1, 45).hasSize(6);
		assertThat(sample.picks()).hasSize(4);
		assertThat(sample.picks()).extracting(WeightedSampler.Pick::number).doesNotContain(1, 45);
	}

	@Test
	void 가중치_0인_번호만_남아도_6개를_채운다() {
		// given: 7번만 가중치가 있다
		WeightedSampler.Weight onlySeven = (number, chosen) -> number == 7 ? 5 : 0;

		// when
		WeightedSampler.Sample sample = WeightedSampler.sample(RecommendationConstraint.NONE, onlySeven, new Random(3));

		// then
		assertThat(sample.numbers().values()).contains(7).hasSize(6);
		assertThat(sample.picks().get(0)).isEqualTo(new WeightedSampler.Pick(7, 5));
	}

	@Test
	void 가중치에_비례한_자리에서_번호를_고른다() {
		// given: 항상 0을 내는 난수는 가중치가 있는 첫 번호를 고른다
		WeightedSampler.Weight fromTen = (number, chosen) -> number >= 10 ? 1 : 0;

		// when
		WeightedSampler.Sample sample = WeightedSampler.sample(RecommendationConstraint.NONE, fromTen, new ZeroRandom());

		// then
		assertThat(sample.numbers().values()).containsExactly(10, 11, 12, 13, 14, 15);
	}

	/** 모든 난수를 0으로 내는 스텁. */
	static final class ZeroRandom extends Random {

		private static final long serialVersionUID = 1L;

		@Override
		protected int next(int bits) {
			return 0;
		}

	}

}
