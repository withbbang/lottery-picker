package com.lotterypicker.lotto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PrizeRankTest {

	private static final LottoNumbers WINNING = new LottoNumbers(List.of(1, 2, 3, 4, 5, 6));
	private static final int BONUS = 7;

	private static PrizeRank rankOf(Integer... line) {
		return PrizeRank.of(new LottoNumbers(List.of(line)), WINNING, BONUS);
	}

	@Test
	void 여섯개_일치는_1등이다() {
		// when, then
		assertThat(rankOf(1, 2, 3, 4, 5, 6)).isEqualTo(PrizeRank.FIRST);
	}

	@Test
	void 다섯개와_보너스_일치는_2등이다() {
		// when, then
		assertThat(rankOf(1, 2, 3, 4, 5, 7)).isEqualTo(PrizeRank.SECOND);
	}

	@Test
	void 다섯개_일치는_3등이다() {
		// when, then
		assertThat(rankOf(1, 2, 3, 4, 5, 8)).isEqualTo(PrizeRank.THIRD);
	}

	@Test
	void 네개_일치는_4등이다() {
		// when, then
		assertThat(rankOf(1, 2, 3, 4, 8, 9)).isEqualTo(PrizeRank.FOURTH);
	}

	@Test
	void 세개_일치는_5등이다() {
		// when, then
		assertThat(rankOf(1, 2, 3, 8, 9, 10)).isEqualTo(PrizeRank.FIFTH);
	}

	@Test
	void 두개_일치는_낙첨이다() {
		// when, then
		assertThat(rankOf(1, 2, 8, 9, 10, 11)).isEqualTo(PrizeRank.NONE);
	}

	@Test
	void 보너스와_4개_일치는_4등이다() {
		// when, then
		assertThat(rankOf(1, 2, 3, 4, 7, 8)).isEqualTo(PrizeRank.FOURTH);
	}

}
