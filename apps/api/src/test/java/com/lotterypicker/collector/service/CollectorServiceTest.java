package com.lotterypicker.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.collector.CollectorException;
import com.lotterypicker.collector.CollectorUnavailableException;
import com.lotterypicker.collector.DhLotteryClient;
import com.lotterypicker.collector.PrizeTier;
import com.lotterypicker.collector.RoundPayload;
import com.lotterypicker.collector.RoundPeriod;
import com.lotterypicker.collector.StorePayload;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.entity.WinningStore;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.round.repository.WinningStoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class CollectorServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), SEOUL);

	private DhLotteryClient client;
	private LottoRoundRepository roundRepository;
	private WinningStoreRepository storeRepository;
	private CollectorService service;
	private final TreeMap<Integer, LottoRound> db = new TreeMap<>();
	private final List<WinningStore> savedStores = new ArrayList<>();

	@BeforeEach
	void setUp() {
		client = mock(DhLotteryClient.class);
		roundRepository = mock(LottoRoundRepository.class);
		storeRepository = mock(WinningStoreRepository.class);
		TransactionOperations tx = new TransactionOperations() {
			@Override
			public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
				return action.doInTransaction(null);
			}
		};
		service = new CollectorService(client, roundRepository, storeRepository, tx, CLOCK);

		when(client.source()).thenReturn("https://www.dhlottery.co.kr");
		when(roundRepository.findAllRoundNos()).thenAnswer(i -> new ArrayList<>(db.keySet()));
		doAnswer(i -> {
			LottoRound r = i.getArgument(0);
			db.put(r.getRoundNo(), r);
			return r;
		}).when(roundRepository).save(any(LottoRound.class));
		when(roundRepository.findByStoreFetchedAtIsNullOrderByRoundNoAsc()).thenAnswer(i ->
			db.values().stream().filter(r -> r.getStoreFetchedAt() == null).toList());
		doAnswer(i -> {
			savedStores.addAll(i.getArgument(0));
			return i.getArgument(0);
		}).when(storeRepository).saveAll(any());
	}

	private static List<RoundPeriod> list(int last) {
		List<RoundPeriod> periods = new ArrayList<>();
		for (int r = 1; r <= last; r++) {
			periods.add(new RoundPeriod(r, LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 26), LocalDate.of(2026, 1, 1)));
		}
		return periods;
	}

	private static RoundPayload payload(int round, LocalDate drawDate) {
		return new RoundPayload(round, new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)), 7, drawDate,
			List.of(new PrizeTier(1, 15L, 1_933_658_450L), new PrizeTier(2, 91L, 53_122_485L),
				new PrizeTier(3, 3265L, 1_480_597L), new PrizeTier(4, 167_745L, 50_000L),
				new PrizeTier(5, 2_723_467L, 5_000L)));
	}

	private static RoundPayload payload(int round) {
		return payload(round, LocalDate.of(2026, 1, 1));
	}

	/** 요청 회차를 포함해 아래로 10개를 돌려주는 페이지. */
	private void stubPages() {
		when(client.fetchRoundPage(anyInt())).thenAnswer(i -> {
			int max = i.getArgument(0);
			List<RoundPayload> page = new ArrayList<>();
			for (int r = max; r > Math.max(0, max - 10); r--) {
				page.add(payload(r));
			}
			return page;
		});
	}

	private void seedRound(int round, LocalDate drawDate, LocalDateTime storeFetchedAt) {
		LottoRound entity = LottoRound.builder()
			.roundNo(round)
			.numbers(new LottoNumbers(List.of(1, 2, 3, 4, 5, 6)))
			.bonus(7)
			.drawDate(drawDate)
			.source("s")
			.fetchedAt(LocalDateTime.of(2026, 11, 6, 9, 0))
			.storeFetchedAt(storeFetchedAt)
			.build();
		db.put(round, entity);
	}

	private static StorePayload store(int rank, String name) {
		return new StorePayload(rank, "서울", name, "서울 어딘가", "자동", 37.5, 127.0, "ok");
	}

	@Test
	void 없는_회차만_저장한다() {
		// given
		for (int r = 1; r <= 5; r++) {
			seedRound(r, LocalDate.of(2026, 1, 1), LocalDateTime.of(2026, 1, 2, 0, 0));
		}
		when(client.fetchRoundList()).thenReturn(list(12));
		stubPages();

		// when
		List<Integer> saved = service.syncMissingRounds();

		// then
		assertThat(saved).containsExactly(6, 7, 8, 9, 10, 11, 12);
		assertThat(db.keySet()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
		assertThat(db.get(12).getRank1Count()).isEqualTo(15);
		assertThat(db.get(12).getRank5Amount()).isEqualTo(5_000L);
		assertThat(db.get(12).getFetchedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 12, 0));
	}

	@Test
	void 페이지에_다른_회차가_있어도_중복_저장하지_않는다() {
		// given
		seedRound(1, LocalDate.of(2026, 1, 1), LocalDateTime.of(2026, 1, 2, 0, 0));
		seedRound(2, LocalDate.of(2026, 1, 1), LocalDateTime.of(2026, 1, 2, 0, 0));
		when(client.fetchRoundList()).thenReturn(list(4));
		stubPages();

		// when
		List<Integer> saved = service.syncMissingRounds();

		// then
		assertThat(saved).containsExactly(3, 4);
		verify(roundRepository, times(2)).save(any(LottoRound.class));
		verify(client, times(1)).fetchRoundPage(anyInt());
	}

	@Test
	void 재실행하면_이어서_수집한다() {
		// given
		when(client.fetchRoundList()).thenReturn(list(25));
		when(client.fetchRoundPage(25)).thenAnswer(i -> {
			List<RoundPayload> page = new ArrayList<>();
			for (int r = 25; r > 15; r--) {
				page.add(payload(r));
			}
			return page;
		});
		when(client.fetchRoundPage(15)).thenThrow(new CollectorException("끊김"));

		// when
		assertThatThrownBy(() -> service.syncMissingRounds()).isInstanceOf(CollectorException.class);

		// then
		assertThat(db.keySet()).containsExactly(16, 17, 18, 19, 20, 21, 22, 23, 24, 25);

		// when 두 번째 실행
		doAnswer(i -> {
			List<RoundPayload> page = new ArrayList<>();
			for (int r = 15; r > 5; r--) {
				page.add(payload(r));
			}
			return page;
		}).when(client).fetchRoundPage(15);
		when(client.fetchRoundPage(5)).thenAnswer(i -> {
			List<RoundPayload> page = new ArrayList<>();
			for (int r = 5; r > 0; r--) {
				page.add(payload(r));
			}
			return page;
		});
		List<Integer> saved = service.syncMissingRounds();

		// then
		assertThat(saved).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
		verify(client, never()).fetchRoundPage(20);
	}

	@Test
	void 요청한_회차가_페이지에_없으면_CollectorException이다() {
		// given
		when(client.fetchRoundList()).thenReturn(list(3));
		when(client.fetchRoundPage(3)).thenReturn(List.of(payload(2), payload(1)));

		// when / then
		assertThatThrownBy(() -> service.syncMissingRounds())
			.isInstanceOf(CollectorException.class)
			.hasMessageContaining("3");
		assertThat(db).isEmpty();
	}

	@Test
	void 판매점이_있으면_저장하고_완료로_표시한다() {
		// given
		seedRound(1248, LocalDate.of(2026, 10, 31), null);
		when(client.fetchStores(1248)).thenReturn(List.of(store(1, "가"), store(2, "나")));

		// when
		service.syncStores();

		// then
		assertThat(savedStores).hasSize(2);
		assertThat(savedStores.get(0).getRound().getRoundNo()).isEqualTo(1248);
		assertThat(savedStores.get(0).getName()).isEqualTo("가");
		assertThat(db.get(1248).getStoreFetchedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 12, 0));
	}

	@Test
	void 추첨후_7일_이내_빈_결과는_재시도_대상으로_남긴다() {
		// given
		seedRound(1250, LocalDate.of(2026, 10, 31), null);
		when(client.fetchStores(1250)).thenReturn(List.of());

		// when
		service.syncStores();

		// then
		assertThat(db.get(1250).getStoreFetchedAt()).isNull();
		assertThat(savedStores).isEmpty();
	}

	@Test
	void 추첨후_7일이_넘은_빈_결과는_완료로_표시한다() {
		// given
		seedRound(1, LocalDate.of(2026, 10, 30), null);
		seedRound(2, LocalDate.of(2026, 10, 29), null);
		when(client.fetchStores(anyInt())).thenReturn(List.of());

		// when
		service.syncStores();

		// then
		assertThat(db.get(1).getStoreFetchedAt()).isNull();
		assertThat(db.get(2).getStoreFetchedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 12, 0));
	}

	@Test
	void 한_회차_판매점이_실패해도_이후_회차를_계속_수집하고_끝에_예외를_던진다() {
		// given
		seedRound(1, LocalDate.of(2026, 1, 1), null);
		seedRound(2, LocalDate.of(2026, 1, 1), null);
		seedRound(3, LocalDate.of(2026, 1, 1), null);
		when(client.fetchStores(1)).thenReturn(List.of(store(1, "가")));
		when(client.fetchStores(2)).thenThrow(new CollectorException("문자열 필드가 없거나 비어 있습니다: shpNm"));
		when(client.fetchStores(3)).thenReturn(List.of(store(1, "다")));

		// when, then
		assertThatThrownBy(service::syncStores)
			.isInstanceOf(CollectorException.class)
			.hasMessageContaining("2");
		assertThat(db.get(1).getStoreFetchedAt()).isNotNull();
		assertThat(db.get(2).getStoreFetchedAt()).isNull();
		assertThat(db.get(3).getStoreFetchedAt()).isNotNull();
		assertThat(savedStores).extracting(WinningStore::getName).containsExactly("가", "다");
	}

	@Test
	void 연속_3회_요청_자체가_실패하면_남은_회차를_건너뛰고_예외를_던진다() {
		// given
		for (int r = 1; r <= 6; r++) {
			seedRound(r, LocalDate.of(2026, 1, 1), null);
		}
		when(client.fetchStores(anyInt())).thenAnswer(i -> {
			if ((int) i.getArgument(0) == 1) {
				return List.of(store(1, "가"));
			}
			throw new CollectorUnavailableException("동행복권 요청에 실패했습니다", new RuntimeException());
		});

		// when, then
		assertThatThrownBy(service::syncStores).isInstanceOf(CollectorException.class);
		verify(client, times(4)).fetchStores(anyInt());
		verify(client, never()).fetchStores(5);
		assertThat(db.get(1).getStoreFetchedAt()).isNotNull();
	}

	@Test
	void 요청_실패_사이에_성공이_있으면_연속_횟수를_세지_않는다() {
		// given
		for (int r = 1; r <= 6; r++) {
			seedRound(r, LocalDate.of(2026, 1, 1), null);
		}
		when(client.fetchStores(anyInt())).thenAnswer(i -> {
			if ((int) i.getArgument(0) % 2 == 0) {
				return List.of(store(1, "가"));
			}
			throw new CollectorUnavailableException("동행복권 요청에 실패했습니다", new RuntimeException());
		});

		// when, then
		assertThatThrownBy(service::syncStores).isInstanceOf(CollectorException.class);
		verify(client, times(6)).fetchStores(anyInt());
	}

	@Test
	void 이미_완료된_회차는_다시_요청하지_않는다() {
		// given
		seedRound(1, LocalDate.of(2026, 1, 1), LocalDateTime.of(2026, 1, 2, 0, 0));

		// when
		service.syncStores();

		// then
		verify(client, never()).fetchStores(anyInt());
	}

	@Test
	void 동시_실행이면_두번째는_아무것도_하지_않는다() throws Exception {
		// given
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(client.fetchRoundList()).thenAnswer(i -> {
			entered.countDown();
			release.await(5, TimeUnit.SECONDS);
			return list(0);
		});
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<List<Integer>> first = executor.submit(service::syncMissingRounds);
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

			// when
			List<Integer> second = service.syncMissingRounds();

			// then
			assertThat(second).isEmpty();
			verify(client, times(1)).fetchRoundList();
			release.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS)).isEmpty();
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

}
