package com.lotterypicker.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.dto.RankedNumberResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NumberStatisticsServiceTest {

	private static final LocalDate NOV_9 = LocalDate.of(2026, 11, 9);
	private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

	private DailyRecommendationRepository recommendations;
	private LottoRoundRepository rounds;
	private NumberStatisticsService service;

	@BeforeEach
	void setUp() {
		recommendations = mock(DailyRecommendationRepository.class);
		rounds = mock(LottoRoundRepository.class);
		service = new NumberStatisticsService(recommendations, rounds, CLOCK);
	}

	private static DailyRecommendation line(LocalDate date, StrategyType strategy, int from, int to) {
		return DailyRecommendation.builder()
			.date(date)
			.roundNo(1250)
			.strategy(strategy)
			.lineIndex(0)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)))
			.evidence(List.of())
			.relaxation(Relaxation.NONE)
			.fixedSnapshot("")
			.excludedSnapshot("")
			.historyFromRound(from)
			.historyToRound(to)
			.createdAt(date.atTime(17, 0))
			.build();
	}

	private static LottoRound round(int no, Integer... numbers) {
		return LottoRound.builder()
			.roundNo(no)
			.numbers(new LottoNumbers(List.of(numbers)))
			.bonus(45)
			.drawDate(LocalDate.of(2026, 1, 3).plusWeeks(no))
			.source("https://www.dhlottery.co.kr")
			.fetchedAt(LocalDateTime.of(2026, 11, 1, 9, 0))
			.build();
	}

	private static int valueOf(NumberStatisticsResponse response, NumberMetric metric, int number) {
		return response.metrics().get(metric).stream()
			.filter(r -> r.number() == number)
			.mapToInt(RankedNumberResponse::value)
			.findFirst()
			.orElseThrow();
	}

	@Test
	void 추천에_저장된_회차_범위만으로_계산한다() {
		// given: 추천은 1~2회로 만들었고, 3회는 나중에 수집됐다
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_9))
			.willReturn(List.of(line(NOV_9, StrategyType.RECENT_HOT, 1, 2)));
		given(rounds.findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(2), any()))
			.willReturn(List.of(round(1, 1, 2, 3, 4, 5, 6), round(2, 1, 7, 8, 9, 10, 11)));

		// when
		NumberStatisticsResponse response = service.find(NOV_9).orElseThrow();

		// then
		assertThat(response.historyFromRound()).isEqualTo(1);
		assertThat(response.historyToRound()).isEqualTo(2);
		assertThat(response.totalRounds()).isEqualTo(2);
		assertThat(valueOf(response, NumberMetric.ALL_TIME, 1)).isEqualTo(2);
		assertThat(valueOf(response, NumberMetric.ALL_TIME, 40)).isZero();
	}

	@Test
	void 추천을_만든_뒤_수집된_회차는_빼고_계산한다() {
		// given: 추천은 11월 9일 17시에 만들었다. 범위 안의 빠진 회차가 그 뒤에 채워질 수 있다
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_9))
			.willReturn(List.of(line(NOV_9, StrategyType.RECENT_HOT, 1, 2)));
		given(rounds.findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(1, 2, NOV_9.atTime(17, 0)))
			.willReturn(List.of(round(1, 1, 2, 3, 4, 5, 6)));

		// when
		NumberStatisticsResponse response = service.find(NOV_9).orElseThrow();

		// then: 추천 시각까지 수집된 회차만 센다
		verify(rounds).findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(2),
				eq(NOV_9.atTime(17, 0)));
		assertThat(response.totalRounds()).isEqualTo(1);
	}

	@Test
	void 같은_추천의_통계는_한_번만_계산한다() {
		// given: 같은 날짜를 두 번 본다
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_9))
			.willReturn(List.of(line(NOV_9, StrategyType.RECENT_HOT, 1, 2)));
		given(rounds.findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(2), any()))
			.willReturn(List.of(round(1, 1, 2, 3, 4, 5, 6), round(2, 1, 7, 8, 9, 10, 11)));

		// when
		NumberStatisticsResponse first = service.find(NOV_9).orElseThrow();
		NumberStatisticsResponse second = service.find(NOV_9).orElseThrow();

		// then: 회차는 한 번만 읽는다
		verify(rounds, times(1)).findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(2), any());
		assertThat(second).isEqualTo(first);
	}

	@Test
	void 대조군_줄은_기준으로_쓰지_않는다() {
		// given: 정렬상 대조군이 먼저 와도 추천 줄의 범위를 쓴다
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_9)).willReturn(
				List.of(line(NOV_9, StrategyType.CONTROL, 5, 9), line(NOV_9, StrategyType.RECENT_HOT, 1, 2)));
		given(rounds.findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(2), any()))
			.willReturn(List.of(round(1, 1, 2, 3, 4, 5, 6), round(2, 1, 7, 8, 9, 10, 11)));

		// when
		NumberStatisticsResponse response = service.find(NOV_9).orElseThrow();

		// then
		assertThat(response.historyToRound()).isEqualTo(2);
	}

	@Test
	void 세_지표_모두_45개씩_준다() {
		// given
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_9))
			.willReturn(List.of(line(NOV_9, StrategyType.RECENT_HOT, 1, 1)));
		given(rounds.findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(1), any())).willReturn(List.of(round(1, 1, 2, 3, 4, 5, 6)));

		// when
		NumberStatisticsResponse response = service.find(NOV_9).orElseThrow();

		// then
		assertThat(response.metrics().keySet()).isEqualTo(Set.of(NumberMetric.values()));
		assertThat(response.metrics().values()).allSatisfy(ranked -> assertThat(ranked).hasSize(45));
		assertThat(response.recentWindow()).isEqualTo(50);
		assertThat(response.date()).isEqualTo(NOV_9);
	}

	@Test
	void 날짜가_없으면_가장_최근_추천_기준이다() {
		// given
		DailyRecommendation latest = line(NOV_10, StrategyType.RECENT_HOT, 1, 1);
		given(recommendations.findFirstByDateLessThanEqualOrderByDateDesc(NOV_10)).willReturn(Optional.of(latest));
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_10)).willReturn(List.of(latest));
		given(rounds.findByRoundNoBetweenAndFetchedAtLessThanEqualOrderByRoundNoAsc(eq(1), eq(1), any())).willReturn(List.of(round(1, 1, 2, 3, 4, 5, 6)));

		// when
		Optional<NumberStatisticsResponse> response = service.find(null);

		// then
		assertThat(response).map(NumberStatisticsResponse::date).contains(NOV_10);
	}

	@Test
	void 추천이_하나도_없으면_빈_값이다() {
		// given
		given(recommendations.findFirstByDateLessThanEqualOrderByDateDesc(NOV_10)).willReturn(Optional.empty());

		// when / then
		assertThat(service.find(null)).isEmpty();
	}

	@Test
	void 지정한_날짜에_추천이_없으면_COMMON_NOT_FOUND다() {
		// given
		given(recommendations.findByDateOrderByLineIndexAsc(NOV_9)).willReturn(List.of());

		// when / then
		assertThatThrownBy(() -> service.find(NOV_9)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

}
