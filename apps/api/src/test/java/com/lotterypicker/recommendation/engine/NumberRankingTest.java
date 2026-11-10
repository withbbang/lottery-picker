package com.lotterypicker.recommendation.engine;

import static com.lotterypicker.recommendation.engine.TestHistories.draw;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NumberRankingTest {

	private static final Pattern EVIDENCE = Pattern.compile("^번호 (\\d+): .*?(\\d+)회(?: 출현|째 미출현)$");

	private static NumberStatistics stats(HistoricalDraw... draws) {
		return NumberStatistics.from(new DrawHistory(List.of(draws)));
	}

	private static RankedNumber find(List<RankedNumber> ranked, int number) {
		return ranked.stream().filter(r -> r.number() == number).findFirst().orElseThrow();
	}

	@Test
	void 순위는_값_내림차순이고_같은_값은_번호_오름차순이다() {
		// given
		NumberStatistics stats = NumberStatistics.from(TestHistories.seeded(3, 80));

		// when
		List<RankedNumber> ranked = NumberRanking.rank(stats, NumberMetric.ALL_TIME);

		// then
		assertThat(ranked).hasSize(45);
		IntStream.range(1, ranked.size()).forEach(i -> {
			RankedNumber prev = ranked.get(i - 1);
			RankedNumber cur = ranked.get(i);
			assertThat(cur.value()).isLessThanOrEqualTo(prev.value());
			if (cur.value() == prev.value()) {
				assertThat(cur.number()).isGreaterThan(prev.number());
			}
		});
	}

	@Test
	void 같은_값은_공동_순위이고_다음_순위는_건너뛴다() {
		// given
		NumberStatistics stats = stats(draw(1, 1, 2, 3, 4, 5, 6), draw(2, 1, 2, 3, 7, 8, 9));

		// when
		List<RankedNumber> ranked = NumberRanking.rank(stats, NumberMetric.ALL_TIME);

		// then
		assertThat(List.of(1, 2, 3)).allSatisfy(n -> {
			assertThat(find(ranked, n).rank()).isEqualTo(1);
			assertThat(find(ranked, n).tied()).isTrue();
		});
		assertThat(List.of(4, 5, 6, 7, 8, 9)).allSatisfy(n -> {
			assertThat(find(ranked, n).rank()).isEqualTo(4);
			assertThat(find(ranked, n).tied()).isTrue();
		});
		assertThat(find(ranked, 10).rank()).isEqualTo(10);
		assertThat(find(ranked, 45).rank()).isEqualTo(10);
	}

	@Test
	void 혼자인_값은_tied가_false다() {
		// given
		NumberStatistics stats = stats(draw(1, 1, 2, 3, 4, 5, 6), draw(2, 1, 7, 8, 9, 10, 11));

		// when
		RankedNumber first = NumberRanking.rank(stats, NumberMetric.ALL_TIME).get(0);

		// then
		assertThat(first.number()).isEqualTo(1);
		assertThat(first.rank()).isEqualTo(1);
		assertThat(first.tied()).isFalse();
	}

	@Test
	void 미출현_지표는_오래_안_나온_번호가_위다() {
		// given
		NumberStatistics stats = NumberStatistics.from(TestHistories.seeded(5, 60));
		int longest = IntStream.rangeClosed(1, 45).map(stats::roundsSinceLast).max().orElseThrow();

		// when
		RankedNumber first = NumberRanking.rank(stats, NumberMetric.COLD).get(0);

		// then
		assertThat(first.value()).isEqualTo(longest);
	}

	@Test
	void 근거_문구의_숫자와_순위_값이_같다() {
		// given
		NumberStatistics stats = NumberStatistics.from(TestHistories.seeded(7, 120));
		RecommendationConstraint none = RecommendationConstraint.NONE;
		List<LineStrategy> strategies = List.of(new RecentHotStrategy(), new LongColdStrategy(),
				new AllTimeStrategy());

		for (LineStrategy strategy : strategies) {
			// when
			DrawnLine line = strategy.draw(stats, none, new Random(42));
			List<RankedNumber> ranked = NumberRanking.rank(stats,
					NumberMetric.forStrategy(strategy.type()).orElseThrow());

			// then
			assertThat(line.evidence()).isNotEmpty();
			for (String text : line.evidence()) {
				Matcher m = EVIDENCE.matcher(text);
				assertThat(m.matches()).as(text).isTrue();
				int number = Integer.parseInt(m.group(1));
				int value = Integer.parseInt(m.group(2));
				assertThat(find(ranked, number).value()).as(text).isEqualTo(value);
			}
		}
	}

	@Test
	void 통계_지표가_없는_전략은_빈_값이다() {
		assertThat(List.of(StrategyType.PAIR, StrategyType.BALANCED, StrategyType.COMBINED, StrategyType.CONTROL))
			.allSatisfy(type -> assertThat(NumberMetric.forStrategy(type)).isEmpty());
		assertThat(NumberMetric.forStrategy(StrategyType.RECENT_HOT)).contains(NumberMetric.RECENT);
		assertThat(NumberMetric.forStrategy(StrategyType.LONG_COLD)).contains(NumberMetric.COLD);
		assertThat(NumberMetric.forStrategy(StrategyType.ALL_TIME)).contains(NumberMetric.ALL_TIME);
	}

}
