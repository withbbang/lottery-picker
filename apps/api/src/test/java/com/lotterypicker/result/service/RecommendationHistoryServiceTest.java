package com.lotterypicker.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.dto.RecommendationHistoryLineResponse;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationHistoryServiceTest {

	private static final LocalDate NOV_7 = LocalDate.of(2026, 11, 7);
	private static final LocalDate NOV_9 = LocalDate.of(2026, 11, 9);

	@Mock
	private DailyRecommendationRepository recommendations;

	@Mock
	private RecommendationResultRepository results;

	@Mock
	private LottoRoundRepository rounds;

	@InjectMocks
	private RecommendationHistoryService service;

	private static DailyRecommendation line(long id, LocalDate date, int round, StrategyType strategy, int index) {
		DailyRecommendation line = DailyRecommendation.builder()
			.date(date)
			.roundNo(round)
			.strategy(strategy)
			.lineIndex(index)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 30, 45)))
			.evidence(List.of("근거"))
			.relaxation(Relaxation.NONE)
			.fixedSnapshot("")
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(round - 1)
			.createdAt(date.atTime(17, 0))
			.build();
		ReflectionTestUtils.setField(line, "id", id);
		return line;
	}

	private static RecommendationResult result(long recommendationId, int matchCount, PrizeRank rank) {
		return RecommendationResult.builder()
			.recommendationId(recommendationId)
			.roundNo(1249)
			.matchCount(matchCount)
			.prizeRank(rank)
			.prizeAmount(0)
			.createdAt(LocalDateTime.of(2026, 11, 7, 20, 45))
			.build();
	}

	private static LottoRound winning(int round, List<Integer> numbers) {
		return winning(round, numbers, 44);
	}

	private static LottoRound winning(int round, List<Integer> numbers, int bonus) {
		return LottoRound.builder()
			.roundNo(round)
			.numbers(new LottoNumbers(numbers))
			.bonus(bonus)
			.drawDate(NOV_7)
			.source("https://www.dhlottery.co.kr")
			.fetchedAt(LocalDateTime.of(2026, 11, 7, 21, 0))
			.build();
	}

	@Test
	void 날짜마다_한_벌로_묶고_채점된_줄에만_결과를_붙인다() {
		// given: 11/9(1250회, 채점 전)와 11/7(1249회, 채점됨)
		given(recommendations.findRecommendationDates(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(NOV_9, NOV_7), PageRequest.of(0, 10), 2));
		given(recommendations.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(List.of(NOV_9, NOV_7)))
			.willReturn(List.of(line(3, NOV_9, 1250, StrategyType.RECENT_HOT, 0),
					line(1, NOV_7, 1249, StrategyType.RECENT_HOT, 0), line(2, NOV_7, 1249, StrategyType.COMBINED, 5)));
		given(results.findByRecommendationIdIn(List.of(3L, 1L, 2L)))
			.willReturn(List.of(result(1, 4, PrizeRank.FOURTH), result(2, 0, PrizeRank.NONE)));
		given(rounds.findAllById(List.of(1249))).willReturn(List.of(winning(1249, List.of(1, 2, 3, 4, 10, 11))));

		// when
		var page = service.history(0, 10);

		// then
		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.items()).hasSize(2);
		assertThat(page.items().get(0).date()).isEqualTo(NOV_9);
		assertThat(page.items().get(0).round()).isEqualTo(1250);
		assertThat(page.items().get(0).lines()).singleElement()
			.satisfies(l -> assertThat(l.matchCount()).isNull())
			.satisfies(l -> assertThat(l.prizeRank()).isNull())
			.satisfies(l -> assertThat(l.matchedNumbers()).isNull())
			.satisfies(l -> assertThat(l.matchedBonus()).isNull());
		assertThat(page.items().get(1).round()).isEqualTo(1249);
		assertThat(page.items().get(1).lines()).extracting(RecommendationHistoryLineResponse::strategy,
				RecommendationHistoryLineResponse::matchCount, RecommendationHistoryLineResponse::prizeRank)
			.containsExactly(tuple(StrategyType.RECENT_HOT, 4, PrizeRank.FOURTH),
					tuple(StrategyType.COMBINED, 0, PrizeRank.NONE));
	}

	@Test
	void 지난_줄에도_근거_번호와_통계_지표를_담는다() {
		// given
		DailyRecommendation recent = DailyRecommendation.builder()
			.date(NOV_7)
			.roundNo(1249)
			.strategy(StrategyType.RECENT_HOT)
			.lineIndex(0)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 30, 45)))
			.evidence(List.of("번호 30: 최근 50회 중 9회 출현"))
			.evidenceNumbers(List.of(30))
			.relaxation(Relaxation.NONE)
			.fixedSnapshot("")
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(1248)
			.createdAt(NOV_7.atTime(17, 0))
			.build();
		ReflectionTestUtils.setField(recent, "id", 1L);
		given(recommendations.findRecommendationDates(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(NOV_7), PageRequest.of(0, 10), 1));
		given(recommendations.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(List.of(NOV_7)))
			.willReturn(List.of(recent, line(2, NOV_7, 1249, StrategyType.PAIR, 3)));
		given(results.findByRecommendationIdIn(List.of(1L, 2L))).willReturn(List.of());

		// when
		var lines = service.history(0, 10).items().get(0).lines();

		// then
		assertThat(lines.get(0).evidenceNumbers()).containsExactly(30);
		assertThat(lines.get(0).statsMetric()).isEqualTo(NumberMetric.RECENT);
		assertThat(lines.get(1).evidenceNumbers()).isEmpty();
		assertThat(lines.get(1).statsMetric()).isNull();
	}

	@Test
	void 채점된_줄은_당첨_본번호와_겹친_번호를_오름차순으로_준다() {
		// given: 추천 1·2·3·4·30·45, 당첨 1·3·30·40·41·42 + 보너스 45
		given(recommendations.findRecommendationDates(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(NOV_7), PageRequest.of(0, 10), 1));
		given(recommendations.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(List.of(NOV_7)))
			.willReturn(List.of(line(1, NOV_7, 1249, StrategyType.RECENT_HOT, 0)));
		given(results.findByRecommendationIdIn(List.of(1L))).willReturn(List.of(result(1, 3, PrizeRank.FIFTH)));
		given(rounds.findAllById(List.of(1249))).willReturn(List.of(winning(1249, List.of(42, 1, 30, 3, 40, 41), 45)));

		// when
		var page = service.history(0, 10);

		// then: 보너스 45는 넣지 않는다
		assertThat(page.items().get(0).lines().get(0).matchedNumbers()).containsExactly(1, 3, 30);
	}

	@Test
	void 줄에_보너스_번호가_있으면_matchedBonus로_준다() {
		// given: 추천 1·2·3·4·30·45, 당첨 1·3·30·40·41·42 + 보너스 45
		given(recommendations.findRecommendationDates(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(NOV_7), PageRequest.of(0, 10), 1));
		given(recommendations.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(List.of(NOV_7)))
			.willReturn(List.of(line(1, NOV_7, 1249, StrategyType.RECENT_HOT, 0)));
		given(results.findByRecommendationIdIn(List.of(1L))).willReturn(List.of(result(1, 3, PrizeRank.FIFTH)));
		given(rounds.findAllById(List.of(1249))).willReturn(List.of(winning(1249, List.of(42, 1, 30, 3, 40, 41), 45)));

		// when
		var page = service.history(0, 10);

		// then
		assertThat(page.items().get(0).lines().get(0).matchedBonus()).isEqualTo(45);
	}

	@Test
	void 줄에_보너스_번호가_없으면_matchedBonus는_null이다() {
		// given: 보너스 44는 추천 1·2·3·4·30·45에 없다
		given(recommendations.findRecommendationDates(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(NOV_7), PageRequest.of(0, 10), 1));
		given(recommendations.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(List.of(NOV_7)))
			.willReturn(List.of(line(1, NOV_7, 1249, StrategyType.RECENT_HOT, 0)));
		given(results.findByRecommendationIdIn(List.of(1L))).willReturn(List.of(result(1, 4, PrizeRank.FOURTH)));
		given(rounds.findAllById(List.of(1249))).willReturn(List.of(winning(1249, List.of(1, 2, 3, 4, 10, 11))));

		// when
		var page = service.history(0, 10);

		// then
		assertThat(page.items().get(0).lines().get(0).matchedBonus()).isNull();
	}

	@Test
	void 추천이_없으면_줄을_조회하지_않고_빈_페이지를_준다() {
		// given
		given(recommendations.findRecommendationDates(PageRequest.of(0, 10)))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		// when
		var page = service.history(0, 10);

		// then
		assertThat(page.items()).isEmpty();
		assertThat(page.totalPages()).isZero();
		verify(recommendations, never()).findByDateInAndControlFalseOrderByDateDescLineIndexAsc(any());
		verify(results, never()).findByRecommendationIdIn(any());
		verify(rounds, never()).findAllById(any());
	}

}
