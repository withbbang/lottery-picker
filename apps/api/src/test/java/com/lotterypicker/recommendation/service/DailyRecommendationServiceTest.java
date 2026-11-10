package com.lotterypicker.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.engine.DrawHistory;
import com.lotterypicker.recommendation.engine.HistoricalDraw;
import com.lotterypicker.recommendation.engine.RecommendationConstraint;
import com.lotterypicker.recommendation.engine.RecommendationEngine;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import com.lotterypicker.recommendation.repository.DailyRecommendationRepository;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class DailyRecommendationServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), SEOUL);
	private static final LocalDate DATE = LocalDate.of(2026, 11, 6);
	private static final LocalDate LATEST_DRAW = LocalDate.of(2026, 10, 31);
	private static final int LATEST_ROUND = 1248;
	private static final int ROUNDS = 60;

	private RecommendationEngine engine;
	private RecommendationSettingsService settings;
	private LottoRoundRepository rounds;
	private DailyRecommendationRepository repository;
	private DailyRecommendationService service;
	private final NotificationService notifications = mock(NotificationService.class);
	private final List<DailyRecommendation> db = new ArrayList<>();
	private final RecordingTransactions transactions = new RecordingTransactions();
	private TimeZone originalZone;

	@BeforeEach
	void setUp() {
		originalZone = TimeZone.getDefault();
		engine = spy(new RecommendationEngine());
		settings = mock(RecommendationSettingsService.class);
		rounds = mock(LottoRoundRepository.class);
		repository = mock(DailyRecommendationRepository.class);
		when(settings.currentConstraint()).thenReturn(RecommendationConstraint.NONE);
		when(rounds.findAllByOrderByRoundNoAsc()).thenReturn(storedRounds(LATEST_ROUND, LATEST_DRAW, ROUNDS));
		when(repository.existsByDate(any())).thenAnswer(inv -> db.stream()
			.anyMatch(line -> line.getDate().equals(inv.getArgument(0))));
		when(repository.findByDateOrderByLineIndexAsc(any())).thenAnswer(inv -> db.stream()
			.filter(line -> line.getDate().equals(inv.getArgument(0)))
			.sorted(Comparator.comparingInt(DailyRecommendation::getLineIndex))
			.toList());
		when(repository.saveAll(anyIterable())).thenAnswer(inv -> {
			Iterable<DailyRecommendation> lines = inv.getArgument(0);
			lines.forEach(db::add);
			return db;
		});
		service = serviceAt(CLOCK);
	}

	@AfterEach
	void restoreTimeZone() {
		TimeZone.setDefault(originalZone);
	}

	private DailyRecommendationService serviceAt(Clock clock) {
		return new DailyRecommendationService(engine, settings, rounds, repository, new Random(42), clock,
			transactions, notifications);
	}

	/** 추첨일이 7일 간격인 회차들. 오름차순으로 돌려준다. */
	private static List<LottoRound> storedRounds(int latestRound, LocalDate latestDraw, int count) {
		Random random = new Random(7);
		return IntStream.range(0, count).map(i -> count - 1 - i).mapToObj(back -> {
			List<Integer> pool = new ArrayList<>(IntStream.rangeClosed(1, 45).boxed().toList());
			Collections.shuffle(pool, random);
			List<Integer> picked = pool.subList(0, 7);
			return LottoRound.builder()
				.roundNo(latestRound - back)
				.numbers(new LottoNumbers(picked.subList(0, 6)))
				.bonus(picked.get(6))
				.drawDate(latestDraw.minusWeeks(back))
				.source("https://www.dhlottery.co.kr")
				.fetchedAt(LocalDateTime.of(2026, 11, 1, 9, 0))
				.build();
		}).toList();
	}

	@Test
	void 추천과_대조군_10줄을_저장한다() {
		// when
		Optional<RecommendationSet> result = service.generateIfAbsent(DATE);

		// then: 근거 조합 줄은 따로 센다
		assertThat(db).filteredOn(line -> line.getStrategy() != StrategyType.COMBINED).hasSize(10);
		assertThat(db).filteredOn(DailyRecommendation::isControl)
			.hasSize(5)
			.allMatch(line -> line.getStrategy() == StrategyType.CONTROL);
		assertThat(db.stream().filter(line -> !line.isControl() && line.getStrategy() != StrategyType.COMBINED)
			.sorted(Comparator.comparingInt(DailyRecommendation::getLineIndex))
			.map(DailyRecommendation::getStrategy)
			.toList())
			.containsExactly(StrategyType.RECENT_HOT, StrategyType.LONG_COLD, StrategyType.ALL_TIME, StrategyType.PAIR,
					StrategyType.BALANCED);
		assertThat(db).allMatch(line -> line.getDate().equals(DATE) && line.getRoundNo() == 1249);
		assertThat(result).isPresent();
		assertThat(result.get().date()).isEqualTo(DATE);
		assertThat(result.get().round()).isEqualTo(1249);
		assertThat(result.get().lines()).filteredOn(line -> line.strategy() != StrategyType.COMBINED)
			.hasSize(5)
			.extracting(StoredLine::strategy)
			.doesNotContain(StrategyType.CONTROL);
	}

	@Test
	void 통계_지표가_있는_전략_줄은_근거_문구의_번호를_근거_번호로_저장한다() {
		// when
		service.generateIfAbsent(DATE);

		// then: 근거 문구 "번호 N: …"의 N과 같은 번호를 같은 순서로
		List<StrategyType> withMetric = List.of(StrategyType.RECENT_HOT, StrategyType.LONG_COLD, StrategyType.ALL_TIME);
		assertThat(db).filteredOn(line -> withMetric.contains(line.getStrategy())).hasSize(3).allSatisfy(line -> {
			List<Integer> fromText = line.getEvidence().stream()
				.filter(text -> text.startsWith("번호 "))
				.map(text -> Integer.parseInt(text.substring(3, text.indexOf(':'))))
				.toList();
			assertThat(line.getEvidenceNumbers()).isNotEmpty().isEqualTo(fromText);
		});
	}

	@Test
	void 통계_지표가_없는_줄은_근거_번호가_비어_있다() {
		// when
		service.generateIfAbsent(DATE);

		// then
		List<StrategyType> withMetric = List.of(StrategyType.RECENT_HOT, StrategyType.LONG_COLD, StrategyType.ALL_TIME);
		assertThat(db).filteredOn(line -> !withMetric.contains(line.getStrategy()))
			.isNotEmpty()
			.allSatisfy(line -> assertThat(line.getEvidenceNumbers()).isEmpty());
	}

	@Test
	void 근거_조합_줄은_line_index_5부터_저장한다() {
		// when
		Optional<RecommendationSet> result = service.generateIfAbsent(DATE);

		// then
		List<DailyRecommendation> combined = db.stream()
			.filter(line -> line.getStrategy() == StrategyType.COMBINED)
			.sorted(Comparator.comparingInt(DailyRecommendation::getLineIndex))
			.toList();
		assertThat(combined).isNotEmpty()
			.allSatisfy(line -> assertThat(line.isControl()).isFalse())
			.allSatisfy(line -> assertThat(line.getRelaxation()).isEqualTo(Relaxation.NONE));
		assertThat(combined).extracting(DailyRecommendation::getLineIndex)
			.isEqualTo(IntStream.range(5, 5 + combined.size()).boxed().toList());
		assertThat(result.orElseThrow().lines()).filteredOn(line -> line.strategy() == StrategyType.COMBINED)
			.hasSize(combined.size());
	}

	@Test
	void 같은_날_두번_호출해도_한_벌만_만든다() {
		// given
		service.generateIfAbsent(DATE);

		int firstSize = db.size();

		// when
		Optional<RecommendationSet> second = service.generateIfAbsent(DATE);

		// then
		assertThat(second).isEmpty();
		assertThat(db).hasSize(firstSize);
	}

	@Test
	void 동시_생성으로_유니크_위반이_나면_빈_값이다() {
		// given: 확인 시점에는 없었지만 저장 시점에는 다른 쪽이 먼저 저장했다
		when(repository.existsByDate(DATE)).thenReturn(false, true);
		when(repository.saveAll(anyIterable())).thenThrow(new DataIntegrityViolationException("uk"));

		// when
		Optional<RecommendationSet> result = service.generateIfAbsent(DATE);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void 유니크_키_순서로_저장한다() {
		// when: 뒤의 줄이 앞선 줄보다 작은 키면 MySQL에서 동시 생성이 유니크 위반 대신 데드락으로 끝난다
		service.generateIfAbsent(DATE);

		// then
		assertThat(db).isSortedAccordingTo(Comparator
			.comparing((DailyRecommendation line) -> line.getStrategy().name())
			.thenComparingInt(DailyRecommendation::getLineIndex));
	}

	@Test
	void 그날_추천이_없는데_무결성_위반이_나면_숨기지_않는다() {
		// given: 동시 생성이 아닌 저장 실패다
		when(repository.existsByDate(DATE)).thenReturn(false);
		when(repository.saveAll(anyIterable())).thenThrow(new DataIntegrityViolationException("too long"));

		// when, then
		assertThatThrownBy(() -> service.generateIfAbsent(DATE)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 잠금_실패여도_다른_쪽이_저장했으면_빈_값이다() {
		// given: 데드락 희생자가 됐지만 다른 쪽이 한 벌을 저장했다
		when(repository.existsByDate(DATE)).thenReturn(false, true);
		when(repository.saveAll(anyIterable())).thenThrow(new CannotAcquireLockException("deadlock"));

		// when
		Optional<RecommendationSet> result = service.generateIfAbsent(DATE);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void 잠금_실패인데_그날_추천이_없으면_숨기지_않는다() {
		// given
		when(repository.existsByDate(DATE)).thenReturn(false);
		when(repository.saveAll(anyIterable())).thenThrow(new CannotAcquireLockException("lock timeout"));

		// when, then
		assertThatThrownBy(() -> service.generateIfAbsent(DATE)).isInstanceOf(CannotAcquireLockException.class);
	}

	@Test
	void 회차가_없으면_RECOMMENDATION_NO_HISTORY이다() {
		// given
		when(rounds.findAllByOrderByRoundNoAsc()).thenReturn(List.of());

		// when, then
		assertThatThrownBy(() -> service.generateIfAbsent(DATE))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_NO_HISTORY));
		assertThat(db).isEmpty();
	}

	@Test
	void 설정과_입력_회차_범위를_함께_저장한다() {
		// given
		when(settings.currentConstraint()).thenReturn(RecommendationConstraint.of(Set.of(7, 3), Set.of(45, 1)));

		// when
		service.generateIfAbsent(DATE);

		// then
		assertThat(db).filteredOn(line -> line.getStrategy() != StrategyType.COMBINED).hasSize(10);
		assertThat(db).allSatisfy(line -> {
			assertThat(line.getFixedSnapshot()).isEqualTo("3,7");
			assertThat(line.getExcludedSnapshot()).isEqualTo("1,45");
			assertThat(line.getHistoryFromRound()).isEqualTo(LATEST_ROUND - ROUNDS + 1);
			assertThat(line.getHistoryToRound()).isEqualTo(LATEST_ROUND);
			assertThat(line.hasSettings()).isTrue();
			assertThat(line.numbers().contains(3)).isTrue();
			assertThat(line.numbers().contains(7)).isTrue();
			assertThat(line.numbers().contains(45)).isFalse();
		});
	}

	@Test
	void 설정이_없으면_hasSettings는_false이다() {
		// when
		service.generateIfAbsent(DATE);

		// then
		assertThat(db).noneMatch(DailyRecommendation::hasSettings);
	}

	@Test
	void 통계는_저장된_회차만_쓴다() {
		// given: 직전 회차 수집이 실패해 1247까지만 있다
		when(rounds.findAllByOrderByRoundNoAsc())
			.thenReturn(storedRounds(1247, LocalDate.of(2026, 10, 24), ROUNDS));

		// when
		service.generateIfAbsent(DATE);

		// then: 회차 번호는 달력으로 1249, 통계 입력은 저장된 1247까지다
		ArgumentCaptor<DrawHistory> history = ArgumentCaptor.forClass(DrawHistory.class);
		verify(engine).generate(history.capture(), any(), any());
		assertThat(history.getValue().draws()).extracting(HistoricalDraw::round)
			.hasSize(ROUNDS)
			.endsWith(1247);
		assertThat(db).allMatch(line -> line.getRoundNo() == 1249 && line.getHistoryToRound() == 1247);
	}

	@Test
	void 설정을_바꿔도_지난_추천은_바뀌지_않는다() {
		// given
		service.generateIfAbsent(DATE);
		List<List<Integer>> before = db.stream().map(line -> line.numbers().values()).toList();

		// when
		when(settings.currentConstraint()).thenReturn(RecommendationConstraint.of(Set.of(1), Set.of(2)));

		// then
		var stored = service.find(DATE).orElseThrow();
		assertThat(db.stream().map(line -> line.numbers().values()).toList()).isEqualTo(before);
		assertThat(db).noneMatch(DailyRecommendation::hasSettings);
		assertThat(stored.lines()).filteredOn(line -> line.strategy() != StrategyType.COMBINED).hasSize(5);
	}

	@Test
	void 서버_시간대가_UTC여도_한국_날짜로_저장한다() {
		// given
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		DailyRecommendationService utcServer = serviceAt(
				Clock.fixed(OffsetDateTime.parse("2026-11-06T17:00:00+09:00").toInstant(), SEOUL));

		// when
		utcServer.generateIfAbsent(utcServer.today());

		// then
		assertThat(db).isNotEmpty().allSatisfy(line -> {
			assertThat(line.getDate()).isEqualTo(LocalDate.of(2026, 11, 6));
			assertThat(line.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 17, 0));
		});
	}

	@Test
	void 서버_시간대가_UTC여도_자정_넘긴_한국_날짜를_오늘로_본다() {
		// given: 한국은 11-07 00:30, UTC는 아직 11-06이다
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		DailyRecommendationService utcServer = serviceAt(Clock.fixed(Instant.parse("2026-11-06T15:30:00Z"), SEOUL));

		// when
		LocalDate today = utcServer.today();

		// then
		assertThat(today).isEqualTo(LocalDate.of(2026, 11, 7));
	}

	@Test
	void 번호를_뽑는_동안에는_트랜잭션을_잡지_않고_저장만_트랜잭션_안에서_한다() {
		// given
		List<Boolean> inTransactionDuringGenerate = new ArrayList<>();
		doAnswer(inv -> {
			inTransactionDuringGenerate.add(transactions.active);
			return inv.callRealMethod();
		}).when(engine).generate(any(), any(), any());
		List<Boolean> inTransactionDuringSave = new ArrayList<>();
		when(repository.saveAll(anyIterable())).thenAnswer(inv -> {
			inTransactionDuringSave.add(transactions.active);
			return List.of();
		});

		// when
		service.generateIfAbsent(DATE);

		// then
		assertThat(inTransactionDuringGenerate).containsExactly(false);
		assertThat(inTransactionDuringSave).containsExactly(true);
	}

	@Test
	void 대조군은_조회_결과에_없다() {
		// given
		service.generateIfAbsent(DATE);

		// when
		var found = service.find(DATE).orElseThrow();

		// then
		assertThat(found.lines()).filteredOn(line -> line.strategy() != StrategyType.COMBINED).hasSize(5);
		assertThat(found.lines()).noneMatch(line -> line.strategy() == StrategyType.CONTROL);
		assertThat(found.round()).isEqualTo(1249);
	}

	@Test
	void 없는_날짜는_조회_결과가_비어_있다() {
		// when, then
		assertThat(service.find(DATE)).isEmpty();
	}

	@Test
	void 조회_결과에_그날_추천_알림의_상태를_담는다() {
		// given
		service.generateIfAbsent(DATE);
		when(notifications.statusOf(NotificationKind.DAILY_RECOMMENDATION, "2026-11-06"))
			.thenReturn(NotificationStatus.FAILED);

		// when
		var found = service.find(DATE).orElseThrow();

		// then
		assertThat(found.notificationStatus()).isEqualTo(NotificationStatus.FAILED);
	}

	@Test
	void 저장된_추천을_대조군_없이_다시_읽는다() {
		// given
		RecommendationSet created = service.generateIfAbsent(DATE).orElseThrow();

		// when
		RecommendationSet stored = service.findSet(DATE).orElseThrow();

		// then
		assertThat(stored).isEqualTo(created);
	}

	@Test
	void 같은_주_토요일까지는_마감이_아니다() {
		// given: 금요일 추천만 있고 토요일 추천은 아직 없다
		service.generateIfAbsent(DATE);
		LocalDate saturday = DATE.plusDays(1);
		when(repository.findFirstByDateLessThanEqualOrderByDateDesc(saturday))
			.thenReturn(Optional.of(db.get(0)));

		// when
		RecommendationSetResponse found = service.findLatest(saturday).orElseThrow();

		// then
		assertThat(found.date()).isEqualTo(DATE);
		assertThat(found.carriedOver()).isTrue();
		assertThat(found.roundClosed()).isFalse();
	}

	@Test
	void 토요일_판매_마감_뒤에는_금요일_추천도_마감이다() {
		// given: 토요일 17시 생성을 놓쳐 21시에도 금요일 추천이 가장 최근이다
		service.generateIfAbsent(DATE);
		LocalDate saturday = DATE.plusDays(1);
		when(repository.findFirstByDateLessThanEqualOrderByDateDesc(saturday))
			.thenReturn(Optional.of(db.get(0)));
		DailyRecommendationService at21 = serviceAt(
				Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), SEOUL));

		// when
		RecommendationSetResponse found = at21.findLatest(saturday).orElseThrow();

		// then
		assertThat(found.roundClosed()).isTrue();
		assertThat(found.todayPending()).isFalse();
	}

	@Test
	void 오후_5시_전_이월이면_오늘_추천을_기다리는_중이다() {
		// given
		service.generateIfAbsent(DATE);
		LocalDate saturday = DATE.plusDays(1);
		when(repository.findFirstByDateLessThanEqualOrderByDateDesc(saturday))
			.thenReturn(Optional.of(db.get(0)));
		DailyRecommendationService at9 = serviceAt(Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), SEOUL));

		// when
		RecommendationSetResponse found = at9.findLatest(saturday).orElseThrow();

		// then
		assertThat(found.todayPending()).isTrue();
		assertThat(found.roundClosed()).isFalse();
	}

	@Test
	void 오후_5시가_지나도_오늘_추천이_없으면_기다리는_중이_아니다() {
		// given: 평일 17시 생성이 실패했다
		service.generateIfAbsent(DATE);
		LocalDate monday = LocalDate.of(2026, 11, 9);
		when(repository.findFirstByDateLessThanEqualOrderByDateDesc(monday))
			.thenReturn(Optional.of(db.get(0)));
		DailyRecommendationService at18 = serviceAt(Clock.fixed(Instant.parse("2026-11-09T09:00:00Z"), SEOUL));

		// when
		RecommendationSetResponse found = at18.findLatest(monday).orElseThrow();

		// then
		assertThat(found.carriedOver()).isTrue();
		assertThat(found.todayPending()).isFalse();
	}

	@Test
	void 없는_날짜의_저장된_추천은_비어_있다() {
		// when, then
		assertThat(service.findSet(DATE)).isEmpty();
	}

	private static final class RecordingTransactions implements TransactionOperations {

		private boolean active;

		@Override
		public <T> T execute(TransactionCallback<T> action) {
			active = true;
			try {
				return action.doInTransaction(mock(TransactionStatus.class));
			} finally {
				active = false;
			}
		}

		@Override
		public void executeWithoutResult(Consumer<TransactionStatus> action) {
			execute(status -> {
				action.accept(status);
				return null;
			});
		}

	}

}
