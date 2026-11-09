package com.lotterypicker.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.collector.service.CollectorService;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.notification.MessageFormatter;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.result.service.PostDrawService;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.repository.LottoRoundRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

class CollectionJobTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDate EXPECTED_DRAW = LocalDate.of(2026, 11, 7);

	private final CollectorService collector = mock(CollectorService.class);
	private final PostDrawService postDraw = mock(PostDrawService.class);
	private final NotificationService notifications = mock(NotificationService.class);
	private final LottoRoundRepository rounds = mock(LottoRoundRepository.class);
	private final CollectionLock lock = new CollectionLock();

	@BeforeEach
	void setUp() {
		LottoRound latest = LottoRound.builder()
			.roundNo(1248)
			.numbers(new LottoNumbers(List.of(16, 18, 27, 36, 38, 42)))
			.bonus(22)
			.drawDate(LocalDate.of(2026, 10, 31))
			.build();
		when(rounds.findTopByOrderByRoundNoDesc()).thenReturn(Optional.of(latest));
		when(collector.syncMissingRounds()).thenReturn(List.of());
		when(postDraw.pendingRounds()).thenReturn(List.of());
	}

	private CollectionJob jobAt(String seoulDateTime) {
		Clock clock = Clock.fixed(LocalDateTime.parse(seoulDateTime).atZone(SEOUL).toInstant(), SEOUL);
		return new CollectionJob(collector, postDraw, notifications, rounds, lock, clock);
	}

	@Test
	void 새_회차를_처리한다() {
		// given
		when(collector.syncMissingRounds()).thenReturn(List.of(1249));

		// when
		jobAt("2026-11-07T20:40").poll();

		// then
		verify(postDraw).process(1249);
	}

	@Test
	void 수집기_예외는_COLLECTION_ERROR_알림을_보낸다() {
		// given
		CollectorException failure = new CollectorException("형식 오류");
		when(collector.syncMissingRounds()).thenThrow(failure);

		// when, then: 스케줄러 로그에 남도록 예외는 그대로 던진다
		assertThatThrownBy(jobAt("2026-11-07T20:40")::poll).isSameAs(failure);
		verify(notifications).sendOnce(NotificationKind.COLLECTION_ERROR, "2026-11-07",
				MessageFormatter.collectionError(EXPECTED_DRAW));
	}

	@Test
	void HTML_응답_같은_형식_오류여도_결과를_만들지_않는다() {
		// given
		when(collector.syncMissingRounds()).thenThrow(new CollectorUnavailableException("JSON이 아닌 응답"));

		// when
		assertThatThrownBy(jobAt("2026-11-07T20:40")::poll).isInstanceOf(CollectorUnavailableException.class);

		// then
		verify(postDraw, never()).process(anyInt());
	}

	@Test
	void 지연이면_COLLECTION_FAILED_알림을_한번만_보내고_계속_확인한다() {
		// when: 일요일 13시, 14시에 모두 확인한다
		jobAt("2026-11-08T13:00").poll();
		jobAt("2026-11-08T14:00").poll();

		// then: 같은 키라 NotificationService가 한 번만 보낸다
		verify(notifications, times(2)).sendOnce(NotificationKind.COLLECTION_FAILED, "2026-11-07",
				MessageFormatter.collectionFailed(EXPECTED_DRAW));
		verify(collector, times(2)).syncMissingRounds();
	}

	@Test
	void 일요일_12시_전에는_지연_알림을_보내지_않는다() {
		// when
		jobAt("2026-11-08T11:00").poll();

		// then
		verify(collector).syncMissingRounds();
		verify(notifications, never()).sendOnce(eq(NotificationKind.COLLECTION_FAILED), any(), any());
	}

	@Test
	void 여러_회차가_빠졌으면_모두_처리한다() {
		// given: 이번에 받은 두 회차와, 추천은 있는데 결과가 없던 회차
		when(collector.syncMissingRounds()).thenReturn(List.of(1249, 1250));
		when(postDraw.pendingRounds()).thenReturn(List.of(1248, 1249));

		// when
		jobAt("2026-11-14T21:00").poll();

		// then
		InOrder order = inOrder(postDraw);
		order.verify(postDraw).process(1248);
		order.verify(postDraw).process(1249);
		order.verify(postDraw).process(1250);
		verify(postDraw, times(3)).process(anyInt());
	}

	@Test
	void 기동_수집은_시각과_관계없이_수집하고_미처리_회차를_처리한다() {
		// given
		when(collector.syncMissingRounds()).thenReturn(List.of(1249));
		when(postDraw.pendingRounds()).thenReturn(List.of(1247));

		// when: 금요일 낮이라 5분 확인 대상은 아니다
		jobAt("2026-11-13T12:00").collectRounds();

		// then
		verify(postDraw).process(1247);
		verify(postDraw).process(1249);
	}

	@Test
	void 확인_시각이_아니면_수집기를_부르지_않는다() {
		// when
		jobAt("2026-11-08T00:05").poll();
		jobAt("2026-11-06T12:00").poll();

		// then
		verify(collector, never()).syncMissingRounds();
	}

	@Test
	void 확인_시각이_아니어도_매번_보내지_못한_종료_알림을_다시_보낸다() {
		// when
		jobAt("2026-11-06T12:00").poll();

		// then
		verify(postDraw).resendUnsent();
		verify(collector, never()).syncMissingRounds();
	}

	@Test
	void 수집과_처리를_마친_뒤_재발송한다() {
		// given
		when(collector.syncMissingRounds()).thenReturn(List.of(1249));

		// when
		jobAt("2026-11-07T20:40").poll();

		// then
		InOrder order = inOrder(collector, postDraw);
		order.verify(collector).syncMissingRounds();
		order.verify(postDraw).process(1249);
		order.verify(postDraw).resendUnsent();
	}

	@Test
	void 다른_수집이_실행_중이면_이번_실행은_건너뛴다() {
		// given
		CollectionJob job = jobAt("2026-11-07T20:40");

		// when: 기동 수집이 잠금을 잡고 있는 동안 5분 확인이 돈다
		boolean ran = lock.tryRun("기동 수집", () -> {
			job.poll();
			job.syncStoresDaily();
		});

		// then
		assertThat(ran).isTrue();
		verify(collector, never()).syncMissingRounds();
		verify(collector, never()).syncStores();
		verify(postDraw, never()).resendUnsent();
	}

	@Test
	void 판매점_수집_예외도_COLLECTION_ERROR_알림을_보낸다() {
		// given
		CollectorException failure = new CollectorException("판매점 수집에 실패한 회차: [1248]");
		doThrow(failure).when(collector).syncStores();

		// when, then
		assertThatThrownBy(jobAt("2026-11-09T06:02")::syncStoresDaily).isSameAs(failure);
		verify(notifications).sendOnce(NotificationKind.COLLECTION_ERROR, "2026-11-07",
				MessageFormatter.collectionError(EXPECTED_DRAW));
	}

	@Test
	void 한국_시각_5분마다_확인하고_6시_2분에_판매점을_수집한다() throws Exception {
		// when
		Scheduled poll = CollectionJob.class.getMethod("poll").getAnnotation(Scheduled.class);
		Scheduled stores = CollectionJob.class.getMethod("syncStoresDaily").getAnnotation(Scheduled.class);

		// then
		assertThat(poll.cron()).isEqualTo("0 */5 * * * *");
		assertThat(poll.zone()).isEqualTo("Asia/Seoul");
		assertThat(stores.cron()).isEqualTo("0 2 6 * * *");
		assertThat(stores.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void 판매점_수집은_5분_확인과_같은_시각에_돌지_않는다() throws Exception {
		// given: 둘이 같은 순간에 돌면 잠금을 못 잡은 판매점 수집이 그날 건너뛰어진다
		CronExpression poll = CronExpression
			.parse(CollectionJob.class.getMethod("poll").getAnnotation(Scheduled.class).cron());
		CronExpression stores = CronExpression
			.parse(CollectionJob.class.getMethod("syncStoresDaily").getAnnotation(Scheduled.class).cron());
		LocalDateTime start = LocalDateTime.of(2026, 11, 9, 0, 0);

		// when: 하루 동안의 판매점 수집 시각
		List<LocalDateTime> storeTimes = new ArrayList<>();
		for (LocalDateTime t = stores.next(start); t != null && t.isBefore(start.plusDays(1)); t = stores.next(t)) {
			storeTimes.add(t);
		}

		// then: 판매점 수집 시각 직전 초부터 다음 확인이 그 시각과 겹치지 않는다
		assertThat(storeTimes).isNotEmpty();
		for (LocalDateTime t : storeTimes) {
			assertThat(poll.next(t.minusSeconds(1))).isNotEqualTo(t);
		}
	}

}
