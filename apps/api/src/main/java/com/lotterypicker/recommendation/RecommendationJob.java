package com.lotterypicker.recommendation;

import com.lotterypicker.notification.MessageFormatter;
import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.service.NotificationService;
import com.lotterypicker.recommendation.service.DailyRecommendationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.scheduling.enabled")
public class RecommendationJob {

	private final DailyRecommendationService service;
	private final NotificationService notifications;
	private final Clock clock;

	@Scheduled(cron = "0 0 17 * * *", zone = "Asia/Seoul")
	public void run() {
		generateAndNotify(service.today());
	}

	/**
	 * 17시에 서버가 꺼져 있었으면 기동 시 그날 추천을 만든다. 지난 날짜는 소급하지 않는다.
	 * 이미 만들었지만 보내지 못한 추천은 판매 중일 때만 다시 보낸다.
	 */
	public void catchUp() {
		LocalDate today = service.today();
		ZonedDateTime now = ZonedDateTime.now(clock);
		boolean exists = service.existsFor(today);
		if (CatchUpPolicy.shouldGenerate(now, exists)) {
			generateAndNotify(today);
		} else if (exists && CatchUpPolicy.salesOpen(now)) {
			service.findSet(today).ifPresent(this::notifyRecommendation);
		}
	}

	private void generateAndNotify(LocalDate date) {
		Optional<RecommendationSet> created;
		try {
			created = service.generateIfAbsent(date);
		} catch (RuntimeException e) {
			notifyFailure(date, e);
			throw e;
		}
		created.or(() -> service.findSet(date)).ifPresent(this::notifyRecommendation);
	}

	private void notifyRecommendation(RecommendationSet set) {
		notifications.sendOnce(NotificationKind.DAILY_RECOMMENDATION, set.date().toString(),
				MessageFormatter.recommendation(set));
	}

	private void notifyFailure(LocalDate date, RuntimeException cause) {
		try {
			notifications.sendOnce(NotificationKind.RECOMMENDATION_FAILED, date.toString(),
					MessageFormatter.recommendationFailed(date));
		} catch (RuntimeException e) {
			cause.addSuppressed(e);
		}
	}

}
