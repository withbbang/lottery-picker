package com.lotterypicker.recommendation;

import com.lotterypicker.common.config.ClockConfig;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** 17시에 서버가 꺼져 있었을 때 기동 시 그날 추천을 보충할지 판단한다. */
public final class CatchUpPolicy {

	private static final LocalTime GENERATION_TIME = LocalTime.of(17, 0);
	private static final LocalTime SALES_CLOSE = LocalTime.of(20, 0);

	private CatchUpPolicy() {
	}

	public static boolean shouldGenerate(ZonedDateTime now, boolean alreadyGenerated) {
		if (alreadyGenerated) {
			return false;
		}
		ZonedDateTime seoul = now.withZoneSameInstant(ClockConfig.SEOUL);
		LocalTime time = seoul.toLocalTime();
		if (time.isBefore(GENERATION_TIME)) {
			return false;
		}
		// 판매가 마감된 회차의 추천이 생기거나 당첨번호가 통계에 섞이지 않게 한다.
		return seoul.getDayOfWeek() != DayOfWeek.SATURDAY || time.isBefore(SALES_CLOSE);
	}

}
