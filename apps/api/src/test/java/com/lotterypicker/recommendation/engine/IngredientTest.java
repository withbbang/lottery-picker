package com.lotterypicker.recommendation.engine;

import static com.lotterypicker.recommendation.engine.TestHistories.draw;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 근거 조합의 재료는 근거 문구에 적힌 번호와 같아야 한다. */
class IngredientTest {

	private static final NumberStatistics STATS = NumberStatistics.from(TestHistories.seeded(3, 300));
	private static final Pattern SINGLE = Pattern.compile("번호 (\\d+):");
	private static final Pattern PAIR = Pattern.compile("(\\d+)·(\\d+) 쌍");

	private static LineStrategy strategy(String name) {
		return switch (name) {
			case "RECENT_HOT" -> new RecentHotStrategy();
			case "LONG_COLD" -> new LongColdStrategy();
			default -> new AllTimeStrategy();
		};
	}

	private static List<Integer> numbersIn(List<String> evidence, Pattern pattern) {
		List<Integer> numbers = new ArrayList<>();
		for (String text : evidence) {
			Matcher m = pattern.matcher(text);
			while (m.find()) {
				for (int g = 1; g <= m.groupCount(); g++) {
					numbers.add(Integer.parseInt(m.group(g)));
				}
			}
		}
		return numbers;
	}

	@ParameterizedTest
	@ValueSource(strings = { "RECENT_HOT", "LONG_COLD", "ALL_TIME" })
	void 재료는_근거_문구의_번호와_같다(String name) {
		// when
		DrawnLine line = strategy(name).draw(STATS, RecommendationConstraint.NONE, new Random(7));

		// then
		assertThat(line.ingredients()).isNotEmpty();
		assertThat(line.ingredients().stream().map(i -> i.numbers().get(0)).toList())
			.isEqualTo(numbersIn(line.evidence(), SINGLE));
		assertThat(line.ingredients()).allSatisfy(i -> assertThat(i.numbers()).hasSize(1));
		assertThat(line.ingredients().stream().map(Ingredient::rank).toList())
			.isEqualTo(IntStream.range(0, line.ingredients().size()).boxed().toList());
	}

	@Test
	void 쌍_재료는_첫_근거_쌍이다() {
		// when
		DrawnLine line = new PairStrategy().draw(STATS, RecommendationConstraint.NONE, new Random(7));

		// then
		assertThat(line.ingredients()).hasSize(1);
		Ingredient pair = line.ingredients().get(0);
		assertThat(pair.rank()).isZero();
		assertThat(pair.numbers()).isEqualTo(numbersIn(line.evidence().subList(0, 1), PAIR));
	}

	@Test
	void 균형_전략은_재료가_없다() {
		// when
		DrawnLine line = new BalancedStrategy().draw(STATS, RecommendationConstraint.NONE, new Random(7));

		// then
		assertThat(line.ingredients()).isEmpty();
	}

	@Test
	void 근거가_없으면_재료도_없다() {
		// given: 7번만 출현한 데이터에서 7을 고정하면 근거로 쓸 번호가 없다
		DrawHistory history = new DrawHistory(List.of(draw(1, 7, 8, 9, 10, 11, 12)));
		RecommendationConstraint c = RecommendationConstraint.of(Set.of(7), Set.of(8, 9, 10, 11, 12));

		// when
		DrawnLine line = new AllTimeStrategy().draw(NumberStatistics.from(history), c, new Random(17));

		// then
		assertThat(line.ingredients()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "RECENT_HOT", "LONG_COLD", "ALL_TIME" })
	void 고정_번호는_재료에_없다(String name) {
		// given
		RecommendationConstraint c = RecommendationConstraint.of(Set.of(7), Set.of());

		// when
		DrawnLine line = strategy(name).draw(STATS, c, new Random(7));

		// then
		assertThat(line.ingredients()).noneMatch(i -> i.numbers().contains(7));
	}

}
