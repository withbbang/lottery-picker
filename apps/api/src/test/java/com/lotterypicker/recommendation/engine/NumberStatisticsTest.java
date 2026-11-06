package com.lotterypicker.recommendation.engine;

import static com.lotterypicker.recommendation.engine.TestHistories.draw;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumberStatisticsTest {

	private static final DrawHistory HISTORY = new DrawHistory(List.of(
			draw(1, 1, 2, 3, 4, 5, 6),
			draw(2, 1, 2, 7, 8, 9, 10),
			draw(3, 1, 11, 12, 13, 14, 15),
			draw(4, 2, 3, 20, 30, 40, 45),
			draw(5, 1, 2, 3, 40, 44, 45)));

	@Test
	void 전체_빈도를_센다() {
		// when
		NumberStatistics stats = NumberStatistics.from(HISTORY);

		// then
		assertThat(stats.frequency(1)).isEqualTo(4);
		assertThat(stats.frequency(45)).isEqualTo(2);
		assertThat(stats.frequency(33)).isZero();
		assertThat(stats.totalRounds()).isEqualTo(5);
	}

	@Test
	void 최근_N회_빈도는_창_밖을_세지_않는다() {
		// when
		NumberStatistics stats = NumberStatistics.from(HISTORY);

		// then
		assertThat(stats.recentFrequency(1, 2)).isEqualTo(1);
		assertThat(stats.recentFrequency(2, 2)).isEqualTo(2);
		assertThat(stats.recentFrequency(6, 2)).isZero();
		assertThat(stats.recentFrequency(6, 5)).isEqualTo(1);
		assertThat(stats.recentFrequency(1, 100)).isEqualTo(4);
	}

	@Test
	void 회차_입력_순서와_무관하게_최근을_계산한다() {
		// given
		List<HistoricalDraw> reversed = new ArrayList<>(HISTORY.draws());
		Collections.reverse(reversed);

		// when
		NumberStatistics stats = NumberStatistics.from(new DrawHistory(reversed));

		// then
		assertThat(stats.recentFrequency(6, 2)).isZero();
		assertThat(stats.roundsSinceLast(6)).isEqualTo(4);
	}

	@Test
	void 미출현_번호의_경과는_전체_회차수이다() {
		// when
		NumberStatistics stats = NumberStatistics.from(HISTORY);

		// then
		assertThat(stats.roundsSinceLast(33)).isEqualTo(5);
		assertThat(stats.roundsSinceLast(6)).isEqualTo(4);
		assertThat(stats.roundsSinceLast(1)).isZero();
	}

	@Test
	void 쌍_빈도는_순서와_무관하다() {
		// when
		NumberStatistics stats = NumberStatistics.from(HISTORY);

		// then
		assertThat(stats.pairCount(1, 2)).isEqualTo(3);
		assertThat(stats.pairCount(2, 1)).isEqualTo(3);
		assertThat(stats.pairCount(1, 33)).isZero();
	}

	@Test
	void 합계_범위는_nearest_rank_25에서_75백분위이다() {
		// when: 합계 정렬 21, 37, 66, 135, 140 → 2번째와 4번째
		IntRange range = NumberStatistics.from(HISTORY).sumRange();

		// then
		assertThat(range).isEqualTo(new IntRange(37, 135));
	}

	@Test
	void 홀수_개수_범위는_nearest_rank_25에서_75백분위이다() {
		// when: 홀수 개수 정렬 2, 3, 3, 3, 4
		IntRange range = NumberStatistics.from(HISTORY).oddCountRange();

		// then
		assertThat(range).isEqualTo(new IntRange(3, 3));
	}

	@Test
	void 저번호_개수_범위는_nearest_rank_25에서_75백분위이다() {
		// when: 저번호 개수 정렬 3, 3, 6, 6, 6
		IntRange range = NumberStatistics.from(HISTORY).lowCountRange();

		// then
		assertThat(range).isEqualTo(new IntRange(3, 6));
	}

	@Test
	void 저번호는_22까지이다() {
		// given
		DrawHistory history = new DrawHistory(List.of(draw(1, 18, 19, 20, 21, 22, 23)));

		// when
		IntRange range = NumberStatistics.from(history).lowCountRange();

		// then
		assertThat(range).isEqualTo(new IntRange(5, 5));
	}

	@Test
	void 범위는_양_끝을_포함한다() {
		// given
		IntRange range = new IntRange(3, 5);

		// then
		assertThat(range.contains(3)).isTrue();
		assertThat(range.contains(5)).isTrue();
		assertThat(range.contains(2)).isFalse();
		assertThat(range.contains(6)).isFalse();
	}

	@Test
	void before는_해당_회차_미만만_남긴다() {
		// when
		DrawHistory before = HISTORY.before(3);

		// then
		assertThat(before.draws()).extracting(HistoricalDraw::round).containsExactly(1, 2);
	}

}
