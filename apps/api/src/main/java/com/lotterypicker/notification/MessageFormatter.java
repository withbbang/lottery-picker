package com.lotterypicker.notification;

import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.time.LocalDate;
import java.util.stream.Collectors;

/** 텔레그램 본문을 만든다. 대조군은 성적표 전용이므로 알림에 넣지 않는다. */
public final class MessageFormatter {

	private MessageFormatter() {
	}

	public static String recommendation(RecommendationSet set) {
		String lines = set.lines().stream()
			.filter(line -> line.strategy() != StrategyType.CONTROL)
			.map(MessageFormatter::line)
			.collect(Collectors.joining("\n"));
		return set.date() + " · " + set.round() + "회 추천\n\n" + lines + "\n\n" + RecommendationSetResponse.DISCLAIMER;
	}

	public static String recommendationFailed(LocalDate date) {
		return date + " 추천 생성에 실패했습니다. 서버 로그를 확인해 주세요.";
	}

	private static String line(StoredLine line) {
		String numbers = line.numbers().values().stream().map(String::valueOf).collect(Collectors.joining(", "));
		return label(line.strategy()) + ": " + numbers;
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
