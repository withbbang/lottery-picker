package com.lotterypicker.bot.service;

import com.lotterypicker.bot.BotCommand;
import com.lotterypicker.bot.BotCommand.Parsed;
import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.notification.BotMessageFormatter;
import com.lotterypicker.notification.TelegramChat;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import com.lotterypicker.recommendation.service.RecommendationSettingsService;
import com.lotterypicker.result.service.PostDrawService;
import com.lotterypicker.result.service.StrategyPerformanceService;
import com.lotterypicker.round.service.RoundService;
import com.lotterypicker.statistics.service.NumberStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 봇 명령을 웹과 같은 서비스로 처리해 답할 문구를 만든다. 예외는 밖으로 던지지 않고 오류 문구로 바꾼다.
 * 조회 서비스가 각자 트랜잭션을 가지므로 여기서는 트랜잭션을 걸지 않는다(안쪽 예외가 바깥 커밋을 깨뜨리지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotCommandService {

	private static final int FIRST_RANK = 1;

	private final DailyRecommendationService recommendations;
	private final PostDrawService postDraw;
	private final StrategyPerformanceService performances;
	private final RoundService rounds;
	private final NumberStatisticsService statistics;
	private final RecommendationSettingsService settings;

	/** {@code chat}에서 쓸 수 없는 명령은 모르는 명령과 같게 답한다. */
	public String reply(String text, TelegramChat chat) {
		try {
			return BotCommand.parse(text)
				.filter(parsed -> parsed.command().availableIn(chat))
				.map(parsed -> handle(parsed, chat))
				.orElseGet(BotMessageFormatter::unknown);
		} catch (RuntimeException e) {
			log.error("텔레그램 명령 처리 실패", e);
			return BotMessageFormatter.error();
		}
	}

	private String handle(Parsed parsed, TelegramChat chat) {
		return switch (parsed.command()) {
			case TODAY -> today();
			case RESULT -> postDraw.latestSummary().map(BotMessageFormatter::result).orElseGet(BotMessageFormatter::noResult);
			case PERFORMANCE -> BotMessageFormatter.performance(performances.performances());
			case ROUND -> round(parsed.argument());
			case STATS -> statistics.find(null).map(BotMessageFormatter::stats).orElseGet(BotMessageFormatter::noStats);
			case SETTINGS -> BotMessageFormatter.settings(settings.get());
			case HELP -> BotMessageFormatter.help(chat);
		};
	}

	private String today() {
		return recommendations.findLatest(recommendations.today())
			.flatMap(latest -> recommendations.findSet(latest.date()).map(set -> BotMessageFormatter.today(latest, set)))
			.orElseGet(BotMessageFormatter::noRecommendation);
	}

	private String round(@Nullable String argument) {
		Integer round = parseRound(argument);
		if (round == null) {
			return BotMessageFormatter.roundUsage();
		}
		try {
			return BotMessageFormatter.round(rounds.detail(round), rounds.winningStores(round, FIRST_RANK, null));
		} catch (BusinessException e) {
			if (e.getErrorCode() == ErrorCode.COMMON_NOT_FOUND) {
				return BotMessageFormatter.roundNotFound(argument);
			}
			throw e;
		}
	}

	@Nullable
	private static Integer parseRound(@Nullable String argument) {
		if (argument == null || !argument.matches("\\d{1,9}")) {
			return null;
		}
		int round = Integer.parseInt(argument);
		return round >= 1 ? round : null;
	}

}
