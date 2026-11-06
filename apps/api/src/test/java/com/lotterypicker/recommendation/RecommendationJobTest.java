package com.lotterypicker.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.recommendation.service.DailyRecommendationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class RecommendationJobTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final DailyRecommendationService service = mock(DailyRecommendationService.class);

	private RecommendationJob jobAt(String instant, LocalDate today) {
		when(service.today()).thenReturn(today);
		return new RecommendationJob(service, Clock.fixed(Instant.parse(instant), SEOUL));
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
			.withBean(Clock.class, () -> Clock.system(SEOUL));

		// when, then
		runner.withPropertyValues("app.scheduling.enabled=true")
			.run(context -> assertThat(context).hasSingleBean(RecommendationJob.class));
		runner.withPropertyValues("app.scheduling.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(RecommendationJob.class));
	}

}
