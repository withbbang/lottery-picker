package com.lotterypicker.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class PollPolicyTest {

	private static final LocalDate LATEST = LocalDate.of(2026, 10, 31);

	private static ZonedDateTime at(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Asia/Seoul"));
	}

	@Test
	void 토요일_20시39분은_대상이_아니다() {
		// when
		var expected = PollPolicy.expectedDraw(at("2026-11-07T20:39"), LATEST);

		// then
		assertThat(expected).isEmpty();
		assertThat(PollPolicy.isDue(at("2026-11-07T20:39"), LATEST)).isFalse();
	}

	@Test
	void 토요일_20시40분부터_5분마다_확인한다() {
		// when, then
		assertThat(PollPolicy.expectedDraw(at("2026-11-07T20:40"), LATEST)).contains(LocalDate.of(2026, 11, 7));
		assertThat(PollPolicy.isDue(at("2026-11-07T20:40"), LATEST)).isTrue();
		assertThat(PollPolicy.isDue(at("2026-11-07T20:45"), LATEST)).isTrue();
	}

	@Test
	void 자정_이후에는_정시에만_확인한다() {
		// when, then
		assertThat(PollPolicy.isDue(at("2026-11-08T00:05"), LATEST)).isFalse();
		assertThat(PollPolicy.isDue(at("2026-11-08T01:00"), LATEST)).isTrue();
	}

	@Test
	void 수집이_끝나면_확인하지_않는다() {
		// when, then
		assertThat(PollPolicy.isDue(at("2026-11-07T21:00"), LocalDate.of(2026, 11, 7))).isFalse();
	}

	@Test
	void 일요일_12시부터_지연이다() {
		// when, then
		assertThat(PollPolicy.isOverdue(at("2026-11-08T11:59"), LATEST)).isFalse();
		assertThat(PollPolicy.isOverdue(at("2026-11-08T12:00"), LATEST)).isTrue();
	}

	@Test
	void 월요일에도_정시마다_계속_확인한다() {
		// when, then
		assertThat(PollPolicy.isDue(at("2026-11-09T10:00"), LATEST)).isTrue();
		assertThat(PollPolicy.isOverdue(at("2026-11-09T10:00"), LATEST)).isTrue();
	}

}
