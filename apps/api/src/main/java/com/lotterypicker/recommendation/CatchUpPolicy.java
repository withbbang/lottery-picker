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
		if (now.withZoneSameInstant(ClockConfig.SEOUL).toLocalTime().isBefore(GENERATION_TIME)) {
			return false;
		}
		// 판매가 마감된 회차의 추천이 생기거나 당첨번호가 통계에 섞이지 않게 한다.
		return salesOpen(now);
	}

	/** 그날 만든 추천의 회차를 아직 살 수 있는지. 토요일 20시에 판매가 마감된다. */
	public static boolean salesOpen(ZonedDateTime now) {
		ZonedDateTime seoul = now.withZoneSameInstant(ClockConfig.SEOUL);
		return seoul.getDayOfWeek() != DayOfWeek.SATURDAY || seoul.toLocalTime().isBefore(SALES_CLOSE);
	}

}
