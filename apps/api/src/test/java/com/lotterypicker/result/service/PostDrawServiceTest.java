package com.lotterypicker.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.notification.TelegramClient;
import com.lotterypicker.notification.TelegramException;
import com.lotterypicker.notification.repository.NotificationLogRepository;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** 채점과 중복 방지는 유니크 제약과 발송 기록에 기대므로 실제 DB로 검증한다. 텔레그램만 모킹한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostDrawServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDate DRAW_DATE = LocalDate.of(2026, 11, 7);
	private static final List<StrategyType> STRATEGIES = List.of(StrategyType.RECENT_HOT, StrategyType.LONG_COLD,
			StrategyType.ALL_TIME, StrategyType.PAIR, StrategyType.BALANCED);
	private static final List<Integer> MISS = List.of(20, 21, 22, 23, 24, 25);

	@Autowired
	private LottoRoundRepository rounds;

	@Autowired
	private DailyRecommendationRepository recommendations;

	@Autowired
	private RecommendationResultRepository results;

	@Autowired
	private NotificationLogRepository logs;

	private final TelegramClient telegram = mock(TelegramClient.class);

	@BeforeEach
	void setUp() {
		when(telegram.isConfigured()).thenReturn(true);
	}

	private PostDrawService serviceOn(String seoulDateTime) {
		Clock clock = Clock.fixed(LocalDateTime.parse(seoulDateTime).atZone(SEOUL).toInstant(), SEOUL);
		return new PostDrawService(rounds, recommendations, results, new NotificationService(telegram, logs, clock, 0),
				clock);
	}

	private static LottoRound round(int roundNo, LocalDate drawDate, LocalDateTime fetchedAt) {
		return LottoRound.builder()
			.roundNo(roundNo)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)))
			.bonus(7)
			.drawDate(drawDate)
			.rank1Count(1).rank1Amount(2_000_000_000L)
			.rank2Count(1).rank2Amount(50_000_000L)
			.rank3Count(1).rank3Amount(1_500_000L)
			.rank4Count(1).rank4Amount(50_000L)
			.rank5Count(1).rank5Amount(5_000L)
			.source("test")
			.fetchedAt(fetchedAt)
			.build();
	}

	private static DailyRecommendation line(LocalDate date, int roundNo, StrategyType strategy, int index,
			List<Integer> numbers) {
		return DailyRecommendation.builder()
			.date(date)
			.roundNo(roundNo)
			.strategy(strategy)
			.lineIndex(index)
			.numbers(new LottoNumbers(numbers))
			.evidence(List.of("근거"))
			.relaxation(Relaxation.NONE)
			.fixedSnapshot("")
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(roundNo - 1)
			.createdAt(date.atTime(17, 0))
			.build();
	}

	/**
	 * 일~토 7일 × (추천 5줄 + 대조군 5줄). 추천은 첫날 4등 1줄, 5등 1줄이고 나머지는 낙첨이다.
	 * 대조군은 첫날 3등 1줄이 있어 요약에 섞이면 드러난다.
	 */
	private void saveWeek(int roundNo, LocalDate saturday) {
		List<DailyRecommendation> lines = new ArrayList<>();
		for (int day = 0; day < 7; day++) {
			LocalDate date = saturday.minusDays(6 - day);
			for (int i = 0; i < STRATEGIES.size(); i++) {
				List<Integer> numbers = MISS;
				if (day == 0 && i == 0) {
					numbers = List.of(1, 2, 3, 4, 30, 31);
				} else if (day == 0 && i == 1) {
					numbers = List.of(1, 2, 3, 30, 31, 32);
				}
				lines.add(line(date, roundNo, STRATEGIES.get(i), i, numbers));
			}
			for (int i = 0; i < 5; i++) {
				List<Integer> numbers = day == 0 && i == 0 ? List.of(1, 2, 3, 4, 5, 40) : MISS;
				lines.add(line(date, roundNo, StrategyType.CONTROL, 5 + i, numbers));
			}
		}
		recommendations.saveAll(lines);
	}

	private String sentText() {
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(telegram).send(text.capture());
		return text.getValue();
	}

	@Test
	void 회차의_추천과_대조군_전부를_채점한다() {
		// given
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);

		// when
		serviceOn("2026-11-07T20:45").process(1249);

		// then
		List<RecommendationResult> scored = results.findByRoundNo(1249);
		assertThat(scored).hasSize(70);
		assertThat(scored).filteredOn(r -> r.getPrizeRank() == PrizeRank.FOURTH)
			.singleElement()
			.satisfies(r -> {
				assertThat(r.getMatchCount()).isEqualTo(4);
				assertThat(r.getPrizeAmount()).isEqualTo(50_000L);
			});
		assertThat(scored).filteredOn(r -> r.getPrizeRank() == PrizeRank.THIRD)
			.singleElement()
			.satisfies(r -> assertThat(r.getPrizeAmount()).isEqualTo(1_500_000L));
		assertThat(scored).filteredOn(r -> r.getPrizeRank() == PrizeRank.NONE)
			.allSatisfy(r -> assertThat(r.getPrizeAmount()).isZero())
			.hasSize(67);
	}

	@Test
	void 두번_실행해도_결과와_알림이_한번이다() {
		// given
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);
		PostDrawService service = serviceOn("2026-11-07T20:45");

		// when
		service.process(1249);
		service.process(1249);

		// then
		assertThat(results.findByRoundNo(1249)).hasSize(70);
		verify(telegram, times(1)).send(anyString());
	}

	@Test
	void 알림은_대조군을_제외하고_요약한다() {
		// given
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);

		// when
		serviceOn("2026-11-07T20:45").process(1249);

		// then
		assertThat(sentText()).contains("1249회 추첨 결과", " 1 ·  2 ·  3 ·  4 ·  5 ·  6 + 7", "추천 35줄 · 최고 4등",
				"1등 0 · 2등 0 · 3등 0 · 4등 1 · 5등 1 · 낙첨 33", "가상 당첨금 55,000원 (구매했다고 가정)",
				"가상 수익 +20,000원 (줄당 1,000원, 35,000원 구매 가정)")
			.doesNotContain("대조군", "1,500,000", "늦게");
	}

	@Test
	void 지연_수집이면_그_사실을_적는다() {
		// given: 일요일 12시 이후에 수집됐다
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 8, 13, 0)));
		saveWeek(1249, DRAW_DATE);

		// when
		serviceOn("2026-11-08T13:00").process(1249);

		// then
		assertThat(sentText()).contains("당첨번호를 늦게 수집했습니다(2026-11-08 13:00).");
	}

	@Test
	void 추천이_없는_오래된_회차는_알리지_않는다() {
		// given: 초기 적재로 들어온 지난 회차
		rounds.save(round(1200, LocalDate.of(2025, 11, 29), LocalDateTime.of(2026, 11, 7, 20, 45)));

		// when
		serviceOn("2026-11-07T20:45").process(1200);

		// then
		verify(telegram, never()).send(anyString());
		assertThat(results.findByRoundNo(1200)).isEmpty();
	}

	@Test
	void 추천이_없어도_7일_내_회차는_알린다() {
		// given
		LottoRound round = round(1248, LocalDate.of(2026, 10, 31), LocalDateTime.of(2026, 10, 31, 20, 45));

		// when, then: 경계 7일은 알리고 8일은 알리지 않는다
		assertThat(PostDrawService.shouldNotify(round, false, LocalDate.of(2026, 11, 7))).isTrue();
		assertThat(PostDrawService.shouldNotify(round, false, LocalDate.of(2026, 11, 8))).isFalse();
		assertThat(PostDrawService.shouldNotify(round, true, LocalDate.of(2027, 1, 1))).isTrue();
	}

	@Test
	void 추천이_없는_7일_내_회차는_추천_없음을_알린다() {
		// given
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));

		// when
		serviceOn("2026-11-07T20:45").process(1249);

		// then
		assertThat(sentText()).contains("1249회 추첨 결과", "이 회차 추천이 없습니다.");
	}

	@Test
	void 해당_회차_추천이_없으면_결과를_낙첨으로_만들지_않는다() {
		// given: 1250회 추천은 있지만 1250회 당첨번호는 아직 없다
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1250, LocalDate.of(2026, 11, 14));
		PostDrawService service = serviceOn("2026-11-07T20:45");

		// when
		service.process(1249);
		service.process(1250);

		// then
		assertThat(results.findByRoundNo(1249)).isEmpty();
		assertThat(results.findByRoundNo(1250)).isEmpty();
	}

	@Test
	void 종료_알림_발송이_실패하면_재발송에서_한번_보낸다() {
		// given: 첫 처리에서 3회 시도가 모두 실패한다
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);
		PostDrawService service = serviceOn("2026-11-07T20:45");
		doThrow(new TelegramException("down"), new TelegramException("down"), new TelegramException("down"))
			.doNothing()
			.when(telegram).send(anyString());
		service.process(1249);

		// when: 다음 확인 두 번
		service.resendUnsent();
		service.resendUnsent();

		// then: 실패 3회 + 성공 1회. 보낸 뒤에는 다시 보내지 않는다
		verify(telegram, times(4)).send(anyString());
		assertThat(results.findByRoundNo(1249)).hasSize(70);
	}

	@Test
	void 채점_후_알림_전에_멈췄으면_재발송에서_채점하고_알린다() {
		// given: 당첨번호와 추천은 있지만 처리하지 못했다
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);

		// when
		serviceOn("2026-11-07T21:00").resendUnsent();

		// then
		assertThat(results.findByRoundNo(1249)).hasSize(70);
		assertThat(sentText()).contains("1249회 추첨 결과");
	}

	@Test
	void 재발송은_최신_회차와_직전_회차만_본다() {
		// given: 추천이 있는 오래된 회차는 대상이 아니다
		rounds.save(round(1247, LocalDate.of(2026, 10, 24), LocalDateTime.of(2026, 10, 24, 20, 45)));
		saveWeek(1247, LocalDate.of(2026, 10, 24));
		rounds.save(round(1248, LocalDate.of(2026, 10, 31), LocalDateTime.of(2026, 10, 31, 20, 45)));
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));

		// when
		serviceOn("2026-11-07T21:00").resendUnsent();

		// then: 1249(7일 내)과 1248(경계 7일)만 알리고 1247은 건드리지 않는다
		verify(telegram, times(2)).send(anyString());
		assertThat(results.findByRoundNo(1247)).isEmpty();
	}

	@Test
	void 추첨_후_7일이_지난_회차는_발송_실패여도_재발송하지_않는다() {
		// given: 10/31 추첨 당일 발송이 3회 모두 실패했다
		rounds.save(round(1248, LocalDate.of(2026, 10, 31), LocalDateTime.of(2026, 10, 31, 20, 45)));
		saveWeek(1248, LocalDate.of(2026, 10, 31));
		doThrow(new TelegramException("down")).when(telegram).send(anyString());
		serviceOn("2026-10-31T20:45").process(1248);
		doNothing().when(telegram).send(anyString());

		// when: 8일 뒤(새 회차 추첨 다음 날)에 텔레그램이 복구됐다
		serviceOn("2026-11-08T10:00").resendUnsent();

		// then: 지난주 회차를 새 소식처럼 보내지 않는다
		verify(telegram, times(3)).send(anyString());
	}

	@Test
	void 텔레그램이_설정되지_않았으면_재발송_처리를_하지_않는다() {
		// given
		when(telegram.isConfigured()).thenReturn(false);
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);

		// when
		serviceOn("2026-11-07T21:00").resendUnsent();

		// then: 5분마다 헛도는 조회를 하지 않는다. 채점은 수집 경로의 대기 회차 처리가 맡는다
		assertThat(results.findByRoundNo(1249)).isEmpty();
	}

	@Test
	void 늦게_수집한_오래된_회차도_수집_경로에서는_채점하고_알린다() {
		// given: 서버가 오래 꺼져 있다가 추천이 있던 회차를 9일 뒤에 수집했다
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 16, 9, 0)));
		saveWeek(1249, DRAW_DATE);

		// when
		serviceOn("2026-11-16T09:00").process(1249);

		// then
		assertThat(results.findByRoundNo(1249)).hasSize(70);
		assertThat(sentText()).contains("당첨번호를 늦게 수집했습니다(2026-11-16 09:00).");
	}

	@Test
	void 당첨번호가_저장됐고_결과가_없는_추천_회차만_대기한다() {
		// given
		rounds.save(round(1249, DRAW_DATE, LocalDateTime.of(2026, 11, 7, 20, 45)));
		saveWeek(1249, DRAW_DATE);
		saveWeek(1250, LocalDate.of(2026, 11, 14));
		PostDrawService service = serviceOn("2026-11-07T20:45");

		// when
		List<Integer> before = service.pendingRounds();
		service.process(1249);
		List<Integer> after = service.pendingRounds();

		// then
		assertThat(before).containsExactly(1249);
		assertThat(after).isEmpty();
	}

}
