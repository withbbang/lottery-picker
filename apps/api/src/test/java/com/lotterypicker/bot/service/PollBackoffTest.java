package com.lotterypicker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PollBackoffTest {

	@Test
	void 기다림은_5초부터_두_배씩_5분까지다() {
		// given
		PollBackoff backoff = new PollBackoff();
		List<Long> seconds = new ArrayList<>();

		// when
		for (int i = 0; i < 8; i++) {
			seconds.add(backoff.nextDelay().toSeconds());
		}
		backoff.reset();

		// then
		assertThat(seconds).containsExactly(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L);
		assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(5));
	}

}
