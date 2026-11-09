package com.lotterypicker.collector;

import com.lotterypicker.common.config.ClockConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 새 회차를 언제 확인할지 정한다. 토요일 20:40부터 자정 전까지 5분마다, 그 뒤로는 수집될 때까지 정시마다 확인한다.
 * 다음 추첨일 다음 날 12:00까지 없으면 지연이다. 기준은 저장된 최신 회차의 추첨일 + 7일이다.
 */
public final class PollPolicy {

	private static final int DAYS_PER_ROUND = 7;
	private static final LocalTime FIRST_CHECK = LocalTime.of(20, 40);
	private static final LocalTime OVERDUE_TIME = LocalTime.of(12, 0);
	private static final int FREQUENT_INTERVAL_MINUTES = 5;

	private PollPolicy() {
	}

	/** 확인 대상인 추첨일. 최신 회차 다음 추첨의 20:40 전이면 빈 값이다. */
	public static Optional<LocalDate> expectedDraw(ZonedDateTime now, LocalDate latestDrawDate) {
		LocalDate next = latestDrawDate.plusDays(DAYS_PER_ROUND);
		LocalDateTime seoul = seoul(now);
		return seoul.isBefore(next.atTime(FIRST_CHECK)) ? Optional.empty() : Optional.of(next);
	}

	public static boolean isDue(ZonedDateTime now, LocalDate latestDrawDate) {
		return expectedDraw(now, latestDrawDate).map(draw -> {
			LocalDateTime seoul = seoul(now);
			boolean drawNight = seoul.toLocalDate().equals(draw);
			return drawNight ? seoul.getMinute() % FREQUENT_INTERVAL_MINUTES == 0 : seoul.getMinute() == 0;
		}).orElse(false);
	}

	public static boolean isOverdue(ZonedDateTime now, LocalDate latestDrawDate) {
		return expectedDraw(now, latestDrawDate)
			.map(draw -> !seoul(now).isBefore(overdueAt(draw)))
			.orElse(false);
	}

	/** 이 시각 이후에 수집한 회차는 늦게 수집한 것이다. */
	public static LocalDateTime overdueAt(LocalDate drawDate) {
		return drawDate.plusDays(1).atTime(OVERDUE_TIME);
	}

	private static LocalDateTime seoul(ZonedDateTime now) {
		return now.withZoneSameInstant(ClockConfig.SEOUL).toLocalDateTime();
	}

}
