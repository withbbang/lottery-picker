package com.lotterypicker.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.notification.MessageFormatter;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class RecommendationJobTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final DailyRecommendationService service = mock(DailyRecommendationService.class);

	private final NotificationService notifications = mock(NotificationService.class);

	private RecommendationJob jobAt(String instant, LocalDate today) {
		when(service.today()).thenReturn(today);
		return new RecommendationJob(service, notifications, Clock.fixed(Instant.parse(instant), SEOUL));
	}

	private static RecommendationSet setOf(LocalDate date) {
		return new RecommendationSet(date, 1249, List.of(new StoredLine(StrategyType.RECENT_HOT,
				new LottoNumbers(List.of(1, 8, 15, 22, 33, 45)), List.of("근거"), Relaxation.NONE)));
	}

	@Test
	void 생성하면_알림을_보낸다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T08:00:00Z", today);
		when(service.generateIfAbsent(today)).thenReturn(Optional.of(setOf(today)));

		// when
		job.run();

		// then
		verify(notifications).sendOnce(NotificationKind.DAILY_RECOMMENDATION, "2026-11-06",
				MessageFormatter.recommendation(setOf(today)));
	}

	@Test
	void 발송이_실패해도_추천은_유지된다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T08:00:00Z", today);
		when(service.generateIfAbsent(today)).thenReturn(Optional.of(setOf(today)));
		when(notifications.sendOnce(any(), any(), any())).thenReturn(NotificationStatus.FAILED);

		// when, then: 저장은 이미 끝났고 잡도 실패로 끝나지 않는다
		assertThatCode(job::run).doesNotThrowAnyException();
	}

	@Test
	void 이미_생성됐고_미발송이면_다시_보낸다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T09:00:00Z", today);
		when(service.existsFor(today)).thenReturn(true);
		when(service.findSet(today)).thenReturn(Optional.of(setOf(today)));

		// when
		job.catchUp();

		// then
		verify(notifications).sendOnce(NotificationKind.DAILY_RECOMMENDATION, "2026-11-06",
				MessageFormatter.recommendation(setOf(today)));
	}

	@Test
	void 스케줄_실행도_이미_생성된_추천이_미발송이면_보낸다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T08:00:00Z", today);
		when(service.generateIfAbsent(today)).thenReturn(Optional.empty());
		when(service.findSet(today)).thenReturn(Optional.of(setOf(today)));

		// when
		job.run();

		// then
		verify(notifications).sendOnce(NotificationKind.DAILY_RECOMMENDATION, "2026-11-06",
				MessageFormatter.recommendation(setOf(today)));
	}

	@Test
	void 판매_마감_후_기동이면_미발송_추천을_보내지_않는다() {
		// given
		LocalDate saturday = LocalDate.of(2026, 11, 7);
		RecommendationJob job = jobAt("2026-11-07T11:00:00Z", saturday);
		when(service.existsFor(saturday)).thenReturn(true);
		when(service.findSet(saturday)).thenReturn(Optional.of(setOf(saturday)));

		// when
		job.catchUp();

		// then
		verify(notifications, never()).sendOnce(any(), any(), any());
	}

	@Test
	void 추천_생성_실패는_RECOMMENDATION_FAILED_알림을_보낸다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T08:00:00Z", today);
		IllegalStateException failure = new IllegalStateException("engine");
		when(service.generateIfAbsent(today)).thenThrow(failure);

		// when, then: 스케줄러 로그에 남도록 예외는 그대로 던진다
		assertThatThrownBy(job::run).isSameAs(failure);
		verify(notifications).sendOnce(NotificationKind.RECOMMENDATION_FAILED, "2026-11-06",
				MessageFormatter.recommendationFailed(today));
	}

	@Test
	void 실패_알림_오류가_원래_예외를_가리지_않는다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T08:00:00Z", today);
		IllegalStateException failure = new IllegalStateException("engine");
		when(service.generateIfAbsent(today)).thenThrow(failure);
		when(notifications.sendOnce(any(), any(), any())).thenThrow(new IllegalStateException("db"));

		// when, then
		assertThatThrownBy(job::run).isSameAs(failure);
	}

	@Test
	void 오늘_날짜로_생성한다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T08:00:00Z", today);

		// when
		job.run();

		// then
		verify(service).generateIfAbsent(today);
	}

	@Test
	void 매일_한국_시각_17시에_돈다() throws Exception {
		// when
		Scheduled scheduled = RecommendationJob.class.getMethod("run").getAnnotation(Scheduled.class);

		// then
		assertThat(scheduled.cron()).isEqualTo("0 0 17 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void 기동_보충은_17시가_지났고_추천이_없으면_생성한다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T09:00:00Z", today);
		when(service.existsFor(today)).thenReturn(false);

		// when
		job.catchUp();

		// then
		verify(service).generateIfAbsent(today);
	}

	@Test
	void 기동_보충은_이미_추천이_있으면_생성하지_않는다() {
		// given
		LocalDate today = LocalDate.of(2026, 11, 6);
		RecommendationJob job = jobAt("2026-11-06T09:00:00Z", today);
		when(service.existsFor(today)).thenReturn(true);

		// when
		job.catchUp();

		// then
		verify(service, never()).generateIfAbsent(any());
	}

	@Test
	void 기동_보충은_토요일_판매_마감_후에는_생성하지_않는다() {
		// given
		LocalDate saturday = LocalDate.of(2026, 11, 7);
		RecommendationJob job = jobAt("2026-11-07T11:00:00Z", saturday);
		when(service.existsFor(saturday)).thenReturn(false);

		// when
		job.catchUp();

		// then
		verify(service, never()).generateIfAbsent(any());
	}

	@Test
	void 스케줄링이_켜져_있을_때만_등록한다() {
		// given
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RecommendationJob.class)
			.withBean(DailyRecommendationService.class, () -> service)
			.withBean(NotificationService.class, () -> notifications)
			.withBean(Clock.class, () -> Clock.system(SEOUL));

		// when, then
		runner.withPropertyValues("app.scheduling.enabled=true")
			.run(context -> assertThat(context).hasSingleBean(RecommendationJob.class));
		runner.withPropertyValues("app.scheduling.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(RecommendationJob.class));
	}

}
