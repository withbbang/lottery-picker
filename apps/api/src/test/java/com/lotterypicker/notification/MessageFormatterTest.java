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
	void 날짜와_회차_5줄_전략과_고지문을_포함한다() {
		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, fiveLines()));

		// then
		assertThat(text).contains("2026-11-06", "1249회")
			.contains("최근 빈도: 1, 11, 19, 27, 38, 44")
			.contains("장기 미출현: 2, 11, 19, 27, 38, 44")
			.contains("전체 빈도: 3, 11, 19, 27, 38, 44")
			.contains("번호 쌍: 4, 11, 19, 27, 38, 44")
			.contains("균형: 5, 11, 19, 27, 38, 44")
			.contains("번호 선택 방식 설명이며 당첨 확률과 무관합니다.");
	}

	@Test
	void 대조군은_포함하지_않는다() {
		// given
		List<StoredLine> lines = fiveLines();
		lines.add(line(StrategyType.CONTROL, 6));

		// when
		String text = MessageFormatter.recommendation(new RecommendationSet(DATE, 1249, lines));

		// then
		assertThat(text).doesNotContain("6, 11, 19, 27, 38, 44");
	}

	@Test
	void 추천_실패_문구에_날짜를_적는다() {
		// when
		String text = MessageFormatter.recommendationFailed(DATE);

		// then
		assertThat(text).contains("2026-11-06", "실패");
	}

}
