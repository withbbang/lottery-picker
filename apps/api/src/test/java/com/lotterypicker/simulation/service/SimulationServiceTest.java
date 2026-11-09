package com.lotterypicker.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.engine.DrawHistory;
import com.lotterypicker.recommendation.engine.HistoricalDraw;
import com.lotterypicker.recommendation.engine.PopularityFilter;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.simulation.SimulationReport;
import com.lotterypicker.simulation.StrategySimulation;
import com.lotterypicker.simulation.entity.SimulationResult;
import com.lotterypicker.simulation.repository.SimulationResultRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SimulationServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final long SEED = 42L;

	private final LottoRoundRepository rounds = mock(LottoRoundRepository.class);
	private final SimulationResultRepository results = mock(SimulationResultRepository.class);
	private final Clock clock = Clock.fixed(LocalDateTime.parse("2026-11-09T10:00").atZone(SEOUL).toInstant(), SEOUL);
	private final SimulationService service = new SimulationService(rounds, results, clock);

	@Test
	void 시작_회차_이전_데이터만_쓴다() {
		// given: 105회 결과는 그대로 두고 그 뒤 회차 번호만 바꾼다
		DrawHistory original = history(SEED, 110);
		DrawHistory changedFuture = withRoundsAfter(original, 105, 7L);

		for (StrategyType strategy : StrategyType.values()) {
			// when
			List<LottoNumbers> before = SimulationService.linesFor(original, 105, strategy, 50, SEED);
			List<LottoNumbers> after = SimulationService.linesFor(changedFuture, 105, strategy, 50, SEED);

			// then
			assertThat(after).as(strategy.name()).isEqualTo(before);
		}
	}

	@Test
	void 회차_자체의_당첨번호도_추출에_쓰지_않는다() {
		// given: 105회 당첨번호를 바꾼다
		DrawHistory original = history(SEED, 105);
		DrawHistory changedTarget = withRoundsAfter(original, 104, 7L);

		for (StrategyType strategy : StrategyType.values()) {
			// when
			List<LottoNumbers> before = SimulationService.linesFor(original, 105, strategy, 50, SEED);
			List<LottoNumbers> after = SimulationService.linesFor(changedTarget, 105, strategy, 50, SEED);

			// then
			assertThat(after).as(strategy.name()).isEqualTo(before);
		}
	}

	@Test
	void 회차_전략별_시드는_정해진_식을_따른다() {
		// when
		long seed = SimulationService.seedFor(SEED, 105, StrategyType.PAIR);

		// then
		assertThat(seed).isEqualTo(SEED ^ (105 * 1_000_003L) ^ StrategyType.PAIR.ordinal());
	}

	@Test
	void 한_회차의_전략별_시드는_섞어서_상위_비트까지_다르다() {
		// given: 섞기 전 시드는 전략 순번만큼 하위 3비트만 다르다
		List<Long> mixed = new ArrayList<>();
		for (StrategyType strategy : StrategyType.values()) {
			mixed.add(SimulationService.mixedSeedFor(SEED, 105, strategy));
		}

		// then: 상위 32비트가 모두 다르고, 어느 두 시드도 16비트 이상 다르다
		assertThat(mixed.stream().map(seed -> seed >>> 32).distinct()).hasSize(StrategyType.values().length);
		for (int i = 0; i < mixed.size(); i++) {
			for (int j = i + 1; j < mixed.size(); j++) {
				assertThat(Long.bitCount(mixed.get(i) ^ mixed.get(j))).isGreaterThanOrEqualTo(16);
			}
		}
	}

	@Test
	void 섞은_시드는_정해진_식의_값을_SplitMix64로_섞은_값이다() {
		// given
		long z = SimulationService.seedFor(SEED, 105, StrategyType.CONTROL);
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		z = z ^ (z >>> 31);

		// when / then
		assertThat(SimulationService.mixedSeedFor(SEED, 105, StrategyType.CONTROL)).isEqualTo(z);
	}

	@Test
	void _101회부터_시작한다() {
		// given
		givenStoredRounds(history(SEED, 103));

		// when
		service.refresh();

		// then: 기본 시작 회차는 101회, 100회 이하는 집계하지 않는다
		SimulationResult saved = savedResult();
		assertThat(SimulationService.START_ROUND).isEqualTo(101);
		assertThat(saved.getFromRound()).isEqualTo(101);
		assertThat(saved.getToRound()).isEqualTo(103);
		assertThat(saved.getStrategies())
			.allSatisfy(s -> assertThat(s.lineCount()).isEqualTo(3L * SimulationService.REPETITIONS));
	}

	@Test
	void 전략당_회차별_N회_추출한다() {
		// given
		DrawHistory history = history(SEED, 103);

		// when
		SimulationReport report = service.run(history, 101, 200, SEED);

		// then
		assertThat(report.repetitions()).isEqualTo(200);
		assertThat(report.strategies()).extracting(StrategySimulation::strategy)
			.containsExactly(StrategyType.values());
		assertThat(report.strategies()).allSatisfy(s -> {
			assertThat(s.lineCount()).isEqualTo(600);
			assertThat(s.rankCounts().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(600);
		});
	}

	@Test
	void 집계는_일치_개수와_등수를_센다() {
		// given
		DrawHistory history = history(SEED, 102);

		// when
		SimulationReport report = service.run(history, 101, 100, SEED);

		// then: 같은 시드의 줄을 직접 채점한 값과 같다
		HistoricalDraw target = history.draws().get(history.draws().size() - 2);
		HistoricalDraw last = history.draws().get(history.draws().size() - 1);
		StrategySimulation control = report.strategies().stream()
			.filter(s -> s.strategy() == StrategyType.CONTROL)
			.findFirst()
			.orElseThrow();
		long matches = 0;
		long hits = 0;
		for (HistoricalDraw draw : List.of(target, last)) {
			for (LottoNumbers line : SimulationService.linesFor(history, draw.round(), StrategyType.CONTROL, 100, SEED)) {
				matches += line.countMatches(draw.numbers());
				if (PrizeRank.of(line, draw.numbers(), draw.bonus()) != PrizeRank.NONE) {
					hits++;
				}
			}
		}
		assertThat(control.averageMatches()).isEqualTo(matches / 200.0);
		assertThat(control.atLeastFifthRate()).isEqualTo(hits / 200.0);
	}

	@Test
	void 시드가_같으면_결과가_같다() {
		// given
		DrawHistory history = history(SEED, 104);

		// when
		SimulationReport first = service.run(history, 101, 50, SEED);
		SimulationReport second = service.run(history, 101, 50, SEED);

		// then
		assertThat(second).isEqualTo(first);
	}

	@Test
	void 인기_필터와_중복_제약을_적용하지_않는다() {
		// given
		DrawHistory history = history(SEED, 101);
		Set<LottoNumbers> winners = history.before(101).draws().stream()
			.map(HistoricalDraw::numbers)
			.collect(Collectors.toSet());

		for (StrategyType strategy : StrategyType.values()) {
			// when
			List<LottoNumbers> lines = SimulationService.linesFor(history, 101, strategy, 200, SEED);

			// then: 인기 조합도 다시 뽑지 않고 그대로 센다. 중복 제약이 없으니 같은 줄이 겹쳐도 된다
			assertThat(lines).as(strategy.name()).hasSize(200);
			assertThat(lines).as(strategy.name()).anyMatch(line -> PopularityFilter.isPopular(line, winners));
		}
	}

	@Test
	void 최신_결과만_저장한다() {
		// given
		givenStoredRounds(history(SEED, 102));

		// when
		service.refresh();
		service.refresh();

		// then: 단일 행을 덮어쓴다
		ArgumentCaptor<SimulationResult> captor = ArgumentCaptor.forClass(SimulationResult.class);
		verify(results, times(2)).save(captor.capture());
		assertThat(captor.getAllValues()).extracting(SimulationResult::getId)
			.containsOnly(SimulationResult.SINGLE_ROW_ID);
		assertThat(captor.getAllValues().get(1).getComputedAt()).isEqualTo(LocalDateTime.parse("2026-11-09T10:00"));
	}

	@Test
	void 시작_회차까지_데이터가_없으면_저장하지_않는다() {
		// given
		givenStoredRounds(history(SEED, 100));

		// when
		service.refresh();

		// then
		verify(rounds, never()).findAllByOrderByRoundNoAsc();
		verify(results, never()).save(any());
	}

	@Test
	void 결과가_없으면_기동_시_계산한다() {
		// given
		givenStoredRounds(history(SEED, 101));

		// when
		service.refresh();

		// then
		verify(results).save(any());
	}

	@Test
	void 저장된_결과가_최신_회차보다_뒤처져_있으면_다시_계산한다() {
		// given: 수집이 1249회를 저장한 뒤 실패해 재계산이 빠졌던 상태
		givenStoredRounds(history(SEED, 103));
		givenSavedUpTo(102);

		// when
		service.refresh();

		// then
		assertThat(savedResult().getToRound()).isEqualTo(103);
	}

	@Test
	void 저장된_결과가_최신_회차까지면_다시_계산하지_않는다() {
		// given
		givenStoredRounds(history(SEED, 103));
		givenSavedUpTo(103);

		// when
		service.refresh();

		// then: 최신 회차와 결과 회차만 확인하고 계산하지 않는다
		verify(rounds, never()).findAllByOrderByRoundNoAsc();
		verify(results, never()).save(any());
	}

	@Test
	void 회차가_없으면_계산하지_않는다() {
		// when
		service.refresh();

		// then
		verify(rounds, never()).findAllByOrderByRoundNoAsc();
		verify(results, never()).save(any());
	}

	@Test
	void 계산_중_요청은_동시에_돌리지_않고_끝난_뒤_한_번만_더_계산한다() throws Exception {
		// given: 첫 계산이 회차를 읽는 중에 멈춘다
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger concurrent = new AtomicInteger();
		AtomicInteger maxConcurrent = new AtomicInteger();
		List<LottoRound> stored = toRounds(history(SEED, 101));
		when(rounds.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(stored.get(stored.size() - 1)));
		AtomicInteger calls = new AtomicInteger();
		when(rounds.findAllByOrderByRoundNoAsc()).thenAnswer(invocation -> {
			int now = concurrent.incrementAndGet();
			maxConcurrent.accumulateAndGet(now, Math::max);
			if (calls.incrementAndGet() == 1) {
				started.countDown();
				release.await(5, TimeUnit.SECONDS);
			}
			concurrent.decrementAndGet();
			return stored;
		});
		ExecutorService executor = Executors.newSingleThreadExecutor();

		// when: 계산 중에 두 번 더 요청한다
		executor.submit(service::refresh);
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		service.refresh();
		service.refresh();
		release.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		// then: 겹쳐 돌지 않고, 밀린 요청은 한 번으로 합쳐 다시 계산한다
		assertThat(maxConcurrent.get()).isEqualTo(1);
		assertThat(calls.get()).isEqualTo(2);
		verify(results, times(2)).save(any());
	}

	@Test
	void 저장한_결과를_응답으로_읽는다() {
		// given
		givenStoredRounds(history(SEED, 101));
		service.refresh();
		SimulationResult saved = savedResult();
		when(results.findById(SimulationResult.SINGLE_ROW_ID)).thenReturn(Optional.of(saved));

		// when
		var response = service.findLatest().orElseThrow();

		// then
		assertThat(response.fromRound()).isEqualTo(101);
		assertThat(response.toRound()).isEqualTo(101);
		assertThat(response.repetitions()).isEqualTo(SimulationService.REPETITIONS);
		assertThat(response.computedAt().toString()).isEqualTo("2026-11-09T10:00+09:00");
		assertThat(response.theoryAtLeastFifthRate()).isEqualTo(194_130d / 8_145_060d);
		assertThat(response.scopeNote())
			.isEqualTo("전략의 번호 추출 규칙만 평가합니다. 고정·제외 번호와 인기 조합·중복 제약은 적용하지 않습니다.");
		assertThat(response.strategies()).hasSize(StrategyType.values().length);
	}

	@Test
	void 결과가_없으면_빈_값이다() {
		// given
		when(results.findById(SimulationResult.SINGLE_ROW_ID)).thenReturn(Optional.empty());

		// when / then
		assertThat(service.findLatest()).isEmpty();
	}

	private void givenStoredRounds(DrawHistory history) {
		List<LottoRound> stored = toRounds(history);
		when(rounds.findAllByOrderByRoundNoAsc()).thenReturn(stored);
		when(rounds.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(stored.get(stored.size() - 1)));
	}

	private void givenSavedUpTo(int toRound) {
		SimulationReport report = new SimulationService(rounds, results, clock).run(history(SEED, toRound), toRound, 1,
				SEED);
		when(results.findById(SimulationResult.SINGLE_ROW_ID))
			.thenReturn(Optional.of(SimulationResult.latest(report, LocalDateTime.parse("2026-11-01T10:00"))));
	}

	private SimulationResult savedResult() {
		ArgumentCaptor<SimulationResult> captor = ArgumentCaptor.forClass(SimulationResult.class);
		verify(results).save(captor.capture());
		return captor.getValue();
	}

	private static List<LottoRound> toRounds(DrawHistory history) {
		LocalDate first = LocalDate.of(2002, 12, 7);
		return history.draws().stream()
			.map(draw -> LottoRound.builder()
				.roundNo(draw.round())
				.numbers(draw.numbers())
				.bonus(draw.bonus())
				.drawDate(first.plusWeeks(draw.round() - 1))
				.build())
			.toList();
	}

	/** 결정적 합성 회차. 보너스는 본번호와 겹치지 않는다. */
	static DrawHistory history(long seed, int count) {
		Random random = new Random(seed);
		List<HistoricalDraw> draws = new ArrayList<>();
		for (int round = 1; round <= count; round++) {
			draws.add(randomDraw(round, random));
		}
		return new DrawHistory(draws);
	}

	private static DrawHistory withRoundsAfter(DrawHistory history, int round, long seed) {
		Random random = new Random(seed);
		return new DrawHistory(history.draws().stream()
			.map(draw -> draw.round() > round ? randomDraw(draw.round(), random) : draw)
			.toList());
	}

	private static HistoricalDraw randomDraw(int round, Random random) {
		List<Integer> pool = new ArrayList<>(IntStream.rangeClosed(1, 45).boxed().toList());
		Collections.shuffle(pool, random);
		List<Integer> picked = pool.subList(0, 6);
		return new HistoricalDraw(round, new LottoNumbers(List.copyOf(picked)), pool.get(6));
	}

}
