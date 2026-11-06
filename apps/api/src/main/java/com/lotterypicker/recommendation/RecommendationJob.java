package com.lotterypicker.recommendation;

import com.lotterypicker.recommendation.service.DailyRecommendationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.scheduling.enabled")
public class RecommendationJob {

	private final DailyRecommendationService service;
	private final Clock clock;

	@Scheduled(cron = "0 0 17 * * *", zone = "Asia/Seoul")
	public void run() {
		service.generateIfAbsent(service.today());
	}

	/** 17시에 서버가 꺼져 있었으면 기동 시 그날 추천을 만든다. 지난 날짜는 소급하지 않는다. */
	public void catchUp() {
		LocalDate today = service.today();
		if (CatchUpPolicy.shouldGenerate(ZonedDateTime.now(clock), service.existsFor(today))) {
			service.generateIfAbsent(today);
		}
	}

}
