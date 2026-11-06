package com.lotterypicker.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RoundCalendarTest {

	private static final LocalDate DRAW_1247 = LocalDate.of(2026, 10, 24);
	private static final LocalDate DRAW_1248 = LocalDate.of(2026, 10, 31);

	@Test
	void 토요일의_대상은_그날이다() {
		// when
		LocalDate target = RoundCalendar.targetSaturday(LocalDate.of(2026, 11, 7));

		// then
		assertThat(target).isEqualTo(LocalDate.of(2026, 11, 7));
	}

	@Test
	void 일요일의_대상은_다음_토요일이다() {
		// when
		LocalDate target = RoundCalendar.targetSaturday(LocalDate.of(2026, 11, 8));

		// then
		assertThat(target).isEqualTo(LocalDate.of(2026, 11, 14));
	}

	@Test
	void 회차를_계산한다() {
		// when, then
		assertThat(RoundCalendar.roundFor(LocalDate.of(2026, 11, 6), 1248, DRAW_1248)).isEqualTo(1249);
		assertThat(RoundCalendar.roundFor(LocalDate.of(2026, 11, 7), 1248, DRAW_1248)).isEqualTo(1249);
		assertThat(RoundCalendar.roundFor(LocalDate.of(2026, 11, 8), 1248, DRAW_1248)).isEqualTo(1250);
	}

	@Test
	void 직전_수집이_실패해도_회차가_맞다() {
		// when
		int round = RoundCalendar.roundFor(LocalDate.of(2026, 11, 6), 1247, DRAW_1247);

		// then
		assertThat(round).isEqualTo(1249);
	}

}
