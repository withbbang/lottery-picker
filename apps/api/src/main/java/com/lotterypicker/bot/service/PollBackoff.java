package com.lotterypicker.bot.service;

import java.time.Duration;

/** 폴링 실패 시 기다릴 시간. 5초부터 두 배씩 늘려 5분에서 멈춘다. 성공하면 {@link #reset()}. */
class PollBackoff {

	private static final Duration FIRST = Duration.ofSeconds(5);
	private static final Duration MAX = Duration.ofMinutes(5);

	private Duration next = FIRST;

	Duration nextDelay() {
		Duration delay = next;
		next = next.multipliedBy(2).compareTo(MAX) > 0 ? MAX : next.multipliedBy(2);
		return delay;
	}

	void reset() {
		next = FIRST;
	}

}
