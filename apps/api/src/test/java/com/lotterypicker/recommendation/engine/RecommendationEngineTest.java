package com.lotterypicker.recommendation.engine;

import static com.lotterypicker.recommendation.engine.TestHistories.draw;
import static com.lotterypicker.recommendation.engine.TestHistories.numbers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecommendationEngineTest {

	private static final DrawHistory HISTORY = TestHistories.seeded(11, 300);

	private final RecommendationEngine engine = new RecommendationEngine();

	private static Set<Integer> range(int from, int to) {
		return IntStream.rangeClosed(from, to).boxed().collect(Collectors.toSet());
	}

	private static Set<Integer> allExcept(Integer... kept) {
		Set<Integer> excluded = range(1, 45);
		excluded.removeAll(List.of(kept));
		return excluded;
	}

	private static List<DrawnLine> allLines(GeneratedSet set) {
		List<DrawnLine> lines = new ArrayList<>(set.lines().stream().map(GeneratedLine::line).toList());
		lines.addAll(set.control());
		return lines;
	}

	private static int overlapWithoutFixed(LottoNumbers a, LottoNumbers b, Set<Integer> fixed) {
		return (int) a.values().stream().filter(b::contains).filter(n -> !fixed.contains(n)).count();
	}

	private static void assertOverlapAtMostTwo(GeneratedSet set, Set<Integer> fixed) {
		List<GeneratedLine> lines = set.lines();
		for (int i = 0; i < lines.size(); i++) {
			for (int j = i + 1; j < lines.size(); j++) {
				assertThat(overlapWithoutFixed(lines.get(i).line().numbers(), lines.get(j).line().numbers(), fixed))
					.isLessThanOrEqualTo(2);
			}
		}
	}

	@Test
	void 전략별로_한_줄씩_다섯_줄을_만든다() {
		// when
		GeneratedSet set = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(1));

		// then
		assertThat(set.lines()).extracting(line -> line.line().strategy()).containsExactly(
				StrategyType.RECENT_HOT, StrategyType.LONG_COLD, StrategyType.ALL_TIME, StrategyType.PAIR,
				StrategyType.BALANCED);
	}

	@Test
	void 고정_번호를_모든_줄과_대조군에_넣는다() {
		// given
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(1, 45), Set.of());

		// when
		GeneratedSet set = engine.generate(HISTORY, constraint, new Random(2));

		// then
		assertThat(allLines(set)).hasSize(10)
			.allSatisfy(line -> assertThat(line.numbers().values()).contains(1, 45));
	}

	@Test
	void 제외_번호를_모든_줄과_대조군에서_뺀다() {
		// given
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), Set.of(1, 7, 45));

		// when
		GeneratedSet set = engine.generate(HISTORY, constraint, new Random(3));

		// then
		assertThat(allLines(set)).hasSize(10)
			.allSatisfy(line -> assertThat(line.numbers().values()).doesNotContain(1, 7, 45));
	}

	@Test
	void 기본_설정에서_고정을_뺀_줄간_겹침은_2개_이하이다() {
		// when
		GeneratedSet set = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(4));

		// then
		assertThat(set.lines()).allSatisfy(line -> assertThat(line.relaxation()).isEqualTo(Relaxation.NONE));
		assertOverlapAtMostTwo(set, Set.of());
	}

	@Test
	void 겹침_계산은_고정_번호를_뺀다() {
		// given: 고정 3개를 겹침에 넣으면 모든 줄이 3개 이상 겹쳐 완화된다
		Set<Integer> fixed = Set.of(1, 2, 3);

		// when
		GeneratedSet set = engine.generate(HISTORY, RecommendationConstraint.of(fixed, Set.of()), new Random(5));

		// then
		assertThat(set.lines()).allSatisfy(line -> assertThat(line.relaxation()).isEqualTo(Relaxation.NONE));
		assertOverlapAtMostTwo(set, fixed);
	}

	@Test
	void 기본_설정에서_인기_조합은_나오지_않는다() {
		// given
		Set<LottoNumbers> winners = HISTORY.draws().stream().map(HistoricalDraw::numbers).collect(Collectors.toSet());

		// when
		GeneratedSet set = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(6));

		// then
		assertThat(set.lines()).allSatisfy(line -> {
			assertThat(line.relaxation()).isEqualTo(Relaxation.NONE);
			assertThat(PopularityFilter.isPopular(line.line().numbers(), winners)).isFalse();
		});
	}

	@Test
	void 후보가_과거_1등_조합뿐이면_인기_제약까지_완화한다() {
		// given: 후보가 정확히 6개이고 그 조합이 과거 1등이면 인기 제약을 지킬 수 없다
		Integer[] kept = { 1, 10, 20, 30, 40, 45 };
		DrawHistory history = new DrawHistory(List.of(draw(1, kept)));
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), allExcept(kept));

		// when
		GeneratedSet set = engine.generate(history, constraint, new Random(7));

		// then
		assertThat(set.lines()).allSatisfy(
				line -> assertThat(line.relaxation()).isEqualTo(Relaxation.OVERLAP_AND_POPULARITY));
	}

	@Test
	void 대조군은_5줄이고_CONTROL_전략이다() {
		// when
		GeneratedSet set = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(8));

		// then
		assertThat(set.control()).hasSize(5)
			.allSatisfy(line -> assertThat(line.strategy()).isEqualTo(StrategyType.CONTROL));
	}

	@Test
	void 대조군에는_인기_필터를_적용하지_않는다() {
		// given: 항상 0을 내는 난수는 1~6만 뽑는다. 회차도 1~6뿐이라 균형 범위는 통과한다
		DrawHistory history = new DrawHistory(List.of(draw(1, 1, 2, 3, 4, 5, 6)));

		// when
		GeneratedSet set = engine.generate(history, RecommendationConstraint.NONE, new WeightedSamplerTest.ZeroRandom());

		// then
		assertThat(set.control()).hasSize(5).allSatisfy(line -> {
			assertThat(line.numbers()).isEqualTo(numbers(1, 2, 3, 4, 5, 6));
			assertThat(PopularityFilter.isPopular(line.numbers(), Set.of())).isTrue();
			assertThat(line.evidence()).isEmpty();
		});
	}

	@Test
	void 후보가_정확히_6개면_완화하고_5줄을_만든다() {
		// given
		Integer[] kept = { 1, 10, 20, 30, 40, 45 };
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), allExcept(kept));

		// when
		GeneratedSet set = engine.generate(HISTORY, constraint, new Random(9));

		// then
		assertThat(set.lines()).hasSize(5)
			.allSatisfy(line -> assertThat(line.line().numbers()).isEqualTo(numbers(kept)));
		assertThat(set.lines()).extracting(GeneratedLine::relaxation).containsExactly(Relaxation.NONE,
				Relaxation.OVERLAP, Relaxation.OVERLAP, Relaxation.OVERLAP, Relaxation.OVERLAP);
		assertThat(set.lines().get(0).line().evidence()).doesNotContain("중복 제한 완화");
		assertThat(set.lines().subList(1, 5))
			.allSatisfy(line -> assertThat(line.line().evidence()).contains("중복 제한 완화"));
	}

	@Test
	void _32에서_45를_모두_제외하면_생일_필터를_완화한다() {
		// given
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), range(32, 45));

		// when
		GeneratedSet set = engine.generate(HISTORY, constraint, new Random(10));

		// then
		assertThat(set.lines()).hasSize(5).allSatisfy(line -> {
			assertThat(line.relaxation()).isEqualTo(Relaxation.OVERLAP_AND_POPULARITY);
			assertThat(line.line().evidence()).contains("중복·인기 조합 제한 완화");
		});
	}

	@Test
	void 고정_번호_규칙은_완화하지_않는다() {
		// given: 인기 제약을 지킬 수 없어 완화해도 고정·제외는 그대로다
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(7, 9), range(32, 45));

		// when
		GeneratedSet set = engine.generate(HISTORY, constraint, new Random(11));

		// then
		assertThat(set.lines()).allSatisfy(line -> {
			assertThat(line.relaxation()).isEqualTo(Relaxation.OVERLAP_AND_POPULARITY);
			assertThat(line.line().numbers().values()).contains(7, 9);
			assertThat(line.line().numbers().values()).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(31));
		});
	}

	@Test
	void 시드가_같으면_결과가_같다() {
		// when
		GeneratedSet first = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(12));
		GeneratedSet second = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(12));

		// then
		assertThat(first).isEqualTo(second);
	}

	@Test
	void 회차가_없으면_RECOMMENDATION_NO_HISTORY이다() {
		// when, then
		assertThatThrownBy(() -> engine.generate(new DrawHistory(List.of()), RecommendationConstraint.NONE, new Random(13)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.RECOMMENDATION_NO_HISTORY);
	}

	@Test
	void 근거_문구_형식이_맞다() {
		// given: 후보 6개를 고정해 근거 수치를 정확히 맞춘 1248회 합성 데이터
		Integer[] kept = { 3, 12, 20, 29, 34, 39 };
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), allExcept(kept));

		// when
		GeneratedSet set = engine.generate(evidenceHistory(), constraint, new Random(14));

		// then
		assertThat(set.lines().get(0).relaxation()).isEqualTo(Relaxation.NONE);
		assertThat(set.lines().get(0).line().evidence()).contains("번호 3: 최근 50회 중 11회 출현");
		assertThat(set.lines().get(1).line().evidence()).contains("번호 39: 23회째 미출현");
		assertThat(set.lines().get(2).line().evidence()).contains("번호 3: 전체 1248회 중 190회 출현");
		assertThat(set.lines().get(3).line().evidence()).contains("12·34 쌍이 8회 함께 출현");
		assertThat(set.lines().get(4).line().evidence()).contains("홀3:짝3, 합계 137, 과거 분포 중앙 구간");
	}

	@Test
	void 근거는_완화_문구를_빼면_줄당_최대_2개이다() {
		// when
		GeneratedSet set = engine.generate(HISTORY, RecommendationConstraint.NONE, new Random(15));

		// then
		assertThat(set.lines()).allSatisfy(line -> assertThat(line.line().evidence()).hasSizeBetween(1, 2));
	}

	@Test
	void 근거는_가중치가_가장_높았던_번호를_쓴다() {
		// given
		NumberStatistics stats = NumberStatistics.from(HISTORY);

		// when
		DrawnLine line = new AllTimeStrategy().draw(stats, RecommendationConstraint.NONE, new Random(16));

		// then
		int highest = line.numbers().values().stream().mapToInt(stats::frequency).max().orElseThrow();
		List<String> expected = line.numbers().values().stream()
			.filter(number -> stats.frequency(number) == highest)
			.map(number -> "번호 " + number + ": 전체 300회 중 " + highest + "회 출현")
			.toList();
		assertThat(line.evidence().get(0)).isIn(expected);
	}

	@Test
	void 수치가_같은_번호도_근거를_각각_남긴다() {
		// given: 후보 6개가 모두 1회씩 나온 데이터
		DrawHistory history = new DrawHistory(List.of(draw(1, 1, 2, 3, 4, 5, 6)));
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), allExcept(1, 2, 3, 4, 5, 6));

		// when
		DrawnLine line = new AllTimeStrategy().draw(NumberStatistics.from(history), constraint, new Random(20));

		// then
		assertThat(line.evidence()).hasSize(2);
		assertThat(line.evidence()).doesNotHaveDuplicates();
		assertThat(line.evidence()).allMatch(text -> text.matches("번호 [1-6]: 전체 1회 중 1회 출현"));
	}

	@Test
	void 고정_번호는_근거에_쓰지_않는다() {
		// given: 7번만 출현한 데이터에서 7을 고정하면 근거로 쓸 값은 다른 번호의 0회뿐이다
		DrawHistory history = new DrawHistory(List.of(draw(1, 7, 8, 9, 10, 11, 12)));
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(7), Set.of(8, 9, 10, 11, 12));

		// when
		DrawnLine line = new AllTimeStrategy().draw(NumberStatistics.from(history), constraint, new Random(17));

		// then
		assertThat(line.evidence()).isEmpty();
	}

	@Test
	void 균형_전략은_합계와_홀짝과_저고를_범위_안에_둔다() {
		// given
		NumberStatistics stats = NumberStatistics.from(HISTORY);

		// when
		DrawnLine line = new BalancedStrategy().draw(stats, RecommendationConstraint.NONE, new Random(18));

		// then
		List<Integer> values = line.numbers().values();
		int sum = values.stream().mapToInt(Integer::intValue).sum();
		int odd = (int) values.stream().filter(n -> n % 2 == 1).count();
		int low = (int) values.stream().filter(n -> n <= 22).count();
		assertThat(stats.sumRange().contains(sum)).isTrue();
		assertThat(stats.oddCountRange().contains(odd)).isTrue();
		assertThat(stats.lowCountRange().contains(low)).isTrue();
		assertThat(line.evidence()).containsExactly(
				"홀" + odd + ":짝" + (6 - odd) + ", 합계 " + sum + ", 과거 분포 중앙 구간");
	}

	@Test
	void 균형_조건을_못_맞추면_미충족을_근거에_적는다() {
		// given: 합계 범위가 21뿐인데 1을 제외하면 합계 21은 불가능하다
		NumberStatistics stats = NumberStatistics.from(new DrawHistory(List.of(draw(1, 1, 2, 3, 4, 5, 6))));
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(), Set.of(1));

		// when
		DrawnLine line = new BalancedStrategy().draw(stats, constraint, new Random(19));

		// then
		assertThat(line.evidence()).contains("균형 조건 미충족");
		assertThat(line.evidence()).noneMatch(text -> text.contains("과거 분포 중앙 구간"));
	}

	/**
	 * 평소 회차 {1,2,5,40,44,45}는 홀3, 저번호3, 합계 137이고 전체의 84%라 세 범위가 이 값으로 고정된다.
	 * 3: 전체 190회, 최근 50회 중 11회. 12·34: 함께 8회. 39: 23회째 미출현이 후보 중 가장 길다.
	 */
	private static DrawHistory evidenceHistory() {
		List<HistoricalDraw> draws = new ArrayList<>();
		for (int round = 1; round <= 1248; round++) {
			draws.add(evidenceRound(round));
		}
		return new DrawHistory(draws);
	}

	private static HistoricalDraw evidenceRound(int round) {
		if (round <= 179 || round >= 1238) {
			return draw(round, 3, 1, 2, 5, 40, 44);
		}
		if (round >= 200 && round <= 207) {
			return draw(round, 12, 34, 1, 2, 5, 40);
		}
		return switch (round) {
			case 1225 -> draw(round, 39, 1, 2, 5, 40, 44);
			case 1234 -> draw(round, 29, 1, 2, 5, 40, 44);
			case 1235 -> draw(round, 20, 1, 2, 5, 40, 44);
			case 1236 -> draw(round, 12, 1, 2, 5, 40, 44);
			case 1237 -> draw(round, 34, 1, 2, 5, 40, 44);
			default -> draw(round, 1, 2, 5, 40, 44, 45);
		};
	}

}
