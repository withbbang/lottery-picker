package com.lotterypicker.collector;

import com.lotterypicker.recommendation.RecommendationJob;
import com.lotterypicker.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 누락 회차와 판매점을 백그라운드로 수집하고, 새 회차와 채점 대기 회차를 처리한다.
 * 전체 적재가 오래 걸려도 기동을 막지 않는다. 그날 추천 보충은 회차 적재 뒤, 오래 걸릴 수 있는 판매점 수집 앞에서 한다.
 * 5분 확인이 이미 수집 중이면 수집은 건너뛰고 추천 보충만 한다.
 * 마지막으로 시뮬레이션 결과가 최신 회차를 반영했는지 확인을 요청한다. 수집을 건너뛰었거나 실패해도 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.scheduling.enabled")
public class CollectorStartup {

	private final CollectionLock lock;
	private final CollectionJob collection;
	private final RecommendationJob recommendationJob;
	private final SimulationService simulation;

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void collectOnStartup() {
		if (!lock.tryRun("기동 수집", this::collect)) {
			catchUpRecommendation();
		}
		requestSimulation();
	}

	private void requestSimulation() {
		try {
			simulation.refresh();
		} catch (RuntimeException e) {
			log.error("기동 시 시뮬레이션 확인 요청 실패", e);
		}
	}

	private void collect() {
		try {
			log.info("기동 수집 시작");
			try {
				collection.collectRounds();
			} finally {
				// 수집이 실패해도 회차는 달력으로 계산되므로 그날 추천은 만든다.
				catchUpRecommendation();
			}
			collection.collectStores();
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
