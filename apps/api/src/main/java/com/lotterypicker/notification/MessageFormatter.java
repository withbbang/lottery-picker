package com.lotterypicker.notification;

import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 텔레그램 본문을 만든다. 대조군은 성적표 전용이므로 알림에 넣지 않는다.
 * 본문은 HTML로 해석되므로 고정 문구 밖의 값에 {@code < > &}가 들어가면 이스케이프해야 한다.
 * 문구 목록은 {@code docs/telegram-messages.md}에 맞춘다.
 */
public final class MessageFormatter {

	private MessageFormatter() {
	}

	/**
	 * 번호 줄은 고정폭 블록에 번호를 앞에 둔다. 한글은 기기마다 폭이 달라 라벨을 앞에 두면 번호 열이 어긋난다.
	 */
	public static String recommendation(RecommendationSet set) {
		String lines = set.lines().stream()
			.filter(line -> line.strategy() != StrategyType.CONTROL)
			.map(MessageFormatter::line)
			.collect(Collectors.joining("\n"));
		return dateWithDay(set.date()) + " · " + set.round() + "회 추천\n\n"
				+ "<pre>" + lines + "</pre>\n\n"
				+ RecommendationSetResponse.DISCLAIMER;
	}

	public static String recommendationFailed(LocalDate date) {
		return date + " 추천 생성에 실패했습니다. 서버 로그를 확인해 주세요.";
	}

	private static String dateWithDay(LocalDate date) {
		return date + "(" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	private static String line(StoredLine line) {
		String numbers = line.numbers().values().stream()
			.map(n -> String.format("%2d", n))
			.collect(Collectors.joining(" · "));
		return numbers + "  " + label(line.strategy());
	}

	private static String label(StrategyType strategy) {
		return switch (strategy) {
			case RECENT_HOT -> "최근 빈도";
			case LONG_COLD -> "장기 미출현";
			case ALL_TIME -> "전체 빈도";
			case PAIR -> "번호 쌍";
			case BALANCED -> "균형";
			case CONTROL -> "대조군";
		};
	}

}
