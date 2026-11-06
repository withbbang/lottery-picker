package com.lotterypicker.collector;

import com.lotterypicker.collector.service.CollectorService;
import com.lotterypicker.recommendation.RecommendationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 누락 회차와 판매점을 백그라운드로 수집한다. 전체 적재가 오래 걸려도 기동을 막지 않는다.
 * 그날 추천 보충은 회차 적재 뒤, 오래 걸릴 수 있는 판매점 수집 앞에서 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.scheduling.enabled")
public class CollectorStartup {

	private final CollectorService collectorService;
	private final RecommendationJob recommendationJob;

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void collectOnStartup() {
		try {
			log.info("기동 수집 시작");
			try {
				collectorService.syncMissingRounds();
			} finally {
				// 수집이 실패해도 회차는 달력으로 계산되므로 그날 추천은 만든다.
				catchUpRecommendation();
			}
			collectorService.syncStores();
			log.info("기동 수집 완료");
		} catch (RuntimeException e) {
			log.error("기동 수집 실패, 다음 기동에서 이어서 수집합니다.", e);
		}
	}

	private void catchUpRecommendation() {
		try {
			recommendationJob.catchUp();
		} catch (RuntimeException e) {
			log.error("기동 시 추천 보충 실패", e);
		}
	}

}
