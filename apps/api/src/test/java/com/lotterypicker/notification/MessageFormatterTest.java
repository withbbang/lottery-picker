package com.lotterypicker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
	void 추천_실패_문구에_날짜를_적는다() {
		// when
		String text = MessageFormatter.recommendationFailed(DATE);

		// then
		assertThat(text).contains("2026-11-06", "실패");
	}

}
