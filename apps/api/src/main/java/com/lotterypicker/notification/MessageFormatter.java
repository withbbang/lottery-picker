package com.lotterypicker.notification;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.result.DrawSummary;
import com.lotterypicker.result.ResultTally;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 텔레그램 본문을 만든다. 대조군은 성적표 전용이므로 알림에 넣지 않는다.
 * 본문은 HTML로 해석되므로 고정 문구 밖의 값에 {@code < > &}가 들어가면 이스케이프해야 한다.
 * 문구 목록은 {@code docs/telegram-messages.md}에 맞춘다.
 */
public final class MessageFormatter {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM/dd");

	private MessageFormatter() {
	}

	/**
	 * 번호 줄은 고정폭 블록에 번호를 앞에 둔다. 한글은 기기마다 폭이 달라 라벨을 앞에 두면 번호 열이 어긋난다.
	 */
	public static String recommendation(RecommendationSet set) {
		String lines = set.lines().stream()
			.filter(line -> line.strategy() != StrategyType.CONTROL && line.strategy() != StrategyType.COMBINED)
			.map(MessageFormatter::line)
			.collect(Collectors.joining("\n"));
		String combined = set.lines().stream()
			.filter(line -> line.strategy() == StrategyType.COMBINED)
			.map(line -> numbers(line.numbers()))
			.collect(Collectors.joining("\n"));
		return dateWithDay(set.date()) + " · " + set.round() + "회 추천\n\n"
				+ "<pre>" + lines + "</pre>\n\n"
				+ (combined.isEmpty() ? "" : "근거 조합\n<pre>" + combined + "</pre>\n\n")
				+ RecommendationSetResponse.DISCLAIMER;
	}

	public static String recommendationFailed(LocalDate date) {
		return date + " 추천 생성에 실패했습니다. 서버 로그를 확인해 주세요.";
	}

	/**
	 * 대조군을 뺀 추천 줄을 추천 날짜별로 맞힌 개수·등수와 함께 나열하고 등수별 줄 수를 붙인다.
	 * 줄은 번호 → 결과 → 전략 순서다. 한글 폭이 기기마다 달라 전략 라벨을 맨 뒤에 둔다.
	 * 날짜 사이는 빈 줄로 띄우고, 근거 조합은 그날 안에서 순서대로 번호를 붙인다(근거 조합1, 2, …).
	 */
	public static String drawFinished(DrawSummary summary) {
		StringBuilder text = new StringBuilder()
			.append(summary.round()).append("회 추첨 결과 · ").append(dateWithDay(summary.drawDate()));
		if (summary.lateCollectedAt() != null) {
			text.append("\n당첨번호를 늦게 수집했습니다(").append(summary.lateCollectedAt().format(DATE_TIME)).append(").");
		}
		text.append("\n\n<pre>").append(numbers(summary.numbers())).append(" + ").append(summary.bonus())
			.append("</pre>\n\n");

		ResultTally tally = summary.tally();
		if (tally.lineCount() == 0) {
			return text.append("이 회차 추천이 없습니다.").toString();
		}
		String counts = Arrays.stream(PrizeRank.values())
			.map(rank -> rankLabel(rank) + " " + tally.rankCounts().get(rank))
			.collect(Collectors.joining(" · "));
		List<String> rows = new ArrayList<>();
		LocalDate day = null;
		int combined = 0;
		for (DrawSummary.Line line : summary.lines()) {
			if (!line.date().equals(day)) {
				if (day != null) {
					rows.add("");
				}
				day = line.date();
				combined = 0;
				rows.add(MONTH_DAY.format(day) + "(" + dayOfWeek(day) + ")");
			}
			String strategy = label(line.strategy());
			if (line.strategy() == StrategyType.COMBINED) {
				strategy += ++combined;
			}
			rows.add(numbers(line.numbers()) + "  " + line.matchCount() + "개 " + rankLabel(line.rank()) + "  "
					+ strategy);
		}
		return text.append("<pre>").append(String.join("\n", rows)).append("</pre>\n\n")
			.append(counts).append("\n\n")
			.append(RecommendationSetResponse.DISCLAIMER)
			.toString();
	}

	public static String collectionFailed(LocalDate expectedDraw) {
		return dateWithDay(expectedDraw) + " 추첨 당첨번호를 아직 수집하지 못했습니다.\n"
				+ "수집될 때까지 1시간마다 계속 확인합니다.";
	}

	public static String collectionError(LocalDate expectedDraw) {
		return dateWithDay(expectedDraw) + " 추첨 회차 수집 중 오류가 났습니다.\n"
				+ "잘못된 값은 저장하지 않았습니다. 서버 로그를 확인해 주세요.";
	}

	static String dateWithDay(LocalDate date) {
		return date + "(" + dayOfWeek(date) + ")";
	}

	private static String dayOfWeek(LocalDate date) {
		return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
	}

	private static String line(StoredLine line) {
		return numbers(line.numbers()) + "  " + label(line.strategy());
	}

	private static String numbers(LottoNumbers numbers) {
		return numbers.values().stream()
			.map(n -> String.format("%2d", n))
			.collect(Collectors.joining(" · "));
	}

	static String won(long amount) {
		return String.format(Locale.KOREA, "%,d원", amount);
	}

	private static String rankLabel(PrizeRank rank) {
		return switch (rank) {
			case FIRST -> "1등";
			case SECOND -> "2등";
			case THIRD -> "3등";
			case FOURTH -> "4등";
			case FIFTH -> "5등";
			case NONE -> "낙첨";
		};
	}

	static String label(StrategyType strategy) {
		return switch (strategy) {
			case RECENT_HOT -> "최근 빈도";
			case LONG_COLD -> "장기 미출현";
			case ALL_TIME -> "전체 빈도";
			case PAIR -> "번호 쌍";
			case BALANCED -> "균형";
			case CONTROL -> "대조군";
			case COMBINED -> "근거 조합";
		};
	}

}
