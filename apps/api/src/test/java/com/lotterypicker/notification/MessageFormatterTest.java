package com.lotterypicker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.result.DrawSummary;
import com.lotterypicker.result.ResultTally;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageFormatterTest {

	private static final LocalDate DATE = LocalDate.of(2026, 11, 6);

	private static StoredLine line(StrategyType strategy, int first) {
		return new StoredLine(strategy, new LottoNumbers(List.of(first, 11, 19, 27, 38, 44)), List.of("근거"),
				Relaxation.NONE);
	}

	private static List<StoredLine> fiveLines() {
		return new ArrayList<>(List.of(line(StrategyType.RECENT_HOT, 1), line(StrategyType.LONG_COLD, 2),
				line(StrategyType.ALL_TIME, 3), line(StrategyType.PAIR, 4), line(StrategyType.BALANCED, 5)));
	}

	@Test
	void 날짜_요일_회차_고정폭_5줄과_고지문을_보낸다() {
		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, fiveLines()));

		// then
		assertThat(text).isEqualTo("""
				2026-11-06(금) · 1249회 추천

				<pre> 1 · 11 · 19 · 27 · 38 · 44  최근 빈도
				 2 · 11 · 19 · 27 · 38 · 44  장기 미출현
				 3 · 11 · 19 · 27 · 38 · 44  전체 빈도
				 4 · 11 · 19 · 27 · 38 · 44  번호 쌍
				 5 · 11 · 19 · 27 · 38 · 44  균형</pre>

				실제 당첨 확률과 무관합니다.""");
	}

	@Test
	void 한자리_번호는_공백으로_채워_열을_맞춘다() {
		// given
		StoredLine line = new StoredLine(StrategyType.LONG_COLD, new LottoNumbers(List.of(2, 9, 15, 23, 31, 40)),
				List.of("근거"), Relaxation.NONE);

		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, List.of(line)));

		// then
		assertThat(text).contains(" 2 ·  9 · 15 · 23 · 31 · 40  장기 미출현");
	}

	@Test
	void 대조군은_포함하지_않는다() {
		// given
		List<StoredLine> lines = fiveLines();
		lines.add(line(StrategyType.CONTROL, 6));

		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, lines));

		// then
		assertThat(text).doesNotContain(" 6 · 11 · 19 · 27 · 38 · 44", "대조군");
	}

	@Test
	void 근거_조합은_별도_블록이다() {
		// given
		List<StoredLine> lines = fiveLines();
		lines.add(new StoredLine(StrategyType.COMBINED, new LottoNumbers(List.of(7, 12, 21, 30, 33, 42)),
				List.of("근거"), Relaxation.NONE));

		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, lines));

		// then
		assertThat(text).contains("""
				 5 · 11 · 19 · 27 · 38 · 44  균형</pre>

				근거 조합
				<pre> 7 · 12 · 21 · 30 · 33 · 42</pre>""");
	}

	@Test
	void 근거_조합이_없으면_블록을_넣지_않는다() {
		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, fiveLines()));

		// then
		assertThat(text).doesNotContain("근거 조합");
	}

	private static DrawSummary summary(LocalDateTime lateCollectedAt, ResultTally tally) {
		return summary(lateCollectedAt, tally, List.of());
	}

	private static DrawSummary summary(LocalDateTime lateCollectedAt, ResultTally tally, List<DrawSummary.Line> lines) {
		return new DrawSummary(1249, LocalDate.of(2026, 11, 7), new LottoNumbers(List.of(3, 11, 19, 27, 38, 44)), 7,
				lateCollectedAt, tally, lines);
	}

	private static DrawSummary.Line scored(LocalDate date, StrategyType strategy, List<Integer> numbers, int matchCount,
			PrizeRank rank) {
		return new DrawSummary.Line(date, new LottoNumbers(numbers), strategy, matchCount, rank);
	}

	private static ResultTally tally() {
		Map<PrizeRank, Integer> counts = new EnumMap<>(PrizeRank.class);
		counts.put(PrizeRank.FOURTH, 1);
		counts.put(PrizeRank.FIFTH, 3);
		counts.put(PrizeRank.NONE, 31);
		return new ResultTally(35, counts, 65_000);
	}

	@Test
	void 추첨_종료_알림은_날짜별로_줄마다_맞힌_개수와_등수와_전략을_적는다() {
		// given
		LocalDate sunday = LocalDate.of(2026, 11, 1);
		LocalDate monday = LocalDate.of(2026, 11, 2);
		List<DrawSummary.Line> lines = List.of(
				scored(sunday, StrategyType.RECENT_HOT, List.of(3, 12, 19, 30, 38, 41), 3, PrizeRank.FIFTH),
				scored(sunday, StrategyType.LONG_COLD, List.of(2, 9, 15, 23, 31, 40), 0, PrizeRank.NONE),
				scored(monday, StrategyType.COMBINED, List.of(3, 11, 19, 27, 30, 31), 4, PrizeRank.FOURTH));

		// when
		String text = MessageFormatter.drawFinished(summary(null, tally(), lines));

		// then
		assertThat(text).isEqualTo("""
				1249회 추첨 결과 · 2026-11-07(토)

				<pre> 3 · 11 · 19 · 27 · 38 · 44 + 7</pre>

				<pre>11/01(일)
				 3 · 12 · 19 · 30 · 38 · 41  3개 5등  최근 빈도
				 2 ·  9 · 15 · 23 · 31 · 40  0개 낙첨  장기 미출현
				11/02(월)
				 3 · 11 · 19 · 27 · 30 · 31  4개 4등  근거 조합</pre>

				1등 0 · 2등 0 · 3등 0 · 4등 1 · 5등 3 · 낙첨 31

				실제 당첨 확률과 무관합니다.""");
	}

	@Test
	void 늦게_수집한_회차는_수집_시각을_적는다() {
		// when
		String text = MessageFormatter.drawFinished(summary(LocalDateTime.of(2026, 11, 8, 13, 0), tally()));

		// then
		assertThat(text).startsWith("""
				1249회 추첨 결과 · 2026-11-07(토)
				당첨번호를 늦게 수집했습니다(2026-11-08 13:00).

				<pre>""");
	}

	@Test
	void 추천이_없는_회차는_추천_없음만_적는다() {
		// when
		String text = MessageFormatter.drawFinished(summary(null, new ResultTally(0, Map.of(), 0)));

		// then
		assertThat(text).isEqualTo("""
				1249회 추첨 결과 · 2026-11-07(토)

				<pre> 3 · 11 · 19 · 27 · 38 · 44 + 7</pre>

				이 회차 추천이 없습니다.""");
	}

	@Test
	void 수집_지연_알림은_계속_확인한다고_적는다() {
		// when
		String text = MessageFormatter.collectionFailed(LocalDate.of(2026, 11, 7));

		// then
		assertThat(text).isEqualTo("""
				2026-11-07(토) 추첨 당첨번호를 아직 수집하지 못했습니다.
				수집될 때까지 1시간마다 계속 확인합니다.""");
	}

	@Test
	void 수집_오류_알림은_잘못된_값을_저장하지_않았다고_적는다() {
		// when
		String text = MessageFormatter.collectionError(LocalDate.of(2026, 11, 7));

		// then
		assertThat(text).isEqualTo("""
				2026-11-07(토) 추첨 회차 수집 중 오류가 났습니다.
				잘못된 값은 저장하지 않았습니다. 서버 로그를 확인해 주세요.""");
	}

	@Test
	void 추천_실패_문구에_날짜를_적는다() {
		// when
		String text = MessageFormatter.recommendationFailed(DATE);

		// then
		assertThat(text).contains("2026-11-06", "실패");
	}

}
