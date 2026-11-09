package com.lotterypicker.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.result.dto.StrategyPerformanceResponse;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.result.entity.RecommendationResult;
import com.lotterypicker.result.repository.RecommendationResultRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StrategyPerformanceServiceTest {

	private static final int ROUND = 1249;

	@Mock
	private RecommendationResultRepository results;

	@Mock
	private DailyRecommendationRepository recommendations;

	private final List<DailyRecommendation> lines = new ArrayList<>();

	private final List<RecommendationResult> scored = new ArrayList<>();

	private long nextId = 1;

	private StrategyPerformanceService service() {
		given(results.findAll()).willReturn(scored);
		given(recommendations.findAllById(scored.stream().map(RecommendationResult::getRecommendationId).toList()))
			.willReturn(lines);
		return new StrategyPerformanceService(results, recommendations);
	}

	/** 줄 하나와 (채점했다면) 그 결과를 만든다. */
	private void add(StrategyType strategy, String fixed, int matchCount, PrizeRank rank, boolean isScored) {
		long id = nextId++;
		DailyRecommendation line = DailyRecommendation.builder()
			.date(LocalDate.of(2026, 11, 7))
			.roundNo(ROUND)
			.strategy(strategy)
			.lineIndex(0)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 30, 31)))
			.evidence(List.of("근거"))
			.relaxation(Relaxation.NONE)
			.fixedSnapshot(fixed)
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(ROUND - 1)
			.createdAt(LocalDateTime.of(2026, 11, 7, 17, 0))
			.build();
		ReflectionTestUtils.setField(line, "id", id);
		if (!isScored) {
			return;
		}
		lines.add(line);
		scored.add(RecommendationResult.builder()
			.recommendationId(id)
			.roundNo(ROUND)
			.matchCount(matchCount)
			.prizeRank(rank)
			.prizeAmount(0)
			.createdAt(LocalDateTime.of(2026, 11, 7, 20, 45))
			.build());
	}

	private static StrategyPerformanceResponse row(StrategyPerformancesResponse r, StrategyType type) {
		return r.strategies().stream().filter(s -> s.strategy() == type).findFirst().orElseThrow();
	}

	@Test
	void 전략별과_대조군을_각각_집계한다() {
		// given
		add(StrategyType.RECENT_HOT, "", 1, PrizeRank.NONE, true);
		add(StrategyType.RECENT_HOT, "", 1, PrizeRank.NONE, true);
		add(StrategyType.PAIR, "", 2, PrizeRank.NONE, true);
		add(StrategyType.CONTROL, "", 0, PrizeRank.NONE, true);

		// when
		StrategyPerformancesResponse response = service().performances();

		// then
		assertThat(response.strategies()).extracting(StrategyPerformanceResponse::strategy)
			.containsExactly(StrategyType.values());
		assertThat(row(response, StrategyType.RECENT_HOT).lineCount()).isEqualTo(2);
		assertThat(row(response, StrategyType.PAIR).lineCount()).isEqualTo(1);
		assertThat(row(response, StrategyType.CONTROL).lineCount()).isEqualTo(1);
		assertThat(row(response, StrategyType.LONG_COLD).lineCount()).isZero();
	}

	@Test
	void 근거_조합_줄도_따로_집계한다() {
		// given
		add(StrategyType.COMBINED, "", 3, PrizeRank.FIFTH, true);
		add(StrategyType.COMBINED, "", 1, PrizeRank.NONE, true);

		// when
		StrategyPerformanceResponse row = row(service().performances(), StrategyType.COMBINED);

		// then
		assertThat(row.lineCount()).isEqualTo(2);
		assertThat(row.rankCounts().get(PrizeRank.FIFTH)).isEqualTo(1);
	}

	@Test
	void 평균_일치와_5등_이상_비율을_계산한다() {
		// given: 일치 0,1,3,4 -> 평균 2.0, 5등 이상 2/4
		add(StrategyType.ALL_TIME, "", 0, PrizeRank.NONE, true);
		add(StrategyType.ALL_TIME, "", 1, PrizeRank.NONE, true);
		add(StrategyType.ALL_TIME, "", 3, PrizeRank.FIFTH, true);
		add(StrategyType.ALL_TIME, "", 4, PrizeRank.FOURTH, true);

		// when
		StrategyPerformanceResponse row = row(service().performances(), StrategyType.ALL_TIME);

		// then
		assertThat(row.averageMatches()).isCloseTo(2.0, within(1e-9));
		assertThat(row.atLeastFifthRate()).isCloseTo(0.5, within(1e-9));
		assertThat(row.rankCounts().get(PrizeRank.FIFTH)).isEqualTo(1);
		assertThat(row.rankCounts().get(PrizeRank.FOURTH)).isEqualTo(1);
		assertThat(row.rankCounts().get(PrizeRank.NONE)).isEqualTo(2);
		assertThat(row.rankCounts().get(PrizeRank.FIRST)).isZero();
	}

	@Test
	void 이론값은_194130_나누기_8145060이다() {
		// given
		add(StrategyType.PAIR, "", 0, PrizeRank.NONE, true);

		// when
		double theory = service().performances().theoryAtLeastFifthRate();

		// then
		assertThat(theory).isCloseTo(0.023834, within(0.000001));
	}

	@Test
	void 이론_비교는_설정_없는_줄만_쓴다() {
		// given: 설정 없는 줄 2(5등 1), 설정 있는 줄 2(5등 2)
		add(StrategyType.BALANCED, "", 3, PrizeRank.FIFTH, true);
		add(StrategyType.BALANCED, "", 0, PrizeRank.NONE, true);
		add(StrategyType.BALANCED, "7", 3, PrizeRank.FIFTH, true);
		add(StrategyType.BALANCED, "7", 4, PrizeRank.FOURTH, true);

		// when
		StrategyPerformanceResponse row = row(service().performances(), StrategyType.BALANCED);

		// then
		assertThat(row.lineCount()).isEqualTo(4);
		assertThat(row.atLeastFifthRate()).isCloseTo(0.75, within(1e-9));
		assertThat(row.noSettingsLineCount()).isEqualTo(2);
		assertThat(row.noSettingsAtLeastFifthRate()).isCloseTo(0.5, within(1e-9));
	}

	@Test
	void 천줄_미만이면_표본이_부족하다() {
		// given: 전략 999줄, 대조군 1000줄
		for (int i = 0; i < 999; i++) {
			add(StrategyType.RECENT_HOT, "", 0, PrizeRank.NONE, true);
		}
		for (int i = 0; i < 1000; i++) {
			add(StrategyType.CONTROL, "", 0, PrizeRank.NONE, true);
		}

		// when
		StrategyPerformancesResponse response = service().performances();

		// then
		assertThat(row(response, StrategyType.RECENT_HOT).sampleSufficient()).isFalse();
		assertThat(row(response, StrategyType.CONTROL).sampleSufficient()).isTrue();
	}

	@Test
	void 결과가_없는_줄은_세지_않는다() {
		// given: 미추첨 회차의 줄은 결과가 없다
		add(StrategyType.PAIR, "", 3, PrizeRank.FIFTH, true);
		add(StrategyType.PAIR, "", 0, PrizeRank.NONE, false);

		// when
		StrategyPerformanceResponse row = row(service().performances(), StrategyType.PAIR);

		// then
		assertThat(row.lineCount()).isEqualTo(1);
		assertThat(row.atLeastFifthRate()).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void 줄이_없으면_평균과_비율은_0이다() {
		// given

		// when
		StrategyPerformanceResponse row = row(service().performances(), StrategyType.PAIR);

		// then
		assertThat(row.lineCount()).isZero();
		assertThat(row.averageMatches()).isZero();
		assertThat(row.atLeastFifthRate()).isZero();
		assertThat(row.noSettingsAtLeastFifthRate()).isZero();
		assertThat(row.sampleSufficient()).isFalse();
	}

}
