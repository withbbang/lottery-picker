package com.lotterypicker.notification;

import com.lotterypicker.bot.BotCommand;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.dto.RecommendationSettingsResponse;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.result.DrawSummary;
import com.lotterypicker.result.dto.StrategyPerformanceResponse;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.round.dto.PrizeTierResponse;
import com.lotterypicker.round.dto.RoundDetailResponse;
import com.lotterypicker.round.dto.WinningStoreResponse;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.dto.RankedNumberResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

/** 텔레그램 봇 명령 응답 문구. 숫자는 서비스가 준 DTO 그대로 쓰고, 여기서는 문구로 바꾸기만 한다. */
public final class BotMessageFormatter {

	private static final int STATS_TOP = 5;

	private BotMessageFormatter() {
	}

	public static String help() {
		return "사용할 수 있는 명령\n" + BotCommand.menu()
			.stream()
			.map(item -> "/" + item.command() + " " + item.description())
			.collect(Collectors.joining("\n"));
	}

	public static String unknown() {
		return "모르는 명령입니다. /help 로 명령 목록을 볼 수 있습니다.";
	}

	public static String error() {
		return "처리 중 오류가 났습니다. 잠시 후 다시 시도해 주세요.";
	}

	public static String noRecommendation() {
		return "아직 추천이 없습니다.";
	}

	/** 오늘 추천은 일일 추천 알림과 같은 문구. 이월이면 안내 한 줄을 앞에 붙인다. */
	public static String today(RecommendationSetResponse latest, RecommendationSet set) {
		String body = MessageFormatter.recommendation(set);
		if (!latest.carriedOver()) {
			return body;
		}
		String note = "오늘 추천이 아직 없어 " + monthDay(latest.date()) + " 추천을 보여 드립니다.";
		if (latest.todayPending()) {
			note += " 오늘 추천은 17시에 나옵니다.";
		}
		return note + "\n\n" + body;
	}

	public static String result(DrawSummary summary) {
		return MessageFormatter.drawFinished(summary);
	}

	public static String noResult() {
		return "아직 채점한 회차가 없습니다.";
	}

	public static String roundUsage() {
		return "회차 번호를 숫자로 적어 주세요. 예: /round 1249";
	}

	public static String roundNotFound(String argument) {
		return HtmlUtils.htmlEscape(argument) + "회 당첨 정보가 없습니다.";
	}

	public static String round(RoundDetailResponse detail, List<WinningStoreResponse> firstRankStores) {
		StringBuilder text = new StringBuilder();
		text.append(detail.round()).append("회 · ").append(MessageFormatter.dateWithDay(detail.drawDate()))
			.append("\n\n<pre>")
			.append(detail.numbers().stream().map(n -> String.format("%2d", n)).collect(Collectors.joining(" · ")))
			.append(" + ").append(detail.bonus()).append("</pre>\n\n");
		for (PrizeTierResponse tier : detail.prizes()) {
			text.append(tier.rank()).append("등 ")
				.append(String.format(Locale.KOREA, "%,d명", tier.winnerCount()))
				.append(" · ").append(MessageFormatter.won(tier.amountPerWinner())).append('\n');
		}
		text.append('\n');
		if (firstRankStores.isEmpty()) {
			return text.append("1등 판매점 정보가 없습니다.").toString();
		}
		Map<String, Long> counts = firstRankStores.stream()
			.collect(Collectors.groupingBy(WinningStoreResponse::region, Collectors.counting()));
		String regions = counts.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.map(entry -> HtmlUtils.htmlEscape(entry.getKey()) + " " + entry.getValue())
			.collect(Collectors.joining(" · "));
		return text.append("1등 판매점 지역\n").append(regions).toString();
	}

	public static String noStats() {
		return "아직 추천이 없어 번호 통계가 없습니다.";
	}

	public static String stats(NumberStatisticsResponse stats) {
		StringBuilder text = new StringBuilder();
		text.append("번호 통계 · ").append(monthDay(stats.date())).append(" 추천 기준 (")
			.append(stats.historyFromRound()).append('~').append(stats.historyToRound()).append("회)");
		metric(text, "최근 " + stats.recentWindow() + "회 출현", stats.metrics().get(NumberMetric.RECENT),
				value -> value + "회");
		metric(text, "미출현 회차", stats.metrics().get(NumberMetric.COLD),
				value -> value == 0 ? "직전 회차 출현" : value + "회째");
		metric(text, "전체 출현", stats.metrics().get(NumberMetric.ALL_TIME), value -> value + "회");
		return text.append("\n\n과거에 자주·드물게 나온 정도일 뿐, ").append(RecommendationSetResponse.DISCLAIMER).toString();
	}

	private static void metric(StringBuilder text, String title, List<RankedNumberResponse> ranked,
			Function<Integer, String> value) {
		if (ranked == null || ranked.isEmpty()) {
			return;
		}
		text.append("\n\n").append(title).append("\n<pre>")
			.append(ranked.stream()
				.limit(STATS_TOP)
				.map(n -> String.format("%2d", n.number()) + "  " + value.apply(n.value()) + "  "
						+ (n.tied() ? "공동 " : "") + n.rank() + "위")
				.collect(Collectors.joining("\n")))
			.append("</pre>");
	}

	public static String noPerformance() {
		return "아직 채점한 추천이 없습니다.";
	}

	public static String performance(StrategyPerformancesResponse response) {
		if (response.strategies().stream().allMatch(s -> s.lineCount() == 0)) {
			return noPerformance();
		}
		String rows = response.strategies()
			.stream()
			.map(BotMessageFormatter::performanceRow)
			.collect(Collectors.joining("\n"));
		return "전략별 성적표 (채점된 줄 기준)\n5등 이상 비율 · 평균 맞힌 개수 · 줄 수\n<pre>" + rows + "</pre>\n"
				+ String.format(Locale.ROOT, "이론값 %.1f%%", response.theoryAtLeastFifthRate() * 100) + "\n\n"
				+ RecommendationSetResponse.DISCLAIMER;
	}

	private static String performanceRow(StrategyPerformanceResponse s) {
		return String.format(Locale.ROOT, "%5.1f%%  %.2f개  %d줄  %s", s.atLeastFifthRate() * 100, s.averageMatches(),
				s.lineCount(), MessageFormatter.label(s.strategy())) + (s.sampleSufficient() ? "" : " (표본 부족)");
	}

	public static String settings(RecommendationSettingsResponse settings) {
		return "고정 번호: " + list(settings.fixed()) + "\n제외 번호: " + list(settings.excluded())
				+ "\n바꾸기는 웹의 설정 화면에서 할 수 있습니다.";
	}

	private static String list(List<Integer> numbers) {
		if (numbers.isEmpty()) {
			return "없음";
		}
		return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
	}

	private static String monthDay(LocalDate date) {
		return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
	}

}
