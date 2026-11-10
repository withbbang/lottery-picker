package com.lotterypicker.recommendation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CombinedLinesTest {

	private static final Predicate<LottoNumbers> NONE_REJECTED = n -> false;

	/** 재료만 의미 있는 소스 줄. 번호 자체는 조합에 쓰이지 않는다. */
	private static DrawnLine source(StrategyType strategy, Ingredient... ingredients) {
		return new DrawnLine(strategy, new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)), List.of(), List.of(ingredients));
	}

	private static Ingredient single(int number, int rank) {
		return new Ingredient(List.of(number), rank);
	}

	/** 단독 6개(10·20, 30·40, 33·44) + 쌍 5·15 → 재료 번호 8개. */
	private static List<DrawnLine> eightNumbers() {
		return List.of(source(StrategyType.RECENT_HOT, single(10, 0), single(20, 1)),
				source(StrategyType.LONG_COLD, single(30, 0), single(40, 1)),
				source(StrategyType.ALL_TIME, single(33, 0), single(44, 1)),
				source(StrategyType.PAIR, new Ingredient(List.of(5, 15), 0)), source(StrategyType.BALANCED));
	}

	private static List<DrawnLine> sixSingles() {
		return List.of(source(StrategyType.RECENT_HOT, single(10, 0), single(20, 1)),
				source(StrategyType.LONG_COLD, single(30, 0), single(40, 1)),
				source(StrategyType.ALL_TIME, single(33, 0), single(44, 1)), source(StrategyType.PAIR),
				source(StrategyType.BALANCED));
	}

	@Test
	void 재료가_많으면_5줄까지만_만든다() {
		// when
		List<DrawnLine> lines = CombinedLines.combine(eightNumbers(), Set.of(), NONE_REJECTED);

		// then
		assertThat(lines).hasSize(CombinedLines.MAX_LINES)
			.allSatisfy(line -> assertThat(line.strategy()).isEqualTo(StrategyType.COMBINED));
		assertThat(lines.stream().map(DrawnLine::numbers).distinct()).hasSize(5);
	}

	@Test
	void 재료가_정확히_6개면_한_줄이다() {
		// when
		List<DrawnLine> lines = CombinedLines.combine(sixSingles(), Set.of(), NONE_REJECTED);

		// then
		assertThat(lines).hasSize(1);
		assertThat(lines.get(0).numbers().values()).containsExactly(10, 20, 30, 33, 40, 44);
	}

	@Test
	void 재료가_6개_미만이면_빈_목록이다() {
		// given: 쌍 2개 + 단독 3개 = 5개
		List<DrawnLine> sources = List.of(source(StrategyType.RECENT_HOT, single(10, 0)),
				source(StrategyType.LONG_COLD, single(30, 0)), source(StrategyType.ALL_TIME, single(33, 0)),
				source(StrategyType.PAIR, new Ingredient(List.of(5, 15), 0)), source(StrategyType.BALANCED));

		// when, then
		assertThat(CombinedLines.combine(sources, Set.of(), NONE_REJECTED)).isEmpty();
	}

	@Test
	void 쌍은_두_번호가_함께_들어간다() {
		// when
		List<DrawnLine> lines = CombinedLines.combine(eightNumbers(), Set.of(), NONE_REJECTED);

		// then
		assertThat(lines).allSatisfy(line -> assertThat(line.numbers().contains(5))
			.isEqualTo(line.numbers().contains(15)));
	}

	@Test
	void 점수가_높은_조합이_먼저다() {
		// when: 쌍(4점) + 각 전략 1순위(2점씩) + 2순위 하나(1점) = 11점이 최고, 동점은 번호 사전순
		List<DrawnLine> lines = CombinedLines.combine(eightNumbers(), Set.of(), NONE_REJECTED);

		// then
		assertThat(lines.get(0).numbers().values()).containsExactly(5, 10, 15, 20, 30, 33);
	}

	@Test
	void 같은_입력이면_같은_결과다() {
		// when
		List<DrawnLine> first = CombinedLines.combine(eightNumbers(), Set.of(), NONE_REJECTED);
		List<DrawnLine> second = CombinedLines.combine(eightNumbers(), Set.of(), NONE_REJECTED);

		// then
		assertThat(first).isEqualTo(second);
	}

	@Test
	void 고정_번호는_모든_줄에_있다() {
		// when
		List<DrawnLine> lines = CombinedLines.combine(eightNumbers(), Set.of(1), NONE_REJECTED);

		// then
		assertThat(lines).isNotEmpty().allSatisfy(line -> assertThat(line.numbers().contains(1)).isTrue());
	}

	@Test
	void 고정_5개면_단독_재료마다_한_줄이다() {
		// given: 단독 3개(10, 30, 33)와 쌍 하나. 쌍은 2개라 고정 5개에 더할 수 없다
		List<DrawnLine> sources = List.of(source(StrategyType.RECENT_HOT, single(10, 0)),
				source(StrategyType.LONG_COLD, single(30, 0)), source(StrategyType.ALL_TIME, single(33, 0)),
				source(StrategyType.PAIR, new Ingredient(List.of(5, 15), 0)), source(StrategyType.BALANCED));

		// when
		List<DrawnLine> lines = CombinedLines.combine(sources, Set.of(1, 2, 3, 4, 6), NONE_REJECTED);

		// then
		assertThat(lines).hasSize(3);
	}

	@Test
	void 겹치는_재료는_한_번만_세고_출처를_모두_적는다() {
		// given: 7은 최근 빈도와 전체 빈도 모두의 근거다
		List<DrawnLine> sources = List.of(source(StrategyType.RECENT_HOT, single(7, 0), single(20, 1)),
				source(StrategyType.LONG_COLD, single(30, 0), single(40, 1)),
				source(StrategyType.ALL_TIME, single(7, 0), single(44, 1)),
				source(StrategyType.PAIR, new Ingredient(List.of(5, 15), 0)), source(StrategyType.BALANCED));

		// when
		List<DrawnLine> lines = CombinedLines.combine(sources, Set.of(), NONE_REJECTED);

		// then
		assertThat(lines).isNotEmpty();
		assertThat(lines.get(0).evidence().get(0)).contains("7(최근 빈도·전체 빈도)");
	}

	@Test
	void 쌍과_단독이_번호를_공유해도_줄은_6개다() {
		// given: 단독 7과 쌍 7·12가 7을 공유한다
		List<DrawnLine> sources = List.of(source(StrategyType.RECENT_HOT, single(7, 0), single(20, 1)),
				source(StrategyType.LONG_COLD, single(30, 0), single(40, 1)), source(StrategyType.ALL_TIME, single(44, 0)),
				source(StrategyType.PAIR, new Ingredient(List.of(7, 12), 0)), source(StrategyType.BALANCED));

		// when
		List<DrawnLine> lines = CombinedLines.combine(sources, Set.of(), NONE_REJECTED);

		// then
		assertThat(lines).isNotEmpty().allSatisfy(line -> assertThat(line.numbers().values()).hasSize(6));
	}

	@Test
	void 거부된_조합은_건너뛴다() {
		// given
		LottoNumbers best = new LottoNumbers(List.of(5, 10, 15, 20, 30, 33));

		// when
		List<DrawnLine> lines = CombinedLines.combine(eightNumbers(), Set.of(), best::equals);

		// then
		assertThat(lines).extracting(DrawnLine::numbers).doesNotContain(best);
	}

	@Test
	void 모두_인기_조합이면_빈_목록이다() {
		// when, then
		assertThat(CombinedLines.combine(eightNumbers(), Set.of(), n -> true)).isEmpty();
	}

	@Test
	void 근거_문구_형식이_맞다() {
		// given: 고정 1 + 쌍 5·15 + 단독 3개 = 정확히 6개
		List<DrawnLine> sources = List.of(source(StrategyType.RECENT_HOT, single(10, 0)),
				source(StrategyType.LONG_COLD, single(30, 0)), source(StrategyType.ALL_TIME, single(33, 0)),
				source(StrategyType.PAIR, new Ingredient(List.of(5, 15), 0)), source(StrategyType.BALANCED));

		// when
		List<DrawnLine> lines = CombinedLines.combine(sources, Set.of(1), NONE_REJECTED);

		// then
		assertThat(lines).hasSize(1);
		assertThat(lines.get(0).evidence())
			.containsExactly("1(고정) · 5·15(번호 쌍) · 10(최근 빈도) · 30(장기 미출현) · 33(전체 빈도)");
		assertThat(lines.get(0).ingredients()).isEmpty();
	}

	@Test
	void 줄끼리는_서로_다르다() {
		// when
		List<DrawnLine> lines = CombinedLines.combine(eightNumbers(), Set.of(), NONE_REJECTED);

		// then
		List<LottoNumbers> numbers = new ArrayList<>(lines.stream().map(DrawnLine::numbers).toList());
		assertThat(numbers).doesNotHaveDuplicates();
	}

	/** 고정 1과 쌍 1·45가 1을 공유한다. 단독 1순위 5개(각 2점). */
	private static List<DrawnLine> pairWithFixedPartner() {
		return List.of(source(StrategyType.RECENT_HOT, single(10, 0), single(20, 0)),
				source(StrategyType.LONG_COLD, single(30, 0), single(40, 0)),
				source(StrategyType.ALL_TIME, single(33, 0)),
				source(StrategyType.PAIR, new Ingredient(List.of(1, 45), 0)), source(StrategyType.BALANCED));
	}

	@Test
	void 고정_번호와_겹치는_쌍은_새로_더하는_번호만큼만_점수를_받는다() {
		// when: 쌍이 더하는 번호는 45 하나라 단독과 같은 2점, 동점이면 사전순
		List<DrawnLine> lines = CombinedLines.combine(pairWithFixedPartner(), Set.of(1), NONE_REJECTED);

		// then
		assertThat(lines.get(0).numbers().values()).containsExactly(1, 10, 20, 30, 33, 40);
	}

	@Test
	void 쌍에_들어간_고정_번호는_근거에_두_번_적지_않는다() {
		// when
		List<DrawnLine> lines = CombinedLines.combine(pairWithFixedPartner(), Set.of(1), NONE_REJECTED);

		// then
		assertThat(lines).filteredOn(line -> line.numbers().contains(45))
			.isNotEmpty()
			.allSatisfy(line -> assertThat(line.evidence().get(0)).startsWith("1·45(번호 쌍)")
				.doesNotContain("1(고정)"));
		assertThat(lines).filteredOn(line -> !line.numbers().contains(45))
			.allSatisfy(line -> assertThat(line.evidence().get(0)).startsWith("1(고정)"));
	}

	@Test
	void 번호가_겹치는_묶음은_한_줄에_같이_고르지_않는다() {
		// given: 단독 5(최근 빈도)와 쌍 5·15(번호 쌍)가 5를 공유한다
		List<DrawnLine> sources = List.of(source(StrategyType.RECENT_HOT, single(5, 0), single(20, 1)),
				source(StrategyType.LONG_COLD, single(30, 0), single(40, 1)),
				source(StrategyType.ALL_TIME, single(33, 0), single(44, 1)),
				source(StrategyType.PAIR, new Ingredient(List.of(5, 15), 0)), source(StrategyType.BALANCED));

		// when
		List<DrawnLine> lines = CombinedLines.combine(sources, Set.of(), NONE_REJECTED);

		// then: 근거에 5가 두 번 적히지 않는다
		assertThat(lines).isNotEmpty()
			.allSatisfy(line -> assertThat(line.evidence().get(0)).doesNotContain("5(최근 빈도) · 5·15"));
	}

}
