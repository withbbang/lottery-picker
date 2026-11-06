package com.lotterypicker.collector;

import com.lotterypicker.collector.service.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 기동 직후 누락 회차와 판매점을 백그라운드로 수집한다. 전체 적재가 오래 걸려도 기동을 막지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("app.scheduling.enabled")
public class CollectorStartup {

	private final CollectorService collectorService;

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void collectOnStartup() {
		try {
			log.info("기동 수집 시작");
			collectorService.syncMissingRounds();
			collectorService.syncStores();
			log.info("기동 수집 완료");
		} catch (RuntimeException e) {
			log.error("기동 수집 실패, 다음 기동에서 이어서 수집합니다.", e);
		}
	}

}
