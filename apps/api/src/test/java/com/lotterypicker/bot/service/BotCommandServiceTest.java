package com.lotterypicker.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.notification.BotMessageFormatter;
import com.lotterypicker.notification.NotificationStatus;
import com.lotterypicker.notification.TelegramChat;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.dto.RecommendationSettingsResponse;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import com.lotterypicker.recommendation.service.RecommendationSettingsService;
import com.lotterypicker.result.DrawSummary;
import com.lotterypicker.result.ResultTally;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.result.service.PostDrawService;
import com.lotterypicker.result.service.StrategyPerformanceService;
import com.lotterypicker.round.dto.PrizeTierResponse;
import com.lotterypicker.round.dto.RoundDetailResponse;
import com.lotterypicker.round.dto.WinningStoreResponse;
import com.lotterypicker.round.service.RoundService;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.service.NumberStatisticsService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotCommandServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	private final DailyRecommendationService recommendations = mock(DailyRecommendationService.class);
	private final PostDrawService postDraw = mock(PostDrawService.class);
	private final StrategyPerformanceService performances = mock(StrategyPerformanceService.class);
	private final RoundService rounds = mock(RoundService.class);
	private final NumberStatisticsService statistics = mock(NumberStatisticsService.class);
	private final RecommendationSettingsService settings = mock(RecommendationSettingsService.class);

	private final BotCommandService service = new BotCommandService(recommendations, postDraw, performances, rounds,
			statistics, settings);

	@Test
	void today는_최근_추천을_알림_문구로_답한다() {
		// given
		RecommendationSetResponse latest = new RecommendationSetResponse(TODAY, 1250, List.of(), 1, 1249,
				RecommendationSetResponse.DISCLAIMER, NotificationStatus.SENT, false, false, false);
		RecommendationSet set = new RecommendationSet(TODAY, 1250, List.of(new StoredLine(StrategyType.RECENT_HOT,
				new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)), List.of("근거"), Relaxation.NONE)));
		when(recommendations.today()).thenReturn(TODAY);
		when(recommendations.findLatest(TODAY)).thenReturn(Optional.of(latest));
		when(recommendations.findSet(TODAY)).thenReturn(Optional.of(set));

		// when / then
		assertThat(service.reply("/today", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.today(latest, set));
	}

	@Test
	void 추천이_없으면_없다고_답한다() {
		// given
		when(recommendations.today()).thenReturn(TODAY);
		when(recommendations.findLatest(TODAY)).thenReturn(Optional.empty());

		// when / then
		assertThat(service.reply("/today", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.noRecommendation());
	}

	@Test
	void result는_최근_채점_요약을_답한다() {
		// given
		DrawSummary summary = new DrawSummary(1249, LocalDate.of(2026, 11, 7),
				new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)), 7, null, ResultTally.of(List.of()), List.of());
		when(postDraw.latestSummary()).thenReturn(Optional.of(summary));

		// when / then
		assertThat(service.reply("/result", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.result(summary));
	}

	@Test
	void 채점_전이면_없다고_답한다() {
		// given
		when(postDraw.latestSummary()).thenReturn(Optional.empty());

		// when / then
		assertThat(service.reply("/result", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.noResult());
	}

	@Test
	void round는_회차와_1등_판매점으로_답한다() {
		// given
		RoundDetailResponse detail = new RoundDetailResponse(1249, List.of(1, 2, 3, 4, 5, 6), 7,
				LocalDate.of(2026, 11, 7), List.of(new PrizeTierResponse(1, 1, 100L)));
		List<WinningStoreResponse> stores = List.of(new WinningStoreResponse(1, "서울", "판매점", "주소", "자동", "정상"));
		when(rounds.detail(1249)).thenReturn(detail);
		when(rounds.winningStores(1249, 1, null)).thenReturn(stores);

		// when
		String text = service.reply("/round 1249", TelegramChat.OWNER);

		// then
		assertThat(text).isEqualTo(BotMessageFormatter.round(detail, stores));
		verify(rounds).winningStores(1249, 1, null);
	}

	@Test
	void round_인자가_잘못되면_사용법을_답한다() {
		// given / when / then
		for (String text : List.of("/round", "/round abc", "/round 0", "/round -1", "/round 99999999999")) {
			assertThat(service.reply(text, TelegramChat.OWNER)).as(text).isEqualTo(BotMessageFormatter.roundUsage());
		}
		verifyNoInteractions(rounds);
	}

	@Test
	void 없는_회차는_없다고_답한다() {
		// given
		when(rounds.detail(anyInt())).thenThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));

		// when / then
		assertThat(service.reply("/round 1300", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.roundNotFound("1300"));
	}

	@Test
	void stats_performance_settings_help를_답한다() {
		// given
		NumberStatisticsResponse stats = new NumberStatisticsResponse(TODAY, 1, 1249, 1249, 50, Map.of());
		StrategyPerformancesResponse perf = new StrategyPerformancesResponse(0.0224, List.of());
		RecommendationSettingsResponse current = new RecommendationSettingsResponse(List.of(3), List.of());
		when(statistics.find(null)).thenReturn(Optional.of(stats));
		when(performances.performances()).thenReturn(perf);
		when(settings.get()).thenReturn(current);

		// when / then
		assertThat(service.reply("/stats", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.stats(stats));
		assertThat(service.reply("/performance", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.performance(perf));
		assertThat(service.reply("/settings", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.settings(current));
		assertThat(service.reply("/help", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.help(TelegramChat.OWNER));
	}

	@Test
	void 통계가_없으면_없다고_답한다() {
		// given
		when(statistics.find(null)).thenReturn(Optional.empty());

		// when / then
		assertThat(service.reply("/stats", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.noStats());
	}

	@Test
	void 모르는_명령이나_일반_글은_안내한다() {
		// given / when / then
		assertThat(service.reply("/foo", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.unknown());
		assertThat(service.reply("안녕", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.unknown());
	}

	@Test
	void 예상_못한_오류는_일반_오류_문구로_답한다() {
		// given
		when(performances.performances()).thenThrow(new IllegalStateException("boom"));

		// when / then
		assertThat(service.reply("/performance", TelegramChat.OWNER)).isEqualTo(BotMessageFormatter.error());
	}

	@Test
	void 가족방의_settings는_모르는_명령으로_답하고_설정을_읽지_않는다() {
		// when
		String reply = service.reply("/settings", TelegramChat.FAMILY);

		// then
		assertThat(reply).isEqualTo(BotMessageFormatter.unknown());
		verifyNoInteractions(settings);
	}

	@Test
	void 가족방의_help는_가족방_명령만_보여_준다() {
		// when
		String reply = service.reply("/help", TelegramChat.FAMILY);

		// then
		assertThat(reply).isEqualTo(BotMessageFormatter.help(TelegramChat.FAMILY)).doesNotContain("/settings");
	}

}
