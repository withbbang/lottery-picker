package com.lotterypicker.recommendation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** 판매 기간은 일~토이고, 일~토 추천은 그 주 토요일 회차에 속한다. */
public final class RoundCalendar {

	private static final int DAYS_PER_ROUND = 7;

	private RoundCalendar() {
	}

	public static LocalDate targetSaturday(LocalDate date) {
		return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
	}

	/** 저장된 최신 회차에서 달력으로 센다. 직전 수집이 실패해도 회차가 어긋나지 않는다. */
	public static int roundFor(LocalDate date, int latestRound, LocalDate latestDrawDate) {
		long days = ChronoUnit.DAYS.between(latestDrawDate, targetSaturday(date));
		return latestRound + (int) (days / DAYS_PER_ROUND);
	}

}
